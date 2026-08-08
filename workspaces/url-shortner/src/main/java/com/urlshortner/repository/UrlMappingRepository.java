package com.urlshortner.repository;

import com.urlshortner.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing {@link UrlMapping} entities.
 * Provides data access operations for short code resolution, existence checks,
 * click count updates, and automated expiration handling.
 */
@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    /**
     * Finds a URL mapping by its unique short code.
     *
     * @param shortCode unique short code identifier
     * @return an {@link Optional} containing the URL mapping if found
     */
    Optional<UrlMapping> findByShortCode(String shortCode);

    /**
     * Finds an active URL mapping by its unique short code.
     *
     * @param shortCode unique short code identifier
     * @return an {@link Optional} containing the active URL mapping if found
     */
    Optional<UrlMapping> findByShortCodeAndIsActiveTrue(String shortCode);

    /**
     * Checks if a URL mapping exists for the given short code.
     *
     * @param shortCode unique short code identifier
     * @return true if mapping exists, false otherwise
     */
    boolean existsByShortCode(String shortCode);

    /**
     * Finds all active URL mappings that have exceeded their expiration timestamp.
     *
     * @param now current timestamp
     * @return list of expired but active URL mappings
     */
    List<UrlMapping> findByExpiresAtBeforeAndIsActiveTrue(Instant now);

    /**
     * Atomically increments the click counter and updates the last accessed timestamp for a short code.
     *
     * @param shortCode      unique short identifier
     * @param lastAccessedAt timestamp of the access event
     * @return number of affected rows
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1, u.lastAccessedAt = :lastAccessedAt WHERE u.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode, @Param("lastAccessedAt") Instant lastAccessedAt);

    /**
     * Bulk deactivates expired URL mappings whose expiration time is on or before the specified timestamp.
     *
     * @param now current timestamp to evaluate expiration
     * @return number of deactivated records
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.isActive = false WHERE u.expiresAt IS NOT NULL AND u.expiresAt <= :now AND u.isActive = true")
    int deactivateExpiredUrls(@Param("now") Instant now);

    /**
     * Updates the active status for a given short code mapping.
     *
     * @param shortCode unique short identifier
     * @param isActive  new active status flag
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.isActive = :isActive WHERE u.shortCode = :shortCode")
    int updateIsActiveByShortCode(@Param("shortCode") String shortCode, @Param("isActive") boolean isActive);
}