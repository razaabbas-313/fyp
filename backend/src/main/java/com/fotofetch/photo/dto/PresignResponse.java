package com.fotofetch.photo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignResponse {

    private List<PresignedFile> uploads;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedFile {
        private UUID photoId;
        private String originalFilename;
        private String uploadUrl;
        private String s3Key;
    }
}
