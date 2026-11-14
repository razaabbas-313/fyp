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

    @Transactional
    public void confirmUploads(UUID eventId, ConfirmUploadRequest request, User user) {
        Event event = eventService.getEventEntityByIdAndUser(eventId, user);
        String collectionId = event.getRekognitionCollectionId();

        int updatedCount = 0;
        List<UUID> confirmedPhotoIds = new ArrayList<>();

        for (UUID photoId : request.getPhotoIds()) {
            log.info("Looking for photo: {} in event: {}", photoId, eventId);

            Optional<Photo> photoOptional = photoRepository.findByIdAndEventId(photoId, eventId);

            if (photoOptional.isEmpty()) {
                log.error("PHOTO NOT FOUND: {} for event: {}", photoId, eventId);
                continue;
            }

            Photo photo = photoOptional.get();

            // Idempotency: Skip already UPLOADED photos to prevent duplicate Rekognition indexing
            if (photo.getUploadStatus() == UploadStatus.UPLOADED) {
                log.info("Photo {} already confirmed and UPLOADED, skipping duplicate confirm", photoId);
                continue;
            }

            // Verify the object was actually uploaded to S3
            if (!s3StorageService.doesObjectExist(photo.getOriginalS3Key())) {
                log.warn("Photo {} not found in S3 at key: {}, skipping confirmation", photoId, photo.getOriginalS3Key());
                continue;
            }

            log.info("Photo found: {}. Current upload status: {}", photo.getId(), photo.getUploadStatus());

            photo.setUploadStatus(UploadStatus.UPLOADED);
            photoRepository.save(photo);

            log.info("Upload status updated to UPLOADED for photo: {}", photoId);
            updatedCount++;
            confirmedPhotoIds.add(photo.getId());
        }

        photoRepository.flush();

        log.info(
                "Upload confirmation finished. Requested: {}, Successfully updated: {}",
                request.getPhotoIds().size(),
                updatedCount
        );

        // Ensure asynchronous processing starts strictly AFTER the database transaction commits
        if (!confirmedPhotoIds.isEmpty()) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (UUID confirmedId : confirmedPhotoIds) {
                            photoProcessingService.processPhotoAsync(confirmedId, collectionId);
                        }
                    }
                });
            } else {
                for (UUID confirmedId : confirmedPhotoIds) {
                    photoProcessingService.processPhotoAsync(confirmedId, collectionId);
                }
            }
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
    public List<SearchMatchResponse> searchGuestPhotos(String publicId, byte[] selfieBytes, Float threshold) {
        Event event = eventService.getEventEntityByPublicId(publicId);
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
            // Full original download URL is granted specifically to verified facial-recognition matches
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
