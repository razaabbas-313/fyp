package com.fotofetch.guest.identity;

import com.fotofetch.common.BadRequestException;
import com.fotofetch.common.ResourceNotFoundException;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventService;
import com.fotofetch.guest.identity.dto.GuestIdentityResponse;
import com.fotofetch.guest.session.GuestSession;
import com.fotofetch.guest.session.GuestSessionService;
import com.fotofetch.photo.Photo;
import com.fotofetch.photo.PhotoRepository;
import com.fotofetch.photo.PhotoService;
import com.fotofetch.photo.dto.GuestSearchResponse;
import com.fotofetch.photo.dto.SearchMatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestIdentityService {

    private final GuestIdentityRepository guestIdentityRepository;
    private final GuestSessionService guestSessionService;
    private final EventService eventService;
    private final PhotoRepository photoRepository;
    private final PhotoService photoService;

    public String generateUniqueUsername(UUID eventId, String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            rawName = "guest";
        }
        String clean = rawName.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (clean.isEmpty()) {
            clean = "guest";
        }

        if (!guestIdentityRepository.existsByEventIdAndUsernameIgnoreCase(eventId, clean)) {
            return clean;
        }

        int counter = 2;
        while (true) {
            String candidate = clean + counter;
            if (!guestIdentityRepository.existsByEventIdAndUsernameIgnoreCase(eventId, candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    @Transactional
    public GuestIdentityResponse registerGuestIdentity(String publicId, String name, String sessionToken) {
        if (name == null || name.trim().length() < 2) {
            throw new BadRequestException("Name must be at least 2 characters");
        }

        GuestSession session = guestSessionService.validateSession(sessionToken, publicId);
        Event event = session.getEvent();

        if (session.getMatchedPhotoIds() == null || session.getMatchedPhotoIds().isEmpty()) {
            throw new BadRequestException("No matched photos found in this session to tag.");
        }

        String username = generateUniqueUsername(event.getId(), name);

        GuestIdentity identity = GuestIdentity.builder()
                .event(event)
                .name(name.trim())
                .username(username)
                .matchedPhotoIds(new HashSet<>(session.getMatchedPhotoIds()))
                .build();

        GuestIdentity saved = guestIdentityRepository.save(identity);
        log.info("Registered guest identity '{}' as @{} with {} photo(s) for event {}",
                saved.getName(), saved.getUsername(), saved.getMatchedPhotoIds().size(), publicId);

        return GuestIdentityResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .username(saved.getUsername())
                .photoCount(saved.getMatchedPhotoIds().size())
                .message("Your identity has been tagged as @" + saved.getUsername() + ". You can search your photos anytime using this username.")
                .build();
    }

    @Transactional
    public GuestSearchResponse searchByNameOrUsername(String publicId, String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new BadRequestException("Search query cannot be empty");
        }

        Event event = eventService.getEventEntityByPublicId(publicId);
        String trimmedQuery = query.trim();

        // 1. Try exact username lookup (case-insensitive)
        Optional<GuestIdentity> identityOpt = guestIdentityRepository
                .findByEventIdAndUsernameIgnoreCase(event.getId(), trimmedQuery);

        // 2. If not found by username, try exact name lookup
        if (identityOpt.isEmpty()) {
            List<GuestIdentity> byName = guestIdentityRepository
                    .findByEventIdAndNameIgnoreCase(event.getId(), trimmedQuery);
            if (!byName.isEmpty()) {
                identityOpt = Optional.of(byName.get(0));
            }
        }

        // 3. If still not found, try partial name lookup
        if (identityOpt.isEmpty()) {
            List<GuestIdentity> partialName = guestIdentityRepository
                    .findByEventIdAndNameContainingIgnoreCase(event.getId(), trimmedQuery);
            if (!partialName.isEmpty()) {
                identityOpt = Optional.of(partialName.get(0));
            }
        }

        if (identityOpt.isEmpty()) {
            throw new ResourceNotFoundException("No tagged guest identity found matching '" + trimmedQuery + "' in this event.");
        }

        GuestIdentity identity = identityOpt.get();
        Set<UUID> photoIds = identity.getMatchedPhotoIds();
        if (photoIds == null || photoIds.isEmpty()) {
            return GuestSearchResponse.builder()
                    .sessionToken(null)
                    .expiresAt(null)
                    .matches(Collections.emptyList())
                    .faceDetected(true)
                    .indexingCount(0L)
                    .taggedName(identity.getName())
                    .taggedUsername(identity.getUsername())
                    .build();
        }

        List<Photo> photos = photoRepository.findByEventIdAndIdIn(event.getId(), photoIds);
        List<SearchMatchResponse> matchResponses = photoService.mapPhotosToMatchResponses(photos);

        // Generate an authorized session for this guest identity so they can view and download their photos
        GuestSessionService.GuestSessionResult sessionResult = guestSessionService.createSession(event, photoIds);

        return GuestSearchResponse.builder()
                .sessionToken(sessionResult.getRawToken())
                .expiresAt(sessionResult.getExpiresAt())
                .matches(matchResponses)
                .faceDetected(true)
                .indexingCount(0L)
                .taggedName(identity.getName())
                .taggedUsername(identity.getUsername())
                .build();
    }

    @Transactional(readOnly = true)
    public List<GuestIdentityResponse> listEventIdentities(String publicId) {
        Event event = eventService.getEventEntityByPublicId(publicId);
        List<GuestIdentity> identities = guestIdentityRepository.findByEventIdOrderByCreatedAtDesc(event.getId());

        List<GuestIdentityResponse> responses = new ArrayList<>();
        for (GuestIdentity identity : identities) {
            responses.add(GuestIdentityResponse.builder()
                    .id(identity.getId())
                    .name(identity.getName())
                    .username(identity.getUsername())
                    .photoCount(identity.getMatchedPhotoIds().size())
                    .message("@" + identity.getUsername())
                    .build());
        }
        return responses;
    }
}
