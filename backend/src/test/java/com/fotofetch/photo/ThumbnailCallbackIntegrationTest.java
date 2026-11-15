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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ThumbnailCallbackIntegrationTest {

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
                .name("Test Photographer")
                .email("photographer@test.com")
                .password("hashed_password")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent = eventRepository.save(Event.builder()
                .name("Gala Night")
                .eventDate(LocalDate.now())
                .publicId("gala-2026")
                .rekognitionCollectionId("fotofetch-event-gala-2026")
                .user(testUser)
                .build());
    }

    private Photo createTestPhoto(UploadStatus uploadStatus, ThumbnailStatus thumbStatus, IndexingStatus indexingStatus) {
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-photo.jpg", testEvent.getId(), photoId);
        return photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key(originalKey)
                .originalFilename("photo.jpg")
                .contentType("image/jpeg")
                .fileSize(2048000L)
                .uploadStatus(uploadStatus)
                .thumbnailStatus(thumbStatus)
                .indexingStatus(indexingStatus)
                .isNew(true)
                .build());
    }

    private ThumbnailCallbackRequest.ThumbnailCallbackRequestBuilder createCallbackBuilder(Photo photo) {
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photo.getId());
        return ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(photo.getId())
                .eventId(testEvent.getId())
                .originalS3Key(photo.getOriginalS3Key())
                .thumbnailS3Key(expectedThumbKey)
                .status("COMPLETED")
                .width(1024)
                .height(768)
                .fileSize(150000L);
    }

    @Test
    @DisplayName("1. Valid HMAC: Callback successfully updates thumbnail status and schedules Rekognition if photo is UPLOADED")
    void testValidHmacCallbackSuccess() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photo.getId());

        ThumbnailCallbackRequest request = createCallbackBuilder(photo).build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photo.getId()), eq(expectedThumbKey)))
                .thenReturn(1);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.indexingScheduled").value(true));

        Photo updated = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(ThumbnailStatus.COMPLETED, updated.getThumbnailStatus());
        assertEquals(expectedThumbKey, updated.getThumbnailS3Key());
        assertNull(updated.getLastProcessingError());
    }

    @Test
    @DisplayName("2. Invalid HMAC: Callback with forged or tampered signature is rejected with 401 Unauthorized")
    void testInvalidHmacRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ThumbnailCallbackRequest request = createCallbackBuilder(photo).build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String badSignature = "deadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678";

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", badSignature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        Photo unChanged = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(ThumbnailStatus.PENDING, unChanged.getThumbnailStatus());
    }

    @Test
    @DisplayName("3. Expired Timestamp: Callback older than 5 minutes is rejected with 401 Unauthorized")
    void testExpiredTimestampRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ThumbnailCallbackRequest request = createCallbackBuilder(photo).build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        // 10 minutes in the past
        long expiredTimestamp = System.currentTimeMillis() - (10 * 60 * 1000L);
        String signature = HmacSignatureValidator.calculateSignatureHex(expiredTimestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", expiredTimestamp)
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("4. Future Timestamp: Callback with timestamp > 5 minutes in future is rejected")
    void testFutureTimestampRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ThumbnailCallbackRequest request = createCallbackBuilder(photo).build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        // 10 minutes in the future
        long futureTimestamp = System.currentTimeMillis() + (10 * 60 * 1000L);
        String signature = HmacSignatureValidator.calculateSignatureHex(futureTimestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", futureTimestamp)
                        .content(rawBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. Duplicate successful callback: Acknowledges duplicate without double processing")
    void testDuplicateSuccessfulCallback() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.COMPLETED, IndexingStatus.COMPLETED);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photo.getId());
        photo.setThumbnailS3Key(expectedThumbKey);
        photoRepository.save(photo);

        ThumbnailCallbackRequest request = createCallbackBuilder(photo).build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ALREADY_COMPLETED"))
                .andExpect(jsonPath("$.data.indexingScheduled").value(false));

        // Rekognition indexing should NOT be triggered again
        verify(rekognitionService, never()).indexPhotoFaces(anyString(), any(UUID.class), anyString());
    }

    @Test
    @DisplayName("6. Callback before upload confirmation: Saves thumbnail completion but defers Rekognition until confirmUploads")
    void testCallbackBeforeUploadConfirmation() throws Exception {
        // Photo is in uploadStatus = PENDING
        Photo photo = createTestPhoto(UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photo.getId());

        ThumbnailCallbackRequest request = createCallbackBuilder(photo).build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        // 1. Callback arrives BEFORE confirmation
        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.indexingScheduled").value(false));

        Photo midState = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(ThumbnailStatus.COMPLETED, midState.getThumbnailStatus());
        assertEquals(UploadStatus.PENDING, midState.getUploadStatus());

        // 2. Later, photographer confirms upload
        software.amazon.awssdk.services.s3.model.HeadObjectResponse mockHead =
                software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder()
                        .contentLength(2048000L)
                        .build();
        when(s3StorageService.getObjectMetadata(photo.getOriginalS3Key())).thenReturn(Optional.of(mockHead));
        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photo.getId()), eq(expectedThumbKey)))
                .thenReturn(1);

        photoService.confirmUploads(testEvent.getId(), new ConfirmUploadRequest(List.of(photo.getId())), testUser);

        Photo confirmed = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, confirmed.getUploadStatus());
        assertEquals(ThumbnailStatus.COMPLETED, confirmed.getThumbnailStatus());
    }

    @Test
    @DisplayName("7. Photo deleted before callback: Acknowledges safely without recreating database row")
    void testPhotoDeletedBeforeCallback() throws Exception {
        UUID nonExistentPhotoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-deleted.jpg", testEvent.getId(), nonExistentPhotoId);
        String thumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), nonExistentPhotoId);

        ThumbnailCallbackRequest request = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(nonExistentPhotoId)
                .eventId(testEvent.getId())
                .originalS3Key(originalKey)
                .thumbnailS3Key(thumbKey)
                .status("COMPLETED")
                .build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_FOUND_ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.indexingScheduled").value(false));

        // Ensure record was NOT recreated in DB
        assertFalse(photoRepository.existsById(nonExistentPhotoId));
    }

    @Test
    @DisplayName("8. Wrong Event ID: Callback with mismatched eventId is rejected with 400 Bad Request")
    void testWrongEventIdRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ThumbnailCallbackRequest request = createCallbackBuilder(photo)
                .eventId(UUID.randomUUID()) // Wrong event ID
                .build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Event ID mismatch")));
    }

    @Test
    @DisplayName("9. Wrong Original S3 Key: Callback with mismatched original S3 key is rejected with 400 Bad Request")
    void testWrongOriginalS3KeyRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ThumbnailCallbackRequest request = createCallbackBuilder(photo)
                .originalS3Key("events/other/originals/photo.jpg") // Wrong original S3 key
                .build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Original S3 key mismatch")));
    }

    @Test
    @DisplayName("10. Wrong Thumbnail S3 Key: Callback with unexpected thumbnail key format is rejected with 400 Bad Request")
    void testWrongThumbnailS3KeyRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ThumbnailCallbackRequest request = createCallbackBuilder(photo)
                .thumbnailS3Key("wrong-path/malicious.jpg") // Wrong thumbnail key
                .build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid thumbnail S3 key")));
    }

    @Test
    @DisplayName("11. Failure Callback: Persists sanitized error, sets status FAILED, and does NOT complete Rekognition")
    void testFailedCallbackPersistsSanitizedError() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ThumbnailCallbackRequest request = createCallbackBuilder(photo)
                .thumbnailS3Key(null)
                .status("FAILED")
                .errorMessage("Image decoding crashed on byte 1024\u0000\u0007")
                .build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.indexingScheduled").value(false));

        Photo updated = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(ThumbnailStatus.FAILED, updated.getThumbnailStatus());
        assertEquals(IndexingStatus.PENDING, updated.getIndexingStatus()); // Not marked completed!
        assertEquals(1, updated.getProcessingAttempts());
        assertNotNull(updated.getNextRetryAt());
        assertFalse(updated.getLastProcessingError().contains("\u0000")); // Sanitized!
    }

    @Test
    @DisplayName("12. Crash/Retry Recovery: If server restarts after thumbnail completion, PhotoRecoveryScheduler recovers indexing")
    void testCrashRecoveryBetweenThumbnailCompletionAndRekognitionIndexing() {
        // Simulate photo where thumbnail was completed via Lambda, upload confirmed, but server crashed before Rekognition ran
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.COMPLETED, IndexingStatus.PENDING);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photo.getId());
        photo.setThumbnailS3Key(expectedThumbKey);
        photoRepository.saveAndFlush(photo);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photo.getId()), eq(expectedThumbKey)))
                .thenReturn(3);

        // Watchdog scheduler runs
        photoRecoveryScheduler.recoverStuckPhotos();

        // Rekognition indexing is recovered
        verify(rekognitionService, timeout(2000).times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photo.getId()),
                eq(expectedThumbKey)
        );

        // Await asynchronous database update completion
        long deadline = System.currentTimeMillis() + 3000;
        Photo recovered = null;
        while (System.currentTimeMillis() < deadline) {
            recovered = photoRepository.findById(photo.getId()).orElse(null);
            if (recovered != null && recovered.getIndexingStatus() == IndexingStatus.COMPLETED) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }

        assertNotNull(recovered);
        assertEquals(ThumbnailStatus.COMPLETED, recovered.getThumbnailStatus());
        assertEquals(IndexingStatus.COMPLETED, recovered.getIndexingStatus());
        assertEquals(3, recovered.getFacesFound());
    }

    @Test
    @DisplayName("13. Concurrent Rekognition Indexing: CAS lock ensures indexPhotoFaces is executed exactly once across concurrent workers")
    void testConcurrentRekognitionIndexing_AtomicCasProtection() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.COMPLETED, IndexingStatus.PENDING);
        String thumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photo.getId());
        photo.setThumbnailS3Key(thumbKey);
        photoRepository.saveAndFlush(photo);

        when(rekognitionService.indexPhotoFaces(eq(testEvent.getRekognitionCollectionId()), eq(photo.getId()), eq(thumbKey)))
                .thenAnswer(invocation -> {
                    Thread.sleep(100);
                    return 2;
                });

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Void> task = () -> {
            photoProcessingService.processPhoto(photo.getId(), testEvent.getRekognitionCollectionId());
            return null;
        };

        java.util.concurrent.Future<Void> future1 = executor.submit(task);
        java.util.concurrent.Future<Void> future2 = executor.submit(task);

        future1.get(5, java.util.concurrent.TimeUnit.SECONDS);
        future2.get(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // Rekognition indexing must be called EXACTLY ONCE
        verify(rekognitionService, times(1)).indexPhotoFaces(
                eq(testEvent.getRekognitionCollectionId()),
                eq(photo.getId()),
                eq(thumbKey)
        );

        Photo updated = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(IndexingStatus.COMPLETED, updated.getIndexingStatus());
        assertEquals(2, updated.getFacesFound());
    }

    @Test
    @DisplayName("14. Replay Protection: Repeated identical callback is rejected with 400 Bad Request")
    void testRepeatedIdenticalCallbackRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        ThumbnailCallbackRequest request = createCallbackBuilder(photo).build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        // First attempt succeeds
        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Exact replay is rejected
        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("has already been processed")));
    }

    @Test
    @DisplayName("15. Replay Protection: Repeated callback with the same callbackId is rejected even if payload modified")
    void testRepeatedCallbackWithSameIdRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String callbackId = "callback-" + UUID.randomUUID();

        ThumbnailCallbackRequest request1 = createCallbackBuilder(photo)
                .callbackId(callbackId)
                .status("COMPLETED")
                .build();

        byte[] rawBody1 = objectMapper.writeValueAsBytes(request1);
        long timestamp1 = System.currentTimeMillis();
        String sig1 = HmacSignatureValidator.calculateSignatureHex(timestamp1, rawBody1, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", sig1)
                        .header("X-FotoFetch-Timestamp", timestamp1)
                        .content(rawBody1))
                .andExpect(status().isOk());

        // Second request with SAME callbackId but status FAILED
        ThumbnailCallbackRequest request2 = createCallbackBuilder(photo)
                .callbackId(callbackId)
                .status("FAILED")
                .errorMessage("Different failure payload")
                .build();

        byte[] rawBody2 = objectMapper.writeValueAsBytes(request2);
        long timestamp2 = System.currentTimeMillis();
        String sig2 = HmacSignatureValidator.calculateSignatureHex(timestamp2, rawBody2, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", sig2)
                        .header("X-FotoFetch-Timestamp", timestamp2)
                        .content(rawBody2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("has already been processed")));
    }

    @Test
    @DisplayName("16. Replay Protection: Concurrent duplicate callbacks with same ID - one succeeds, second is rejected")
    void testConcurrentDuplicateCallbacks() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String callbackId = "concurrent-cb-" + UUID.randomUUID();

        ThumbnailCallbackRequest request = createCallbackBuilder(photo)
                .callbackId(callbackId)
                .build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicInteger okCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger badRequestCount = new java.util.concurrent.atomic.AtomicInteger();

        java.util.concurrent.Callable<Void> task = () -> {
            try {
                var result = mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-FotoFetch-Signature", signature)
                                .header("X-FotoFetch-Timestamp", timestamp)
                                .content(rawBody))
                        .andReturn();
                int status = result.getResponse().getStatus();
                if (status == 200) {
                    okCount.incrementAndGet();
                } else if (status == 400) {
                    badRequestCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
            return null;
        };

        java.util.concurrent.Future<Void> f1 = executor.submit(task);
        java.util.concurrent.Future<Void> f2 = executor.submit(task);
        f1.get(5, java.util.concurrent.TimeUnit.SECONDS);
        f2.get(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, okCount.get(), "Exactly one concurrent callback must succeed");
        assertEquals(1, badRequestCount.get(), "The duplicate concurrent callback must be rejected with 400 Bad Request");
        assertTrue(processedCallbackRepository.existsById(callbackId));
    }

    @Test
    @DisplayName("17. Replay Protection: Valid callback replayed within the 5-minute timestamp window is rejected")
    void testReplayWithinFiveMinuteWindowRejected() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        String callbackId = "window-cb-" + UUID.randomUUID();

        ThumbnailCallbackRequest request = createCallbackBuilder(photo)
                .callbackId(callbackId)
                .build();

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        // Valid timestamp: 2 minutes ago (well within 5-minute window)
        long timestamp = System.currentTimeMillis() - (2 * 60 * 1000L);
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, callbackSecret);

        // 1. First execution succeeds
        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isOk());

        // 2. Replay 2 seconds later (still well within 5-minute window)
        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", signature)
                        .header("X-FotoFetch-Timestamp", timestamp)
                        .content(rawBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("has already been processed")));
    }

    @Test
    @DisplayName("18. Legitimate Retry: New valid callback for same photo with new callbackId is accepted")
    void testLegitimateRetryWithNewCallbackIdAccepted() throws Exception {
        Photo photo = createTestPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        // 1. First attempt fails (Lambda reports failure with callbackId 1)
        String failCallbackId = "fail-attempt-" + UUID.randomUUID();
        ThumbnailCallbackRequest failRequest = createCallbackBuilder(photo)
                .callbackId(failCallbackId)
                .thumbnailS3Key(null)
                .status("FAILED")
                .errorMessage("Temporary transcoding failure")
                .build();

        byte[] rawBody1 = objectMapper.writeValueAsBytes(failRequest);
        long ts1 = System.currentTimeMillis();
        String sig1 = HmacSignatureValidator.calculateSignatureHex(ts1, rawBody1, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", sig1)
                        .header("X-FotoFetch-Timestamp", ts1)
                        .content(rawBody1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        Photo failedPhoto = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(ThumbnailStatus.FAILED, failedPhoto.getThumbnailStatus());
        assertEquals(1, failedPhoto.getProcessingAttempts());

        // 2. Legitimate retry succeeds with a NEW callbackId
        String successCallbackId = "success-retry-" + UUID.randomUUID();
        ThumbnailCallbackRequest successRequest = createCallbackBuilder(photo)
                .callbackId(successCallbackId)
                .status("COMPLETED")
                .build();

        byte[] rawBody2 = objectMapper.writeValueAsBytes(successRequest);
        long ts2 = System.currentTimeMillis();
        String sig2 = HmacSignatureValidator.calculateSignatureHex(ts2, rawBody2, callbackSecret);

        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-FotoFetch-Signature", sig2)
                        .header("X-FotoFetch-Timestamp", ts2)
                        .content(rawBody2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        Photo completedPhoto = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(ThumbnailStatus.COMPLETED, completedPhoto.getThumbnailStatus());

        // Both callback IDs are recorded in the database
        assertTrue(processedCallbackRepository.existsById(failCallbackId));
        assertTrue(processedCallbackRepository.existsById(successCallbackId));
    }
}
