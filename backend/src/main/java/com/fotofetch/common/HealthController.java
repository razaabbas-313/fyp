package com.fotofetch.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${fotofetch.aws.region:ap-south-1}")
    private String awsRegion;

    @Value("${fotofetch.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${fotofetch.aws.access-key-id:}")
    private String accessKeyId;

    @GetMapping({"/api/health", "/health", "/"})
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean awsConfigured = accessKeyId != null
                && !accessKeyId.isBlank()
                && !accessKeyId.contains("your_aws_access_key_id");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "FotoFetch AI Backend");
        response.put("awsConfigured", awsConfigured);
        response.put("awsRegion", awsRegion);
        response.put("s3Bucket", bucketName);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }
}
