package com.fotofetch.ai;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service facade for facial recognition operations.
 * Delegates to the configured {@link FaceRecognitionProvider} strategy (e.g. AWS Rekognition or self-hosted AI engine).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RekognitionService {

    private final FaceRecognitionProvider faceRecognitionProvider;

    @Data
    @Builder
    public static class MatchResult {
        private String externalImageId;
        private Float similarity;
        private String faceId;
    }

    public String getActiveProviderName() {
        return faceRecognitionProvider.getProviderName();
    }

    public void createCollection(String collectionId) {
        faceRecognitionProvider.createCollection(collectionId);
    }

    public void deleteCollection(String collectionId) {
        faceRecognitionProvider.deleteCollection(collectionId);
    }

    public int indexPhotoFaces(String collectionId, UUID photoId, String s3Key) {
        return faceRecognitionProvider.indexPhotoFaces(collectionId, photoId, s3Key);
    }

    public List<MatchResult> searchFacesBySelfie(String collectionId, byte[] selfieBytes, Float threshold) {
        List<FaceMatchResult> providerResults = faceRecognitionProvider.searchFacesBySelfie(collectionId, selfieBytes, threshold);
        return providerResults.stream()
                .map(r -> MatchResult.builder()
                        .externalImageId(r.getExternalImageId())
                        .similarity(r.getSimilarity())
                        .faceId(r.getFaceId())
                        .build())
                .collect(Collectors.toList());
    }

    public void deleteFacesForPhotos(String collectionId, Collection<UUID> photoIds) {
        faceRecognitionProvider.deleteFacesForPhotos(collectionId, photoIds);
    }
}
