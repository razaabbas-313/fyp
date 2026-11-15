package com.fotofetch.lambda.image;

public class ThumbnailResult {

    private final String thumbnailKey;
    private final int width;
    private final int height;
    private final long fileSize;

    public ThumbnailResult(String thumbnailKey, int width, int height, long fileSize) {
        this.thumbnailKey = thumbnailKey;
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
    }

    public String getThumbnailKey() {
        return thumbnailKey;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long fileSize() {
        return fileSize;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getFileSize() {
        return fileSize;
    }
}
