package com.fotofetch.photo;

import com.fotofetch.common.ApiResponse;
import com.fotofetch.photo.dto.*;
import com.fotofetch.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignResponse>> createPresignedUploads(
            @PathVariable UUID eventId,
            @Valid @RequestBody PresignRequest request,
            @AuthenticationPrincipal User user) {
        PresignResponse response = photoService.createPresignedUploads(eventId, request, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Presigned upload URLs generated"));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmUploads(
            @PathVariable UUID eventId,
            @Valid @RequestBody ConfirmUploadRequest request,
            @AuthenticationPrincipal User user) {
        photoService.confirmUploads(eventId, request, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Photos confirmed and queued for processing"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PhotoResponse>>> getPhotos(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal User user) {
        List<PhotoResponse> photos = photoService.getPhotosForEvent(eventId, user);
        return ResponseEntity.ok(ApiResponse.success(photos));
    }
}
