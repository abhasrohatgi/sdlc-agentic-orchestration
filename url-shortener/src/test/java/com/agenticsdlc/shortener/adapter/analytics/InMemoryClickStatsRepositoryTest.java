package com.agenticsdlc.shortener.adapter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.shortener.domain.ClickEvent;
import com.agenticsdlc.shortener.domain.LinkStats;
import com.agenticsdlc.shortener.domain.ShortCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryClickStatsRepositoryTest {

    private static final ShortCode CODE = ShortCode.of("aB3xK9p");
    private static final Instant NOON = Instant.parse("2026-08-05T12:00:00Z");

    private final InMemoryClickStatsRepository stats = new InMemoryClickStatsRepository();

    private static ClickEvent click(Instant at, String referrer) {
        return new ClickEvent(CODE, at, referrer, "test-agent");
    }

    @Test
    @DisplayName("a link with no clicks reports empty statistics rather than null")
    void emptyForUnknownLink() {
        LinkStats result = stats.statsFor(CODE);

        assertThat(result.totalClicks()).isZero();
        assertThat(result.firstClick()).isEmpty();
        assertThat(result.lastClick()).isEmpty();
        assertThat(result.clicksByDay()).isEmpty();
    }

    @Test
    @DisplayName("counts clicks and tracks the first and last")
    void countsAndBounds() {
        stats.apply(click(NOON, null));
        stats.apply(click(NOON.plus(Duration.ofHours(2)), null));
        stats.apply(click(NOON.minus(Duration.ofHours(3)), null));

        LinkStats result = stats.statsFor(CODE);

        assertThat(result.totalClicks()).isEqualTo(3);
        assertThat(result.firstClick()).contains(NOON.minus(Duration.ofHours(3)));
        assertThat(result.lastClick()).contains(NOON.plus(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("buckets clicks by UTC date, in chronological order")
    void bucketsByDay() {
        stats.apply(click(NOON, null));
        stats.apply(click(NOON, null));
        stats.apply(click(NOON.plus(Duration.ofDays(1)), null));

        LinkStats result = stats.statsFor(CODE);

        assertThat(result.clicksByDay())
                .containsExactly(
                        java.util.Map.entry(LocalDate.of(2026, 8, 5), 2L),
                        java.util.Map.entry(LocalDate.of(2026, 8, 6), 1L));
    }

    @Test
    @DisplayName("ranks referrers by count, highest first")
    void ranksReferrers() {
        stats.apply(click(NOON, "https://news.example"));
        stats.apply(click(NOON, "https://news.example"));
        stats.apply(click(NOON, "https://blog.example"));

        assertThat(stats.statsFor(CODE).topReferrers())
                .containsExactly(
                        java.util.Map.entry("https://news.example", 2L),
                        java.util.Map.entry("https://blog.example", 1L));
    }

    @Test
    @DisplayName("clicks with no referrer are attributed to direct traffic, not discarded")
    void tracksDirectTraffic() {
        stats.apply(click(NOON, null));
        stats.apply(click(NOON, "  "));

        assertThat(stats.statsFor(CODE).topReferrers())
                .containsEntry(InMemoryClickStatsRepository.DIRECT, 2L);
    }

    @Test
    @DisplayName("the day series is pruned so a long-lived link cannot grow without bound")
    void prunesOldDays() {
        for (int day = 0; day < 100; day++) {
            stats.apply(click(NOON.plus(Duration.ofDays(day)), null));
        }

        assertThat(stats.statsFor(CODE).clicksByDay())
                .hasSizeLessThanOrEqualTo(InMemoryClickStatsRepository.RETAINED_DAYS);
        // Total is unaffected by pruning: the clicks happened, only the daily detail ages out.
        assertThat(stats.statsFor(CODE).totalClicks()).isEqualTo(100);
    }

    @Test
    @DisplayName("distinct referrers are capped, but capped clicks still count toward the total")
    void capsReferrerCardinality() {
        // The Referer header is attacker-controlled. Without a cap, one click per forged
        // value grows this map indefinitely - memory exhaustion reachable by anyone who can
        // click a link.
        int forged = InMemoryClickStatsRepository.MAX_TRACKED_REFERRERS + 500;
        for (int i = 0; i < forged; i++) {
            stats.apply(click(NOON, "https://forged-" + i + ".example"));
        }

        LinkStats result = stats.statsFor(CODE);

        assertThat(result.topReferrers())
                .hasSizeLessThanOrEqualTo(InMemoryClickStatsRepository.MAX_TRACKED_REFERRERS);
        assertThat(result.totalClicks())
                .as("no traffic should go missing; only its attribution is capped")
                .isEqualTo(forged);
    }

    @Test
    @DisplayName("forgetting a link discards its statistics")
    void forgets() {
        stats.apply(click(NOON, null));
        stats.forget(CODE);

        assertThat(stats.statsFor(CODE).totalClicks()).isZero();
    }

    @Test
    @DisplayName("statistics are kept per link")
    void isolatesLinks() {
        ShortCode other = ShortCode.of("other12");
        stats.apply(click(NOON, null));
        stats.apply(new ClickEvent(other, NOON, null, null));
        stats.apply(new ClickEvent(other, NOON, null, null));

        assertThat(stats.statsFor(CODE).totalClicks()).isEqualTo(1);
        assertThat(stats.statsFor(other).totalClicks()).isEqualTo(2);
    }

    @Test
    @DisplayName("returned maps are unmodifiable, so a caller cannot corrupt the aggregate")
    void snapshotsAreUnmodifiable() {
        stats.apply(click(NOON, "https://news.example"));
        LinkStats result = stats.statsFor(CODE);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> result.clicksByDay().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> result.topReferrers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
