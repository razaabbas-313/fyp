package com.fotofetch.download;

import com.fotofetch.photo.Photo;
import com.fotofetch.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZipService {

    private final S3StorageService s3StorageService;

    public void streamPhotosZip(List<Photo> photos, OutputStream outputStream) throws IOException {
        log.info("Starting streaming ZIP for {} photos", photos.size());
        
        Set<String> addedFilenames = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            for (Photo photo : photos) {
                if (photo.getOriginalS3Key() == null) {
                    continue;
                }

                // Ensure unique filenames within the ZIP archive
                String entryName = photo.getOriginalFilename();
                int counter = 1;
                while (addedFilenames.contains(entryName)) {
                    int dotIdx = photo.getOriginalFilename().lastIndexOf('.');
                    if (dotIdx > 0) {
                        String name = photo.getOriginalFilename().substring(0, dotIdx);
                        String ext = photo.getOriginalFilename().substring(dotIdx);
                        entryName = name + "_" + counter + ext;
                    } else {
                        entryName = photo.getOriginalFilename() + "_" + counter;
                    }
                    counter++;
                }
                addedFilenames.add(entryName);

                try {
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zos.putNextEntry(zipEntry);

                    try (ResponseInputStream<GetObjectResponse> s3Stream =
                                 s3StorageService.getObjectStream(photo.getOriginalS3Key())) {
                        s3Stream.transferTo(zos);
                    }
                    zos.closeEntry();
                    log.info("Added photo to ZIP stream: {}", entryName);
                } catch (Exception ex) {
                    log.warn("Failed to stream photo {} into ZIP: {}", photo.getId(), ex.getMessage());
                }
            }
            zos.finish();
            log.info("Finished streaming ZIP successfully");
        }
    }
}
