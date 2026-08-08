package com.urlshortner.service;

import com.urlshortner.dto.ShortenUrlRequest;
import com.urlshortner.dto.UrlAnalyticsDto;
import com.urlshortner.dto.UrlResponse;

/**
 * Service interface defining core URL shortening, short code resolution,
 * metadata management, and analytics operations.
 */
public interface UrlShortenerService {

    /**
     * Shortens a provided long URL according to the specifications in the request.
     *
     * @param request payload containing the original URL, optional custom alias, and optional expiration
     * @return {@link UrlResponse} containing details of the created short URL mapping
     */
    UrlResponse shortenUrl(ShortenUrlRequest request);

    /**
     * Resolves a short code to its original target URL and records click analytics asynchronously.
     *
     * @param shortCode unique short identifier
     * @param clientIp client IP address for analytics tracking
     * @param userAgent HTTP User-Agent header value for device classification
     * @param referrer HTTP Referer header value for traffic source tracking
     * @return original destination URL
     */
    String resolveUrl(String shortCode, String clientIp, String userAgent, String referrer);

    /**
     * Retrieves mapping metadata and configuration for a short code.
     *
     * @param shortCode unique short identifier
     * @return {@link UrlResponse} with details of the URL mapping
     */
    UrlResponse getUrlDetails(String shortCode);

    /**
     * Deactivates or removes a short URL mapping by its code.
     *
     * @param shortCode unique short identifier to delete
     */
    void deleteShortUrl(String shortCode);

    /**
     * Fetches click metrics and access analytics for a short code.
     *
     * @param shortCode unique short identifier
     * @return {@link UrlAnalyticsDto} containing click metrics and aggregated statistics
     */
    UrlAnalyticsDto getUrlAnalytics(String shortCode);
}