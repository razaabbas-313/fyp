package com.fotofetch.photo.dto;

import jakarta.validation.constraints.NotEmpty;
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
    private List<FileItem> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileItem {
        private String filename;
        private String contentType;
        private Long fileSize;
    }
}
