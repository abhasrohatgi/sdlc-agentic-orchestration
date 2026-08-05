package com.agenticsdlc.shortener.adapter.web;

import com.agenticsdlc.shortener.domain.LinkStats;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Click statistics for one link.
 *
 * @param code         the link
 * @param totalClicks  total clicks recorded
 * @param firstClickAt first click, or {@code null} if never clicked
 * @param lastClickAt  most recent click, or {@code null} if never clicked
 * @param clicksByDay  chronological series keyed by UTC date, retained window only
 * @param topReferrers referrer counts, highest first
 */
public record StatsResponse(
        String code,
        long totalClicks,
        Instant firstClickAt,
        Instant lastClickAt,
        Map<String, Long> clicksByDay,
        Map<String, Long> topReferrers) {

    public static StatsResponse from(LinkStats stats) {
        // Dates are rendered as ISO strings rather than left as LocalDate keys, because a
        // JSON object key must be a string and letting the serialiser choose the format
        // would make the wire contract depend on serialiser configuration.
        Map<String, Long> byDay = new LinkedHashMap<>();
        stats.clicksByDay().forEach((LocalDate day, Long count) -> byDay.put(day.toString(), count));

        return new StatsResponse(
                stats.code().value(),
                stats.totalClicks(),
                stats.firstClickAt(),
                stats.lastClickAt(),
                byDay,
                stats.topReferrers());
    }
}
