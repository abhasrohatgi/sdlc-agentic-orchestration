package com.urlshortner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Data Transfer Object representing referrer domain click distribution metrics.
 * Encapsulates the origin domain/URL, total click count from that referrer,
 * and calculated percentage relative to total traffic.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferrerStatsDto {

    @JsonProperty("referrer")
    private String referrer;

    @JsonProperty("clickCount")
    private long clickCount;

    @JsonProperty("percentage")
    private double percentage;

    /**
     * Default constructor for JSON deserialization.
     */
    public ReferrerStatsDto() {
    }

    /**
     * Constructor without percentage calculation.
     *
     * @param referrer   the referrer domain or URL
     * @param clickCount total number of clicks from this referrer
     */
    public ReferrerStatsDto(String referrer, long clickCount) {
        this.referrer = referrer;
        this.clickCount = clickCount;
        this.percentage = 0.0;
    }

    /**
     * All-arguments constructor.
     *
     * @param referrer   the referrer domain or URL
     * @param clickCount total number of clicks from this referrer
     * @param percentage percentage of total clicks represented by this referrer
     */
    public ReferrerStatsDto(String referrer, long clickCount, double percentage) {
        this.referrer = referrer;
        this.clickCount = clickCount;
        this.percentage = percentage;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReferrerStatsDto that = (ReferrerStatsDto) o;
        return clickCount == that.clickCount &&
                Double.compare(that.percentage, percentage) == 0 &&
                Objects.equals(referrer, that.referrer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referrer, clickCount, percentage);
    }

    @Override
    public String toString() {
        return "ReferrerStatsDto{" +
                "referrer='" + referrer + '\'' +
                ", clickCount=" + clickCount +
                ", percentage=" + percentage +
                '}';
    }
}