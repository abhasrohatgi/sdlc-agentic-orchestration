package com.urlshortner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * JPA Entity representing individual click events and metadata recorded
 * when a shortened URL is resolved and accessed by a client.
 */
@Entity(name = "UrlClick")
@Table(
    name = "url_clicks",
    indexes = {
        @Index(name = "idx_url_click_short_code", columnList = "short_code"),
        @Index(name = "idx_url_click_short_code_clicked_at", columnList = "short_code, clicked_at")
    }
)
public class UrlAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 50)
    private String shortCode;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "referrer", length = 2048)
    private String referrer;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser", length = 50)
    private String browser;

    @Column(name = "operating_system", length = 50)
    private String operatingSystem;

    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "clicked_at", nullable = false, updatable = false)
    private Instant clickedAt;

    /**
     * Default constructor required by JPA.
     */
    public UrlAnalytics() {
    }

    /**
     * Convenience constructor for initializing a click analytics record.
     *
     * @param shortCode       unique short identifier
     * @param clientIp        IP address of the requesting client
     * @param userAgent       User-Agent header string
     * @param referrer        HTTP Referer header string
     * @param deviceType      classified device type (e.g., Desktop, Mobile, Tablet)
     * @param browser         parsed browser name
     * @param operatingSystem parsed operating system
     * @param country         ISO country code derived from client IP
     */
    public UrlAnalytics(String shortCode, String clientIp, String userAgent, String referrer,
                        String deviceType, String browser, String operatingSystem, String country) {
        this.shortCode = shortCode;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.deviceType = deviceType;
        this.browser = browser;
        this.operatingSystem = operatingSystem;
        this.country = country;
    }

    @PrePersist
    protected void onCreate() {
        if (this.clickedAt == null) {
            this.clickedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(String operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(Instant clickedAt) {
        this.clickedAt = clickedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlAnalytics that = (UrlAnalytics) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "UrlAnalytics{" +
                "id=" + id +
                ", shortCode='" + shortCode + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", deviceType='" + deviceType + '\'' +
                ", browser='" + browser + '\'' +
                ", operatingSystem='" + operatingSystem + '\'' +
                ", country='" + country + '\'' +
                ", clickedAt=" + clickedAt +
                '}';
    }
}