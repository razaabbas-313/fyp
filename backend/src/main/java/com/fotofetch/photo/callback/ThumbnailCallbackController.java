package com.fotofetch.photo.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fotofetch.common.ApiResponse;
import com.fotofetch.common.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/photos")
@RequiredArgsConstructor
@Slf4j
public class ThumbnailCallbackController {

    private final HmacSignatureValidator hmacSignatureValidator;
    private final ThumbnailCallbackService thumbnailCallbackService;
    private final ObjectMapper objectMapper;

    @PostMapping("/thumbnail-callback")
    public ResponseEntity<ApiResponse<ThumbnailCallbackResponse>> handleThumbnailCallback(
            @RequestHeader(value = "X-FotoFetch-Signature", required = false) String signature,
            @RequestHeader(value = "X-Signature", required = false) String fallbackSignature,
            @RequestHeader(value = "X-FotoFetch-Timestamp", required = false) Long timestamp,
            @RequestHeader(value = "X-Timestamp", required = false) Long fallbackTimestamp,
            @RequestBody byte[] rawBody) {

        String activeSignature = signature != null && !signature.isBlank() ? signature : fallbackSignature;
        Long activeTimestamp = timestamp != null ? timestamp : fallbackTimestamp;

        // 1. Verify HMAC-SHA256 signature and timestamp validity window
        hmacSignatureValidator.validate(activeSignature, activeTimestamp, rawBody);

        // 2. Deserialize raw request bytes into DTO
        ThumbnailCallbackRequest request;
        try {
            request = objectMapper.readValue(rawBody, ThumbnailCallbackRequest.class);
        } catch (Exception ex) {
            log.warn("Failed to parse callback JSON body: {}", ex.getMessage());
            throw new BadRequestException("Malformed or invalid JSON body: " + ex.getMessage());
        }

        // 3. Process callback with validation, idempotency, and state persistence
        ThumbnailCallbackResponse response = thumbnailCallbackService.handleCallback(request);
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }
}
