package com.fotofetch.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findByEventIdOrderByCreatedAtDesc(UUID eventId);
    Optional<Photo> findByIdAndEventId(UUID id, UUID eventId);
    List<Photo> findByEventIdAndIdIn(UUID eventId, Collection<UUID> ids);
    long countByEventId(UUID eventId);
    long countByEventIdAndIndexingStatus(UUID eventId, IndexingStatus indexingStatus);
    
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.event.user.id = :userId")
    long countTotalPhotosByUserId(@Param("userId") UUID userId);
}
