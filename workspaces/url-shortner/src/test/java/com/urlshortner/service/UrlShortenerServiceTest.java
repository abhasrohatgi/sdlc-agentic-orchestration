package com.urlshortner.service;

import com.urlshortner.dto.ShortenUrlRequest;
import com.urlshortner.dto.UrlAnalyticsDto;
import com.urlshortner.dto.UrlResponse;
import com.urlshortner.entity.UrlMapping;
import com.urlshortner.exception.AliasAlreadyExistsException;
import com.urlshortner.exception.UrlExpiredException;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.UrlMappingRepository;
import com.urlshortner.service.impl.UrlShortenerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;

    @Mock
    private HashService hashService;

    @Mock
    private Base62Service base62Service;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UrlShortenerServiceImpl urlShortenerService;

    private static final String BASE_URL = "http://localhost:8080";
    private static final String ORIGINAL_URL = "https://www.example.com/articles/java-21-features";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlShortenerService, "baseUrl", BASE_URL);
    }

    @Nested
    @DisplayName("Shorten URL Tests")
    class ShortenUrlTests {

        @Test
        @DisplayName("Should successfully shorten URL with generated short code")
        void shortenUrl_WithGeneratedCode_Success() {
            ShortenUrlRequest request = new ShortenUrlRequest();
            request.setOriginalUrl(ORIGINAL_URL);

            long expectedHash = 987654321L;
            String generatedCode = "bX9K1a";

            when(hashService.hash64(eq(ORIGINAL_URL), anyInt())).thenReturn(expectedHash);
            when(base62Service.encode(Math.abs(expectedHash))).thenReturn(generatedCode);
            when(urlMappingRepository.existsByShortCode(generatedCode)).thenReturn(false);

            UrlMapping savedEntity = new UrlMapping();
            savedEntity.setId(1L);
            savedEntity.setShortCode(generatedCode);
            savedEntity.setOriginalUrl(ORIGINAL_URL);
            savedEntity.setActive(true);
            savedEntity.setCreatedAt(Instant.now());

            when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(savedEntity);

            UrlResponse response = urlShortenerService.shortenUrl(request);

            assertThat(response).isNotNull();
            assertThat(response.getShortCode()).isEqualTo(generatedCode);
            assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/" + generatedCode);
            assertThat(response.getOriginalUrl()).isEqualTo(ORIGINAL_URL);
            assertThat(response.isActive()).isTrue();

            ArgumentCaptor<UrlMapping> entityCaptor = ArgumentCaptor.forClass(UrlMapping.class);
            verify(urlMappingRepository).save(entityCaptor.capture());
            UrlMapping captured = entityCaptor.getValue();
            assertThat(captured.getShortCode()).isEqualTo(generatedCode);
            assertThat(captured.getOriginalUrl()).isEqualTo(ORIGINAL_URL);
        }

        @Test
        @DisplayName("Should successfully shorten URL with custom alias when available")
        void shortenUrl_WithCustomAlias_Success() {
            String customAlias = "my-custom-link";
            ShortenUrlRequest request = new ShortenUrlRequest();
            request.setOriginalUrl(ORIGINAL_URL);
            request.setCustomAlias(customAlias);

            when(urlMappingRepository.existsByShortCode(customAlias)).thenReturn(false);

            UrlMapping savedEntity = new UrlMapping();
            savedEntity.setId(2L);
            savedEntity.setShortCode(customAlias);
            savedEntity.setCustomAlias(customAlias);
            savedEntity.setOriginalUrl(ORIGINAL_URL);
            savedEntity.setActive(true);
            savedEntity.setCreatedAt(Instant.now());

            when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(savedEntity);

            UrlResponse response = urlShortenerService.shortenUrl(request);

            assertThat(response).isNotNull();
            assertThat(response.getShortCode()).isEqualTo(customAlias);
            assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/" + customAlias);
            verify(hashService, never()).hash64(anyString(), anyInt());
            verify(base62Service, never()).encode(any());
        }

        @Test
        @DisplayName("Should throw AliasAlreadyExistsException when custom alias is taken")
        void shortenUrl_WithCustomAlias_AlreadyExists_ThrowsException() {
            String existingAlias = "existing-alias";
            ShortenUrlRequest request = new ShortenUrlRequest();
            request.setOriginalUrl(ORIGINAL_URL);
            request.setCustomAlias(existingAlias);

            when(urlMappingRepository.existsByShortCode(existingAlias)).thenReturn(true);

            assertThatThrownBy(() -> urlShortenerService.shortenUrl(request))
                    .isInstanceOf(AliasAlreadyExistsException.class)
                    .hasMessageContaining(existingAlias);

            verify(urlMappingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should resolve hash collisions by incrementing seed")
        void shortenUrl_HashCollision_ResolvesWithNextSeed() {
            ShortenUrlRequest request = new ShortenUrlRequest();
            request.setOriginalUrl(ORIGINAL_URL);

            String collidedCode = "code01";
            String resolvedCode = "code02";

            when(hashService.hash64(ORIGINAL_URL, 0)).thenReturn(100L);
            when(hashService.hash64(ORIGINAL_URL, 1)).thenReturn(200L);

            when(base62Service.encode(100L)).thenReturn(collidedCode);
            when(base62Service.encode(200L)).thenReturn(resolvedCode);

            when(urlMappingRepository.existsByShortCode(collidedCode)).thenReturn(true);
            when(urlMappingRepository.existsByShortCode(resolvedCode)).thenReturn(false);

            UrlMapping savedEntity = new UrlMapping();
            savedEntity.setShortCode(resolvedCode);
            savedEntity.setOriginalUrl(ORIGINAL_URL);
            savedEntity.setActive(true);

            when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(savedEntity);

            UrlResponse response = urlShortenerService.shortenUrl(request);

            assertThat(response.getShortCode()).isEqualTo(resolvedCode);
            verify(hashService, times(2)).hash64(eq(ORIGINAL_URL), anyInt());
        }
    }

    @Nested
    @DisplayName("Resolve URL Tests")
    class ResolveUrlTests {

        private final String shortCode = "resolv1";
        private final String clientIp = "192.168.1.1";
        private final String userAgent = "Mozilla/5.0";
        private final String referrer = "https://google.com";

        @Test
        @DisplayName("Should resolve URL from Redis cache when hit")
        void resolveUrl_FromCache_Success() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("url:shortcode:" + shortCode)).thenReturn(ORIGINAL_URL);

            String resolvedUrl = urlShortenerService.resolveUrl(shortCode, clientIp, userAgent, referrer);

            assertThat(resolvedUrl).isEqualTo(ORIGINAL_URL);
            verify(urlMappingRepository, never()).findByShortCodeAndIsActiveTrue(anyString());
            verify(analyticsService).recordClickAsync(shortCode, clientIp, userAgent, referrer);
        }

        @Test
        @DisplayName("Should resolve URL from DB on cache miss, write to cache, and record analytics")
        void resolveUrl_FromDatabase_SuccessAndCaches() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("url:shortcode:" + shortCode)).thenReturn(null);

            UrlMapping mapping = new UrlMapping();
            mapping.setShortCode(shortCode);
            mapping.setOriginalUrl(ORIGINAL_URL);
            mapping.setActive(true);

            when(urlMappingRepository.findByShortCodeAndIsActiveTrue(shortCode)).thenReturn(Optional.of(mapping));

            String resolvedUrl = urlShortenerService.resolveUrl(shortCode, clientIp, userAgent, referrer);

            assertThat(resolvedUrl).isEqualTo(ORIGINAL_URL);
            verify(valueOperations).set(eq("url:shortcode:" + shortCode), eq(ORIGINAL_URL), any());
            verify(analyticsService).recordClickAsync(shortCode, clientIp, userAgent, referrer);
        }

        @Test
        @DisplayName("Should throw UrlNotFoundException when code does not exist in DB")
        void resolveUrl_NotFound_ThrowsException() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("url:shortcode:" + shortCode)).thenReturn(null);
            when(urlMappingRepository.findByShortCodeAndIsActiveTrue(shortCode)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> urlShortenerService.resolveUrl(shortCode, clientIp, userAgent, referrer))
                    .isInstanceOf(UrlNotFoundException.class);

            verify(analyticsService, never()).recordClickAsync(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw UrlExpiredException when shortened URL has passed expiration")
        void resolveUrl_Expired_ThrowsException() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("url:shortcode:" + shortCode)).thenReturn(null);

            UrlMapping mapping = new UrlMapping();
            mapping.setShortCode(shortCode);
            mapping.setOriginalUrl(ORIGINAL_URL);
            mapping.setActive(true);
            mapping.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

            when(urlMappingRepository.findByShortCodeAndIsActiveTrue(shortCode)).thenReturn(Optional.of(mapping));

            assertThatThrownBy(() -> urlShortenerService.resolveUrl(shortCode, clientIp, userAgent, referrer))
                    .isInstanceOf(UrlExpiredException.class);

            verify(analyticsService, never()).recordClickAsync(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Details, Delete and Analytics Tests")
    class MetadataAndAnalyticsTests {

        private final String shortCode = "meta123";

        @Test
        @DisplayName("Should retrieve URL details successfully")
        void getUrlDetails_Success() {
            UrlMapping mapping = new UrlMapping();
            mapping.setShortCode(shortCode);
            mapping.setOriginalUrl(ORIGINAL_URL);
            mapping.setActive(true);
            mapping.setClickCount(42L);
            mapping.setCreatedAt(Instant.now());

            when(urlMappingRepository.findByShortCode(shortCode)).thenReturn(Optional.of(mapping));

            UrlResponse response = urlShortenerService.getUrlDetails(shortCode);

            assertThat(response).isNotNull();
            assertThat(response.getShortCode()).isEqualTo(shortCode);
            assertThat(response.getOriginalUrl()).isEqualTo(ORIGINAL_URL);
            assertThat(response.getClickCount()).isEqualTo(42L);
        }

        @Test
        @DisplayName("Should throw UrlNotFoundException when details requested for non-existent code")
        void getUrlDetails_NotFound_ThrowsException() {
            when(urlMappingRepository.findByShortCode(shortCode)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> urlShortenerService.getUrlDetails(shortCode))
                    .isInstanceOf(UrlNotFoundException.class);
        }

        @Test
        @DisplayName("Should deactivate mapping and evict cache on deleteShortUrl")
        void deleteShortUrl_Success() {
            UrlMapping mapping = new UrlMapping();
            mapping.setShortCode(shortCode);
            mapping.setOriginalUrl(ORIGINAL_URL);
            mapping.setActive(true);

            when(urlMappingRepository.findByShortCode(shortCode)).thenReturn(Optional.of(mapping));

            urlShortenerService.deleteShortUrl(shortCode);

            assertThat(mapping.isActive()).isFalse();
            verify(urlMappingRepository).save(mapping);
            verify(stringRedisTemplate).delete("url:shortcode:" + shortCode);
        }

        @Test
        @DisplayName("Should delegate to AnalyticsService on getUrlAnalytics")
        void getUrlAnalytics_DelegatesToAnalyticsService() {
            UrlAnalyticsDto expectedAnalytics = new UrlAnalyticsDto();

            when(analyticsService.getAnalytics(shortCode)).thenReturn(expectedAnalytics);

            UrlAnalyticsDto actualAnalytics = urlShortenerService.getUrlAnalytics(shortCode);

            assertThat(actualAnalytics).isSameAs(expectedAnalytics);
            verify(analyticsService).getAnalytics(shortCode);
        }
    }
}