package com.fotofetch.lambda.image;

import com.fotofetch.lambda.config.LambdaConfig;
import com.fotofetch.lambda.exception.PermanentProcessingException;
import com.fotofetch.lambda.exception.TransientProcessingException;
import com.fotofetch.lambda.model.ThumbnailJob;
import net.coobird.thumbnailator.Thumbnails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class ThumbnailProcessor {

    private final S3Client s3Client;
    private final LambdaConfig config;

    public ThumbnailProcessor(S3Client s3Client, LambdaConfig config) {
        this.s3Client = s3Client;
        this.config = config;
    }

    /**
     * Downloads the original image, verifies dimensions and format against decompression bombs,
     * resizes to a 1024x1024 JPEG thumbnail at 0.85 quality, and uploads to S3.
     */
    public ThumbnailResult processThumbnail(ThumbnailJob job) {
        String bucket = job.getS3Bucket();
        String originalKey = job.getOriginalS3Key();
        String thumbnailKey = job.getExpectedThumbnailS3Key();

        byte[] originalBytes = downloadOriginal(bucket, originalKey);

        // Validate image dimensions and format before processing
        DimensionInfo dimensions = inspectAndValidateImage(originalBytes);

        // Generate thumbnail using Thumbnailator
        byte[] thumbnailBytes = generateThumbnailBytes(originalBytes);

        // Compute actual thumbnail dimensions
        int targetWidth = dimensions.width;
        int targetHeight = dimensions.height;
        double scale = Math.min(
                (double) LambdaConfig.THUMBNAIL_MAX_WIDTH / targetWidth,
                (double) LambdaConfig.THUMBNAIL_MAX_HEIGHT / targetHeight
        );
        int finalThumbWidth = scale < 1.0 ? Math.max(1, (int) Math.round(targetWidth * scale)) : targetWidth;
        int finalThumbHeight = scale < 1.0 ? Math.max(1, (int) Math.round(targetHeight * scale)) : targetHeight;

        // Upload thumbnail to S3
        uploadThumbnail(bucket, thumbnailKey, thumbnailBytes);

        return new ThumbnailResult(thumbnailKey, finalThumbWidth, finalThumbHeight, thumbnailBytes.length);
    }

    private byte[] downloadOriginal(String bucket, String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest)) {
            long size = s3Stream.response().contentLength() != null ? s3Stream.response().contentLength() : 0L;
            if (size > config.getMaxFileSizeBytes()) {
                throw new PermanentProcessingException(String.format(
                        "File size (%d bytes) exceeds configured maximum limit of %d bytes",
                        size, config.getMaxFileSizeBytes()));
            }

            return s3Stream.readAllBytes();
        } catch (NoSuchKeyException ex) {
            throw new PermanentProcessingException("Original photo object does not exist in S3: " + key, ex);
        } catch (S3Exception ex) {
            int statusCode = ex.statusCode();
            if (statusCode == 404) {
                throw new PermanentProcessingException("Original photo object does not exist in S3: " + key, ex);
            }
            if (statusCode >= 500 || statusCode == 429) {
                throw new TransientProcessingException("Transient S3 error fetching object (" + statusCode + "): " + ex.getMessage(), ex);
            }
            throw new PermanentProcessingException("Permanent S3 error fetching object: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new TransientProcessingException("Network/IO error reading S3 object stream: " + ex.getMessage(), ex);
        }
    }

    private DimensionInfo inspectAndValidateImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new PermanentProcessingException("Empty image data received from S3");
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (iis == null) {
                throw new PermanentProcessingException("Cannot create ImageInputStream from image data");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new PermanentProcessingException("Unsupported image format: no suitable ImageReader found");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0) {
                    throw new PermanentProcessingException(String.format("Invalid image dimensions: %dx%d", width, height));
                }

                // Protect against decompression bombs
                long totalPixels = (long) width * (long) height;
                if (totalPixels > config.getMaxImagePixels()) {
                    throw new PermanentProcessingException(String.format(
                            "Decompression bomb protection: Image dimensions (%dx%d = %d pixels) exceed maximum allowed pixels (%d)",
                            width, height, totalPixels, config.getMaxImagePixels()));
                }

                if (width > config.getMaxDimension() || height > config.getMaxDimension()) {
                    throw new PermanentProcessingException(String.format(
                            "Image dimension exceeds maximum allowed dimension (%dx%d > %d)",
                            width, height, config.getMaxDimension()));
                }

                return new DimensionInfo(width, height);
            } finally {
                reader.dispose();
            }
        } catch (PermanentProcessingException pEx) {
            throw pEx;
        } catch (Exception ex) {
            throw new PermanentProcessingException("Corrupt image data or failed decoding: " + ex.getMessage(), ex);
        }
    }

    private byte[] generateThumbnailBytes(byte[] originalBytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(originalBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(originalBytes.length, 512 * 1024))) {

            Thumbnails.of(in)
                    .size(LambdaConfig.THUMBNAIL_MAX_WIDTH, LambdaConfig.THUMBNAIL_MAX_HEIGHT)
                    .outputFormat("jpg")
                    .outputQuality(LambdaConfig.THUMBNAIL_QUALITY)
                    .toOutputStream(out);

            byte[] result = out.toByteArray();
            if (result.length == 0) {
                throw new PermanentProcessingException("Thumbnail generation produced empty output");
            }
            return result;
        } catch (Exception ex) {
            throw new PermanentProcessingException("Thumbnailator processing failed: " + ex.getMessage(), ex);
        }
    }

    private void uploadThumbnail(String bucket, String key, byte[] bytes) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("image/jpeg")
                .contentLength((long) bytes.length)
                .build();

        try {
            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
        } catch (S3Exception ex) {
            int statusCode = ex.statusCode();
            if (statusCode >= 500 || statusCode == 429) {
                throw new TransientProcessingException("Transient S3 error uploading thumbnail (" + statusCode + "): " + ex.getMessage(), ex);
            }
            throw new PermanentProcessingException("Permanent S3 error uploading thumbnail: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new TransientProcessingException("Failed to upload thumbnail to S3: " + ex.getMessage(), ex);
        }
    }

    private static class DimensionInfo {
        final int width;
        final int height;

        DimensionInfo(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
