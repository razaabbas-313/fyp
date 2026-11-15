package com.fotofetch.lambda.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallbackPayload {

    private String callbackId;
    private UUID photoId;
    private UUID eventId;
    private String originalS3Key;
    private String thumbnailS3Key;
    private String status;
    private String errorMessage;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private Long timestamp;

    public CallbackPayload() {
    }

    public CallbackPayload(UUID photoId,
                           UUID eventId,
                           String originalS3Key,
                           String thumbnailS3Key,
                           String status,
                           String errorMessage,
                           Integer width,
                           Integer height,
                           Long fileSize,
                           Long timestamp) {
        this(UUID.randomUUID().toString(), photoId, eventId, originalS3Key, thumbnailS3Key,
                status, errorMessage, width, height, fileSize, timestamp);
    }

    public CallbackPayload(String callbackId,
                           UUID photoId,
                           UUID eventId,
                           String originalS3Key,
                           String thumbnailS3Key,
                           String status,
                           String errorMessage,
                           Integer width,
                           Integer height,
                           Long fileSize,
                           Long timestamp) {
        this.callbackId = callbackId;
        this.photoId = photoId;
        this.eventId = eventId;
        this.originalS3Key = originalS3Key;
        this.thumbnailS3Key = thumbnailS3Key;
        this.status = status;
        this.errorMessage = errorMessage;
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
        this.timestamp = timestamp;
    }

    public static CallbackPayload success(UUID photoId,
                                          UUID eventId,
                                          String originalS3Key,
                                          String thumbnailS3Key,
                                          int width,
                                          int height,
                                          long fileSize,
                                          long timestamp) {
        return new CallbackPayload(UUID.randomUUID().toString(), photoId, eventId, originalS3Key, thumbnailS3Key,
                "COMPLETED", null, width, height, fileSize, timestamp);
    }

    public static CallbackPayload failure(UUID photoId,
                                          UUID eventId,
                                          String originalS3Key,
                                          String errorMessage,
                                          long timestamp) {
        return new CallbackPayload(UUID.randomUUID().toString(), photoId, eventId, originalS3Key, null,
                "FAILED", errorMessage, null, null, null, timestamp);
    }

    public String getCallbackId() {
        return callbackId;
    }

    public void setCallbackId(String callbackId) {
        this.callbackId = callbackId;
    }

    public UUID getPhotoId() {
        return photoId;
    }

    public void setPhotoId(UUID photoId) {
        this.photoId = photoId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getOriginalS3Key() {
        return originalS3Key;
    }

    public void setOriginalS3Key(String originalS3Key) {
        this.originalS3Key = originalS3Key;
    }

    public String getThumbnailS3Key() {
        return thumbnailS3Key;
    }

    public void setThumbnailS3Key(String thumbnailS3Key) {
        this.thumbnailS3Key = thumbnailS3Key;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
