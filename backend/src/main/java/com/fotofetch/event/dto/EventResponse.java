package com.fotofetch.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private UUID id;
    private String name;
    private String description;
    private LocalDate eventDate;
    private String publicId;
    private String publicUrl;
    private String rekognitionCollectionId;
    private long totalPhotos;
    private long indexedPhotos;
    private long failedPhotos;
    private LocalDateTime createdAt;
}
