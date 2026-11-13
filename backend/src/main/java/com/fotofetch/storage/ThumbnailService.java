package com.fotofetch.storage;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@Slf4j
public class ThumbnailService {

    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 400;
    private static final double OUTPUT_QUALITY = 0.82;

    public byte[] generateThumbnail(byte[] originalBytes) throws IOException {
        log.info("Generating {}x{} JPEG thumbnail from {} bytes original", MAX_WIDTH, MAX_HEIGHT, originalBytes.length);
        
        try (ByteArrayInputStream in = new ByteArrayInputStream(originalBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
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
}
