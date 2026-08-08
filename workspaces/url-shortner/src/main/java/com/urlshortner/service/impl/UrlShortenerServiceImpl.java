package com.urlshortner.service.impl;

import com.urlshortner.dto.ShortenUrlRequest;
import com.urlshortner.dto.UrlAnalyticsDto;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.entity.UrlMapping;
import com.urlshortner.exception.AliasAlreadyExistsException;
import com.urlshortner.exception.UrlExpiredException;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.UrlMappingRepository;
import com.urlshortner.service.AnalyticsService;
import com.urlshortner.service.Base62Service;
import com.urlshortner.service.HashService;
import com.urlshortner.service.UrlShortenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Service implementation managing core URL shortening workflows, including
 * Base62 hashing with collision resolution, Redis multi-level caching,
 * retention lifecycle validation, and asynchronous analytics recording.
 */
@Service
public class UrlShortenerServiceImpl implements UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerServiceImpl.class);
    private static final String REDIS_CACHE_PREFIX = "url:shortcode:";
    private static final int MAX_COLLISION_ATTEMPTS = 10;
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(24);

    private final UrlMappingRepository urlMappingRepository;
    private final HashService hashService;
    private final Base62Service base62Service;
    private final AnalyticsService analyticsService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public UrlShortenerServiceImpl(UrlMappingRepository urlMappingRepository,
                                  HashService hashService,
                                  Base62Service base62Service,
                                  AnalyticsService analyticsService,
                                  StringRedisTemplate stringRedisTemplate) {
        this.urlMappingRepository = urlMappingRepository;
        this.hashService = hashService;
        this.base62Service = base62Service;
        this.analyticsService = analyticsService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional
    public UrlResponse shortenUrl(ShortenUrlRequest request) {
        String originalUrl = request.getOriginalUrl().trim();
        String customAlias = request.getCustomAlias() != null ? request.getCustomAlias().trim() : null;
        Instant expiresAt = request.getExpiresAt();

        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Expiration timestamp must be in the future");
        }

        String shortCode;

        if (customAlias != null && !customAlias.isEmpty()) {
            if (urlMappingRepository.existsByShortCode(customAlias)) {
                log.warn("Custom alias collision detected: {}", customAlias);
                throw AliasAlreadyExistsException.forAlias(customAlias);
            }
            shortCode = customAlias;
        } else {
            shortCode = generateUniqueShortCode(originalUrl);
        }

        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setShortCode(shortCode);
        urlMapping.setOriginalUrl(originalUrl);
        urlMapping.setCustomAlias(customAlias);
        urlMapping.setExpiresAt(expiresAt);
        urlMapping.setActive(true);
        urlMapping.setClickCount(0L);
        urlMapping.setCreatedAt(Instant.now());
        urlMapping.setUpdatedAt(Instant.now());

        UrlMapping savedMapping = urlMappingRepository.save(urlMapping);
        log.info("Successfully created short code mapping: {} -> {}", shortCode, originalUrl);

        cacheUrlMapping(savedMapping);

        return mapToUrlResponse(savedMapping);
    }

    @Override
    @Transactional
    public String resolveUrl(String shortCode, String clientIp, String userAgent, String referrer) {
        String cacheKey = REDIS_CACHE_PREFIX + shortCode;
        String cachedOriginalUrl = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedOriginalUrl != null) {
            log.debug("Cache hit for short code: {}", shortCode);
            analyticsService.recordClickAsync(shortCode, clientIp, userAgent, referrer);
            return cachedOriginalUrl;
        }

        log.debug("Cache miss for short code: {}, querying database", shortCode);
        UrlMapping mapping = urlMappingRepository.findByShortCodeAndIsActiveTrue(shortCode)
                .orElseThrow(() -> UrlNotFoundException.forShortCode(shortCode));

        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Attempted access to expired short code: {}", shortCode);
            mapping.setActive(false);
            urlMappingRepository.save(mapping);
            stringRedisTemplate.delete(cacheKey);
            throw UrlExpiredException.forShortCode(shortCode);
        }

        mapping.setLastAccessedAt(Instant.now());
        mapping.setClickCount(mapping.getClickCount() + 1);
        urlMappingRepository.save(mapping);

        cacheUrlMapping(mapping);

        analyticsService.recordClickAsync(shortCode, clientIp, userAgent, referrer);

        return mapping.getOriginalUrl();
    }

    @Override
    @Transactional(readOnly = true)
    public UrlResponse getUrlDetails(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> UrlNotFoundException.forShortCode(shortCode));

        return mapToUrlResponse(mapping);
    }

    @Override
    @Transactional
    public void deleteShortUrl(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> UrlNotFoundException.forShortCode(shortCode));

        mapping.setActive(false);
        mapping.setUpdatedAt(Instant.now());
        urlMappingRepository.save(mapping);

        String cacheKey = REDIS_CACHE_PREFIX + shortCode;
        stringRedisTemplate.delete(cacheKey);

        log.info("Deactivated and evicted cache for short code: {}", shortCode);
    }

    @Override
    @Transactional(readOnly = true)
    public UrlAnalyticsDto getUrlAnalytics(String shortCode) {
        if (!urlMappingRepository.existsByShortCode(shortCode)) {
            throw UrlNotFoundException.forShortCode(shortCode);
        }
        return analyticsService.getAnalytics(shortCode);
    }

    /**
     * Generates a deterministic short code using MurmurHash3 and Base62 encoding.
     * Applies salt/seed iteration to resolve hash collisions across distinct URLs.
     */
    private String generateUniqueShortCode(String originalUrl) {
        for (int seed = 0; seed < MAX_COLLISION_ATTEMPTS; seed++) {
            long hashValue = hashService.hash64(originalUrl, seed);
            long positiveHash = hashValue & Long.MAX_VALUE;
            String candidateCode = base62Service.encode(positiveHash);

            Optional<UrlMapping> existing = urlMappingRepository.findByShortCode(candidateCode);

            if (existing.isEmpty()) {
                return candidateCode;
            }

            UrlMapping mapping = existing.get();
            if (mapping.getOriginalUrl().equals(originalUrl) && mapping.isActive()) {
                log.info("Reusing existing short code {} for identical original URL", candidateCode);
                return candidateCode;
            }
        }

        log.warn("Collision threshold reached for URL, applying fallback high-entropy code generation");
        long saltedHash = hashService.hash64(originalUrl + System.nanoTime(), 999);
        return base62Service.encode(saltedHash & Long.MAX_VALUE);
    }

    /**
     * Warm up or refresh the Redis entry for a given URL mapping.
     */
    private void cacheUrlMapping(UrlMapping mapping) {
        String cacheKey = REDIS_CACHE_PREFIX + mapping.getShortCode();
        if (mapping.getExpiresAt() != null) {
            Duration remainingTtl = Duration.between(Instant.now(), mapping.getExpiresAt());
            if (!remainingTtl.isNegative() && !remainingTtl.isZero()) {
                stringRedisTemplate.opsForValue().set(cacheKey, mapping.getOriginalUrl(), remainingTtl);
            }
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, mapping.getOriginalUrl(), DEFAULT_CACHE_TTL);
        }
    }

    /**
     * Converts a domain entity to the standard response DTO.
     */
    private UrlResponse mapToUrlResponse(UrlMapping mapping) {
        UrlResponse response = new UrlResponse();
        response.setShortCode(mapping.getShortCode());
        response.setShortUrl(buildFullShortUrl(mapping.getShortCode()));
        response.setOriginalUrl(mapping.getOriginalUrl());
        response.setCreatedAt(mapping.getCreatedAt());
        response.setExpiresAt(mapping.getExpiresAt());
        response.setClickCount(mapping.getClickCount());
        response.setActive(mapping.isActive());
        return response;
    }

    private String buildFullShortUrl(String shortCode) {
        String sanitizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return sanitizedBaseUrl + "/" + shortCode;
    }
}