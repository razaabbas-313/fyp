package com.fotofetch.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceMatchResult {
    private String externalImageId;
    private Float similarity;
    private String faceId;
}
