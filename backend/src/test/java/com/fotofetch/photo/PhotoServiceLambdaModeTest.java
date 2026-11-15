package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventRepository;
import com.fotofetch.photo.callback.ThumbnailCallbackRequest;
import com.fotofetch.photo.callback.ThumbnailCallbackService;
import com.fotofetch.photo.dto.ConfirmUploadRequest;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.user.Role;
import com.fotofetch.user.User;
import com.fotofetch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhotoServiceLambdaModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhotoRecoveryScheduler photoRecoveryScheduler;

    @Autowired
    private ThumbnailCallbackService thumbnailCallbackService;

    @MockBean
    private PhotoProcessingService photoProcessingService;

    @MockBean
    private S3StorageService s3StorageService;

    @MockBean
    private RekognitionService rekognitionService;

    private User testUser;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        reset(photoProcessingService, s3StorageService, rekognitionService);
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        when(s3StorageService.getObjectMetadata(argThat(key -> key != null && key.contains("/originals/")))).thenReturn(
                Optional.of(software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder()
                        .contentLength(1024000L)
                        .contentType("image/jpeg")
                        .build())
        );
        when(s3StorageService.getObjectMetadata(argThat(key -> key != null && key.contains("/thumbnails/")))).thenReturn(
                Optional.empty()
        );

        testUser = userRepository.save(User.builder()
                .name("Lambda Mode Photographer")
                .email("lambda.test@example.com")
                .password("encoded_pass")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent = eventRepository.save(Event.builder()
                .name("Lambda Mode Event")
                .eventDate(LocalDate.now())
                .publicId("lambda-mode-event")
                .rekognitionCollectionId("fotofetch-lambda-event")
                .user(testUser)
                .build());

        photoService.setLambdaThumbnailEnabled(true);
        photoRecoveryScheduler.setLambdaThumbnailEnabled(true);
    }

    private Photo createPhoto(UploadStatus uploadStatus, ThumbnailStatus thumbStatus, IndexingStatus indexingStatus) {
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-test.jpg", testEvent.getId(), photoId);
        return photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key(originalKey)
                .originalFilename("test.jpg")
                .contentType("image/jpeg")
                .fileSize(1024000L)
                .uploadStatus(uploadStatus)
                .thumbnailStatus(thumbStatus)
                .indexingStatus(indexingStatus)
                .isNew(true)
                .build());
    }

    @Test
    @DisplayName("1. Lambda Mode Enabled: When thumbnail is PENDING, confirmUpload does NOT invoke local Render thumbnail processing")
    void testConfirmUpload_WhenLambdaEnabled_AndThumbnailPending_SkipsRenderProcessing() {
        photoService.setLambdaThumbnailEnabled(true);
        Photo photo = createPhoto(UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ConfirmUploadRequest request = ConfirmUploadRequest.builder()
                .photoIds(List.of(photo.getId()))
                .build();

        photoService.confirmUploads(testEvent.getId(), request, testUser);

        Photo updated = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, updated.getUploadStatus());

        // Verify photoProcessingService.processPhotoAsync is NOT invoked!
        verify(photoProcessingService, never()).processPhotoAsync(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("2. Lambda Mode Disabled: When disabled, confirmUpload DOES invoke local Render thumbnail processing")
    void testConfirmUpload_WhenLambdaDisabled_InvokesRenderProcessing() {
        photoService.setLambdaThumbnailEnabled(false);
        Photo photo = createPhoto(UploadStatus.PENDING, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ConfirmUploadRequest request = ConfirmUploadRequest.builder()
                .photoIds(List.of(photo.getId()))
                .build();

        photoService.confirmUploads(testEvent.getId(), request, testUser);

        Photo updated = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, updated.getUploadStatus());

        // Verify photoProcessingService.processPhotoAsync IS invoked!
        verify(photoProcessingService, times(1)).processPhotoAsync(eq(photo.getId()), eq(testEvent.getRekognitionCollectionId()));
    }

    @Test
    @DisplayName("3. Callback-before-Confirm ordering: If thumbnail is already COMPLETED by Lambda, confirmUpload schedules Rekognition")
    void testConfirmUpload_CallbackBeforeConfirm_SchedulesRekognition() {
        photoService.setLambdaThumbnailEnabled(true);
        Photo photo = createPhoto(UploadStatus.PENDING, ThumbnailStatus.COMPLETED, IndexingStatus.PENDING);
        photo.setThumbnailS3Key(String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), photo.getId()));
        photoRepository.saveAndFlush(photo);

        ConfirmUploadRequest request = ConfirmUploadRequest.builder()
                .photoIds(List.of(photo.getId()))
                .build();

        photoService.confirmUploads(testEvent.getId(), request, testUser);

        Photo updated = photoRepository.findById(photo.getId()).orElseThrow();
        assertEquals(UploadStatus.UPLOADED, updated.getUploadStatus());

        // Verify Rekognition is scheduled because thumbnail is already COMPLETED
        verify(photoProcessingService, times(1)).processPhotoAsync(eq(photo.getId()), eq(testEvent.getRekognitionCollectionId()));
    }

    @Test
    @DisplayName("4. Duplicate confirmUpload is idempotent and skips already UPLOADED photos")
    void testDuplicateConfirmUpload_IsIdempotent() {
        photoService.setLambdaThumbnailEnabled(true);
        Photo photo = createPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);

        ConfirmUploadRequest request = ConfirmUploadRequest.builder()
                .photoIds(List.of(photo.getId()))
                .build();

        photoService.confirmUploads(testEvent.getId(), request, testUser);

        verify(photoProcessingService, never()).processPhotoAsync(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("5. PhotoRecoveryScheduler: Delayed Lambda processing (> 5m stuck in PENDING) is NOT claimed by Render worker")
    void testPhotoRecoveryScheduler_DelayedLambdaProcessing_DoesNotStartRenderProcessing() {
        photoService.setLambdaThumbnailEnabled(true);
        photoRecoveryScheduler.setLambdaThumbnailEnabled(true);
        Photo delayedPhoto = createPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        delayedPhoto.setProcessingUpdatedAt(tenMinutesAgo);
        photoRepository.saveAndFlush(delayedPhoto);

        // Run recovery watchdog
        photoRecoveryScheduler.recoverStuckPhotos();

        // Delayed Lambda photo must NOT be submitted to Render worker
        verify(photoProcessingService, never()).processPhotoAsync(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("6. Late Lambda Callback after delay: Arrives after delay and schedules Rekognition indexing cleanly")
    void testLateLambdaCallback_AfterDelay_SchedulesRekognitionCleanly() {
        photoService.setLambdaThumbnailEnabled(true);
        photoRecoveryScheduler.setLambdaThumbnailEnabled(true);
        Photo delayedPhoto = createPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        delayedPhoto.setProcessingUpdatedAt(tenMinutesAgo);
        photoRepository.saveAndFlush(delayedPhoto);

        // Watchdog ran during delay and ignored it
        photoRecoveryScheduler.recoverStuckPhotos();
        verify(photoProcessingService, never()).processPhotoAsync(any(UUID.class), anyString());

        // Now late Lambda callback arrives with COMPLETED
        String thumbKey = String.format("events/%s/thumbnails/%s.jpg", testEvent.getId(), delayedPhoto.getId());
        ThumbnailCallbackRequest request = ThumbnailCallbackRequest.builder()
                .callbackId(UUID.randomUUID().toString())
                .photoId(delayedPhoto.getId())
                .eventId(testEvent.getId())
                .originalS3Key(delayedPhoto.getOriginalS3Key())
                .thumbnailS3Key(thumbKey)
                .status("COMPLETED")
                .build();

        var response = thumbnailCallbackService.handleCallback(request);
        assertTrue(response.isIndexingScheduled());
        assertEquals("COMPLETED", response.getStatus());

        // Verify Rekognition indexing is scheduled cleanly
        verify(photoProcessingService, times(1)).processPhotoAsync(eq(delayedPhoto.getId()), eq(testEvent.getRekognitionCollectionId()));

        Photo updated = photoRepository.findById(delayedPhoto.getId()).orElseThrow();
        assertEquals(ThumbnailStatus.COMPLETED, updated.getThumbnailStatus());
        assertEquals(thumbKey, updated.getThumbnailS3Key());
    }

    @Test
    @DisplayName("7. PhotoRecoveryScheduler: Explicit FAILED thumbnail status reported by Lambda callback IS recovered")
    void testPhotoRecoveryScheduler_ExplicitFailedThumbnailRecovery() {
        photoService.setLambdaThumbnailEnabled(true);
        photoRecoveryScheduler.setLambdaThumbnailEnabled(true);
        Photo failedPhoto = createPhoto(UploadStatus.UPLOADED, ThumbnailStatus.FAILED, IndexingStatus.PENDING);
        failedPhoto.setProcessingAttempts(1);
        failedPhoto.setNextRetryAt(LocalDateTime.now().minusSeconds(10));
        photoRepository.saveAndFlush(failedPhoto);

        // Run recovery watchdog
        photoRecoveryScheduler.recoverStuckPhotos();

        // Genuinely failed photo is recovered
        verify(photoProcessingService, times(1)).processPhotoAsync(eq(failedPhoto.getId()), eq(testEvent.getRekognitionCollectionId()));
    }

    @Test
    @DisplayName("8. PhotoRecoveryScheduler: When Lambda is disabled, recovers stuck PENDING photos (> 5m ago)")
    void testPhotoRecoveryScheduler_WhenLambdaDisabled_RecoversStuckPendingPhotos() {
        photoService.setLambdaThumbnailEnabled(false);
        photoRecoveryScheduler.setLambdaThumbnailEnabled(false);
        Photo stuckPhoto = createPhoto(UploadStatus.UPLOADED, ThumbnailStatus.PENDING, IndexingStatus.PENDING);
        LocalDateTime sixMinutesAgo = LocalDateTime.now().minusMinutes(6);
        stuckPhoto.setProcessingUpdatedAt(sixMinutesAgo);
        photoRepository.saveAndFlush(stuckPhoto);

        // Run recovery watchdog
        photoRecoveryScheduler.recoverStuckPhotos();

        // When lambda is disabled, Render watchdog recovers stuck photo (original behavior)
        verify(photoProcessingService, times(1)).processPhotoAsync(eq(stuckPhoto.getId()), eq(testEvent.getRekognitionCollectionId()));
    }

    @Test
    @DisplayName("9. Security: Specific POST endpoint is permitted to HMAC validation, other internal routes are blocked")
    void testSecurity_OnlySpecificCallbackEndpointIsPermitted() throws Exception {
        // 1. Missing signature on the permitted endpoint reaches HMAC validator and returns 401 Unauthorized with custom HMAC message
        mockMvc.perform(post("/api/internal/photos/thumbnail-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // 2. GET on the callback endpoint is rejected by Spring Security (only POST is permitted)
        mockMvc.perform(get("/api/internal/photos/thumbnail-callback"))
                .andExpect(status().isUnauthorized());

        // 3. POST on other internal endpoints is rejected by Spring Security
        mockMvc.perform(post("/api/internal/photos/other-endpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
