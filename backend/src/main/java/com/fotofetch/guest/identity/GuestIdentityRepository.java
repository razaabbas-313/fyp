package com.fotofetch.guest.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuestIdentityRepository extends JpaRepository<GuestIdentity, UUID> {

    boolean existsByEventIdAndUsernameIgnoreCase(UUID eventId, String username);

    Optional<GuestIdentity> findByEventIdAndUsernameIgnoreCase(UUID eventId, String username);

    List<GuestIdentity> findByEventIdAndNameIgnoreCase(UUID eventId, String name);

    List<GuestIdentity> findByEventIdAndNameContainingIgnoreCase(UUID eventId, String name);

    List<GuestIdentity> findByEventIdOrderByCreatedAtDesc(UUID eventId);
}
