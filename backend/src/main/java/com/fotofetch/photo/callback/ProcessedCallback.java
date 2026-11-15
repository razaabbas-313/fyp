package com.fotofetch.photo.callback;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_callbacks", indexes = {
    @Index(name = "idx_processed_callbacks_photo_id", columnList = "photo_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedCallback implements Persistable<String> {

    @Id
    @Column(name = "callback_id", length = 64, nullable = false)
    private String callbackId;

    @Column(name = "photo_id", nullable = false)
    private UUID photoId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public String getId() {
        return callbackId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
