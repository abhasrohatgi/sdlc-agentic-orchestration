package com.urlshortner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object containing aggregated metrics, click statistics,
 * referrer sources, and device breakdowns for a shortened URL.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyticsResponse {

    @JsonProperty("shortCode")
    private String shortCode;

    @JsonProperty("totalClicks")
    private long totalClicks;

    @JsonProperty("uniqueVisitors")
    private long uniqueVisitors;

    @JsonProperty("clicksLast24Hours")
    private long clicksLast24Hours;

    @JsonProperty("clicksLast7Days")
    private long clicksLast7Days;

    @JsonProperty("topReferrers")
    private Map<String, Long> topReferrers;

    @JsonProperty("deviceBreakdown")
    private Map<String, Long> deviceBreakdown;

    @JsonProperty("browserBreakdown")
    private Map<String, Long> browserBreakdown;

    @JsonProperty("osBreakdown")
    private Map<String, Long> osBreakdown;

    @JsonProperty("countryBreakdown")
    private Map<String, Long> countryBreakdown;

    @JsonProperty("lastClickedAt")
    private Instant lastClickedAt;

    /**
     * Default constructor for JSON deserialization.
     */
    public AnalyticsResponse() {
    }

    /**
     * All-arguments constructor.
     *
     * @param shortCode        the short URL identifier
     * @param totalClicks      total number of clicks recorded
     * @param uniqueVisitors   count of unique client IP addresses
     * @param clicksLast24Hours total clicks recorded within the last 24 hours
     * @param clicksLast7Days  total clicks recorded within the last 7 days
     * @param topReferrers     map of referring domains/URLs to click count
     * @param deviceBreakdown  map of device types to click count
     * @param browserBreakdown map of browser names to click count
     * @param osBreakdown      map of operating systems to click count
     * @param countryBreakdown map of ISO country codes to click count
     * @param lastClickedAt    timestamp of the most recent click event
     */
    public AnalyticsResponse(String shortCode, long totalClicks, long uniqueVisitors,
                             long clicksLast24Hours, long clicksLast7Days,
                             Map<String, Long> topReferrers, Map<String, Long> deviceBreakdown,
                             Map<String, Long> browserBreakdown, Map<String, Long> osBreakdown,
                             Map<String, Long> countryBreakdown, Instant lastClickedAt) {
        this.shortCode = shortCode;
        this.totalClicks = totalClicks;
        this.uniqueVisitors = uniqueVisitors;
        this.clicksLast24Hours = clicksLast24Hours;
        this.clicksLast7Days = clicksLast7Days;
        this.topReferrers = topReferrers;
        this.deviceBreakdown = deviceBreakdown;
        this.browserBreakdown = browserBreakdown;
        this.osBreakdown = osBreakdown;
        this.countryBreakdown = countryBreakdown;
        this.lastClickedAt = lastClickedAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public long getTotalClicks() {
        return totalClicks;
    }

    public void setTotalClicks(long totalClicks) {
        this.totalClicks = totalClicks;
    }

    public long getUniqueVisitors() {
        return uniqueVisitors;
    }

    public void setUniqueVisitors(long uniqueVisitors) {
        this.uniqueVisitors = uniqueVisitors;
    }

    public long getClicksLast24Hours() {
        return clicksLast24Hours;
    }

    public void setClicksLast24Hours(long clicksLast24Hours) {
        this.clicksLast24Hours = clicksLast24Hours;
    }

    public long getClicksLast7Days() {
        return clicksLast7Days;
    }

    public void setClicksLast7Days(long clicksLast7Days) {
        this.clicksLast7Days = clicksLast7Days;
    }

    public Map<String, Long> getTopReferrers() {
        return topReferrers;
    }

    public void setTopReferrers(Map<String, Long> topReferrers) {
        this.topReferrers = topReferrers;
    }

    public Map<String, Long> getDeviceBreakdown() {
        return deviceBreakdown;
    }

    public void setDeviceBreakdown(Map<String, Long> deviceBreakdown) {
        this.deviceBreakdown = deviceBreakdown;
    }

    public Map<String, Long> getBrowserBreakdown() {
        return browserBreakdown;
    }

    public void setBrowserBreakdown(Map<String, Long> browserBreakdown) {
        this.browserBreakdown = browserBreakdown;
    }

    public Map<String, Long> getOsBreakdown() {
        return osBreakdown;
    }

    public void setOsBreakdown(Map<String, Long> osBreakdown) {
        this.osBreakdown = osBreakdown;
    }

    public Map<String, Long> getCountryBreakdown() {
        return countryBreakdown;
    }

    public void setCountryBreakdown(Map<String, Long> countryBreakdown) {
        this.countryBreakdown = countryBreakdown;
    }

    public Instant getLastClickedAt() {
        return lastClickedAt;
    }

    public void setLastClickedAt(Instant lastClickedAt) {
        this.lastClickedAt = lastClickedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnalyticsResponse that = (AnalyticsResponse) o;
        return totalClicks == that.totalClicks &&
                uniqueVisitors == that.uniqueVisitors &&
                clicksLast24Hours == that.clicksLast24Hours &&
                clicksLast7Days == that.clicksLast7Days &&
                Objects.equals(shortCode, that.shortCode) &&
                Objects.equals(topReferrers, that.topReferrers) &&
                Objects.equals(deviceBreakdown, that.deviceBreakdown) &&
                Objects.equals(browserBreakdown, that.browserBreakdown) &&
                Objects.equals(osBreakdown, that.osBreakdown) &&
                Objects.equals(countryBreakdown, that.countryBreakdown) &&
                Objects.equals(lastClickedAt, that.lastClickedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode, totalClicks, uniqueVisitors, clicksLast24Hours,
                clicksLast7Days, topReferrers, deviceBreakdown, browserBreakdown,
                osBreakdown, countryBreakdown, lastClickedAt);
    }

    @Override
    public String toString() {
        return "AnalyticsResponse{" +
                "shortCode='" + shortCode + '\'' +
                ", totalClicks=" + totalClicks +
                ", uniqueVisitors=" + uniqueVisitors +
                ", clicksLast24Hours=" + clicksLast24Hours +
                ", clicksLast7Days=" + clicksLast7Days +
                ", topReferrers=" + topReferrers +
                ", deviceBreakdown=" + deviceBreakdown +
                ", browserBreakdown=" + browserBreakdown +
                ", osBreakdown=" + osBreakdown +
                ", countryBreakdown=" + countryBreakdown +
                ", lastClickedAt=" + lastClickedAt +
                '}';
    }
}