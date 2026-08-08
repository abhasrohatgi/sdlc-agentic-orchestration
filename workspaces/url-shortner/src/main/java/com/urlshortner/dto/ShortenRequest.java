package com.urlshortner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

/**
 * Data Transfer Object representing a request to shorten a target URL.
 * Encapsulates the target original URL, an optional custom short code alias,
 * and an optional expiration timestamp.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShortenRequest {

    @NotBlank(message = "Original URL must not be blank")
    @Size(max = 2048, message = "Original URL length must not exceed 2048 characters")
    @JsonProperty("originalUrl")
    private String originalUrl;

    @Size(min = 3, max = 50, message = "Custom alias must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Custom alias must contain only alphanumeric characters, hyphens, or underscores")
    @JsonProperty("customAlias")
    private String customAlias;

    @Future(message = "Expiration timestamp must be in the future")
    @JsonProperty("expiresAt")
    private Instant expiresAt;

    /**
     * Default constructor for JSON deserialization.
     */
    public ShortenRequest() {
    }

    /**
     * All-args constructor.
     *
     * @param originalUrl the original target URL to shorten
     * @param customAlias optional custom alias for the short code
     * @param expiresAt   optional expiration date/time
     */
    public ShortenRequest(String originalUrl, String customAlias, Instant expiresAt) {
        this.originalUrl = originalUrl;
        this.customAlias = customAlias;
        this.expiresAt = expiresAt;
    }

    /**
     * Convenient constructor for basic URL shortening without alias or expiration.
     *
     * @param originalUrl original target URL
     */
    public ShortenRequest(String originalUrl) {
        this(originalUrl, null, null);
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    /**
     * Alias getter for originalUrl to support alternate naming conventions.
     *
     * @return original URL
     */
    public String getUrl() {
        return originalUrl;
    }

    /**
     * Alias setter for originalUrl to support alternate naming conventions.
     *
     * @param url original URL
     */
    public void setUrl(String url) {
        this.originalUrl = url;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShortenRequest that = (ShortenRequest) o;
        return Objects.equals(originalUrl, that.originalUrl) &&
               Objects.equals(customAlias, that.customAlias) &&
               Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalUrl, customAlias, expiresAt);
    }

    @Override
    public String toString() {
        return "ShortenRequest{" +
                "originalUrl='" + originalUrl + '\'' +
                ", customAlias='" + customAlias + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}