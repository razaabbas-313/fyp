package com.fotofetch.storage;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class ThumbnailService {

    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;
    private static final double OUTPUT_QUALITY = 0.85;

    public byte[] generateThumbnail(InputStream in) throws IOException {
        log.info("Generating {}x{} JPEG thumbnail from input stream", MAX_WIDTH, MAX_HEIGHT);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Thumbnails.of(in)
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .outputFormat("jpg")
                    .outputQuality(OUTPUT_QUALITY)
                    .toOutputStream(out);

            byte[] thumbnailBytes = out.toByteArray();
            log.info("Generated thumbnail successfully (size: {} bytes)", thumbnailBytes.length);
            return thumbnailBytes;
        }
    }

    public byte[] generateThumbnail(byte[] originalBytes) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(originalBytes)) {
            return generateThumbnail(in);
        }
    }
}
