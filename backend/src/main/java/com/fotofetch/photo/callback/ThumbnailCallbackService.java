package com.fotofetch.photo.callback;

import com.fotofetch.common.BadRequestException;
import com.fotofetch.photo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbnailCallbackService {

    private final PhotoRepository photoRepository;
    private final PhotoProcessingService photoProcessingService;
    private final ProcessedCallbackRepository processedCallbackRepository;

    @Value("${fotofetch.processing.max-retries:3}")
    private int maxRetries;

    @Value("${fotofetch.processing.initial-backoff-seconds:30}")
    private int initialBackoffSeconds;

    @Value("${fotofetch.processing.max-backoff-seconds:900}")
    private int maxBackoffSeconds;

    @Transactional
    public ThumbnailCallbackResponse handleCallback(ThumbnailCallbackRequest request) {
        if (request.getCallbackId() == null || request.getCallbackId().isBlank()) {
            log.warn("Thumbnail callback rejected: callbackId is required in callback payload (photoId: {}, eventId: {})",
                    request.getPhotoId(), request.getEventId());
            throw new BadRequestException("callbackId is required in callback payload");
        }

        String callbackId = request.getCallbackId().trim();

        // 1. Check for duplicate/replayed callback ID
        if (processedCallbackRepository.existsById(callbackId)) {
            log.warn("Thumbnail callback rejected: Duplicate callback ID {} has already been processed (photoId: {}, eventId: {})",
                    callbackId, request.getPhotoId(), request.getEventId());
            throw new BadRequestException("Duplicate callback rejected: callbackId " + callbackId + " has already been processed");
        }

        if (request.getPhotoId() == null) {
            log.warn("Thumbnail callback rejected: photoId is required in callback payload (callbackId: {}, eventId: {})",
                    callbackId, request.getEventId());
            throw new BadRequestException("photoId is required in callback payload");
        }

        Optional<Photo> photoOpt = photoRepository.findById(request.getPhotoId());
        if (photoOpt.isEmpty()) {
            // Deleted photo acknowledged safely without recreating its database record
            log.warn("Thumbnail callback received for deleted or non-existent photo: {} (callbackId: {}, eventId: {}). Acknowledging without recreating record.",
                    request.getPhotoId(), callbackId, request.getEventId());
            return ThumbnailCallbackResponse.builder()
                    .photoId(request.getPhotoId())
                    .status("NOT_FOUND_ACKNOWLEDGED")
                    .message("Photo record not found or already deleted; callback acknowledged.")
                    .indexingScheduled(false)
                    .build();
        }

        Photo photo = photoOpt.get();

        // 2. Validate Event ID matches
        if (request.getEventId() == null || !request.getEventId().equals(photo.getEvent().getId())) {
            String errorMsg = String.format(
                    "Event ID mismatch: photo belongs to event %s, but callback reported %s",
                    photo.getEvent().getId(), request.getEventId());
            log.warn("Thumbnail callback rejected: {} (callbackId: {}, photoId: {})", errorMsg, callbackId, photo.getId());
            throw new BadRequestException(errorMsg);
        }

        // 3. Validate Original S3 Key matches database
        if (request.getOriginalS3Key() == null || !request.getOriginalS3Key().equals(photo.getOriginalS3Key())) {
            String errorMsg = String.format(
                    "Original S3 key mismatch: expected %s, but callback reported %s",
                    photo.getOriginalS3Key(), request.getOriginalS3Key());
            log.warn("Thumbnail callback rejected: {} (callbackId: {}, photoId: {})", errorMsg, callbackId, photo.getId());
            throw new BadRequestException(errorMsg);
        }

        // 4. Validate callback status is valid
        String statusStr = request.getStatus() != null ? request.getStatus().trim().toUpperCase() : "";
        if (!statusStr.equals("COMPLETED") && !statusStr.equals("FAILED")) {
            String errorMsg = "Invalid callback status: " + request.getStatus() + ". Allowed: COMPLETED, FAILED";
            log.warn("Thumbnail callback rejected: {} (callbackId: {}, photoId: {})", errorMsg, callbackId, photo.getId());
            throw new BadRequestException(errorMsg);
        }

        // 5. If COMPLETED, validate thumbnail S3 key format
        String expectedThumbnailKey = null;
        if (statusStr.equals("COMPLETED")) {
            expectedThumbnailKey = String.format("events/%s/thumbnails/%s.jpg", photo.getEvent().getId(), photo.getId());
            if (request.getThumbnailS3Key() == null || !expectedThumbnailKey.equals(request.getThumbnailS3Key().trim())) {
                String errorMsg = String.format(
                        "Invalid thumbnail S3 key. Expected: %s, but callback reported: %s",
                        expectedThumbnailKey, request.getThumbnailS3Key());
                log.warn("Thumbnail callback rejected: {} (callbackId: {}, photoId: {})", errorMsg, callbackId, photo.getId());
                throw new BadRequestException(errorMsg);
            }
        }

        // 6. Record callback ID with unique constraint inside transaction
        ProcessedCallback processedCallback = ProcessedCallback.builder()
                .callbackId(callbackId)
                .photoId(photo.getId())
                .status(statusStr)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            processedCallbackRepository.saveAndFlush(processedCallback);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate callback rejected by DB constraint for callbackId: {} (photoId: {})", callbackId, photo.getId());
            throw new BadRequestException("Duplicate callback rejected: callbackId " + callbackId + " has already been processed");
        }

        // 7. Handle COMPLETED status
        if (statusStr.equals("COMPLETED")) {

            // Idempotency check: Repeated COMPLETED callbacks must not create duplicate jobs
            boolean wasAlreadyCompleted = photo.getThumbnailStatus() == ThumbnailStatus.COMPLETED && photo.getThumbnailS3Key() != null;
            if (wasAlreadyCompleted) {
                log.info("Photo {} thumbnail is already COMPLETED. Duplicate callback acknowledged.", photo.getId());
                boolean shouldSchedule = photo.getUploadStatus() == UploadStatus.UPLOADED && photo.getIndexingStatus() == IndexingStatus.PENDING;
                if (shouldSchedule) {
                    scheduleRekognitionAsync(photo);
                }
                return ThumbnailCallbackResponse.builder()
                        .photoId(photo.getId())
                        .status("ALREADY_COMPLETED")
                        .message("Thumbnail was already completed; duplicate callback acknowledged.")
                        .indexingScheduled(shouldSchedule)
                        .build();
            }

            // Persist thumbnail completion
            photo.setThumbnailS3Key(expectedThumbnailKey);
            photo.setThumbnailStatus(ThumbnailStatus.COMPLETED);
            photo.setLastProcessingError(null);
            photo.setProcessingUpdatedAt(LocalDateTime.now());
            photoRepository.saveAndFlush(photo);

            // Handle race condition: only schedule Rekognition when uploadStatus is UPLOADED and indexingStatus is PENDING
            boolean canScheduleIndexing = photo.getUploadStatus() == UploadStatus.UPLOADED && photo.getIndexingStatus() == IndexingStatus.PENDING;
            if (canScheduleIndexing) {
                scheduleRekognitionAsync(photo);
                log.info("Thumbnail COMPLETED and upload is UPLOADED for photo {}. Rekognition indexing scheduled.", photo.getId());
            } else {
                log.info("Thumbnail COMPLETED saved for photo {}. Rekognition indexing deferred (uploadStatus: {}, indexingStatus: {}).",
                        photo.getId(), photo.getUploadStatus(), photo.getIndexingStatus());
            }

            return ThumbnailCallbackResponse.builder()
                    .photoId(photo.getId())
                    .status("COMPLETED")
                    .message("Thumbnail generation COMPLETED recorded successfully.")
                    .indexingScheduled(canScheduleIndexing)
                    .build();
        }

        // 5. Handle FAILED status
        String sanitizedError = sanitizeErrorMessage(request.getErrorMessage());
        photo.setThumbnailStatus(ThumbnailStatus.FAILED);
        photo.setLastProcessingError(sanitizedError);
        photo.setProcessingUpdatedAt(LocalDateTime.now());

        // Manage retry backoff
        int attempts = (photo.getProcessingAttempts() != null ? photo.getProcessingAttempts() : 0) + 1;
        photo.setProcessingAttempts(attempts);
        if (attempts < maxRetries) {
            long backoffSeconds = Math.min((long) initialBackoffSeconds * (1L << (attempts - 1)), maxBackoffSeconds);
            photo.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        } else {
            photo.setNextRetryAt(null);
        }

        photoRepository.saveAndFlush(photo);
        log.warn("Thumbnail generation FAILED via Lambda for photo {}. Error: {}", photo.getId(), sanitizedError);

        return ThumbnailCallbackResponse.builder()
                .photoId(photo.getId())
                .status("FAILED")
                .message("Thumbnail failure recorded: " + sanitizedError)
                .indexingScheduled(false)
                .build();
    }

    private void scheduleRekognitionAsync(Photo photo) {
        UUID photoId = photo.getId();
        String collectionId = photo.getEvent().getRekognitionCollectionId();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    photoProcessingService.processPhotoAsync(photoId, collectionId);
                }
            });
        } else {
            photoProcessingService.processPhotoAsync(photoId, collectionId);
        }
    }

    private String sanitizeErrorMessage(String rawError) {
        if (rawError == null || rawError.isBlank()) {
            return "Thumbnail generation failed via Lambda";
        }
        // Remove control characters (except common whitespace), trim, and clamp to 1000 characters
        String sanitized = rawError.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
        }
        return sanitized.isEmpty() ? "Thumbnail generation failed via Lambda" : sanitized;
    }
}
