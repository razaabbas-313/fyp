package com.fotofetch.event;

import com.fotofetch.ai.RekognitionService;
import com.fotofetch.common.BadRequestException;
import com.fotofetch.common.ResourceNotFoundException;
import com.fotofetch.event.dto.CreateEventRequest;
import com.fotofetch.event.dto.EventResponse;
import com.fotofetch.event.dto.PublicEventResponse;
import com.fotofetch.photo.IndexingStatus;
import com.fotofetch.photo.Photo;
import com.fotofetch.photo.PhotoRepository;
import com.fotofetch.storage.S3StorageService;
import com.fotofetch.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final RekognitionService rekognitionService;
    private final S3StorageService s3StorageService;

    @Value("${fotofetch.aws.rekognition.collection-prefix:fotofetch-event-}")
    private String collectionPrefix;

    private static final String ALPHANUM = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User user) {
        String publicId = generateUniquePublicId(request.getName());
        String collectionId = collectionPrefix + publicId;

        // Create AWS Rekognition collection for event
        rekognitionService.createCollection(collectionId);

        Event event = Event.builder()
                .name(request.getName().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .eventDate(request.getEventDate())
                .publicId(publicId)
                .rekognitionCollectionId(collectionId)
                .user(user)
                .build();

        event = eventRepository.save(event);
        log.info("Created event: {} (id: {}, publicId: {}) for photographer: {}",
                event.getName(), event.getId(), event.getPublicId(), user.getEmail());

        return mapToEventResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsForUser(User user) {
        return eventRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EventResponse getEventByIdAndUser(UUID eventId, User user) {
        Event event = eventRepository.findByIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
        return mapToEventResponse(event);
    }

    @Transactional(readOnly = true)
    public Event getEventEntityByIdAndUser(UUID eventId, User user) {
        return eventRepository.findByIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
    }

    @Transactional(readOnly = true)
    public PublicEventResponse getPublicEvent(String publicId) {
        Event event = eventRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with public link: " + publicId));

        long totalPhotos = photoRepository.countByEventId(event.getId());

        return PublicEventResponse.builder()
                .name(event.getName())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .publicId(event.getPublicId())
                .totalPhotos(totalPhotos)
                .build();
    }

    @Transactional(readOnly = true)
    public Event getEventEntityByPublicId(String publicId) {
        return eventRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with public ID: " + publicId));
    }

    @Transactional
    public void deleteEvent(UUID eventId, User user) {
        Event event = eventRepository.findByIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        // Delete photos from S3
        List<Photo> photos = photoRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        for (Photo photo : photos) {
            try {
                if (photo.getOriginalS3Key() != null) {
                    s3StorageService.deleteObject(photo.getOriginalS3Key());
                }
                if (photo.getThumbnailS3Key() != null) {
                    s3StorageService.deleteObject(photo.getThumbnailS3Key());
                }
            } catch (Exception ex) {
                log.warn("Failed to delete S3 object for photo: {}", photo.getId(), ex);
            }
        }

        // Delete Rekognition collection
        try {
            rekognitionService.deleteCollection(event.getRekognitionCollectionId());
        } catch (Exception ex) {
            log.warn("Failed to delete Rekognition collection: {}", event.getRekognitionCollectionId(), ex);
        }

        eventRepository.delete(event);
        log.info("Deleted event: {} (id: {})", event.getName(), eventId);
    }

    @Transactional(readOnly = true)
    public long getTotalPhotosForUser(UUID userId) {
        return photoRepository.countTotalPhotosByUserId(userId);
    }

    public EventResponse mapToEventResponse(Event event) {
        long total = photoRepository.countByEventId(event.getId());
        long indexed = photoRepository.countByEventIdAndIndexingStatus(event.getId(), IndexingStatus.COMPLETED);
        long failed = photoRepository.countByEventIdAndIndexingStatus(event.getId(), IndexingStatus.FAILED);

        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .publicId(event.getPublicId())
                .publicUrl("/guest/" + event.getPublicId())
                .rekognitionCollectionId(event.getRekognitionCollectionId())
                .totalPhotos(total)
                .indexedPhotos(indexed)
                .failedPhotos(failed)
                .createdAt(event.getCreatedAt())
                .build();
    }

    private String generateUniquePublicId(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (base.length() > 15) {
            base = base.substring(0, 15);
        }
        if (base.isBlank()) {
            base = "event";
        }

        for (int i = 0; i < 10; i++) {
            StringBuilder suffix = new StringBuilder();
            for (int s = 0; s < 6; s++) {
                suffix.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
            }
            String candidate = base + "-" + suffix;
            if (!eventRepository.existsByPublicId(candidate)) {
                return candidate;
            }
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
