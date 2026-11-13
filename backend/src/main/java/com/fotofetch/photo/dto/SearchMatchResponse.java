package com.fotofetch.photo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchMatchResponse {
    private UUID photoId;
    private Float similarity;
    private String originalFilename;
    private Long fileSize;
    private Integer facesFound;
    private String thumbnailUrl;
    private String downloadUrl;
}
