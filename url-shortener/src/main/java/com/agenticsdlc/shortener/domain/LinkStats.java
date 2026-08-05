package com.agenticsdlc.shortener.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregated click statistics for one link.
 *
 * @param code           the link
 * @param totalClicks    clicks recorded since the link was created
 * @param firstClickAt   first recorded click, or {@code null} if never clicked
 * @param lastClickAt    most recent click, or {@code null} if never clicked
 * @param clicksByDay    click counts keyed by UTC date, most recent window only
 * @param topReferrers   referrer counts, highest first
 */
public record LinkStats(
        ShortCode code,
        long totalClicks,
        Instant firstClickAt,
        Instant lastClickAt,
        Map<LocalDate, Long> clicksByDay,
        Map<String, Long> topReferrers) {

    /** Statistics for a link that exists but has never been clicked. */
    public static LinkStats empty(ShortCode code) {
        return new LinkStats(code, 0L, null, null, Map.of(), Map.of());
    }

    public Optional<Instant> firstClick() {
        return Optional.ofNullable(firstClickAt);
    }

    public Optional<Instant> lastClick() {
        return Optional.ofNullable(lastClickAt);
    }
}
