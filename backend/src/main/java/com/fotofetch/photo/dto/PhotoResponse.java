package com.fotofetch.photo.dto;

import com.fotofetch.photo.IndexingStatus;
import com.fotofetch.photo.ThumbnailStatus;
import com.fotofetch.photo.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoResponse {
    private UUID id;
    private String originalFilename;
    private Long fileSize;
    private String contentType;
    private UploadStatus uploadStatus;
    private ThumbnailStatus thumbnailStatus;
    private IndexingStatus indexingStatus;
    private Integer facesFound;
    private String thumbnailUrl;
    private String downloadUrl;
    private LocalDateTime createdAt;
}
