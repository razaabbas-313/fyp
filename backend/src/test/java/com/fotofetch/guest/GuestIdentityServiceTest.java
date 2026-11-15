package com.fotofetch.guest;

import com.fotofetch.common.BadRequestException;
import com.fotofetch.common.ResourceNotFoundException;
import com.fotofetch.common.UnauthorizedException;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventRepository;
import com.fotofetch.guest.identity.GuestIdentity;
import com.fotofetch.guest.identity.GuestIdentityRepository;
import com.fotofetch.guest.identity.GuestIdentityService;
import com.fotofetch.guest.identity.dto.GuestIdentityResponse;
import com.fotofetch.guest.session.GuestSessionRepository;
import com.fotofetch.guest.session.GuestSessionService;
import com.fotofetch.photo.Photo;
import com.fotofetch.photo.PhotoRepository;
import com.fotofetch.photo.UploadStatus;
import com.fotofetch.photo.dto.GuestSearchResponse;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class GuestIdentityServiceTest {

    @Autowired
    private GuestIdentityService guestIdentityService;

    @Autowired
    private GuestIdentityRepository guestIdentityRepository;

    @Autowired
    private GuestSessionService guestSessionService;

    @Autowired
    private GuestSessionRepository guestSessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @MockBean
    private S3StorageService s3StorageService;

    private User testUser;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        guestIdentityRepository.deleteAll();
        guestSessionRepository.deleteAll();
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        testUser = userRepository.save(User.builder()
                .name("Identity Photographer")
                .email("identity_" + suffix + "@photo.com")
                .password("hash")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent = eventRepository.save(Event.builder()
                .name("Annual Tech Summit")
                .eventDate(LocalDate.now())
                .publicId("summit-" + suffix)
                .rekognitionCollectionId("fotofetch-summit-" + suffix)
                .user(testUser)
                .build());

        when(s3StorageService.generatePresignedDownloadUrl(anyString(), anyString()))
                .thenReturn("https://s3.amazonaws.com/test-bucket/download");
        when(s3StorageService.generatePresignedDownloadUrl(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("https://s3.amazonaws.com/test-bucket/thumb");
    }

    @Test
    @DisplayName("Unique username generation: generates base username 'ali' when not taken")
    void testGenerateUniqueUsernameBase() {
        String username = guestIdentityService.generateUniqueUsername(testEvent.getId(), "Ali");
        assertEquals("ali", username);
    }

    @Test
    @DisplayName("Unique username generation: appends number 'ali2', 'ali3' when base username already exists")
    void testGenerateUniqueUsernameCollision() {
        // Save first user as 'ali'
        guestIdentityRepository.save(GuestIdentity.builder()
                .event(testEvent)
                .name("Ali First")
                .username("ali")
                .build());

        String username2 = guestIdentityService.generateUniqueUsername(testEvent.getId(), "Ali");
        assertEquals("ali2", username2);

        // Save 'ali2'
        guestIdentityRepository.save(GuestIdentity.builder()
                .event(testEvent)
                .name("Ali Second")
                .username("ali2")
                .build());

        String username3 = guestIdentityService.generateUniqueUsername(testEvent.getId(), "Ali");
        assertEquals("ali3", username3);
    }

    @Test
    @DisplayName("Face-to-name tagging: registers guest identity with session photos and generates username")
    void testRegisterGuestIdentitySuccess() {
        UUID photo1 = UUID.randomUUID();
        UUID photo2 = UUID.randomUUID();

        // Create a guest search session with 2 matched photos
        GuestSessionService.GuestSessionResult session =
                guestSessionService.createSession(testEvent, Set.of(photo1, photo2));

        GuestIdentityResponse response = guestIdentityService.registerGuestIdentity(
                testEvent.getPublicId(), "Ali Raza", session.getRawToken());

        assertNotNull(response);
        assertEquals("Ali Raza", response.getName());
        assertEquals("aliraza", response.getUsername());
        assertEquals(2, response.getPhotoCount());
        assertTrue(response.getMessage().contains("@aliraza"));

        // Verify persisted in repository
        assertTrue(guestIdentityRepository.findByEventIdAndUsernameIgnoreCase(testEvent.getId(), "aliraza").isPresent());
    }

    @Test
    @DisplayName("Collision resolution during tagging: second 'Ali' gets unique username 'ali2'")
    void testRegisterSecondGuestSameNameGetsAli2() {
        UUID photo1 = UUID.randomUUID();
        GuestSessionService.GuestSessionResult session1 =
                guestSessionService.createSession(testEvent, Set.of(photo1));

        // First guest registers "Ali" -> username "ali"
        GuestIdentityResponse response1 = guestIdentityService.registerGuestIdentity(
                testEvent.getPublicId(), "Ali", session1.getRawToken());
        assertEquals("ali", response1.getUsername());

        // Second guest also registers "Ali" -> username "ali2"
        UUID photo2 = UUID.randomUUID();
        GuestSessionService.GuestSessionResult session2 =
                guestSessionService.createSession(testEvent, Set.of(photo2));

        GuestIdentityResponse response2 = guestIdentityService.registerGuestIdentity(
                testEvent.getPublicId(), "Ali", session2.getRawToken());
        assertEquals("ali2", response2.getUsername());
    }

    @Test
    @Transactional
    @DisplayName("Subsequent name-based search: searching by username retrieves photos and creates authorized download session")
    void testSearchByUsernameExactMatch() {
        UUID photoId = UUID.randomUUID();
        Photo photo = photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key("events/" + testEvent.getId() + "/originals/" + photoId + ".jpg")
                .originalFilename("ali_photo.jpg")
                .contentType("image/jpeg")
                .fileSize(2048000L)
                .uploadStatus(UploadStatus.UPLOADED)
                .isNew(true)
                .build());

        GuestSessionService.GuestSessionResult session =
                guestSessionService.createSession(testEvent, Set.of(photoId));

        guestIdentityService.registerGuestIdentity(testEvent.getPublicId(), "Ali", session.getRawToken());

        // Search by username "ali"
        GuestSearchResponse searchResponse = guestIdentityService.searchByNameOrUsername(testEvent.getPublicId(), "ali");

        assertNotNull(searchResponse);
        assertNotNull(searchResponse.getSessionToken(), "Must generate authorized guest session token");
        assertEquals("Ali", searchResponse.getTaggedName());
        assertEquals("ali", searchResponse.getTaggedUsername());
        assertEquals(1, searchResponse.getMatches().size());
        assertEquals(photoId, searchResponse.getMatches().get(0).getPhotoId());

        // Verify the generated session token allows downloading this photo
        assertDoesNotThrow(() -> {
            guestSessionService.authorizePhotoDownload(
                    searchResponse.getSessionToken(), testEvent.getPublicId(), photoId);
        });
    }

    @Test
    @Transactional
    @DisplayName("Subsequent name-based search: searching by display name (case-insensitive) retrieves tagged identity")
    void testSearchByNameMatch() {
        UUID photoId = UUID.randomUUID();
        photoRepository.save(Photo.builder()
                .id(photoId)
                .event(testEvent)
                .originalS3Key("events/" + testEvent.getId() + "/originals/" + photoId + ".jpg")
                .originalFilename("sara.jpg")
                .contentType("image/jpeg")
                .fileSize(1000L)
                .uploadStatus(UploadStatus.UPLOADED)
                .isNew(true)
                .build());

        GuestSessionService.GuestSessionResult session =
                guestSessionService.createSession(testEvent, Set.of(photoId));

        guestIdentityService.registerGuestIdentity(testEvent.getPublicId(), "Sara Khan", session.getRawToken());

        // Search by name "sara khan"
        GuestSearchResponse searchResponse = guestIdentityService.searchByNameOrUsername(testEvent.getPublicId(), "sara khan");

        assertEquals("Sara Khan", searchResponse.getTaggedName());
        assertEquals("sarakhan", searchResponse.getTaggedUsername());
        assertEquals(1, searchResponse.getMatches().size());
    }

    @Test
    @DisplayName("Subsequent name-based search: searching for non-existent name throws 404 ResourceNotFoundException")
    void testSearchByUnknownNameThrows404() {
        assertThrows(ResourceNotFoundException.class, () -> {
            guestIdentityService.searchByNameOrUsername(testEvent.getPublicId(), "nobody");
        });
    }

    @Test
    @DisplayName("Tagging without valid session token throws UnauthorizedException")
    void testRegisterWithoutValidSessionThrowsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> {
            guestIdentityService.registerGuestIdentity(testEvent.getPublicId(), "Hacker", "invalid-token");
        });
    }

    @Test
    @DisplayName("Tagging with short name throws BadRequestException")
    void testRegisterWithShortNameThrowsBadRequest() {
        UUID photo1 = UUID.randomUUID();
        GuestSessionService.GuestSessionResult session =
                guestSessionService.createSession(testEvent, Set.of(photo1));

        assertThrows(BadRequestException.class, () -> {
            guestIdentityService.registerGuestIdentity(testEvent.getPublicId(), "a", session.getRawToken());
        });
    }
}
