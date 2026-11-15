package com.fotofetch.guest;

import com.fotofetch.common.ForbiddenException;
import com.fotofetch.common.UnauthorizedException;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventRepository;
import com.fotofetch.guest.session.GuestSession;
import com.fotofetch.guest.session.GuestSessionRepository;
import com.fotofetch.guest.session.GuestSessionService;
import com.fotofetch.user.Role;
import com.fotofetch.user.User;
import com.fotofetch.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GuestSessionSecurityTest {

    @Autowired
    private GuestSessionService guestSessionService;

    @Autowired
    private GuestSessionRepository guestSessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private Event testEvent1;
    private Event testEvent2;
    private UUID matchedPhoto1;
    private UUID matchedPhoto2;
    private UUID unmatchedPhoto;

    @BeforeEach
    void setUp() {
        guestSessionRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        User photographer = userRepository.save(User.builder()
                .name("Pro Photographer")
                .email("pro@events.com")
                .password("hash123")
                .role(Role.ROLE_PHOTOGRAPHER)
                .build());

        testEvent1 = eventRepository.save(Event.builder()
                .name("Gala 2026")
                .publicId("gala-2026")
                .eventDate(LocalDate.now())
                .rekognitionCollectionId("fotofetch-event-gala-2026")
                .user(photographer)
                .build());

        testEvent2 = eventRepository.save(Event.builder()
                .name("Wedding 2026")
                .publicId("wedding-2026")
                .eventDate(LocalDate.now())
                .rekognitionCollectionId("fotofetch-event-wedding-2026")
                .user(photographer)
                .build());

        matchedPhoto1 = UUID.randomUUID();
        matchedPhoto2 = UUID.randomUUID();
        unmatchedPhoto = UUID.randomUUID();
    }

    @Test
    @DisplayName("Authorized matched photos can be downloaded with valid session")
    void testAuthorizedDownloadSucceeds() {
        GuestSessionService.GuestSessionResult session = guestSessionService.createSession(
                testEvent1, Set.of(matchedPhoto1, matchedPhoto2));

        assertDoesNotThrow(() ->
                guestSessionService.authorizePhotoDownload(session.getRawToken(), testEvent1.getPublicId(), matchedPhoto1));
    }

    @Test
    @DisplayName("Guest cannot download an un-matched photo (403 Forbidden)")
    void testGuestCannotDownloadUnmatchedPhoto() {
        GuestSessionService.GuestSessionResult session = guestSessionService.createSession(
                testEvent1, Set.of(matchedPhoto1));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                guestSessionService.authorizePhotoDownload(session.getRawToken(), testEvent1.getPublicId(), unmatchedPhoto));
        assertTrue(ex.getMessage().contains("not authorized"));
    }

    @Test
    @DisplayName("Guest cannot add arbitrary photo IDs to a ZIP request (403 Forbidden)")
    void testGuestCannotAddArbitraryPhotoToZip() {
        GuestSessionService.GuestSessionResult session = guestSessionService.createSession(
                testEvent1, Set.of(matchedPhoto1, matchedPhoto2));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                guestSessionService.authorizeZipDownload(
                        session.getRawToken(),
                        testEvent1.getPublicId(),
                        List.of(matchedPhoto1, unmatchedPhoto)));
        assertTrue(ex.getMessage().contains("Unauthorized photo in ZIP request"));
    }

    @Test
    @DisplayName("Guest cannot use session from another event (403 Forbidden)")
    void testGuestCannotUseSessionForOtherEvent() {
        GuestSessionService.GuestSessionResult session = guestSessionService.createSession(
                testEvent1, Set.of(matchedPhoto1));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                guestSessionService.authorizePhotoDownload(
                        session.getRawToken(),
                        testEvent2.getPublicId(), // wrong event!
                        matchedPhoto1));
        assertTrue(ex.getMessage().contains("not authorized for event"));
    }

    @Test
    @DisplayName("Expired session is rejected (401 Unauthorized)")
    void testExpiredSessionIsRejected() {
        GuestSessionService.GuestSessionResult sessionResult = guestSessionService.createSession(
                testEvent1, Set.of(matchedPhoto1));

        // Manually expire session in DB
        GuestSession session = guestSessionRepository.findById(sessionResult.getSessionId()).orElseThrow();
        session.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        guestSessionRepository.save(session);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                guestSessionService.authorizePhotoDownload(
                        sessionResult.getRawToken(),
                        testEvent1.getPublicId(),
                        matchedPhoto1));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Revoked session is rejected (401 Unauthorized)")
    void testRevokedSessionIsRejected() {
        GuestSessionService.GuestSessionResult sessionResult = guestSessionService.createSession(
                testEvent1, Set.of(matchedPhoto1));

        // Revoke session
        guestSessionService.revokeSession(sessionResult.getRawToken(), testEvent1.getPublicId());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                guestSessionService.authorizePhotoDownload(
                        sessionResult.getRawToken(),
                        testEvent1.getPublicId(),
                        matchedPhoto1));
        assertTrue(ex.getMessage().contains("revoked"));
    }

    @Test
    @DisplayName("Invalid or non-existent token is rejected (401 Unauthorized)")
    void testInvalidTokenIsRejected() {
        assertThrows(UnauthorizedException.class, () ->
                guestSessionService.authorizePhotoDownload(
                        "completely-invalid-nonexistent-token-12345",
                        testEvent1.getPublicId(),
                        matchedPhoto1));
    }
}
