package com.fotofetch.ai;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Strategy interface for pluggable facial recognition providers.
 * Supports cloud providers (AWS Rekognition) and self-hosted AI engines (InsightFace/FaceNet).
 */
public interface FaceRecognitionProvider {

    String getProviderName();

    void createCollection(String collectionId);

    void deleteCollection(String collectionId);

    int indexPhotoFaces(String collectionId, UUID photoId, String thumbnailS3Key);

    List<FaceMatchResult> searchFacesBySelfie(String collectionId, byte[] selfieBytes, Float threshold);

    void deleteFacesForPhotos(String collectionId, Collection<UUID> photoIds);
}
