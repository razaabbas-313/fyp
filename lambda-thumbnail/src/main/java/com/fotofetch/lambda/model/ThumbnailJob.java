package com.fotofetch.lambda.model;

import java.util.Objects;
import java.util.UUID;

public class ThumbnailJob {

    private final UUID photoId;
    private final UUID eventId;
    private final String s3Bucket;
    private final String originalS3Key;
    private final String filename;
    private final String messageId;

    public ThumbnailJob(UUID photoId,
                        UUID eventId,
                        String s3Bucket,
                        String originalS3Key,
                        String filename,
                        String messageId) {
        this.photoId = Objects.requireNonNull(photoId, "photoId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.s3Bucket = Objects.requireNonNull(s3Bucket, "s3Bucket cannot be null");
        this.originalS3Key = Objects.requireNonNull(originalS3Key, "originalS3Key cannot be null");
        this.filename = filename != null ? filename : "photo.jpg";
        this.messageId = messageId;
    }

    public UUID getPhotoId() {
        return photoId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public String getOriginalS3Key() {
        return originalS3Key;
    }

    public String getFilename() {
        return filename;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getExpectedThumbnailS3Key() {
        return String.format("events/%s/thumbnails/%s.jpg", eventId, photoId);
    }

    @Override
    public String toString() {
        return "ThumbnailJob{" +
                "photoId=" + photoId +
                ", eventId=" + eventId +
                ", s3Bucket='" + s3Bucket + '\'' +
                ", originalS3Key='" + originalS3Key + '\'' +
                ", messageId='" + messageId + '\'' +
                '}';
    }
}
