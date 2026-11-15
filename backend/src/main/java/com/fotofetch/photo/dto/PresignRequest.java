package com.fotofetch.photo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignRequest {

    @NotEmpty(message = "Files list cannot be empty")
    @Size(max = 50, message = "Cannot request presigned URLs for more than 50 files in a single batch")
    private List<@Valid FileItem> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileItem {
        @NotBlank(message = "Filename is required")
        private String filename;

        @NotBlank(message = "Content type is required")
        @Pattern(regexp = "^image/(jpeg|jpg|png|webp)$", message = "Unsupported image format. Allowed formats: image/jpeg, image/png, image/webp")
        private String contentType;

        @NotNull(message = "File size is required")
        @Min(value = 1, message = "File cannot be empty")
        @Max(value = 26214400, message = "File size cannot exceed 25MB")
        private Long fileSize;
    }
}
