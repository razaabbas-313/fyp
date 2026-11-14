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

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<PublicEventResponse>> getPublicEvent(@PathVariable String publicId) {
        PublicEventResponse response = eventService.getPublicEvent(publicId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{publicId}/photos")
    public ResponseEntity<ApiResponse<List<PhotoResponse>>> getPublicPhotos(@PathVariable String publicId) {
        List<PhotoResponse> photos = photoService.getPublicPhotosForEvent(publicId);
        return ResponseEntity.ok(ApiResponse.success(photos));
    }

    @PostMapping(value = "/{publicId}/search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<SearchMatchResponse>>> searchGuestPhotos(
            @PathVariable String publicId,
            @RequestParam("selfie") MultipartFile selfie,
            @RequestParam(value = "threshold", required = false) Float threshold) {
        
        if (selfie == null || selfie.isEmpty()) {
            throw new BadRequestException("Selfie image file is required");
        }
        if (selfie.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("Selfie image must be 5MB or smaller");
        }

        try {
            byte[] selfieBytes = selfie.getBytes();
            List<SearchMatchResponse> matches = photoService.searchGuestPhotos(publicId, selfieBytes, threshold);
            return ResponseEntity.ok(ApiResponse.success(matches, "Found " + matches.size() + " matching photos"));
        } catch (IOException ex) {
            log.error("Failed to read selfie bytes", ex);
            throw new BadRequestException("Unable to read selfie image");
        }
    }

    @GetMapping("/{publicId}/photos/{photoId}/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPhotoDownloadUrl(
            @PathVariable String publicId,
            @PathVariable UUID photoId) {
        String downloadUrl = photoService.getPhotoDownloadUrl(publicId, photoId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("downloadUrl", downloadUrl)));
    }

    @PostMapping(value = "/{publicId}/photos/zip", produces = "application/zip")
    public void downloadPhotosZip(
            @PathVariable String publicId,
            @Valid @RequestBody ZipDownloadRequest request,
            HttpServletResponse response) throws IOException {

        Event event = eventService.getEventEntityByPublicId(publicId);

        // Fetch and verify only photos that strictly belong to this event
        List<Photo> photos = photoRepository.findByEventIdAndIdIn(event.getId(), request.getPhotoIds());

        if (photos.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No valid photos found for download");
            return;
        }

        String zipFilename = "fotofetch-" + event.getPublicId() + "-photos.zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"");

        zipService.streamPhotosZip(photos, response.getOutputStream());
    }
}
