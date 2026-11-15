package com.fotofetch.photo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PhotoRecoveryScheduler {

    private final PhotoRepository photoRepository;
    private final PhotoProcessingService photoProcessingService;

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
     * 4. When Lambda mode is enabled, does not interfere with PENDING/PROCESSING thumbnails owned by Lambda.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void recoverStuckPhotos() {
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
}
