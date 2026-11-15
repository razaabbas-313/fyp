package com.fotofetch.guest;

import com.fotofetch.common.ApiResponse;
import com.fotofetch.common.BadRequestException;
import com.fotofetch.download.ZipService;
import com.fotofetch.download.dto.ZipDownloadRequest;
import com.fotofetch.event.Event;
import com.fotofetch.event.EventService;
import com.fotofetch.event.dto.PublicEventResponse;
import com.fotofetch.photo.Photo;
import com.fotofetch.photo.PhotoRepository;
import com.fotofetch.photo.PhotoService;
import com.fotofetch.photo.dto.PhotoResponse;
import com.fotofetch.photo.dto.SearchMatchResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/events")
@RequiredArgsConstructor
@Slf4j
public class GuestController {

    private final EventService eventService;
    private final PhotoService photoService;
    private final PhotoRepository photoRepository;
    private final ZipService zipService;
    private final com.fotofetch.guest.session.GuestSessionService guestSessionService;

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<PublicEventResponse>> getPublicEvent(@PathVariable String publicId) {
        PublicEventResponse response = eventService.getPublicEvent(publicId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{publicId}/photos")
    public ResponseEntity<ApiResponse<List<PhotoResponse>>> getPublicPhotos(@PathVariable String publicId) {
        // Disabled to protect guest privacy: guests only access photos where their face matches via POST /{publicId}/search
        return ResponseEntity.ok(ApiResponse.success(java.util.Collections.emptyList(), "Public browsing disabled for guest privacy. Use selfie search to find your photos."));
    }

    @PostMapping(value = "/{publicId}/search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<com.fotofetch.photo.dto.GuestSearchResponse>> searchGuestPhotos(
            @PathVariable String publicId,
            @RequestParam("selfie") MultipartFile selfie,
            @RequestParam(value = "threshold", required = false) Float threshold) {
        
        if (selfie == null || selfie.isEmpty()) {
            throw new BadRequestException("Selfie image file is required");
        }
        if (selfie.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("Selfie image must be 5MB or smaller");
        }
        if (threshold != null && (threshold < 70.0f || threshold > 99.0f)) {
            throw new BadRequestException("Similarity threshold must be between 70.0% and 99.0%");
        }

        try {
            byte[] selfieBytes = selfie.getBytes();
            com.fotofetch.photo.dto.GuestSearchResponse response =
                    photoService.searchGuestPhotosWithSession(publicId, selfieBytes, threshold);
            int count = response.getMatches() != null ? response.getMatches().size() : 0;
            return ResponseEntity.ok(ApiResponse.success(response, "Found " + count + " matching photos"));
        } catch (IOException ex) {
            log.error("Failed to read selfie bytes", ex);
            throw new BadRequestException("Unable to read selfie image");
        }
    }

    @GetMapping("/{publicId}/photos/{photoId}/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPhotoDownloadUrl(
            @PathVariable String publicId,
            @PathVariable UUID photoId,
            @RequestHeader(value = "X-Guest-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {

        String token = resolveGuestToken(guestSessionHeader, authHeader, tokenParam);
        guestSessionService.authorizePhotoDownload(token, publicId, photoId);

        String downloadUrl = photoService.getPhotoDownloadUrl(publicId, photoId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("downloadUrl", downloadUrl)));
    }

    @PostMapping(value = "/{publicId}/photos/zip", produces = "application/zip")
    public void downloadPhotosZip(
            @PathVariable String publicId,
            @Valid @RequestBody ZipDownloadRequest request,
            @RequestHeader(value = "X-Guest-Session", required = false) String guestSessionHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletResponse response) throws IOException {

        String token = resolveGuestToken(guestSessionHeader, authHeader, tokenParam);
        List<UUID> authorizedIds = guestSessionService.authorizeZipDownload(token, publicId, request.getPhotoIds());

        Event event = eventService.getEventEntityByPublicId(publicId);

        // Fetch only photos that belong to this event and were authorized in the guest session
        List<Photo> photos = photoRepository.findByEventIdAndIdIn(event.getId(), authorizedIds);

        if (photos.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No valid photos found for download");
            return;
        }

        String zipFilename = "fotofetch-" + event.getPublicId() + "-photos.zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"");

        zipService.streamPhotosZip(photos, response.getOutputStream());
    }

    private String resolveGuestToken(String guestSessionHeader, String authHeader, String tokenParam) {
        if (guestSessionHeader != null && !guestSessionHeader.isBlank()) {
            return guestSessionHeader;
        }
        if (authHeader != null && !authHeader.isBlank()) {
            return authHeader;
        }
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }
        throw new com.fotofetch.common.UnauthorizedException("Guest session token is required to download photos");
    }
}
