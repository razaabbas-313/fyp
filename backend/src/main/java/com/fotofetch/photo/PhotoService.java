package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.common.BadRequestException;
import com.fotofetch.common.ResourceNotFoundException;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventService;
import com.fotofetch.photo.dto.*;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.storage.ThumbnailService;
import com.fotofetch.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final EventService eventService;
    private final S3StorageService s3StorageService;
    private final ThumbnailService thumbnailService;
    private final RekognitionService rekognitionService;

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
                    .build();

            photosToSave.add(photo);

            uploadItems.add(PresignResponse.PresignedFile.builder()
                    .photoId(photoId)
                    .originalFilename(cleanName)
                    .uploadUrl(uploadUrl)
                    .s3Key(originalKey)
                    .build());
        }

        photoRepository.saveAll(photosToSave);
        log.info("Generated {} presigned upload URLs for event: {}", uploadItems.size(), eventId);

        return PresignResponse.builder()
                .uploads(uploadItems)
                .build();
    }

    @Transactional
    public void confirmUploads(UUID eventId, ConfirmUploadRequest request, User user) {
        Event event = eventService.getEventEntityByIdAndUser(eventId, user);
        String collectionId = event.getRekognitionCollectionId();

        for (UUID photoId : request.getPhotoIds()) {
            Photo photo = photoRepository.findByIdAndEventId(photoId, eventId)
                    .orElse(null);

            if (photo != null) {
                photo.setUploadStatus(UploadStatus.UPLOADED);
                photoRepository.save(photo);
                // Trigger async processing for thumbnail & Rekognition indexing
                processPhotoAsync(photo.getId(), collectionId);
            }
        }
        log.info("Confirmed {} uploads for event: {}", request.getPhotoIds().size(), eventId);
    }

    @Async("photoProcessingExecutor")
    public void processPhotoAsync(UUID photoId, String collectionId) {
        log.info("Starting background processing for photo: {}", photoId);
        Photo photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            log.warn("Photo {} not found for background processing", photoId);
            return;
        }

        // 1. Thumbnail generation
        try {
            photo.setThumbnailStatus(ThumbnailStatus.PROCESSING);
            photoRepository.save(photo);

            byte[] originalBytes = s3StorageService.getObjectBytes(photo.getOriginalS3Key());
            byte[] thumbnailBytes = thumbnailService.generateThumbnail(originalBytes);

            String thumbnailKey = String.format("events/%s/thumbnails/%s.jpg", photo.getEvent().getId(), photoId);
            s3StorageService.uploadThumbnail(thumbnailKey, thumbnailBytes);

            photo.setThumbnailS3Key(thumbnailKey);
            photo.setThumbnailStatus(ThumbnailStatus.COMPLETED);
            photoRepository.save(photo);
            log.info("Thumbnail generated and uploaded for photo: {}", photoId);
        } catch (Exception ex) {
            log.error("Failed to generate thumbnail for photo: {}", photoId, ex);
            photo.setThumbnailStatus(ThumbnailStatus.FAILED);
            photoRepository.save(photo);
        }

        // 2. AWS Rekognition face indexing
        try {
            photo.setIndexingStatus(IndexingStatus.INDEXING);
            photoRepository.save(photo);

            int facesCount = rekognitionService.indexPhotoFaces(collectionId, photo.getId(), photo.getOriginalS3Key());
            photo.setFacesFound(facesCount);
            if (facesCount > 0) {
                photo.setIndexingStatus(IndexingStatus.COMPLETED);
            } else {
                photo.setIndexingStatus(IndexingStatus.NO_FACES_DETECTED);
            }
            photoRepository.save(photo);
            log.info("Rekognition indexed {} face(s) for photo: {}", facesCount, photoId);
        } catch (Exception ex) {
            log.error("Failed to index faces for photo: {}", photoId, ex);
            photo.setIndexingStatus(IndexingStatus.FAILED);
            photoRepository.save(photo);
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
    public String getPhotoDownloadUrl(String publicId, UUID photoId) {
        Event event = eventService.getEventEntityByPublicId(publicId);
        Photo photo = photoRepository.findByIdAndEventId(photoId, event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found in this event"));

        return s3StorageService.generatePresignedDownloadUrl(photo.getOriginalS3Key(), photo.getOriginalFilename());
    }

    @Transactional(readOnly = true)
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
            String thumbKey = photo.getThumbnailS3Key() != null ? photo.getThumbnailS3Key() : photo.getOriginalS3Key();
            String thumbUrl = s3StorageService.generatePresignedDownloadUrl(thumbKey, null);
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

    public PhotoResponse mapToPhotoResponse(Photo photo) {
        String thumbUrl = null;
        if (photo.getThumbnailS3Key() != null) {
            thumbUrl = s3StorageService.generatePresignedDownloadUrl(photo.getThumbnailS3Key(), null);
        } else if (photo.getUploadStatus() == UploadStatus.UPLOADED) {
            thumbUrl = s3StorageService.generatePresignedDownloadUrl(photo.getOriginalS3Key(), null);
        }

        String downloadUrl = null;
        if (photo.getUploadStatus() == UploadStatus.UPLOADED) {
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

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo.jpg";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
