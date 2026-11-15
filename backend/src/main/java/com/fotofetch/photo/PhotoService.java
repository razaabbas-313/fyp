package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.common.BadRequestException;
import com.fotofetch.common.ResourceNotFoundException;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventService;
import com.fotofetch.photo.dto.*;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final EventService eventService;
    private final S3StorageService s3StorageService;
    private final RekognitionService rekognitionService;
    private final PhotoProcessingService photoProcessingService;
    private final com.fotofetch.guest.session.GuestSessionService guestSessionService;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Value("${fotofetch.thumbnail.lambda-enabled:true}")
    private boolean lambdaThumbnailEnabled = true;

    public void setLambdaThumbnailEnabled(boolean lambdaThumbnailEnabled) {
        this.lambdaThumbnailEnabled = lambdaThumbnailEnabled;
    }
    public boolean isLambdaThumbnailEnabled() {
        return lambdaThumbnailEnabled;
    }

    @Transactional
    public PresignResponse createPresignedUploads(UUID eventId, PresignRequest request, User user) {
        Event event = eventService.getEventEntityByIdAndUser(eventId, user);

        List<PresignResponse.PresignedFile> uploadItems = new ArrayList<>();
        List<Photo> photosToSave = new ArrayList<>();

        for (PresignRequest.FileItem fileItem : request.getFiles()) {
            UUID photoId = UUID.randomUUID();
            String cleanName = sanitizeFilename(fileItem.getFilename());
            String originalKey = String.format("events/%s/originals/%s-%s", eventId, photoId, cleanName);

            String uploadUrl = s3StorageService.generatePresignedUploadUrl(originalKey, fileItem.getContentType());

            Photo photo = Photo.builder()
                    .id(photoId)
                    .event(event)
                    .originalS3Key(originalKey)
                    .originalFilename(cleanName)
                    .contentType(fileItem.getContentType())
                    .fileSize(fileItem.getFileSize())
                    .uploadStatus(UploadStatus.PENDING)
                    .thumbnailStatus(ThumbnailStatus.PENDING)
                    .indexingStatus(IndexingStatus.PENDING)
                    .facesFound(0)
                    .isNew(true)
                    .build();

            photosToSave.add(photo);
            event.getPhotos().add(photo);

            uploadItems.add(PresignResponse.PresignedFile.builder()
                    .photoId(photoId)
                    .originalFilename(cleanName)
                    .uploadUrl(uploadUrl)
                    .s3Key(originalKey)
                    .build());
        }

        photoRepository.saveAll(photosToSave);
        photoRepository.flush();
        log.info("Persisted and flushed {} photo records with assigned UUIDs for event: {}", photosToSave.size(), eventId);

        return PresignResponse.builder()
                .uploads(uploadItems)
                .build();
    }

    public void confirmUploads(UUID eventId, ConfirmUploadRequest request, User user) {
        Event event = eventService.getEventEntityByIdAndUser(eventId, user);
        String collectionId = event.getRekognitionCollectionId();

        int updatedCount = 0;

        for (UUID photoId : request.getPhotoIds()) {
            log.info("Looking for photo: {} in event: {}", photoId, eventId);

            // 1. Read-only pre-validation to fetch S3 key without holding row locks
            Optional<Photo> photoOptional = photoRepository.findByIdAndEventId(photoId, eventId);

            if (photoOptional.isEmpty()) {
                log.error("PHOTO NOT FOUND: {} for event: {}", photoId, eventId);
                continue;
            }

            Photo readOnlyPhoto = photoOptional.get();

            // 2. Perform S3 HeadObject operation OUTSIDE and BEFORE acquiring database row locks
            Optional<software.amazon.awssdk.services.s3.model.HeadObjectResponse> headOpt =
                    s3StorageService.getObjectMetadata(readOnlyPhoto.getOriginalS3Key());

            final boolean s3Exists = headOpt.isPresent();
            final long actualSize = s3Exists && headOpt.get().contentLength() != null ? headOpt.get().contentLength() : 0L;
            final boolean sizeExceeded = s3Exists && actualSize > 26214400L;

            // 3. Acquire photo row lock (PESSIMISTIC_WRITE) and update state within a short, isolated transaction
            Boolean updated = transactionTemplate.execute(status -> {
                Optional<Photo> lockedOpt = photoRepository.findByIdForUpdate(photoId);
                if (lockedOpt.isEmpty()) {
                    log.error("PHOTO NOT FOUND during lock acquisition: {} for event: {}", photoId, eventId);
                    return false;
                }

                Photo photo = lockedOpt.get();
                if (entityManager.contains(photo)) {
                    entityManager.refresh(photo, LockModeType.PESSIMISTIC_WRITE);
                }

                // Idempotency: Skip already UPLOADED photos, but ensure Rekognition is triggered if thumbnail is ready
                if (photo.getUploadStatus() == UploadStatus.UPLOADED) {
                    log.info("Photo {} already confirmed and UPLOADED, skipping duplicate confirm", photoId);
                    if (lambdaThumbnailEnabled && photo.getThumbnailStatus() == ThumbnailStatus.COMPLETED
                            && photo.getIndexingStatus() == IndexingStatus.PENDING) {
                        log.info("Photo {} is already UPLOADED and thumbnail is COMPLETED. Scheduling missed Rekognition indexing.", photoId);
                        scheduleRekognitionAsync(photo.getId(), collectionId);
                    }
                    return false;
                }

                if (!s3Exists) {
                    log.warn("Photo {} not found in S3 at key: {}, marking FAILED", photoId, photo.getOriginalS3Key());
                    photo.setUploadStatus(UploadStatus.FAILED);
                    photo.setThumbnailStatus(ThumbnailStatus.FAILED);
                    photo.setIndexingStatus(IndexingStatus.FAILED);
                    photo.setLastProcessingError("Object not found in S3");
                    photo.setProcessingUpdatedAt(LocalDateTime.now());
                    photoRepository.saveAndFlush(photo);
                    return false;
                }

                if (sizeExceeded) {
                    log.error("Photo {} in S3 exceeds 25MB limit (actual: {} bytes). Marking FAILED.", photoId, actualSize);
                    photo.setUploadStatus(UploadStatus.FAILED);
                    photo.setThumbnailStatus(ThumbnailStatus.FAILED);
                    photo.setIndexingStatus(IndexingStatus.FAILED);
                    photo.setLastProcessingError("File size (" + actualSize + " bytes) exceeds maximum allowed limit of 25MB");
                    photo.setProcessingUpdatedAt(LocalDateTime.now());
                    photoRepository.saveAndFlush(photo);
                    return false;
                }

                photo.setFileSize(actualSize);
                photo.setUploadStatus(UploadStatus.UPLOADED);
                photo.setProcessingUpdatedAt(LocalDateTime.now());
                photoRepository.saveAndFlush(photo);

                log.info("Upload status updated to UPLOADED under lock for photo: {} (thumbnailStatus: {}, indexingStatus: {})",
                        photoId, photo.getThumbnailStatus(), photo.getIndexingStatus());

                // Evaluate whether Rekognition indexing can be scheduled under lock
                if (!lambdaThumbnailEnabled) {
                    // When Lambda is disabled, Render handles both thumbnail generation and indexing locally
                    scheduleLocalProcessingAsync(photo.getId(), collectionId);
                } else {
                    // When Lambda is enabled:
                    // Only schedule if thumbnail callback already completed prior to confirm (or while confirm was waiting)
                    if (photo.getThumbnailStatus() == ThumbnailStatus.COMPLETED && photo.getIndexingStatus() == IndexingStatus.PENDING) {
                        log.info("Photo {} thumbnail already completed by Lambda. Scheduling Rekognition indexing.", photoId);
                        scheduleRekognitionAsync(photo.getId(), collectionId);
                    } else {
                        log.info("Photo {} confirmed (UPLOADED). Awaiting Lambda thumbnail callback before Rekognition indexing (thumbnailStatus: {}).",
                                photoId, photo.getThumbnailStatus());
                    }
                }

                return true;
            });

            if (Boolean.TRUE.equals(updated)) {
                updatedCount++;
            }
        }

        log.info(
                "Upload confirmation finished. Requested: {}, Successfully updated: {}",
                request.getPhotoIds().size(),
                updatedCount
        );
    }

    private void scheduleRekognitionAsync(UUID photoId, String collectionId) {
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

    private void scheduleLocalProcessingAsync(UUID photoId, String collectionId) {
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

    @Transactional(readOnly = true)
    public List<PhotoResponse> getPhotosForEvent(UUID eventId, User user) {
        // verify access
        eventService.getEventEntityByIdAndUser(eventId, user);
        return photoRepository.findByEventIdOrderByCreatedAtDesc(eventId)
                .stream()
                .map(this::mapToPhotoResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> getPublicPhotosForEvent(String publicId) {
        // Face-gated privacy: Guests do not have access to browse all event photos.
        // Photos are only returned when their face matches via searchGuestPhotos().
        return java.util.Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public String getPhotoDownloadUrl(String publicId, UUID photoId) {
        Event event = eventService.getEventEntityByPublicId(publicId);
        Photo photo = photoRepository.findByIdAndEventId(photoId, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found in this event"));

        return s3StorageService.generatePresignedDownloadUrl(photo.getOriginalS3Key(), photo.getOriginalFilename());
    }

    // Rekognition search is intentionally non-transactional to avoid holding DB connections
    // during the external AWS Rekognition network roundtrip
    public GuestSearchResponse searchGuestPhotosWithSession(String publicId, byte[] selfieBytes, Float threshold) {
        Event event = eventService.getEventEntityByPublicId(publicId);
        List<SearchMatchResponse> matches = searchGuestPhotosInternal(event, selfieBytes, threshold);
        long indexingCount = photoRepository.countByEventIdAndIndexingStatus(event.getId(), IndexingStatus.PENDING)
                + photoRepository.countByEventIdAndIndexingStatus(event.getId(), IndexingStatus.INDEXING);

        if (matches.isEmpty()) {
            return GuestSearchResponse.builder()
                    .sessionToken(null)
                    .expiresAt(null)
                    .matches(Collections.emptyList())
                    .faceDetected(true)
                    .indexingCount(indexingCount)
                    .build();
        }

        Set<UUID> matchedIds = matches.stream().map(SearchMatchResponse::getPhotoId).collect(Collectors.toSet());
        com.fotofetch.guest.session.GuestSessionService.GuestSessionResult sessionResult =
                guestSessionService.createSession(event, matchedIds);

        return GuestSearchResponse.builder()
                .sessionToken(sessionResult.getRawToken())
                .expiresAt(sessionResult.getExpiresAt())
                .matches(matches)
                .faceDetected(true)
                .indexingCount(indexingCount)
                .build();
    }

    public List<SearchMatchResponse> searchGuestPhotos(String publicId, byte[] selfieBytes, Float threshold) {
        Event event = eventService.getEventEntityByPublicId(publicId);
        return searchGuestPhotosInternal(event, selfieBytes, threshold);
    }

    private List<SearchMatchResponse> searchGuestPhotosInternal(Event event, byte[] selfieBytes, Float threshold) {
        String collectionId = event.getRekognitionCollectionId();

        List<RekognitionService.MatchResult> matches = rekognitionService.searchFacesBySelfie(
                collectionId, selfieBytes, threshold);

        if (matches.isEmpty()) {
            return Collections.emptyList();
        }

        // Group matches by photo UUID (keeping maximum similarity)
        Map<UUID, Float> bestSimilarityMap = new HashMap<>();
        for (RekognitionService.MatchResult match : matches) {
            try {
                UUID photoId = UUID.fromString(match.getExternalImageId());
                Float currentBest = bestSimilarityMap.get(photoId);
                if (currentBest == null || match.getSimilarity() > currentBest) {
                    bestSimilarityMap.put(photoId, match.getSimilarity());
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID in Rekognition ExternalImageId: {}", match.getExternalImageId());
            }
        }

        if (bestSimilarityMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<Photo> matchedPhotos = photoRepository.findByEventIdAndIdIn(event.getId(), bestSimilarityMap.keySet());

        List<SearchMatchResponse> responseList = new ArrayList<>();
        for (Photo photo : matchedPhotos) {
            Float similarity = bestSimilarityMap.get(photo.getId());
            String thumbUrl = null;
            if (photo.getThumbnailS3Key() != null) {
                thumbUrl = s3StorageService.generatePresignedDownloadUrl(photo.getThumbnailS3Key(), null);
            }
            // Presigned download URL for verified facial-recognition match
            String downloadUrl = s3StorageService.generatePresignedDownloadUrl(photo.getOriginalS3Key(), photo.getOriginalFilename());

            responseList.add(SearchMatchResponse.builder()
                    .photoId(photo.getId())
                    .similarity(similarity)
                    .originalFilename(photo.getOriginalFilename())
                    .fileSize(photo.getFileSize())
                    .facesFound(photo.getFacesFound())
                    .thumbnailUrl(thumbUrl)
                    .downloadUrl(downloadUrl)
                    .build());
        }

        // Sort descending by similarity
        responseList.sort((a, b) -> Float.compare(b.getSimilarity(), a.getSimilarity()));
        return responseList;
    }

    public List<SearchMatchResponse> mapPhotosToMatchResponses(List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyList();
        }
        List<SearchMatchResponse> responseList = new ArrayList<>();
        for (Photo photo : photos) {
            String thumbUrl = null;
            if (photo.getThumbnailS3Key() != null) {
                thumbUrl = s3StorageService.generatePresignedDownloadUrl(photo.getThumbnailS3Key(), null);
            }
            String downloadUrl = s3StorageService.generatePresignedDownloadUrl(photo.getOriginalS3Key(), photo.getOriginalFilename());

            responseList.add(SearchMatchResponse.builder()
                    .photoId(photo.getId())
                    .similarity(100.0f) // Verified tagged identity match
                    .originalFilename(photo.getOriginalFilename())
                    .fileSize(photo.getFileSize())
                    .facesFound(photo.getFacesFound())
                    .thumbnailUrl(thumbUrl)
                    .downloadUrl(downloadUrl)
                    .build());
        }
        return responseList;
    }

    @Transactional
    public void retryPhotoProcessing(UUID photoId, User user) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));

        // Verify photographer ownership
        eventService.getEventEntityByIdAndUser(photo.getEvent().getId(), user);

        photo.setProcessingAttempts(0);
        photo.setNextRetryAt(null);
        photo.setLastProcessingError(null);
        if (photo.getThumbnailStatus() == ThumbnailStatus.FAILED) {
            photo.setThumbnailStatus(ThumbnailStatus.PENDING);
        }
        if (photo.getIndexingStatus() == IndexingStatus.FAILED) {
            photo.setIndexingStatus(IndexingStatus.PENDING);
        }
        photoRepository.saveAndFlush(photo);

        photoProcessingService.processPhotoAsync(photo.getId(), photo.getEvent().getRekognitionCollectionId());
    }

    public PhotoResponse mapToPublicPhotoResponse(Photo photo) {
        String thumbUrl = null;
        if (photo.getThumbnailS3Key() != null) {
            thumbUrl = s3StorageService.generatePresignedDownloadUrl(photo.getThumbnailS3Key(), null);
        }

        // Public event gallery ONLY exposes thumbnails.
        // Full HD downloadUrl is strictly null to preserve facial-search privacy.
        return PhotoResponse.builder()
                .id(photo.getId())
                .originalFilename(photo.getOriginalFilename())
                .fileSize(photo.getFileSize())
                .contentType(photo.getContentType())
                .uploadStatus(photo.getUploadStatus())
                .thumbnailStatus(photo.getThumbnailStatus())
                .indexingStatus(photo.getIndexingStatus())
                .facesFound(photo.getFacesFound())
                .thumbnailUrl(thumbUrl)
                .downloadUrl(null)
                .createdAt(photo.getCreatedAt())
                .build();
    }

    public PhotoResponse mapToPhotoResponse(Photo photo) {
        String thumbUrl = null;
        if (photo.getThumbnailS3Key() != null) {
            thumbUrl = s3StorageService.generatePresignedDownloadUrl(photo.getThumbnailS3Key(), null);
        }

        String downloadUrl = null;
        if (photo.getUploadStatus() == UploadStatus.UPLOADED && photo.getOriginalS3Key() != null) {
            downloadUrl = s3StorageService.generatePresignedDownloadUrl(photo.getOriginalS3Key(), photo.getOriginalFilename());
        }

        return PhotoResponse.builder()
                .id(photo.getId())
                .originalFilename(photo.getOriginalFilename())
                .fileSize(photo.getFileSize())
                .contentType(photo.getContentType())
                .uploadStatus(photo.getUploadStatus())
                .thumbnailStatus(photo.getThumbnailStatus())
                .indexingStatus(photo.getIndexingStatus())
                .facesFound(photo.getFacesFound())
                .thumbnailUrl(thumbUrl)
                .downloadUrl(downloadUrl)
                .createdAt(photo.getCreatedAt())
                .build();
    }

    @Transactional
    public void deletePhoto(UUID eventId, UUID photoId, User user) {
        Event event = eventService.getEventEntityByIdAndUser(eventId, user);
        Photo photo = photoRepository.findByIdAndEventId(photoId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found in this event"));

        if (photo.getOriginalS3Key() != null) {
            s3StorageService.deleteObject(photo.getOriginalS3Key());
        }
        if (photo.getThumbnailS3Key() != null) {
            s3StorageService.deleteObject(photo.getThumbnailS3Key());
        }

        rekognitionService.deleteFacesForPhotos(event.getRekognitionCollectionId(), Collections.singletonList(photoId));

        event.getPhotos().remove(photo);
        photoRepository.delete(photo);
        photoRepository.flush();

        log.info("Photo {} deleted successfully for event {}", photoId, eventId);
    }

    @Transactional
    public int deletePhotosBulk(UUID eventId, List<UUID> photoIds, User user) {
        if (photoIds == null || photoIds.isEmpty()) {
            return 0;
        }

        Event event = eventService.getEventEntityByIdAndUser(eventId, user);
        List<Photo> photos = photoRepository.findByEventIdAndIdIn(eventId, photoIds);

        if (photos.isEmpty()) {
            return 0;
        }

        List<String> s3Keys = new ArrayList<>();
        List<UUID> foundIds = new ArrayList<>();
        for (Photo photo : photos) {
            foundIds.add(photo.getId());
            if (photo.getOriginalS3Key() != null) {
                s3Keys.add(photo.getOriginalS3Key());
            }
            if (photo.getThumbnailS3Key() != null) {
                s3Keys.add(photo.getThumbnailS3Key());
            }
        }

        s3StorageService.deleteObjects(s3Keys);
        rekognitionService.deleteFacesForPhotos(event.getRekognitionCollectionId(), foundIds);

        event.getPhotos().removeAll(photos);
        photoRepository.deleteAll(photos);
        photoRepository.flush();

        log.info("Bulk-deleted {} photos for event {}", photos.size(), eventId);
        return photos.size();
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo.jpg";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
