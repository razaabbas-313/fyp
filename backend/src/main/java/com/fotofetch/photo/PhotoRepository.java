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

    List<Photo> findByUploadStatusAndThumbnailStatusIn(UploadStatus uploadStatus, Collection<ThumbnailStatus> thumbnailStatuses);

    @Query("SELECT p FROM Photo p WHERE p.uploadStatus = com.fotofetch.photo.UploadStatus.UPLOADED " +
           "AND (p.thumbnailStatus IN (com.fotofetch.photo.ThumbnailStatus.PENDING, com.fotofetch.photo.ThumbnailStatus.FAILED) " +
           "     OR p.indexingStatus IN (com.fotofetch.photo.IndexingStatus.PENDING, com.fotofetch.photo.IndexingStatus.FAILED) " +
           "     OR (p.thumbnailStatus = com.fotofetch.photo.ThumbnailStatus.PROCESSING AND p.processingUpdatedAt < :stuckBefore) " +
           "     OR (p.indexingStatus = com.fotofetch.photo.IndexingStatus.INDEXING AND p.processingUpdatedAt < :stuckBefore)) " +
           "AND (p.nextRetryAt IS NULL OR p.nextRetryAt <= :now) " +
           "AND p.processingAttempts < :maxRetries " +
           "ORDER BY p.createdAt ASC")
    List<Photo> findRecoverablePhotos(
            @Param("now") java.time.LocalDateTime now,
            @Param("stuckBefore") java.time.LocalDateTime stuckBefore,
            @Param("maxRetries") int maxRetries);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE Photo p SET p.processingAttempts = p.processingAttempts + 1, " +
           "p.lastProcessingAttemptAt = :now, " +
           "p.processingUpdatedAt = :now " +
           "WHERE p.id = :photoId " +
           "AND (p.thumbnailStatus != com.fotofetch.photo.ThumbnailStatus.PROCESSING " +
           "     OR p.processingUpdatedAt IS NULL " +
           "     OR p.processingUpdatedAt < :stuckBefore)")
    int claimPhotoForProcessing(
            @Param("photoId") UUID photoId,
            @Param("now") java.time.LocalDateTime now,
            @Param("stuckBefore") java.time.LocalDateTime stuckBefore);
}
