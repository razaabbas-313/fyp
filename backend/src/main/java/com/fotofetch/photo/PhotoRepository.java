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

    @Query("SELECT p FROM Photo p JOIN FETCH p.event WHERE p.uploadStatus = com.fotofetch.photo.UploadStatus.UPLOADED " +
           "AND (" +
           // 1. Recover Rekognition indexing when thumbnail is already completed
           "     (p.thumbnailStatus = com.fotofetch.photo.ThumbnailStatus.COMPLETED " +
           "      AND (p.indexingStatus = com.fotofetch.photo.IndexingStatus.PENDING " +
           "           OR p.indexingStatus = com.fotofetch.photo.IndexingStatus.FAILED " +
           "           OR (p.indexingStatus = com.fotofetch.photo.IndexingStatus.INDEXING AND p.processingUpdatedAt < :stuckBefore))) " +
           // 2. Recover explicitly failed thumbnail jobs (e.g. Lambda returned FAILED callback)
           "     OR (p.thumbnailStatus = com.fotofetch.photo.ThumbnailStatus.FAILED) " +
           // 3. When Lambda is DISABLED (:lambdaEnabled = false), also recover timeout-based PENDING and PROCESSING thumbnails (original behavior)
           "     OR (:lambdaEnabled = false AND p.thumbnailStatus = com.fotofetch.photo.ThumbnailStatus.PENDING " +
           "         AND (p.processingUpdatedAt < :stuckBefore OR (p.processingUpdatedAt IS NULL AND p.createdAt < :stuckBefore))) " +
           "     OR (:lambdaEnabled = false AND p.thumbnailStatus = com.fotofetch.photo.ThumbnailStatus.PROCESSING " +
           "         AND p.processingUpdatedAt < :stuckBefore)" +
           ") " +
           "AND (p.nextRetryAt IS NULL OR p.nextRetryAt <= :now) " +
           "AND p.processingAttempts < :maxRetries " +
           "ORDER BY p.createdAt ASC")
    List<Photo> findRecoverablePhotos(
            @Param("now") java.time.LocalDateTime now,
            @Param("stuckBefore") java.time.LocalDateTime stuckBefore,
            @Param("maxRetries") int maxRetries,
            @Param("lambdaEnabled") boolean lambdaEnabled);

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

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE Photo p SET p.indexingStatus = com.fotofetch.photo.IndexingStatus.INDEXING, " +
           "p.processingUpdatedAt = :now " +
           "WHERE p.id = :photoId " +
           "AND (p.indexingStatus = com.fotofetch.photo.IndexingStatus.PENDING " +
           "     OR p.indexingStatus = com.fotofetch.photo.IndexingStatus.FAILED " +
           "     OR (p.indexingStatus = com.fotofetch.photo.IndexingStatus.INDEXING AND p.processingUpdatedAt < :stuckBefore))")
    int claimPhotoForIndexing(
            @Param("photoId") UUID photoId,
            @Param("now") java.time.LocalDateTime now,
            @Param("stuckBefore") java.time.LocalDateTime stuckBefore);
}
