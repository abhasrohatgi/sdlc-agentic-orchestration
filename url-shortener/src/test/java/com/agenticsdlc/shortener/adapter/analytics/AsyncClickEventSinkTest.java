package com.agenticsdlc.shortener.adapter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.shortener.domain.ClickEvent;
import com.agenticsdlc.shortener.domain.LinkStats;
import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.port.ClickStatsRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AsyncClickEventSinkTest {

    private static final ShortCode CODE = ShortCode.of("aB3xK9p");
    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");

    private static ClickEvent click() {
        return new ClickEvent(CODE, NOW, null, null);
    }

    @Test
    @DisplayName("events reach the aggregate")
    void deliversEvents() throws Exception {
        InMemoryClickStatsRepository stats = new InMemoryClickStatsRepository();
        try (AsyncClickEventSink sink = new AsyncClickEventSink(stats, 100)) {
            for (int i = 0; i < 10; i++) {
                sink.record(click());
            }
            assertThat(sink.awaitQuiescence(Duration.ofSeconds(2))).isTrue();

            assertThat(stats.statsFor(CODE).totalClicks()).isEqualTo(10);
            assertThat(sink.droppedCount()).isZero();
        }
    }

    @Test
    @DisplayName("recording does not block the caller when the consumer is stalled")
    void neverBlocksTheCaller() throws Exception {
        // This is the property the redirect hot path depends on. A sink that blocks has put
        // analytics latency in front of every user click.
        CountDownLatch release = new CountDownLatch(1);
        ClickStatsRepository stalled = new ClickStatsRepository() {
            @Override
            public void apply(ClickEvent event) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public LinkStats statsFor(ShortCode code) {
                return LinkStats.empty(code);
            }

            @Override
            public void forget(ShortCode code) {
            }
        };

        try (AsyncClickEventSink sink = new AsyncClickEventSink(stalled, 4)) {
            long startNanos = System.nanoTime();
            // Far more events than the queue can hold, against a consumer that is wedged.
            for (int i = 0; i < 1_000; i++) {
                sink.record(click());
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            assertThat(elapsed)
                    .as("1000 records against a stalled consumer must not block")
                    .isLessThan(Duration.ofSeconds(1));
            assertThat(sink.droppedCount()).isPositive();
        } finally {
            release.countDown();
        }
    }

    @Test
    @DisplayName("overflow is dropped and counted rather than silently lost")
    void countsDrops() throws Exception {
        // Silent loss would make an analytics outage indistinguishable from a traffic
        // decline, which is how a broken pipeline survives unnoticed for a quarter.
        CountDownLatch release = new CountDownLatch(1);
        ClickStatsRepository stalled = blockingRepository(release);

        try (AsyncClickEventSink sink = new AsyncClickEventSink(stalled, 2)) {
            for (int i = 0; i < 50; i++) {
                sink.record(click());
            }
            assertThat(sink.droppedCount()).isGreaterThan(40);
        } finally {
            release.countDown();
        }
    }

    @Test
    @DisplayName("one failing event does not kill the pipeline")
    void survivesAFailingEvent() throws Exception {
        // A dead consumer thread stops all analytics silently, which is worse than losing
        // the one event that caused it.
        AtomicInteger applied = new AtomicInteger();
        ClickStatsRepository flaky = new ClickStatsRepository() {
            @Override
            public void apply(ClickEvent event) {
                if (applied.incrementAndGet() == 3) {
                    throw new IllegalStateException("simulated aggregation failure");
                }
            }

            @Override
            public LinkStats statsFor(ShortCode code) {
                return LinkStats.empty(code);
            }

            @Override
            public void forget(ShortCode code) {
            }
        };

        try (AsyncClickEventSink sink = new AsyncClickEventSink(flaky, 100)) {
            for (int i = 0; i < 10; i++) {
                sink.record(click());
            }
            assertThat(sink.awaitQuiescence(Duration.ofSeconds(2))).isTrue();

            assertThat(applied.get())
                    .as("consumer should keep processing after one event throws")
                    .isEqualTo(10);
        }
    }

    @Test
    @DisplayName("a null event is ignored rather than throwing into the redirect path")
    void ignoresNull() {
        InMemoryClickStatsRepository stats = new InMemoryClickStatsRepository();
        try (AsyncClickEventSink sink = new AsyncClickEventSink(stats, 10)) {
            sink.record(null);
            assertThat(sink.droppedCount()).isZero();
        }
    }

    @Test
    @DisplayName("close drains what is already queued rather than discarding it")
    void closeDrains() throws Exception {
        InMemoryClickStatsRepository stats = new InMemoryClickStatsRepository();
        AsyncClickEventSink sink = new AsyncClickEventSink(stats, 1_000);
        for (int i = 0; i < 500; i++) {
            sink.record(click());
        }

        sink.close();

        assertThat(stats.statsFor(CODE).totalClicks())
                .as("queued events should be aggregated during shutdown")
                .isEqualTo(500);
    }

    @Test
    @DisplayName("rejects a non-positive queue capacity")
    void rejectsBadCapacity() {
        InMemoryClickStatsRepository stats = new InMemoryClickStatsRepository();
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new AsyncClickEventSink(stats, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ClickStatsRepository blockingRepository(CountDownLatch release) {
        return new ClickStatsRepository() {
            @Override
            public void apply(ClickEvent event) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public LinkStats statsFor(ShortCode code) {
                return LinkStats.empty(code);
            }

            @Override
            public void forget(ShortCode code) {
            }
        };
    }
}
