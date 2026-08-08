package com.urlshortner.repository;

import com.urlshortner.entity.UrlClick;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for persisting and querying URL click analytics events.
 * Provides aggregation methods for tracking traffic sources, device types,
 * unique visitors, and temporal click distributions.
 */
@Repository
public interface UrlAnalyticsRepository extends JpaRepository<UrlClick, Long> {

    /**
     * Counts total click events recorded for a given short code.
     *
     * @param shortCode unique short identifier
     * @return total count of clicks
     */
    long countByShortCode(String shortCode);

    /**
     * Counts unique visitors (distinct client IP addresses) for a given short code.
     *
     * @param shortCode unique short identifier
     * @return count of distinct client IP addresses
     */
    @Query("SELECT COUNT(DISTINCT c.clientIp) FROM UrlClick c WHERE c.shortCode = :shortCode")
    long countUniqueVisitorsByShortCode(@Param("shortCode") String shortCode);

    /**
     * Counts total clicks recorded for a short code within a specified time window.
     *
     * @param shortCode unique short identifier
     * @param start     start of time window
     * @param end       end of time window
     * @return count of clicks in time range
     */
    long countByShortCodeAndClickedAtBetween(String shortCode, Instant start, Instant end);

    /**
     * Retrieves top referring sources for a short code, ordered by click frequency.
     *
     * @param shortCode unique short identifier
     * @param pageable  pagination and limiting instructions
     * @return list of object arrays containing [referrer string, click count]
     */
    @Query("SELECT c.referrer, COUNT(c) FROM UrlClick c WHERE c.shortCode = :shortCode GROUP BY c.referrer ORDER BY COUNT(c) DESC")
    List<Object[]> findTopReferrersByShortCode(@Param("shortCode") String shortCode, Pageable pageable);

    /**
     * Retrieves device classification breakdown (e.g., Desktop, Mobile, Tablet) for a short code.
     *
     * @param shortCode unique short identifier
     * @return list of object arrays containing [device type string, click count]
     */
    @Query("SELECT c.deviceType, COUNT(c) FROM UrlClick c WHERE c.shortCode = :shortCode GROUP BY c.deviceType ORDER BY COUNT(c) DESC")
    List<Object[]> findDeviceTypeBreakdownByShortCode(@Param("shortCode") String shortCode);

    /**
     * Retrieves browser classification breakdown (e.g., Chrome, Firefox, Safari) for a short code.
     *
     * @param shortCode unique short identifier
     * @return list of object arrays containing [browser string, click count]
     */
    @Query("SELECT c.browser, COUNT(c) FROM UrlClick c WHERE c.shortCode = :shortCode GROUP BY c.browser ORDER BY COUNT(c) DESC")
    List<Object[]> findBrowserBreakdownByShortCode(@Param("shortCode") String shortCode);

    /**
     * Retrieves geographic country breakdown for a short code.
     *
     * @param shortCode unique short identifier
     * @return list of object arrays containing [country code string, click count]
     */
    @Query("SELECT c.country, COUNT(c) FROM UrlClick c WHERE c.shortCode = :shortCode GROUP BY c.country ORDER BY COUNT(c) DESC")
    List<Object[]> findCountryBreakdownByShortCode(@Param("shortCode") String shortCode);

    /**
     * Finds recent click events for a given short code ordered by click timestamp descending.
     *
     * @param shortCode unique short identifier
     * @param pageable  pagination options
     * @return pageable list of recent {@link UrlClick} records
     */
    List<UrlClick> findByShortCodeOrderByClickedAtDesc(String shortCode, Pageable pageable);

    /**
     * Deletes all recorded click events associated with a short code.
     *
     * @param shortCode unique short identifier
     */
    @Modifying
    @Query("DELETE FROM UrlClick c WHERE c.shortCode = :shortCode")
    void deleteByShortCode(@Param("shortCode") String shortCode);
}