package com.fotofetch.photo.callback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbnailCallbackRequest {

    private String callbackId;

    @NotNull(message = "photoId is required")
    private UUID photoId;

    @NotNull(message = "eventId is required")
    private UUID eventId;

    @NotBlank(message = "originalS3Key is required")
    private String originalS3Key;

    private String thumbnailS3Key;

    @NotBlank(message = "status is required")
    private String status;

    private String errorMessage;

    private Integer width;

    private Integer height;

    private Long fileSize;

    private Long timestamp;
}
