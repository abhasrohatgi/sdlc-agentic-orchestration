package com.urlshortner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Data Transfer Object representing the response payload containing details
 * of a created or retrieved shortened URL mapping.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShortenResponse {

    @JsonProperty("shortCode")
    private String shortCode;

    @JsonProperty("shortUrl")
    private String shortUrl;

    @JsonProperty("originalUrl")
    private String originalUrl;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("expiresAt")
    private Instant expiresAt;

    @JsonProperty("clickCount")
    private long clickCount;

    @JsonProperty("active")
    private boolean active;

    /**
     * Default constructor for JSON deserialization.
     */
    public ShortenResponse() {
    }

    /**
     * All-arguments constructor.
     *
     * @param shortCode   the generated or custom unique short code
     * @param shortUrl    the complete absolute short URL
     * @param originalUrl the original destination URL
     * @param createdAt   timestamp when the mapping was created
     * @param expiresAt   optional timestamp when the mapping expires
     * @param clickCount  total click count recorded for the short code
     * @param active      whether the URL mapping is currently active
     */
    public ShortenResponse(String shortCode, String shortUrl, String originalUrl,
                           Instant createdAt, Instant expiresAt, long clickCount, boolean active) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.clickCount = clickCount;
        this.active = active;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShortenResponse that = (ShortenResponse) o;
        return clickCount == that.clickCount &&
                active == that.active &&
                Objects.equals(shortCode, that.shortCode) &&
                Objects.equals(shortUrl, that.shortUrl) &&
                Objects.equals(originalUrl, that.originalUrl) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode, shortUrl, originalUrl, createdAt, expiresAt, clickCount, active);
    }

    @Override
    public String toString() {
        return "ShortenResponse{" +
                "shortCode='" + shortCode + '\'' +
                ", shortUrl='" + shortUrl + '\'' +
                ", originalUrl='" + originalUrl + '\'' +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", clickCount=" + clickCount +
                ", active=" + active +
                '}';
    }
}