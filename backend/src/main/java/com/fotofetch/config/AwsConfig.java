package com.fotofetch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsConfig {

    @Value("${fotofetch.aws.region:us-east-1}")
    private String awsRegion;

    @Value("${fotofetch.aws.access-key-id:}")
    private String accessKeyId;

    @Value("${fotofetch.aws.secret-access-key:}")
    private String secretAccessKey;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (accessKeyId != null && !accessKeyId.isBlank() && !accessKeyId.contains("EXAMPLE")
                && secretAccessKey != null && !secretAccessKey.isBlank() && !secretAccessKey.contains("EXAMPLE")) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        // Fall back to AWS default credentials provider (env vars, ~/.aws/credentials, IAM role, etc.)
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public Region region() {
        return Region.of(awsRegion);
    }

    @Bean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider, Region region) {
        return S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider, Region region) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public RekognitionClient rekognitionClient(AwsCredentialsProvider credentialsProvider, Region region) {
        return RekognitionClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
