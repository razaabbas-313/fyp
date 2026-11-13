package com.fotofetch.photo.dto;

import jakarta.validation.constraints.NotEmpty;
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
public class ConfirmUploadRequest {

    @NotEmpty(message = "Photo IDs cannot be empty")
    private List<UUID> photoIds;
}
