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
        PutObjectRequest.Builder objectRequestBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key);

        if (contentType != null && !contentType.isBlank()) {
            objectRequestBuilder.contentType(contentType);
        }

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
        log.info("Deleting S3 object: {}", key);
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }
}
