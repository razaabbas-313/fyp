package com.fotofetch.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${fotofetch.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${fotofetch.aws.s3.presigned-url-expiration-minutes:30}")
    private int presignedUrlExpirationMinutes;

    public String getBucketName() {
        return bucketName;
    }

    public String generatePresignedUploadUrl(String key, String contentType) {
        log.info("Generating presigned upload URL for key: {}, type: {}", key, contentType);
        // Do not bind contentType into the presigned signature to avoid SignatureDoesNotMatch
        // when browsers send variations (e.g., uppercase, charset, or browser-detected mime types).
        // S3 will still preserve the Content-Type header passed by the browser in the PUT request.
        PutObjectRequest.Builder objectRequestBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpirationMinutes))
                .putObjectRequest(objectRequestBuilder.build())
                .build();

        PresignedPutObjectRequest presignedPut = s3Presigner.presignPutObject(presignRequest);
        return presignedPut.url().toString();
    }

    public String generatePresignedDownloadUrl(String key, String downloadFilename) {
        GetObjectRequest.Builder getBuilder = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key);

        if (downloadFilename != null && !downloadFilename.isBlank()) {
            // Clean filename to prevent header injection
            String cleanName = downloadFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            getBuilder.responseContentDisposition("attachment; filename=\"" + cleanName + "\"");
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpirationMinutes))
                .getObjectRequest(getBuilder.build())
                .build();

        PresignedGetObjectRequest presignedGet = s3Presigner.presignGetObject(presignRequest);
        return presignedGet.url().toString();
    }

    public byte[] getObjectBytes(String key) {
        log.info("Fetching S3 object bytes for key: {}", key);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
    }

    public ResponseInputStream<GetObjectResponse> getObjectStream(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.getObject(getObjectRequest);
    }

    public void uploadThumbnail(String key, byte[] bytes) {
        log.info("Uploading thumbnail to S3: {} ({} bytes)", key, bytes.length);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("image/jpeg")
                .contentLength((long) bytes.length)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
    }

    public void deleteObject(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        log.info("Deleting S3 object: {}", key);
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception ex) {
            log.warn("Failed to delete S3 object {}: {}", key, ex.getMessage());
        }
    }

    public void deleteObjects(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        List<ObjectIdentifier> objects = keys.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> ObjectIdentifier.builder().key(k).build())
                .collect(Collectors.toList());

        if (objects.isEmpty()) {
            return;
        }

        log.info("Batch deleting {} objects from S3 bucket: {}", objects.size(), bucketName);
        try {
            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objects).quiet(true).build())
                    .build();

            s3Client.deleteObjects(deleteRequest);
            log.info("Successfully deleted {} objects from S3", objects.size());
        } catch (Exception ex) {
            log.warn("Failed batch delete in S3: {}", ex.getMessage());
        }
    }
}
