package com.fotofetch.photo;

import com.fotofetch.event.Event;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "photos", indexes = {
    @Index(name = "idx_photo_event_id", columnList = "event_id"),
    @Index(name = "idx_photo_indexing_status", columnList = "indexingStatus")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 1024)
    private String originalS3Key;

    @Column(length = 1024)
    private String thumbnailS3Key;

    @Column(nullable = false)
    private String originalFilename;

    @Column(length = 64)
    private String contentType;

    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private UploadStatus uploadStatus = UploadStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ThumbnailStatus thumbnailStatus = ThumbnailStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private IndexingStatus indexingStatus = IndexingStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer facesFound = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
