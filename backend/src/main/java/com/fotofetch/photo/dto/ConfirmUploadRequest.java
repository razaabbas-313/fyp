package com.fotofetch.photo.dto;

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
public class ConfirmUploadRequest {

    @NotEmpty(message = "Photo IDs cannot be empty")
    @Size(max = 100, message = "Cannot confirm more than 100 photos at once")
    private List<UUID> photoIds;
}
