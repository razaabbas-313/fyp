package com.fotofetch.guest.identity;

import com.fotofetch.event.Event;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "guest_identities",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_guest_identity_event_username", columnNames = {"event_id", "username"})
    },
    indexes = {
        @Index(name = "idx_guest_identities_event_id", columnList = "event_id"),
        @Index(name = "idx_guest_identities_username", columnList = "username"),
        @Index(name = "idx_guest_identities_name", columnList = "name")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "username", nullable = false)
    private String username;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "guest_identity_photos", joinColumns = @JoinColumn(name = "identity_id"))
    @Column(name = "photo_id", nullable = false)
    @Builder.Default
    private Set<UUID> matchedPhotoIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
