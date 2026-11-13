package com.fotofetch.event;

import com.fotofetch.common.ApiResponse;
import com.fotofetch.event.dto.CreateEventRequest;
import com.fotofetch.event.dto.EventResponse;
import com.fotofetch.user.User;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Data
    @Builder
    public static class DashboardStatsResponse {
        private long totalEvents;
        private long totalPhotos;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal User user) {
        EventResponse response = eventService.createEvent(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Event created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> getMyEvents(
            @AuthenticationPrincipal User user) {
        List<EventResponse> events = eventService.getEventsForUser(user);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        EventResponse event = eventService.getEventByIdAndUser(id, user);
        return ResponseEntity.ok(ApiResponse.success(event));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        eventService.deleteEvent(id, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Event deleted successfully"));
    }

    @GetMapping("/dashboard-stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats(
            @AuthenticationPrincipal User user) {
        List<EventResponse> events = eventService.getEventsForUser(user);
        long totalPhotos = eventService.getTotalPhotosForUser(user.getId());
        
        DashboardStatsResponse stats = DashboardStatsResponse.builder()
                .totalEvents(events.size())
                .totalPhotos(totalPhotos)
                .build();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
