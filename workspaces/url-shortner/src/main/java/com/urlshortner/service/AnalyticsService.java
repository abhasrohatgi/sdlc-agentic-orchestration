package com.urlshortner.service;

import com.urlshortner.dto.UrlAnalyticsDto;

/**
 * Service interface defining analytics tracking, click event recording,
 * and metric aggregation functions for shortened URLs.
 */
public interface AnalyticsService {

    /**
     * Asynchronously records a click event for a given short code along with client metadata.
     *
     * @param shortCode unique short identifier
     * @param clientIp IP address of the client making the request
     * @param userAgent User-Agent header string of the client browser or device
     * @param referrer Referrer URL header string indicating the origin of the request
     */
    void recordClickAsync(String shortCode, String clientIp, String userAgent, String referrer);

    /**
     * Retrieves aggregated analytics and click statistics for a specific short code.
     *
     * @param shortCode unique short identifier
     * @return {@link UrlAnalyticsDto} containing click metrics, unique visitors, and device breakdown
     */
    UrlAnalyticsDto getAnalytics(String shortCode);
}