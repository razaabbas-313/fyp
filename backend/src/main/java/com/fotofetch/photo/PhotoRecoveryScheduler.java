package com.fotofetch.photo;

import com.fotofetch.storage.S3StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PhotoRecoveryScheduler {

    private final PhotoRepository photoRepository;
    private final PhotoProcessingService photoProcessingService;
    private final S3StorageService s3StorageService;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Value("${fotofetch.processing.max-retries:3}")
    private int maxRetries;

    @Value("${fotofetch.thumbnail.lambda-enabled:true}")
    private boolean lambdaThumbnailEnabled = true;

    public void setLambdaThumbnailEnabled(boolean lambdaThumbnailEnabled) {
        this.lambdaThumbnailEnabled = lambdaThumbnailEnabled;
    }

    public boolean isLambdaThumbnailEnabled() {
        return lambdaThumbnailEnabled;
    }

    /**
     * Self-healing background recovery.
     * Recovers:
     * 1. Photos interrupted during a server restart or memory crash (stuck in PROCESSING or INDEXING > 5m)
     * 2. Photos whose thumbnail generation or Rekognition indexing failed and are due for their next retry
     * 3. Respects nextRetryAt and processingAttempts < maxRetries
     * 4. Recovers orphaned photos in PENDING status (> 3m) when Lambda mode is enabled by verifying S3 thumbnail existence.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void recoverStuckPhotos() {
        recoverStandardStuckPhotos();
        if (lambdaThumbnailEnabled) {
            recoverOrphanedPendingPhotos();
        }
    }

    public void recoverStandardStuckPhotos() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime stuckBefore = now.minusMinutes(5);

        List<Photo> recoverablePhotos = photoRepository.findRecoverablePhotos(now, stuckBefore, maxRetries, lambdaThumbnailEnabled);

        if (recoverablePhotos.isEmpty()) {
            return;
        }

        log.info("PhotoRecoveryScheduler: Found {} recoverable photo(s) due for processing/retry", recoverablePhotos.size());

        for (Photo photo : recoverablePhotos) {
            String collectionId = photo.getEvent().getRekognitionCollectionId();
            int nextAttempt = (photo.getProcessingAttempts() != null ? photo.getProcessingAttempts() : 0) + 1;
            log.info("PhotoRecoveryScheduler: Submitting photo {} (attempt #{}) to pipeline", photo.getId(), nextAttempt);
            photoProcessingService.processPhotoAsync(photo.getId(), collectionId);
        }
    }

    public void recoverOrphanedPendingPhotos() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime orphanedBefore = now.minusMinutes(3);

        List<Photo> orphanedPhotos = photoRepository.findOrphanedPendingPhotos(now, orphanedBefore, maxRetries);
        if (orphanedPhotos.isEmpty()) {
            return;
        }

        log.info("PhotoRecoveryScheduler: Found {} orphaned photo(s) in PENDING thumbnail status (>3m)", orphanedPhotos.size());

        for (Photo photo : orphanedPhotos) {
            String expectedThumbnailKey = String.format("events/%s/thumbnails/%s.jpg", photo.getEvent().getId(), photo.getId());
            String collectionId = photo.getEvent().getRekognitionCollectionId();

            // Check whether the expected thumbnail actually exists in S3 before marking COMPLETED
            Optional<software.amazon.awssdk.services.s3.model.HeadObjectResponse> thumbOpt =
                    s3StorageService.getObjectMetadata(expectedThumbnailKey);

            if (thumbOpt.isPresent()) {
                log.info("PhotoRecoveryScheduler: Verified thumbnail exists in S3 at {} for photo {}. Reconciling DB state.",
                        expectedThumbnailKey, photo.getId());

                transactionTemplate.execute(status -> {
                    Optional<Photo> lockedOpt = photoRepository.findByIdForUpdate(photo.getId());
                    if (lockedOpt.isEmpty()) {
                        return null;
                    }
                    Photo p = lockedOpt.get();
                    if (entityManager.contains(p)) {
                        entityManager.refresh(p, LockModeType.PESSIMISTIC_WRITE);
                    }

                    if (p.getThumbnailStatus() != ThumbnailStatus.COMPLETED) {
                        p.setThumbnailS3Key(expectedThumbnailKey);
                        p.setThumbnailStatus(ThumbnailStatus.COMPLETED);
                        p.setLastProcessingError(null);
                        p.setProcessingUpdatedAt(LocalDateTime.now());
                        photoRepository.saveAndFlush(p);
                        log.info("PhotoRecoveryScheduler: Successfully reconciled thumbnailStatus to COMPLETED for photo {}", p.getId());
                    }

                    if (p.getUploadStatus() == UploadStatus.UPLOADED && p.getIndexingStatus() == IndexingStatus.PENDING) {
                        log.info("PhotoRecoveryScheduler: Scheduling Rekognition indexing for reconciled photo {}", p.getId());
                        if (TransactionSynchronizationManager.isActualTransactionActive()) {
                            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    photoProcessingService.processPhotoAsync(p.getId(), collectionId);
                                }
                            });
                        } else {
                            photoProcessingService.processPhotoAsync(p.getId(), collectionId);
                        }
                    }
                    return null;
                });
            } else {
                // If thumbnail does NOT exist in S3, do not mark COMPLETED.
                // Lambda thumbnail processing is still pending or delayed.
                // Existing safe behavior is preserved: wait for Lambda callback or explicit FAILED status.
                log.debug("PhotoRecoveryScheduler: Thumbnail not yet present in S3 for photo {}. Awaiting Lambda.",
                        photo.getId());
            }
        }
    }
}
