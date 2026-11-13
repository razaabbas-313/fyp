package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.storage.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoProcessingService {

    private final PhotoRepository photoRepository;
    private final S3StorageService s3StorageService;
    private final ThumbnailService thumbnailService;
    private final RekognitionService rekognitionService;

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

        // 1. Thumbnail generation from original S3 image
        boolean thumbnailSuccess = false;
        try {
            photo.setThumbnailStatus(ThumbnailStatus.PROCESSING);
            photo = photoRepository.saveAndFlush(photo);

            byte[] originalBytes = s3StorageService.getObjectBytes(photo.getOriginalS3Key());
            byte[] thumbnailBytes = thumbnailService.generateThumbnail(originalBytes);

            String thumbnailKey = String.format("events/%s/thumbnails/%s.jpg", photo.getEvent().getId(), photoId);
            s3StorageService.uploadThumbnail(thumbnailKey, thumbnailBytes);

            photo.setThumbnailS3Key(thumbnailKey);
            photo.setThumbnailStatus(ThumbnailStatus.COMPLETED);
            photo = photoRepository.saveAndFlush(photo);
            thumbnailSuccess = true;
            log.info("Thumbnail generated and uploaded to S3 for photo: {}", photoId);
        } catch (Exception ex) {
            log.error("Failed to generate thumbnail for photo: {}", photoId, ex);
            photo.setThumbnailStatus(ThumbnailStatus.FAILED);
            photo.setIndexingStatus(IndexingStatus.FAILED);
            photo = photoRepository.saveAndFlush(photo);
        }

        // Check: Thumbnail MUST be successfully generated and stored in S3 before face indexing
        // Never silently fall back to indexing the original full-size image
        if (!thumbnailSuccess || photo.getThumbnailS3Key() == null) {
            log.warn("Skipping face indexing for photo {} because thumbnail generation failed or thumbnail key is missing.", photoId);
            return;
        }

        // 2. AWS Rekognition face indexing USING THUMBNAIL S3 KEY
        try {
            photo.setIndexingStatus(IndexingStatus.INDEXING);
            photo = photoRepository.saveAndFlush(photo);

            log.info("Indexing faces for photo {} using THUMBNAIL S3 key: {}", photo.getId(), photo.getThumbnailS3Key());
            int facesCount = rekognitionService.indexPhotoFaces(collectionId, photo.getId(), photo.getThumbnailS3Key());
            photo.setFacesFound(facesCount);
            if (facesCount > 0) {
                photo.setIndexingStatus(IndexingStatus.COMPLETED);
            } else {
                photo.setIndexingStatus(IndexingStatus.NO_FACES_DETECTED);
            }
            photo = photoRepository.saveAndFlush(photo);
            log.info("Rekognition indexed {} face(s) using thumbnail for photo: {}", facesCount, photoId);
        } catch (Exception ex) {
            log.error("Failed to index faces using thumbnail for photo: {}", photoId, ex);
            photo.setIndexingStatus(IndexingStatus.FAILED);
            photo = photoRepository.saveAndFlush(photo);
        }
    }
}
