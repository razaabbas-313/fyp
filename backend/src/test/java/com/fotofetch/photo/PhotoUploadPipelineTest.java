package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventRepository;
import com.fotofetch.photo.dto.ConfirmUploadRequest;
import com.fotofetch.photo.dto.PresignRequest;
import com.fotofetch.photo.dto.PresignResponse;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.user.Role;
import com.fotofetch.user.User;
import com.fotofetch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class PhotoUploadPipelineTest {

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
    private RekognitionService rekognitionService;

    @MockBean
    private PhotoProcessingService photoProcessingService;

    private User testUser;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        when(s3StorageService.generatePresignedUploadUrl(anyString(), anyString()))
                .thenReturn("https://test-bucket.s3.amazonaws.com/test-url");

        testUser = userRepository.save(User.builder()
                .name("Test Photographer")
                .email("test@example.com")
                .password("encoded_pass")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent = eventRepository.save(Event.builder()
                .name("Annual Gala")
                .eventDate(LocalDate.now())
                .publicId("gala-2026-x1y2")
                .rekognitionCollectionId("fotofetch-event-gala-2026-x1y2")
                .user(testUser)
                .build());
    }

    @Test
    @DisplayName("Verify that presigned photo IDs match database IDs exactly and confirmation updates status to UPLOADED")
    void testPresignedUploadAndConfirmationPipeline() {
        // 1. Request presigned upload for 2 photos
        PresignRequest request = PresignRequest.builder()
                .files(List.of(
                        PresignRequest.FileItem.builder()
                                .filename("portrait_1.jpg")
                                .contentType("image/jpeg")
                                .fileSize(1024000L)
                                .build(),
                        PresignRequest.FileItem.builder()
                                .filename("portrait_2.jpg")
                                .contentType("image/jpeg")
                                .fileSize(2048000L)
                                .build()
                ))
                .build();

        PresignResponse presignResponse = photoService.createPresignedUploads(testEvent.getId(), request, testUser);

        assertNotNull(presignResponse);
        assertEquals(2, presignResponse.getUploads().size());

        UUID photoId1 = presignResponse.getUploads().get(0).getPhotoId();
        UUID photoId2 = presignResponse.getUploads().get(1).getPhotoId();

        // 2. CRITICAL VERIFICATION: Database record must exist with the EXACT photoId generated and returned!
        Optional<Photo> photo1Opt = photoRepository.findById(photoId1);
        Optional<Photo> photo2Opt = photoRepository.findById(photoId2);

        assertTrue(photo1Opt.isPresent(), "Photo 1 must exist in database with returned photoId: " + photoId1);
        assertTrue(photo2Opt.isPresent(), "Photo 2 must exist in database with returned photoId: " + photoId2);

        Photo photo1 = photo1Opt.get();
        assertEquals(photoId1, photo1.getId(), "Persisted ID must equal returned ID");
        assertEquals(UploadStatus.PENDING, photo1.getUploadStatus());
        assertTrue(photo1.getOriginalS3Key().contains(photoId1.toString()));

        // 3. Confirm uploads
        ConfirmUploadRequest confirmRequest = ConfirmUploadRequest.builder()
                .photoIds(List.of(photoId1, photoId2))
                .build();

        photoService.confirmUploads(testEvent.getId(), confirmRequest, testUser);

        // 4. Verify status updated to UPLOADED
        Optional<Photo> confirmedPhoto1 = photoRepository.findByIdAndEventId(photoId1, testEvent.getId());
        Optional<Photo> confirmedPhoto2 = photoRepository.findByIdAndEventId(photoId2, testEvent.getId());

        assertTrue(confirmedPhoto1.isPresent());
        assertTrue(confirmedPhoto2.isPresent());
        assertEquals(UploadStatus.UPLOADED, confirmedPhoto1.get().getUploadStatus());
        assertEquals(UploadStatus.UPLOADED, confirmedPhoto2.get().getUploadStatus());
    }

    @Test
    @DisplayName("Verify single photo deletion removes photo from DB, S3, and Rekognition")
    void testDeleteSinglePhoto() {
        PresignRequest request = PresignRequest.builder()
                .files(List.of(
                        PresignRequest.FileItem.builder()
                                .filename("to_delete.jpg")
                                .contentType("image/jpeg")
                                .fileSize(500000L)
                                .build()
                ))
                .build();

        PresignResponse presignResponse = photoService.createPresignedUploads(testEvent.getId(), request, testUser);
        UUID photoId = presignResponse.getUploads().get(0).getPhotoId();

        assertTrue(photoRepository.findById(photoId).isPresent());

        // Perform single photo delete
        photoService.deletePhoto(testEvent.getId(), photoId, testUser);

        // Verify photo is removed from DB
        assertTrue(photoRepository.findById(photoId).isEmpty(), "Deleted photo must no longer exist in database");
    }

    @Test
    @DisplayName("Verify bulk photo deletion removes all selected photos from DB, S3, and Rekognition")
    void testBulkDeletePhotos() {
        PresignRequest request = PresignRequest.builder()
                .files(List.of(
                        PresignRequest.FileItem.builder()
                                .filename("bulk_1.jpg")
                                .contentType("image/jpeg")
                                .fileSize(500000L)
                                .build(),
                        PresignRequest.FileItem.builder()
                                .filename("bulk_2.jpg")
                                .contentType("image/jpeg")
                                .fileSize(600000L)
                                .build(),
                        PresignRequest.FileItem.builder()
                                .filename("bulk_3.jpg")
                                .contentType("image/jpeg")
                                .fileSize(700000L)
                                .build()
                ))
                .build();

        PresignResponse presignResponse = photoService.createPresignedUploads(testEvent.getId(), request, testUser);
        List<UUID> photoIds = presignResponse.getUploads().stream().map(PresignResponse.PresignedFile::getPhotoId).toList();

        assertEquals(3, photoRepository.findAll().size());

        // Delete first two photos in bulk
        List<UUID> toDelete = List.of(photoIds.get(0), photoIds.get(1));
        int deletedCount = photoService.deletePhotosBulk(testEvent.getId(), toDelete, testUser);

        assertEquals(2, deletedCount);
        assertTrue(photoRepository.findById(photoIds.get(0)).isEmpty());
        assertTrue(photoRepository.findById(photoIds.get(1)).isEmpty());
        assertTrue(photoRepository.findById(photoIds.get(2)).isPresent(), "Unselected photo 3 should remain");
    }
}
