package com.fotofetch.photo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Self-healing background recovery.
     * Recovers any photos that were marked UPLOADED but whose background processing
     * was interrupted by a server restart or memory event on Render.
     */
    @Scheduled(fixedDelay = 180000, initialDelay = 60000)
    public void recoverStuckPhotos() {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);

        List<Photo> stuckPhotos = photoRepository.findByUploadStatusAndThumbnailStatusIn(
                UploadStatus.UPLOADED,
                List.of(ThumbnailStatus.PENDING, ThumbnailStatus.PROCESSING)
        );

        if (stuckPhotos.isEmpty()) {
            return;
        }

        log.info("PhotoRecoveryScheduler: Found {} potentially stuck photos. Checking timestamps...", stuckPhotos.size());

        for (Photo photo : stuckPhotos) {
            if (photo.getCreatedAt() != null && photo.getCreatedAt().isBefore(fiveMinutesAgo)) {
                String collectionId = photo.getEvent().getRekognitionCollectionId();
                log.info("PhotoRecoveryScheduler: Re-submitting stuck photo {} to processing pipeline", photo.getId());
                photoProcessingService.processPhotoAsync(photo.getId(), collectionId);
            }
        }
    }
}
