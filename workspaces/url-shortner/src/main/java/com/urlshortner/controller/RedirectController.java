package com.urlshortner.controller;

import com.urlshortner.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller handling short code resolution and HTTP redirection.
 * Maps incoming short URLs to their original target destinations and records click analytics.
 */
@RestController
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final UrlShortenerService urlShortenerService;

    public RedirectController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    /**
     * Resolves a short code and redirects the client to the original URL using HTTP 302 (Found).
     * Collects client metadata including IP address, User-Agent, and Referrer for async metrics tracking.
     *
     * @param shortCode   unique short code identifier
     * @param userAgent   HTTP User-Agent header value
     * @param referrer    HTTP Referer header value
     * @param request     HTTP request details for IP extraction
     * @return ResponseEntity with 302 Found status and Location header set to target URL
     */
    @GetMapping("/{shortCode:[a-zA-Z0-9_-]+}")
    public ResponseEntity<Void> redirectToOriginalUrl(
            @PathVariable("shortCode") String shortCode,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            @RequestHeader(value = "Referer", required = false) String referrer,
            HttpServletRequest request) {

        log.debug("Received redirection request for short code: {}", shortCode);

        String clientIp = extractClientIp(request);
        String targetUrl = urlShortenerService.resolveUrl(shortCode, clientIp, userAgent, referrer);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(targetUrl));

        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * Helper method to extract the client IP address from request headers,
     * accounting for reverse proxy configurations like X-Forwarded-For.
     *
     * @param request HttpServletRequest instance
     * @return client IP address string
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}