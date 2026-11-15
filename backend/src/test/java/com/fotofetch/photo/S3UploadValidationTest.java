package com.fotofetch.photo;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.common.BadRequestException;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventRepository;
import com.fotofetch.photo.dto.ConfirmUploadRequest;
import com.fotofetch.photo.dto.PresignRequest;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.storage.ThumbnailService;
import com.fotofetch.user.Role;
import com.fotofetch.user.User;
import com.fotofetch.user.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class S3UploadValidationTest {

    @Autowired
    private PhotoService photoService;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ThumbnailService thumbnailService;

    @MockBean
    private S3StorageService s3StorageService;

    @MockBean
    private RekognitionService rekognitionService;

    @MockBean
    private PhotoProcessingService photoProcessingService;

    private User testUser;
    private Event testEvent;
    private Validator validator;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        testUser = userRepository.save(User.builder()
                .name("Security Photographer")
                .email("security_" + uniqueSuffix + "@photo.com")
                .password("secure_hash")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent = eventRepository.save(Event.builder()
                .name("S3 Security Validation Event")
                .eventDate(LocalDate.now())
                .publicId("s3-sec-" + uniqueSuffix)
                .rekognitionCollectionId("fotofetch-event-s3-" + uniqueSuffix)
                .user(testUser)
                .build());
    }

    @Test
    @DisplayName("PresignRequest validation: allowed image types (jpg, png, webp) pass bean validation")
    void testPresignRequestAllowedTypes() {
        PresignRequest validRequest = PresignRequest.builder()
                .files(List.of(
                        PresignRequest.FileItem.builder().filename("pic1.jpg").contentType("image/jpeg").fileSize(1000L).build(),
                        PresignRequest.FileItem.builder().filename("pic2.png").contentType("image/png").fileSize(2000L).build(),
                        PresignRequest.FileItem.builder().filename("pic3.webp").contentType("image/webp").fileSize(3000L).build()
                ))
                .build();

        var violations = validator.validate(validRequest);
        assertTrue(violations.isEmpty(), "Valid image formats must have no validation violations");
    }

    @Test
    @DisplayName("PresignRequest validation: rejected types (gif, pdf, exe) fail bean validation")
    void testPresignRequestRejectedTypes() {
        PresignRequest invalidRequest = PresignRequest.builder()
                .files(List.of(
                        PresignRequest.FileItem.builder().filename("doc.pdf").contentType("application/pdf").fileSize(1000L).build(),
                        PresignRequest.FileItem.builder().filename("anim.gif").contentType("image/gif").fileSize(2000L).build()
                ))
                .build();

        var violations = validator.validate(invalidRequest);
        assertFalse(violations.isEmpty(), "Non-supported content types must trigger validation errors");
        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("Confirm uploads: missing S3 object marks photo as FAILED")
    void testConfirmUploadsMissingS3ObjectFailsPhoto() {
        UUID photoId = UUID.randomUUID();
        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key("events/" + testEvent.getId() + "/originals/" + photoId + ".jpg")
                .originalFilename("test.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .uploadStatus(UploadStatus.PENDING)
                .isNew(true)
                .build());

        // S3 does not find the object
        when(s3StorageService.getObjectMetadata(photo.getOriginalS3Key())).thenReturn(Optional.empty());

        ConfirmUploadRequest confirmRequest = ConfirmUploadRequest.builder()
                .photoIds(List.of(photoId))
                .build();

        photoService.confirmUploads(testEvent.getId(), confirmRequest, testUser);

        Photo updated = photoRepository.findById(photoId).orElseThrow();
        assertEquals(UploadStatus.FAILED, updated.getUploadStatus());
        assertEquals("Object not found in S3", updated.getLastProcessingError());
    }

    @Test
    @DisplayName("Confirm uploads: oversized S3 object (>25MB) marks photo as FAILED")
    void testConfirmUploadsOversizedS3ObjectFailsPhoto() {
        UUID photoId = UUID.randomUUID();
        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key("events/" + testEvent.getId() + "/originals/" + photoId + ".jpg")
                .originalFilename("huge.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .uploadStatus(UploadStatus.PENDING)
                .isNew(true)
                .build());

        // S3 returns object size = 30 MB (exceeds 25MB limit)
        HeadObjectResponse metadata = HeadObjectResponse.builder()
                .contentLength(30L * 1024 * 1024)
                .contentType("image/jpeg")
                .build();
        when(s3StorageService.getObjectMetadata(photo.getOriginalS3Key())).thenReturn(Optional.of(metadata));

        ConfirmUploadRequest confirmRequest = ConfirmUploadRequest.builder()
                .photoIds(List.of(photoId))
                .build();

        photoService.confirmUploads(testEvent.getId(), confirmRequest, testUser);

        Photo updated = photoRepository.findById(photoId).orElseThrow();
        assertEquals(UploadStatus.FAILED, updated.getUploadStatus());
        assertTrue(updated.getLastProcessingError().contains("exceeds maximum allowed limit of 25MB"));
    }

    @Test
    @DisplayName("Corrupt image bytes are rejected by validateImageDecoding")
    void testCorruptImageBytesRejection() {
        byte[] corruptBytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};
        assertThrows(BadRequestException.class, () -> {
            thumbnailService.validateImageDecoding(corruptBytes);
        });
    }
}
