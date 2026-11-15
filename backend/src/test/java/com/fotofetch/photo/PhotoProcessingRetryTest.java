package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventRepository;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.storage.ThumbnailService;
import com.fotofetch.user.Role;
import com.fotofetch.user.User;
import com.fotofetch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class PhotoProcessingRetryTest {

    @Autowired
    private PhotoProcessingService photoProcessingService;

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

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        testUser = userRepository.save(User.builder()
                .name("Retry Photographer")
                .email("retry_" + uniqueSuffix + "@photo.com")
                .password("secure_hash")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent = eventRepository.save(Event.builder()
                .name("Retry Test Gala")
                .eventDate(LocalDate.now())
                .publicId("retry-gala-" + uniqueSuffix)
                .rekognitionCollectionId("fotofetch-event-retry-" + uniqueSuffix)
                .user(testUser)
                .build());
    }

    @Test
    @Transactional
    @DisplayName("Atomic job claiming: claims unlocked photo and increments processingAttempts")
    void testAtomicJobClaiming() {
        UUID photoId = UUID.randomUUID();
        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key("events/" + testEvent.getId() + "/originals/" + photoId + ".jpg")
                .originalFilename("test.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.PENDING)
                .indexingStatus(IndexingStatus.PENDING)
                .processingAttempts(0)
                .isNew(true)
                .build());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime stuckBefore = now.minusMinutes(5);

        int claimed = photoRepository.claimPhotoForProcessing(photoId, now, stuckBefore);
        assertEquals(1, claimed, "First worker should successfully claim the photo");

        Photo updated = photoRepository.findById(photoId).orElseThrow();
        assertEquals(1, updated.getProcessingAttempts());
        assertNotNull(updated.getLastProcessingAttemptAt());
        assertNotNull(updated.getProcessingUpdatedAt());
    }

    @Test
    @Transactional
    @DisplayName("Atomic job claiming: rejects already-running job that is not stuck")
    void testAtomicClaimRejectsActiveJob() {
        UUID photoId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Photo currently processing recently (1 minute ago)
        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key("events/" + testEvent.getId() + "/originals/" + photoId + ".jpg")
                .originalFilename("active.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.PROCESSING)
                .indexingStatus(IndexingStatus.PENDING)
                .processingAttempts(1)
                .processingUpdatedAt(now.minusMinutes(1))
                .isNew(true)
                .build());

        LocalDateTime stuckBefore = now.minusMinutes(5);

        // Attempting to claim should return 0 (skipped)
        int claimed = photoRepository.claimPhotoForProcessing(photoId, now, stuckBefore);
        assertEquals(0, claimed, "Concurrent worker should NOT claim an actively processing photo");
    }

    @Test
    @Transactional
    @DisplayName("Atomic job claiming: recovers stuck job whose processingUpdatedAt is older than 5 minutes")
    void testAtomicClaimRecoversStuckJob() {
        UUID photoId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Photo stuck in PROCESSING for 10 minutes (e.g. process crashed or OOM)
        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key("events/" + testEvent.getId() + "/originals/" + photoId + ".jpg")
                .originalFilename("stuck.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.PROCESSING)
                .indexingStatus(IndexingStatus.PENDING)
                .processingAttempts(1)
                .processingUpdatedAt(now.minusMinutes(10))
                .isNew(true)
                .build());

        LocalDateTime stuckBefore = now.minusMinutes(5);

        int claimed = photoRepository.claimPhotoForProcessing(photoId, now, stuckBefore);
        assertEquals(1, claimed, "Recovery worker should successfully reclaim a stuck photo");

        Photo updated = photoRepository.findById(photoId).orElseThrow();
        assertEquals(2, updated.getProcessingAttempts());
    }

    @Test
    @DisplayName("Exponential backoff: failure schedules nextRetryAt with backoff")
    void testExponentialBackoffOnFailure() {
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + testEvent.getId() + "/originals/" + photoId + ".jpg";

        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key(originalKey)
                .originalFilename("fail.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.PENDING)
                .indexingStatus(IndexingStatus.PENDING)
                .processingAttempts(0)
                .isNew(true)
                .build());

        when(s3StorageService.getObjectBytes(originalKey)).thenThrow(new RuntimeException("Transient S3 Network Glitch"));

        // Process (attempt 1)
        photoProcessingService.processPhoto(photoId, testEvent.getRekognitionCollectionId());

        Photo updated = photoRepository.findById(photoId).orElseThrow();
        assertEquals(1, updated.getProcessingAttempts());
        assertEquals(ThumbnailStatus.FAILED, updated.getThumbnailStatus());
        assertNotNull(updated.getNextRetryAt(), "nextRetryAt must be scheduled on recoverable failure");
        assertTrue(updated.getNextRetryAt().isAfter(LocalDateTime.now()), "nextRetryAt must be in the future");
    }

    @Test
    @DisplayName("Max retries limit: photo marked permanently FAILED after 3 attempts without further nextRetryAt")
    void testMaxRetriesLimit() {
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + testEvent.getId() + "/originals/" + photoId + ".jpg";

        // Pre-set photo with 2 attempts already made
        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key(originalKey)
                .originalFilename("max_fail.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.PENDING)
                .indexingStatus(IndexingStatus.PENDING)
                .processingAttempts(2)
                .isNew(true)
                .build());

        when(s3StorageService.getObjectBytes(originalKey)).thenThrow(new RuntimeException("Permanent error"));

        // Process (attempt 3 = maxRetries)
        photoProcessingService.processPhoto(photoId, testEvent.getRekognitionCollectionId());

        Photo updated = photoRepository.findById(photoId).orElseThrow();
        assertEquals(3, updated.getProcessingAttempts());
        assertEquals(ThumbnailStatus.FAILED, updated.getThumbnailStatus());
        assertEquals(IndexingStatus.FAILED, updated.getIndexingStatus());
        assertNull(updated.getNextRetryAt(), "nextRetryAt must be null when max retries exceeded");
        assertTrue(updated.getLastProcessingError().contains("Permanent error"));
    }

    @Test
    @Transactional
    @DisplayName("Recoverable query: selects photos where nextRetryAt <= now and attempts < maxRetries")
    void testFindRecoverablePhotosQuery() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Ready for retry
        Photo readyPhoto = photoRepository.save(Photo.builder()
                .id(UUID.randomUUID())
                .event(testEvent)
                .originalS3Key("k1.jpg")
                .originalFilename("ready.jpg")
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.FAILED)
                .indexingStatus(IndexingStatus.PENDING)
                .processingAttempts(1)
                .nextRetryAt(now.minusSeconds(10))
                .isNew(true)
                .build());

        // 2. Not ready yet (nextRetryAt is in the future)
        Photo futurePhoto = photoRepository.save(Photo.builder()
                .id(UUID.randomUUID())
                .event(testEvent)
                .originalS3Key("k2.jpg")
                .originalFilename("future.jpg")
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.FAILED)
                .indexingStatus(IndexingStatus.PENDING)
                .processingAttempts(1)
                .nextRetryAt(now.plusMinutes(5))
                .isNew(true)
                .build());

        // 3. Max retries exceeded
        Photo maxedPhoto = photoRepository.save(Photo.builder()
                .id(UUID.randomUUID())
                .event(testEvent)
                .originalS3Key("k3.jpg")
                .originalFilename("maxed.jpg")
                .uploadStatus(UploadStatus.UPLOADED)
                .thumbnailStatus(ThumbnailStatus.FAILED)
                .indexingStatus(IndexingStatus.FAILED)
                .processingAttempts(3)
                .nextRetryAt(null)
                .isNew(true)
                .build());

        List<Photo> recoverable = photoRepository.findRecoverablePhotos(now, now.minusMinutes(5), 3);

        assertEquals(1, recoverable.size());
        assertEquals(readyPhoto.getId(), recoverable.get(0).getId());
    }
}
