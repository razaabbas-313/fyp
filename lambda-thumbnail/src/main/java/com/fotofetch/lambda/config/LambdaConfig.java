package com.fotofetch.lambda.config;

public class LambdaConfig {

    public static final String DEFAULT_BUCKET_NAME = "fotofetch-ai-photos-480836974477";
    public static final String DEFAULT_REGION = "ap-south-1";
    public static final String DEFAULT_CALLBACK_URL = "https://fotofetch-backend.onrender.com/api/internal/photos/thumbnail-callback";
    public static final long DEFAULT_MAX_IMAGE_PIXELS = 50_000_000L; // 50 megapixels (e.g. ~7000x7000)
    public static final int DEFAULT_MAX_DIMENSION = 15_000;
    public static final long DEFAULT_MAX_FILE_SIZE = 30L * 1024L * 1024L; // 30 MB
    public static final int THUMBNAIL_MAX_WIDTH = 1024;
    public static final int THUMBNAIL_MAX_HEIGHT = 1024;
    public static final double THUMBNAIL_QUALITY = 0.85;

    private final String s3BucketName;
    private final String awsRegion;
    private final String backendCallbackUrl;
    private final String callbackSecret;
    private final long maxImagePixels;
    private final int maxDimension;
    private final long maxFileSizeBytes;

    public LambdaConfig() {
        this(
                getEnvOrDefault("S3_BUCKET_NAME", DEFAULT_BUCKET_NAME),
                getEnvOrDefault("AWS_REGION", DEFAULT_REGION),
                getEnvOrDefault("BACKEND_CALLBACK_URL", DEFAULT_CALLBACK_URL),
                getEnvOrDefault("CALLBACK_SECRET", ""),
                getLongEnvOrDefault("MAX_IMAGE_PIXELS", DEFAULT_MAX_IMAGE_PIXELS),
                getIntEnvOrDefault("MAX_DIMENSION", DEFAULT_MAX_DIMENSION),
                getLongEnvOrDefault("MAX_FILE_SIZE_BYTES", DEFAULT_MAX_FILE_SIZE)
        );
    }

    public LambdaConfig(String s3BucketName,
                        String awsRegion,
                        String backendCallbackUrl,
                        String callbackSecret,
                        long maxImagePixels,
                        int maxDimension,
                        long maxFileSizeBytes) {
        this.s3BucketName = s3BucketName;
        this.awsRegion = awsRegion;
        this.backendCallbackUrl = backendCallbackUrl;
        this.callbackSecret = callbackSecret;
        this.maxImagePixels = maxImagePixels;
        this.maxDimension = maxDimension;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String getS3BucketName() {
        return s3BucketName;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public String getBackendCallbackUrl() {
        return backendCallbackUrl;
    }

    public String getCallbackSecret() {
        return callbackSecret;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    public int getMaxDimension() {
        return maxDimension;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val.trim() : defaultValue;
    }

    private static long getLongEnvOrDefault(String name, long defaultValue) {
        String val = System.getenv(name);
        if (val != null && !val.isBlank()) {
            try {
                return Long.parseLong(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static int getIntEnvOrDefault(String name, int defaultValue) {
        String val = System.getenv(name);
        if (val != null && !val.isBlank()) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
