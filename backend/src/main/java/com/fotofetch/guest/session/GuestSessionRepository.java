package com.fotofetch.guest.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuestSessionRepository extends JpaRepository<GuestSession, UUID> {
    Optional<GuestSession> findBySessionTokenHash(String sessionTokenHash);
}
