package com.fotofetch.download.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class ZipDownloadRequest {

    @NotEmpty(message = "Photo IDs cannot be empty")
    @Size(max = 50, message = "Cannot download more than 50 photos in a single ZIP archive")
    private List<UUID> photoIds;
}
