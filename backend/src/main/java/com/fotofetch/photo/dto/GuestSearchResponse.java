package com.fotofetch.photo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestSearchResponse {
    private String sessionToken;
    private LocalDateTime expiresAt;
    private List<SearchMatchResponse> matches;
    @Builder.Default
    private Boolean faceDetected = true;
    @Builder.Default
    private Long indexingCount = 0L;
}
