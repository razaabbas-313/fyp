package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventRepository;
import com.fotofetch.photo.dto.PhotoResponse;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.storage.ThumbnailService;
import com.fotofetch.user.Role;
import com.fotofetch.user.User;
import com.fotofetch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class PhotoProcessingServiceTest {

    @Autowired
    private PhotoProcessingService photoProcessingService;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private S3StorageService s3StorageService;

    @MockBean
    private ThumbnailService thumbnailService;

    @MockBean
    private RekognitionService rekognitionService;

    private User testUser;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        reset(s3StorageService, thumbnailService, rekognitionService);
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("Studio Pro")
                .email("pro@studio.com")
                .password("secure_hash")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent = eventRepository.save(Event.builder()
                .name("Fashion Show")
                .eventDate(LocalDate.now())
                .publicId("fashion-2026")
                .rekognitionCollectionId("fotofetch-event-fashion-2026")
                .user(testUser)
                .build());
    }

    @Test
    @DisplayName("Rekognition indexing must strictly receive the thumbnail S3 key, not the original S3 key")
    void testRekognitionIndexesThumbnailS3Key() throws IOException {
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + testEvent.getId() + "/originals/" + photoId + "-model.jpg";

        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key(originalKey)
                .originalFilename("model.jpg")
                .contentType("image/jpeg")
                .fileSize(5000000L)
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.PENDING)
                .indexingStatus(IndexingStatus.PENDING)
                .isNew(true)
                .build());

        byte[] fakeOriginalBytes = new byte[]{1, 2, 3, 4};
        byte[] fakeThumbnailBytes = new byte[]{9, 8, 7};

        when(s3StorageService.getObjectBytes(originalKey)).thenReturn(fakeOriginalBytes);
        when(thumbnailService.generateThumbnail(fakeOriginalBytes)).thenReturn(fakeThumbnailBytes);
        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoId), anyString()))
                .thenReturn(2);

        // Execute processing
        photoProcessingService.processPhoto(photoId, testEvent.getRekognitionCollectionId());

        // Verify Rekognition was called with THUMBNAIL S3 key, NOT the original S3 key
        ArgumentCaptor<String> s3KeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rekognitionService, times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photoId),
                s3KeyCaptor.capture()
        );

        String indexedKey = s3KeyCaptor.getValue();
        assertTrue(indexedKey.startsWith("events/" + testEvent.getId() + "/thumbnails/"),
                "Rekognition must index using the thumbnail S3 key: " + indexedKey);
        assertTrue(indexedKey.endsWith(".jpg"));
        assertNotEquals(originalKey, indexedKey, "Rekognition must never index the original S3 key");

        // Verify DB updates
        Photo updated = photoRepository.findById(photoId).orElseThrow();
        assertEquals(ThumbnailStatus.COMPLETED, updated.getThumbnailStatus());
        assertEquals(IndexingStatus.COMPLETED, updated.getIndexingStatus());
        assertEquals(2, updated.getFacesFound());
        assertEquals(indexedKey, updated.getThumbnailS3Key());
    }

    @Test
    @DisplayName("If thumbnail generation fails, Rekognition indexing must NOT run and status must be FAILED")
    void testThumbnailFailureAbortsRekognitionIndexing() throws IOException {
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + testEvent.getId() + "/originals/" + photoId + "-corrupt.jpg";

        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key(originalKey)
                .originalFilename("corrupt.jpg")
                .contentType("image/jpeg")
                .fileSize(100L)
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.PENDING)
                .indexingStatus(IndexingStatus.PENDING)
                .isNew(true)
                .build());

        when(s3StorageService.getObjectBytes(originalKey)).thenThrow(new RuntimeException("S3 read error"));

        // Execute processing
        photoProcessingService.processPhoto(photoId, testEvent.getRekognitionCollectionId());

        // Verify Rekognition indexing is NEVER called
        verify(rekognitionService, never()).indexPhotoFaces(anyString(), any(UUID.class), anyString());

        // Verify photo statuses are updated to FAILED
        Photo updated = photoRepository.findById(photoId).orElseThrow();
        assertEquals(ThumbnailStatus.FAILED, updated.getThumbnailStatus());
        assertEquals(IndexingStatus.FAILED, updated.getIndexingStatus());
    }

    @Test
    @DisplayName("Gallery previews must return thumbnail URLs while download URLs must return original image URLs")
    void testGalleryUsesThumbnailAndDownloadUsesOriginal() {
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + testEvent.getId() + "/originals/" + photoId + "-photo.jpg";
        String thumbnailKey = "events/" + testEvent.getId() + "/thumbnails/" + photoId + ".jpg";

        Photo photo = Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key(originalKey)
                .thumbnailS3Key(thumbnailKey)
                .originalFilename("photo.jpg")
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.COMPLETED)
                .indexingStatus(IndexingStatus.COMPLETED)
                .build();

        when(s3StorageService.generatePresignedDownloadUrl(thumbnailKey, null))
                .thenReturn("https://s3.amazonaws.com/test-bucket/" + thumbnailKey);
        when(s3StorageService.generatePresignedDownloadUrl(originalKey, "photo.jpg"))
                .thenReturn("https://s3.amazonaws.com/test-bucket/" + originalKey + "?response-content-disposition=attachment");

        PhotoResponse response = photoService.mapToPhotoResponse(photo);

        // Thumbnail URL must point to thumbnail key
        assertNotNull(response.getThumbnailUrl());
        assertTrue(response.getThumbnailUrl().contains("thumbnails/" + photoId + ".jpg"));

        // Download URL must point to original key
        assertNotNull(response.getDownloadUrl());
        assertTrue(response.getDownloadUrl().contains("originals/" + photoId + "-photo.jpg"));
    }
}
