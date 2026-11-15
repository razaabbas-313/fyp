package com.fotofetch.guest.session;

import com.fotofetch.common.BadRequestException;
import com.fotofetch.common.ForbiddenException;
import com.fotofetch.common.UnauthorizedException;
import com.fotofetch.event.Event;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestSessionService {

    private final GuestSessionRepository guestSessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${fotofetch.guest.session.expiration-minutes:120}")
    private int sessionExpirationMinutes;

    @Value("${fotofetch.guest.session.max-zip-photos:50}")
    private int maxZipPhotos;

    @Data
    @Builder
    public static class GuestSessionResult {
        private String rawToken;
        private LocalDateTime expiresAt;
        private UUID sessionId;
    }

    @Transactional
    public GuestSessionResult createSession(Event event, Set<UUID> matchedPhotoIds) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = hashToken(rawToken);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(sessionExpirationMinutes);

        GuestSession session = GuestSession.builder()
                .sessionTokenHash(tokenHash)
                .event(event)
                .matchedPhotoIds(matchedPhotoIds != null ? new HashSet<>(matchedPhotoIds) : new HashSet<>())
                .createdAt(now)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        GuestSession saved = guestSessionRepository.save(session);
        log.info("Created secure guest session {} for event {} with {} matched photo(s), expires at {}",
                saved.getId(), event.getId(), saved.getMatchedPhotoIds().size(), expiresAt);

        return GuestSessionResult.builder()
                .rawToken(rawToken)
                .expiresAt(expiresAt)
                .sessionId(saved.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public GuestSession validateSession(String rawToken, String publicId) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Guest authorization token is required to access photo downloads");
        }

        String cleanedToken = rawToken.trim();
        if (cleanedToken.toLowerCase().startsWith("bearer ")) {
            cleanedToken = cleanedToken.substring(7).trim();
        }

        String tokenHash = hashToken(cleanedToken);
        GuestSession session = guestSessionRepository.findBySessionTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or unrecognized guest session token"));

        if (session.isRevoked()) {
            throw new UnauthorizedException("Guest session has been revoked. Please submit a new selfie.");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Guest session has expired. Please submit a new selfie to continue.");
        }

        if (session.getEvent() == null || !session.getEvent().getPublicId().equalsIgnoreCase(publicId)) {
            throw new ForbiddenException("Guest session is not authorized for event: " + publicId);
        }

        return session;
    }

    @Transactional(readOnly = true)
    public void authorizePhotoDownload(String rawToken, String publicId, UUID photoId) {
        GuestSession session = validateSession(rawToken, publicId);

        if (photoId == null || !session.getMatchedPhotoIds().contains(photoId)) {
            log.warn("Unauthorized photo download attempt for photo {} under session {} (event {})",
                    photoId, session.getId(), publicId);
            throw new ForbiddenException("You are not authorized to download this photo. It is not among your facial recognition matches.");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> authorizeZipDownload(String rawToken, String publicId, List<UUID> requestedPhotoIds) {
        GuestSession session = validateSession(rawToken, publicId);

        if (requestedPhotoIds == null || requestedPhotoIds.isEmpty()) {
            throw new BadRequestException("No photo IDs specified for ZIP download");
        }

        if (requestedPhotoIds.size() > maxZipPhotos) {
            throw new BadRequestException("Requested ZIP contains " + requestedPhotoIds.size() +
                    " photos, which exceeds the maximum allowed limit of " + maxZipPhotos + " photos.");
        }

        Set<UUID> matchedSet = session.getMatchedPhotoIds();
        for (UUID requestedId : requestedPhotoIds) {
            if (!matchedSet.contains(requestedId)) {
                log.warn("Unauthorized photo in ZIP request: {} for session {} (event {})",
                        requestedId, session.getId(), publicId);
                throw new ForbiddenException("Unauthorized photo in ZIP request: " + requestedId +
                        ". You may only download photos verified to match your selfie.");
            }
        }

        return requestedPhotoIds;
    }

    @Transactional
    public void revokeSession(String rawToken, String publicId) {
        GuestSession session = validateSession(rawToken, publicId);
        session.setRevoked(true);
        guestSessionRepository.save(session);
        log.info("Revoked guest session {} for event {}", session.getId(), publicId);
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in JVM", e);
        }
    }
}
