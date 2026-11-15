package com.fotofetch.guest.session;

import com.fotofetch.event.Event;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "guest_sessions", indexes = {
    @Index(name = "idx_guest_session_token_hash", columnList = "session_token_hash", unique = true),
    @Index(name = "idx_guest_session_event_id", columnList = "event_id"),
    @Index(name = "idx_guest_session_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_token_hash", nullable = false, length = 64, unique = true)
    private String sessionTokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "guest_session_photos", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "photo_id", nullable = false)
    @Builder.Default
    private Set<UUID> matchedPhotoIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
