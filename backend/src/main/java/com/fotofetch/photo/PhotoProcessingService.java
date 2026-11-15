package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.storage.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoProcessingService {

    private final PhotoRepository photoRepository;
    private final S3StorageService s3StorageService;
    private final ThumbnailService thumbnailService;
    private final RekognitionService rekognitionService;

    @Value("${fotofetch.processing.max-retries:3}")
    private int maxRetries;

    @Value("${fotofetch.processing.initial-backoff-seconds:30}")
    private int initialBackoffSeconds;

    @Value("${fotofetch.processing.max-backoff-seconds:900}")
    private int maxBackoffSeconds;

    @Async("photoProcessingExecutor")
    public void processPhotoAsync(UUID photoId, String collectionId) {
        processPhoto(photoId, collectionId);
    }

    public void processPhoto(UUID photoId, String collectionId) {
        log.info("Starting processing for photo: {} in collection: {}", photoId, collectionId);

        Photo photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            log.error("Photo {} not found in database for background processing", photoId);
            return;
        }

        // Idempotency check: Skip if photo is already fully processed
        boolean thumbnailDone = photo.getThumbnailStatus() == ThumbnailStatus.COMPLETED && photo.getThumbnailS3Key() != null;
        boolean indexingDone = photo.getIndexingStatus() == IndexingStatus.COMPLETED || photo.getIndexingStatus() == IndexingStatus.NO_FACES_DETECTED;

        if (thumbnailDone && indexingDone) {
            log.info("Photo {} is already fully processed (thumbnail and indexing complete). Skipping duplicate processing.", photoId);
            return;
        }

        // Check retry limits
        int attempts = photo.getProcessingAttempts() != null ? photo.getProcessingAttempts() : 0;
        if (attempts >= maxRetries) {
            log.warn("Photo {} has reached maximum processing attempts ({}/{}). Skipping.", photoId, attempts, maxRetries);
            return;
        }

        // Atomic job claim to prevent concurrent worker execution
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime stuckBefore = now.minusMinutes(5);
        int claimed = photoRepository.claimPhotoForProcessing(photoId, now, stuckBefore);
        if (claimed == 0) {
            log.info("Photo {} is currently being processed by another worker. Skipping.", photoId);
            return;
        }

        photo = photoRepository.findById(photoId).orElseThrow();

        // 1. Thumbnail generation (only if not already completed)
        boolean thumbnailSuccess = thumbnailDone;
        if (!thumbnailDone) {
            try {
                photo.setThumbnailStatus(ThumbnailStatus.PROCESSING);
                photo.setProcessingUpdatedAt(LocalDateTime.now());
                photo = photoRepository.saveAndFlush(photo);

                software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> s3Stream = null;
                try {
                    s3Stream = s3StorageService.getObjectStream(photo.getOriginalS3Key());
                } catch (Exception ignored) {
                }

                byte[] thumbnailBytes;
                if (s3Stream != null) {
                    try (software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> stream = s3Stream) {
                        thumbnailBytes = thumbnailService.generateThumbnail(stream);
                    } catch (Exception streamEx) {
                        log.warn("Streaming thumbnail generation failed for {}, falling back to buffered bytes: {}", photoId, streamEx.getMessage());
                        byte[] originalBytes = s3StorageService.getObjectBytes(photo.getOriginalS3Key());
                        thumbnailBytes = thumbnailService.generateThumbnail(originalBytes);
                    }
                } else {
                    byte[] originalBytes = s3StorageService.getObjectBytes(photo.getOriginalS3Key());
                    thumbnailBytes = thumbnailService.generateThumbnail(originalBytes);
                }

                if (thumbnailBytes == null) {
                    throw new IllegalStateException("Thumbnail generation produced null byte array");
                }

                String thumbnailKey = String.format("events/%s/thumbnails/%s.jpg", photo.getEvent().getId(), photoId);
                s3StorageService.uploadThumbnail(thumbnailKey, thumbnailBytes);

                photo.setThumbnailS3Key(thumbnailKey);
                photo.setThumbnailStatus(ThumbnailStatus.COMPLETED);
                photo.setProcessingUpdatedAt(LocalDateTime.now());
                photo = photoRepository.saveAndFlush(photo);
                thumbnailSuccess = true;
                log.info("Thumbnail generated and uploaded to S3 for photo: {}", photoId);
            } catch (Exception ex) {
                log.error("Failed to generate thumbnail for photo: {}", photoId, ex);
                handleProcessingFailure(photo, "Thumbnail generation failed: " + ex.getMessage(), false);
                return;
            }
        }

        // Verify thumbnail is ready before face indexing
        if (!thumbnailSuccess || photo.getThumbnailS3Key() == null) {
            log.warn("Skipping face indexing for photo {} because thumbnail generation failed or thumbnail key is missing.", photoId);
            return;
        }

        // 2. AWS Rekognition face indexing USING THUMBNAIL S3 KEY (only if not already completed)
        if (!indexingDone) {
            try {
                photo.setIndexingStatus(IndexingStatus.INDEXING);
                photo.setProcessingUpdatedAt(LocalDateTime.now());
                photo = photoRepository.saveAndFlush(photo);

                // Prevent duplicate faces in Rekognition collection by cleaning up prior face IDs if retrying
                try {
                    rekognitionService.deleteFacesForPhotos(collectionId, List.of(photo.getId()));
                } catch (Exception cleanupEx) {
                    log.debug("Non-fatal face cleanup check before indexing: {}", cleanupEx.getMessage());
                }

                log.info("Indexing faces for photo {} using THUMBNAIL S3 key: {}", photo.getId(), photo.getThumbnailS3Key());
                int facesCount = rekognitionService.indexPhotoFaces(collectionId, photo.getId(), photo.getThumbnailS3Key());
                photo.setFacesFound(facesCount);
                if (facesCount > 0) {
                    photo.setIndexingStatus(IndexingStatus.COMPLETED);
                } else {
                    photo.setIndexingStatus(IndexingStatus.NO_FACES_DETECTED);
                }
                photo.setNextRetryAt(null);
                photo.setLastProcessingError(null);
                photo.setProcessingUpdatedAt(LocalDateTime.now());
                photoRepository.saveAndFlush(photo);
                log.info("Rekognition indexed {} face(s) using thumbnail for photo: {}", facesCount, photoId);
            } catch (Exception ex) {
                log.error("Failed to index faces using thumbnail for photo: {}", photoId, ex);
                handleProcessingFailure(photo, "Rekognition indexing failed: " + ex.getMessage(), true);
            }
        }
    }

    private void handleProcessingFailure(Photo photo, String errorMsg, boolean isIndexing) {
        int attempts = photo.getProcessingAttempts() != null ? photo.getProcessingAttempts() : 1;
        String sanitizedError = errorMsg != null && errorMsg.length() > 1000 ? errorMsg.substring(0, 1000) : errorMsg;
        photo.setLastProcessingError(sanitizedError);
        photo.setProcessingUpdatedAt(LocalDateTime.now());

        if (attempts >= maxRetries) {
            log.error("Photo {} exceeded maximum retry count ({}/{}). Marking permanently FAILED.",
                    photo.getId(), attempts, maxRetries);
            if (!isIndexing) {
                photo.setThumbnailStatus(ThumbnailStatus.FAILED);
            }
            photo.setIndexingStatus(IndexingStatus.FAILED);
            photo.setNextRetryAt(null);
        } else {
            long backoffSeconds = Math.min((long) initialBackoffSeconds * (1L << (attempts - 1)), maxBackoffSeconds);
            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(backoffSeconds);
            photo.setNextRetryAt(nextRetry);
            log.warn("Scheduling retry #{} for photo {} at {} (backoff: {}s)",
                    attempts + 1, photo.getId(), nextRetry, backoffSeconds);
            if (!isIndexing) {
                photo.setThumbnailStatus(ThumbnailStatus.FAILED);
            }
            photo.setIndexingStatus(IndexingStatus.FAILED);
        }

        photoRepository.saveAndFlush(photo);
    }
}
