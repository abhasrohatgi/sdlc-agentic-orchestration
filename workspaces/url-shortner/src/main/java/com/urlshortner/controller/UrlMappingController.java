package com.urlshortner.controller;

import com.urlshortner.dto.ShortenUrlRequest;
import com.urlshortner.dto.UrlAnalyticsDto;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing endpoints for managing shortened URL mappings,
 * retrieving mapping metadata, deactivating short URLs, and fetching click analytics.
 */
@RestController
@RequestMapping("/api/v1/urls")
public class UrlMappingController {

    private static final Logger log = LoggerFactory.getLogger(UrlMappingController.class);

    private final UrlShortenerService urlShortenerService;

    /**
     * Constructs a new UrlMappingController with the required service dependency.
     *
     * @param urlShortenerService core URL shortening service
     */
    public UrlMappingController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    /**
     * Creates a new shortened URL mapping based on the provided request parameters.
     *
     * @param request payload containing original URL, optional custom alias, and optional expiration
     * @return {@link ResponseEntity} containing the created {@link UrlResponse} with HTTP status 201 (Created)
     */
    @PostMapping
    public ResponseEntity<UrlResponse> shortenUrl(@Valid @RequestBody ShortenUrlRequest request) {
        log.info("Received request to shorten URL: {}", request.getOriginalUrl());
        UrlResponse response = urlShortenerService.shortenUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves mapping metadata and configuration for a specified short code.
     *
     * @param shortCode unique short identifier
     * @return {@link ResponseEntity} containing {@link UrlResponse} with HTTP status 200 (OK)
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<UrlResponse> getUrlDetails(@PathVariable("shortCode") String shortCode) {
        log.debug("Fetching metadata for short code: {}", shortCode);
        UrlResponse response = urlShortenerService.getUrlDetails(shortCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Deactivates or soft-deletes a short URL mapping by its unique short code.
     *
     * @param shortCode unique short identifier to deactivate
     * @return {@link ResponseEntity} with HTTP status 204 (No Content)
     */
    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteShortUrl(@PathVariable("shortCode") String shortCode) {
        log.info("Request received to delete short code: {}", shortCode);
        urlShortenerService.deleteShortUrl(shortCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves aggregated click metrics, device breakdowns, and traffic statistics for a short code.
     *
     * @param shortCode unique short identifier
     * @return {@link ResponseEntity} containing {@link UrlAnalyticsDto} with HTTP status 200 (OK)
     */
    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<UrlAnalyticsDto> getUrlAnalytics(@PathVariable("shortCode") String shortCode) {
        log.debug("Fetching analytics for short code: {}", shortCode);
        UrlAnalyticsDto analytics = urlShortenerService.getUrlAnalytics(shortCode);
        return ResponseEntity.ok(analytics);
    }
}