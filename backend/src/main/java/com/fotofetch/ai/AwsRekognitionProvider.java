package com.fotofetch.ai;

import com.fotofetch.common.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "fotofetch.ai.provider", havingValue = "aws", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AwsRekognitionProvider implements FaceRecognitionProvider {

    public static final float MIN_SIMILARITY_THRESHOLD = 70.0f;
    public static final float MAX_SIMILARITY_THRESHOLD = 99.0f;

    private final RekognitionClient rekognitionClient;

    @Value("${fotofetch.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${fotofetch.aws.rekognition.similarity-threshold:80.0}")
    private float defaultSimilarityThreshold;

    @Value("${fotofetch.aws.rekognition.max-faces:50}")
    private int defaultMaxFaces;

    @PostConstruct
    public void validateConfiguration() {
        if (defaultSimilarityThreshold < MIN_SIMILARITY_THRESHOLD || defaultSimilarityThreshold > MAX_SIMILARITY_THRESHOLD) {
            throw new IllegalStateException(String.format(
                    "Configured Rekognition similarity threshold (%.1f) is outside safe boundaries [%.1f, %.1f]",
                    defaultSimilarityThreshold, MIN_SIMILARITY_THRESHOLD, MAX_SIMILARITY_THRESHOLD));
        }
        log.info("AwsRekognitionProvider initialized with server-controlled similarity threshold: {}%", defaultSimilarityThreshold);
    }

    @Override
    public String getProviderName() {
        return "aws";
    }

    @Override
    public void createCollection(String collectionId) {
        log.info("Creating Rekognition collection: {}", collectionId);
        try {
            CreateCollectionRequest request = CreateCollectionRequest.builder()
                    .collectionId(collectionId)
                    .build();
            CreateCollectionResponse response = rekognitionClient.createCollection(request);
            log.info("Created Rekognition collection successfully: {}, status code: {}",
                    collectionId, response.statusCode());
        } catch (ResourceAlreadyExistsException ex) {
            log.warn("Rekognition collection already exists: {}", collectionId);
        } catch (Exception ex) {
            log.error("Failed to create Rekognition collection: {}", collectionId, ex);
            throw new RuntimeException("Could not initialize facial recognition collection: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void deleteCollection(String collectionId) {
        log.info("Deleting Rekognition collection: {}", collectionId);
        try {
            DeleteCollectionRequest request = DeleteCollectionRequest.builder()
                    .collectionId(collectionId)
                    .build();
            rekognitionClient.deleteCollection(request);
            log.info("Deleted Rekognition collection: {}", collectionId);
        } catch (ResourceNotFoundException ex) {
            log.warn("Rekognition collection not found to delete: {}", collectionId);
        } catch (Exception ex) {
            log.error("Failed to delete Rekognition collection: {}", collectionId, ex);
        }
    }

    @Override
    public int indexPhotoFaces(String collectionId, UUID photoId, String s3Key) {
        log.info("Indexing faces for photoId: {} in collection: {}, s3Key: {}", photoId, collectionId, s3Key);

        S3Object s3Object = S3Object.builder()
                .bucket(bucketName)
                .name(s3Key)
                .build();

        Image image = Image.builder()
                .s3Object(s3Object)
                .build();

        IndexFacesRequest request = IndexFacesRequest.builder()
                .collectionId(collectionId)
                .image(image)
                .externalImageId(photoId.toString())
                .detectionAttributesWithStrings("DEFAULT")
                .qualityFilter(QualityFilter.AUTO)
                .maxFaces(100)
                .build();

        try {
            IndexFacesResponse response = rekognitionClient.indexFaces(request);
            int facesDetected = response.faceRecords() != null ? response.faceRecords().size() : 0;
            log.info("Successfully indexed {} face(s) for photo: {}", facesDetected, photoId);
            return facesDetected;
        } catch (Exception ex) {
            log.error("Failed to index faces for photo: {} in collection: {}", photoId, collectionId, ex);
            throw ex;
        }
    }

    @Override
    public List<FaceMatchResult> searchFacesBySelfie(String collectionId, byte[] selfieBytes, Float threshold) {
        float similarityThreshold = defaultSimilarityThreshold;
        if (threshold != null) {
            similarityThreshold = Math.max(MIN_SIMILARITY_THRESHOLD, Math.min(MAX_SIMILARITY_THRESHOLD, threshold));
            if (similarityThreshold != threshold) {
                log.warn("Requested similarity threshold {}% clamped to safe boundary {}%", threshold, similarityThreshold);
            }
        }
        log.info("Searching faces in collection: {} with threshold: {}%", collectionId, similarityThreshold);

        SdkBytes sdkBytes = SdkBytes.fromByteArray(selfieBytes);
        Image image = Image.builder()
                .bytes(sdkBytes)
                .build();

        SearchFacesByImageRequest request = SearchFacesByImageRequest.builder()
                .collectionId(collectionId)
                .image(image)
                .faceMatchThreshold(similarityThreshold)
                .maxFaces(defaultMaxFaces)
                .build();

        try {
            SearchFacesByImageResponse response = rekognitionClient.searchFacesByImage(request);
            List<FaceMatchResult> results = new ArrayList<>();

            if (response.faceMatches() != null) {
                for (FaceMatch match : response.faceMatches()) {
                    if (match.face() != null && match.face().externalImageId() != null) {
                        results.add(FaceMatchResult.builder()
                                .externalImageId(match.face().externalImageId())
                                .similarity(match.similarity())
                                .faceId(match.face().faceId())
                                .build());
                    }
                }
            }
            log.info("Search found {} matching face(s) in collection: {}", results.size(), collectionId);
            return results;
        } catch (InvalidParameterException ex) {
            log.warn("Invalid parameter or no face found in selfie: {}", ex.getMessage());
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("no faces") || msg.contains("cannot detect")) {
                throw new BadRequestException("No face detected in the selfie. Please take a clear, well-lit photo looking directly at the camera.");
            }
            return new ArrayList<>();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Rekognition search failed for collection: {}", collectionId, ex);
            throw new RuntimeException("Facial recognition search service is temporarily unavailable. Please try again shortly.");
        }
    }

    @Override
    public void deleteFacesForPhotos(String collectionId, Collection<UUID> photoIds) {
        if (photoIds == null || photoIds.isEmpty() || collectionId == null || collectionId.isBlank()) {
            return;
        }

        try {
            Set<String> photoIdStrings = photoIds.stream().map(UUID::toString).collect(Collectors.toSet());
            List<String> faceIdsToDelete = new ArrayList<>();
            String nextToken = null;

            do {
                ListFacesRequest listRequest = ListFacesRequest.builder()
                        .collectionId(collectionId)
                        .maxResults(4096)
                        .nextToken(nextToken)
                        .build();

                ListFacesResponse listResponse = rekognitionClient.listFaces(listRequest);
                if (listResponse.faces() != null) {
                    for (Face face : listResponse.faces()) {
                        if (face.externalImageId() != null && photoIdStrings.contains(face.externalImageId())) {
                            faceIdsToDelete.add(face.faceId());
                        }
                    }
                }
                nextToken = listResponse.nextToken();
            } while (nextToken != null);

            if (!faceIdsToDelete.isEmpty()) {
                DeleteFacesRequest deleteRequest = DeleteFacesRequest.builder()
                        .collectionId(collectionId)
                        .faceIds(faceIdsToDelete)
                        .build();
                DeleteFacesResponse deleteResponse = rekognitionClient.deleteFaces(deleteRequest);
                int deletedCount = deleteResponse.deletedFaces() != null ? deleteResponse.deletedFaces().size() : 0;
                log.info("Deleted {} face record(s) from collection {} for photos", deletedCount, collectionId);
            }
        } catch (Exception ex) {
            log.error("Failed to delete faces from collection {} for photos", collectionId, ex);
        }
    }
}
