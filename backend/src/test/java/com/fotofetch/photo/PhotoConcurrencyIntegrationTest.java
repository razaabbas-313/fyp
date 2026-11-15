package com.fotofetch.photo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fotofetch.ai.RekognitionService;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventRepository;
import com.fotofetch.photo.callback.HmacSignatureValidator;
import com.fotofetch.photo.callback.ProcessedCallbackRepository;
import com.fotofetch.photo.callback.ThumbnailCallbackRequest;
import com.fotofetch.photo.dto.ConfirmUploadRequest;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.storage.ThumbnailService;
import com.fotofetch.user.Role;
import com.fotofetch.user.User;
import com.fotofetch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhotoConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private PhotoRecoveryScheduler photoRecoveryScheduler;

    @Autowired
    private PhotoProcessingService photoProcessingService;

    @Autowired
    private ProcessedCallbackRepository processedCallbackRepository;

    @MockBean
    private S3StorageService s3StorageService;

    @MockBean
    private ThumbnailService thumbnailService;

    @MockBean
    private RekognitionService rekognitionService;

    @Value("${fotofetch.lambda.callback-secret:test-lambda-callback-secret-for-unit-tests-2026}")
    private String callbackSecret;

    private User testUser;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        reset(s3StorageService, thumbnailService, rekognitionService);
        processedCallbackRepository.deleteAll();
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("Concurrency Tester")
                .email("concurrency@fotofetch.com")
                .password("secure_hash")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent = eventRepository.save(Event.builder()
                .id(UUID.fromString("19530627-7908-49af-9709-94b534c24737"))
                .name("Production Gala 2026")
                .eventDate(LocalDate.now())
                .publicId("prod-gala-2026")
                .rekognitionCollectionId("fotofetch-event-prod-gala-2026")
                .user(testUser)
                .build());

        // Default S3 mock behavior: original object exists
        when(s3StorageService.getObjectMetadata(argThat(key -> key != null && key.contains("/originals/"))))
                .thenReturn(Optional.of(HeadObjectResponse.builder().contentLength(2048000L).contentType("image/jpeg").build()));
        // Default S3 mock behavior: thumbnail does not exist unless explicitly configured
        when(s3StorageService.getObjectMetadata(argThat(key -> key != null && key.contains("/thumbnails/"))))
                .thenReturn(Optional.empty());
    }

    private Photo createTestPhoto(UUID photoId, UploadStatus uploadStatus, ThumbnailStatus thumbStatus, IndexingStatus indexingStatus) {
        String originalKey = String.format("events/%s/originals/%s-test.jpg", testEvent.getId(), photoId);
        return photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key(originalKey)
                .originalFilename("test.jpg")
                .contentType("image/jpeg")
                .fileSize(2048000L)
                .uploadStatus(uploadStatus)
                .thumbnailStatus(thumbStatus)
                .indexingStatus(indexingStatus)
                .isNew(true)
                .build());
    }

    private byte[] buildSignedCallback(ThumbnailCallbackRequest request) throws Exception {
        return objectMapper.writeValueAsBytes(request);
    }

    private void executeCallback(ThumbnailCallbackRequest request) throws Exception {
        byte[] rawBody = buildSignedCallback(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Scenario A: Callback arrives first, upload confirmation second")
    void testScenarioA_CallbackFirstThenConfirm() throws Exception {
        UUID photoId = UUID.randomUUID();
        Photo photo = createTestPhoto(photoId, UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoId);

        ThumbnailCallbackRequest request = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(photoId)
                .eventId(testEvent.getId())
                .originalS3Key(photo.getOriginalS3Key())
                .thumbnailS3Key(expectedThumbKey)
                .status("COMPLETED")
                .build();

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoId), eq(expectedThumbKey)))
                .thenReturn(2);

        // 1. Callback arrives first
        executeCallback(request);

        Photo midPhoto = photoRepository.findById(photoId).orElseThrow();
        assertEquals(ThumbnailStatus.COMPLETED, midPhoto.getThumbnailStatus());
        assertEquals(UploadStatus.PENDING, midPhoto.getUploadStatus());
        // Rekognition indexing should not yet have executed because upload was not confirmed
        verify(rekognitionService, never()).indexPhotoFaces(anyString(), eq(photoId), anyString());

        // 2. Upload confirmation arrives second
        photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(List.of(photoId)), testUser);

        // Await async indexing execution
        verify(rekognitionService, timeout(2000).times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photoId),
                eq(expectedThumbKey)
        );

        Photo finalPhoto = photoRepository.findById(photoId).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, finalPhoto.getUploadStatus());
        assertEquals(ThumbnailStatus.COMPLETED, finalPhoto.getThumbnailStatus());
    }

    @Test
    @DisplayName("Scenario B: Upload confirmation arrives first, callback second")
    void testScenarioB_ConfirmFirstThenCallback() throws Exception {
        UUID photoId = UUID.randomUUID();
        Photo photo = createTestPhoto(photoId, UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoId);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoId), eq(expectedThumbKey)))
                .thenReturn(1);

        // 1. Upload confirmation arrives first
        photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(List.of(photoId)), testUser);

        Photo midPhoto = photoRepository.findById(photoId).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, midPhoto.getUploadStatus());
        assertEquals(ThumbnailStatus.PENDING, midPhoto.getThumbnailStatus());
        // Rekognition indexing should not yet have executed because thumbnail is pending
        verify(rekognitionService, never()).indexPhotoFaces(anyString(), eq(photoId), anyString());

        // 2. Callback arrives second
        ThumbnailCallbackRequest request = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(photoId)
                .eventId(testEvent.getId())
                .originalS3Key(photo.getOriginalS3Key())
                .thumbnailS3Key(expectedThumbKey)
                .status("COMPLETED")
                .build();

        executeCallback(request);

        // Await async indexing execution
        verify(rekognitionService, timeout(2000).times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photoId),
                eq(expectedThumbKey)
        );

        Photo finalPhoto = photoRepository.findById(photoId).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, finalPhoto.getUploadStatus());
        assertEquals(ThumbnailStatus.COMPLETED, finalPhoto.getThumbnailStatus());
    }

    @Test
    @DisplayName("Scenario C: Overlapping concurrent execution on the same photo (no lost updates, exactly once indexing)")
    void testScenarioC_ConcurrentOverlappingExecution() throws Exception {
        UUID photoId = UUID.randomUUID();
        Photo photo = createTestPhoto(photoId, UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoId);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoId), eq(expectedThumbKey)))
                .thenReturn(3);

        ThumbnailCallbackRequest request = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(photoId)
                .eventId(testEvent.getId())
                .originalS3Key(photo.getOriginalS3Key())
                .thumbnailS3Key(expectedThumbKey)
                .status("COMPLETED")
                .build();

        byte[] rawBody = buildSignedCallback(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Void> confirmTask = () -> {
            startGate.await();
            photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(List.of(photoId)), testUser);
            return null;
        };

        Callable<Void> callbackTask = () -> {
            startGate.await();
            mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-FotoFetch-Signature", signature)
                            .header("X-FotoFetch-Timestamp", timestamp)
                            .content(rawBody))
                    .andExpect(status().isOk());
            return null;
        };

        Future<Void> f1 = executor.submit(confirmTask);
        Future<Void> f2 = executor.submit(callbackTask);

        // Fire both simultaneously
        startGate.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Rekognition indexing must be called EXACTLY ONCE
        verify(rekognitionService, timeout(3000).times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photoId),
                eq(expectedThumbKey)
        );

        Photo finalPhoto = photoRepository.findById(photoId).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, finalPhoto.getUploadStatus(), "uploadStatus must be UPLOADED (no lost update)");
        assertEquals(ThumbnailStatus.COMPLETED, finalPhoto.getThumbnailStatus(), "thumbnailStatus must be COMPLETED (no lost update)");
        assertEquals(expectedThumbKey, finalPhoto.getThumbnailS3Key());
    }

    @Test
    @DisplayName("Scenario D: Multiple concurrent uploads (4+ photos in bulk) all complete successfully")
    void testScenarioD_BulkUploadConcurrentInterleaving() throws Exception {
        int photoCount = 6;
        List<UUID> photoIds = new ArrayList<>();
        List<ThumbnailCallbackRequest> requests = new ArrayList<>();

        for (int i = 0; i < photoCount; i++) {
            UUID id = UUID.randomUUID();
            photoIds.add(id);
            Photo p = createTestPhoto(id, UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
            String thumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), id);
            requests.add(ThumbnailCallbackRequest.builder()
                    .callbackId(UUID.randomUUID().toString())
                    .photoId(id)
                    .eventId(testEvent.getId())
                    .originalS3Key(p.getOriginalS3Key())
                    .thumbnailS3Key(thumbKey)
                    .status("COMPLETED")
                    .build());

            when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(id), eq(thumbKey)))
                    .thenReturn(1);
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // Confirm uploads in 2 chunks concurrently
        futures.add(pool.submit(() -> {
            startGate.await();
            photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(photoIds.subList(0, 3)), testUser);
            return null;
        }));
        futures.add(pool.submit(() -> {
            startGate.await();
            photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(photoIds.subList(3, 6)), testUser);
            return null;
        }));

        // Fire callbacks concurrently
        for (ThumbnailCallbackRequest req : requests) {
            futures.add(pool.submit(() -> {
                startGate.await();
                executeCallback(req);
                return null;
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // Verify all 6 photos are completed and indexed
        for (UUID id : photoIds) {
            String thumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), id);
            verify(rekognitionService, timeout(3000).times(1)).indexPhotoFaces(
                    eq(testEvent.getRekognitionCollectionId()),
                    eq(id),
                    eq(thumbKey)
            );
            Photo p = photoRepository.findById(id).orElseThrow();
            assertEquals(UploadStatus.UPLOADED, p.getUploadStatus());
            assertEquals(ThumbnailStatus.COMPLETED, p.getThumbnailStatus());
        }
    }

    @Test
    @DisplayName("Scenario E: Duplicate callbacks (replay protection and idempotency)")
    void testScenarioE_DuplicateCallbacks() throws Exception {
        UUID photoId = UUID.randomUUID();
        Photo photo = createTestPhoto(photoId, UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoId);

        String sharedCallbackId = "cb-" + UUID.randomUUID();
        ThumbnailCallbackRequest request = ThumbnailCallbackRequest.builder()
                .callbackId(sharedCallbackId)
                .photoId(photoId)
                .eventId(testEvent.getId())
                .originalS3Key(photo.getOriginalS3Key())
                .thumbnailS3Key(expectedThumbKey)
                .status("COMPLETED")
                .build();

        byte[] rawBody = buildSignedCallback(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        // 1. First callback succeeds
        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 2. Replay with identical callbackId is rejected with 400
        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("has already been processed")));

        // 3. Callback with different callbackId for already COMPLETED photo returns ALREADY_COMPLETED safely
        ThumbnailCallbackRequest duplicateReqNewId = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(photoId)
                .eventId(testEvent.getId())
                .originalS3Key(photo.getOriginalS3Key())
                .thumbnailS3Key(expectedThumbKey)
                .status("COMPLETED")
                .build();

        byte[] rawBody2 = buildSignedCallback(duplicateReqNewId);
        long timestamp2 = System.currentTimeMillis();
        String signature2 = HmacSignatureValidator.calculateSignatureHex(timestamp2, rawBody2, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature2)
                        .header("X-FotoFetch-Timestamp", timestamp2)
                        .content(rawBody2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ALREADY_COMPLETED"))
                .andExpect(jsonPath("$.data.indexingScheduled").value(false));
    }

    @Test
    @DisplayName("Scenario F: Duplicate upload confirmations (idempotency and missed Rekognition triggering)")
    void testScenarioF_DuplicateConfirmations() throws Exception {
        UUID photoId = UUID.randomUUID();
        Photo photo = createTestPhoto(photoId, UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoId);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoId), eq(expectedThumbKey)))
                .thenReturn(1);

        // First confirmation when thumbnail is pending -> defers Rekognition
        photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(List.of(photoId)), testUser);
        verify(rekognitionService, never()).indexPhotoFaces(anyString(), eq(photoId), anyString());

        // Thumbnail completes via Lambda callback
        ThumbnailCallbackRequest request = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(photoId)
                .eventId(testEvent.getId())
                .originalS3Key(photo.getOriginalS3Key())
                .thumbnailS3Key(expectedThumbKey)
                .status("COMPLETED")
                .build();
        executeCallback(request);

        // Second duplicate confirmation called by user
        photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(List.of(photoId)), testUser);

        // Rekognition is triggered without double-indexing
        verify(rekognitionService, timeout(2000).times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photoId),
                eq(expectedThumbKey)
        );
    }

    @Test
    @DisplayName("Scenario G: Rekognition failures and retry backoff")
    void testScenarioG_RekognitionFailuresAndBackoff() {
        UUID photoId = UUID.randomUUID();
        Photo photo = createTestPhoto(photoId, UploadStatus.UPLOADED, ThumbnailStatus.COMPLETED, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoId);
        photo.setThumbnailS3Key(expectedThumbKey);
        photoRepository.saveAndFlush(photo);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoId), eq(expectedThumbKey)))
                .thenThrow(new RuntimeException("Rekognition collection temporarily unavailable"));

        // Trigger processing
        photoProcessingService.processPhoto(photoId, testEvent.getRekognitionCollectionId());

        Photo failedPhoto = photoRepository.findById(photoId).orElseThrow();
        assertEquals(IndexingStatus.FAILED, failedPhoto.getIndexingStatus());
        assertEquals(1, failedPhoto.getProcessingAttempts());
        assertNotNull(failedPhoto.getNextRetryAt(), "nextRetryAt backoff must be set");
        assertTrue(failedPhoto.getNextRetryAt().isAfter(LocalDateTime.now().minusSeconds(1)));
        assertTrue(failedPhoto.getLastProcessingError().contains("Rekognition indexing failed"));
    }

    @Test
    @DisplayName("Scenario H: Recovery of stale/orphaned records with S3 verification")
    void testScenarioH_OrphanedRecoveryWithS3Verification() {
        UUID photoWithS3Thumb = UUID.randomUUID();
        UUID photoWithoutS3Thumb = UUID.randomUUID();

        Photo p1 = createTestPhoto(photoWithS3Thumb, UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        Photo p2 = createTestPhoto(photoWithoutS3Thumb, UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        // Set processingUpdatedAt to 10 minutes ago
        LocalDateTime past = LocalDateTime.now().minusMinutes(10);
        p1.setProcessingUpdatedAt(past);
        p2.setProcessingUpdatedAt(past);
        photoRepository.saveAndFlush(p1);
        photoRepository.saveAndFlush(p2);

        String thumb1 = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoWithS3Thumb);
        String thumb2 = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoWithoutS3Thumb);

        // p1 exists in S3, p2 does not
        when(s3StorageService.getObjectMetadata(thumb1))
                .thenReturn(Optional.of(HeadObjectResponse.builder().contentLength(102400L).build()));
        when(s3StorageService.getObjectMetadata(thumb2))
                .thenReturn(Optional.empty());

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoWithS3Thumb), eq(thumb1)))
                .thenReturn(1);

        // Run recovery
        photoRecoveryScheduler.recoverOrphanedPendingPhotos();

        // p1 must be reconciled and indexed
        Photo recoveredP1 = photoRepository.findById(photoWithS3Thumb).orElseThrow();
        assertEquals(ThumbnailStatus.COMPLETED, recoveredP1.getThumbnailStatus(), "p1 should be reconciled to COMPLETED");
        assertEquals(thumb1, recoveredP1.getThumbnailS3Key());

        // p2 must remain PENDING because it does not exist in S3 yet
        Photo waitingP2 = photoRepository.findById(photoWithoutS3Thumb).orElseThrow();
        assertEquals(ThumbnailStatus.PENDING, waitingP2.getThumbnailStatus(), "p2 must remain PENDING awaiting Lambda");
    }

    @Test
    @DisplayName("Scenario I: Backend restart / recovery while indexing is pending")
    void testScenarioI_RestartRecovery() {
        UUID photoId = UUID.randomUUID();
        Photo photo = createTestPhoto(photoId, UploadStatus.UPLOADED, ThumbnailStatus.COMPLETED, IndexingStatus.PENDING);
        String thumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoId);
        photo.setThumbnailS3Key(thumbKey);
        photoRepository.saveAndFlush(photo);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoId), eq(thumbKey)))
                .thenReturn(4);

        // Run scheduler (representing post-restart watchdog recovery)
        photoRecoveryScheduler.recoverStuckPhotos();

        verify(rekognitionService, timeout(2000).times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photoId),
                eq(thumbKey)
        );
    }

    @Test
    @DisplayName("Scenario J: Verify affected production photos A and C complete successfully")
    void testScenarioJ_ProductionIncidentPhotosAAndC() throws Exception {
        // Production identifiers:
        // Photo A: 59edc442-a5cc-47ea-804c-cd3b90c11a1a
        // Photo C: 32c3bd59-cb5c-4f94-855c-e4759fff66e3
        UUID photoIdA = UUID.fromString("59edc442-a5cc-47ea-804c-cd3b90c11a1a");
        UUID photoIdC = UUID.fromString("32c3bd59-cb5c-4f94-855c-e4759fff66e3");

        Photo photoA = createTestPhoto(photoIdA, UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        Photo photoC = createTestPhoto(photoIdC, UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        String thumbKeyA = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoIdA);
        String thumbKeyC = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photoIdC);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoIdA), eq(thumbKeyA)))
                .thenReturn(2);
        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photoIdC), eq(thumbKeyC)))
                .thenReturn(1);

        // --- Photo A: Callback finishes first, then photographer confirms upload ---
        ThumbnailCallbackRequest reqA = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(photoIdA)
                .eventId(testEvent.getId())
                .originalS3Key(photoA.getOriginalS3Key())
                .thumbnailS3Key(thumbKeyA)
                .status("COMPLETED")
                .build();
        executeCallback(reqA);

        // Confirm Photo A upload
        photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(List.of(photoIdA)), testUser);

        // Verify Photo A completed and indexed
        verify(rekognitionService, timeout(2000).times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photoIdA),
                eq(thumbKeyA)
        );
        Photo finalPhotoA = photoRepository.findById(photoIdA).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, finalPhotoA.getUploadStatus());
        assertEquals(ThumbnailStatus.COMPLETED, finalPhotoA.getThumbnailStatus());

        // --- Photo C: Concurrent interleaving of callback and confirmUploads ---
        ThumbnailCallbackRequest reqC = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(photoIdC)
                .eventId(testEvent.getId())
                .originalS3Key(photoC.getOriginalS3Key())
                .thumbnailS3Key(thumbKeyC)
                .status("COMPLETED")
                .build();

        byte[] rawBodyC = buildSignedCallback(reqC);
        long tsC = System.currentTimeMillis();
        String sigC = HmacSignatureValidator.calculateSignatureHex(tsC, rawBodyC, callbackSecret);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<?> f1 = pool.submit(() -> {
            latch.await();
            photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(List.of(photoIdC)), testUser);
            return null;
        });
        Future<?> f2 = pool.submit(() -> {
            latch.await();
            mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-FotoFetch-Signature", sigC)
                            .header("X-FotoFetch-Timestamp", tsC)
                            .content(rawBodyC))
                    .andExpect(status().isOk());
            return null;
        });

        latch.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Verify Photo C completed and indexed exactly once
        verify(rekognitionService, timeout(2000).times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photoIdC),
                eq(thumbKeyC)
        );
        Photo finalPhotoC = photoRepository.findById(photoIdC).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, finalPhotoC.getUploadStatus());
        assertEquals(ThumbnailStatus.COMPLETED, finalPhotoC.getThumbnailStatus());
    }
}
