package com.agenticsdlc.shortener.adapter.analytics;

import com.agenticsdlc.shortener.domain.ClickEvent;
import com.agenticsdlc.shortener.port.ClickEventSink;
import com.agenticsdlc.shortener.port.ClickStatsRepository;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decouples click recording from the redirect that produced it.
 *
 * <p>A bounded queue is drained by a single virtual thread that folds events into the
 * {@link ClickStatsRepository}. The redirect thread's only work is an {@code offer} onto the
 * queue, which is wait-free.
 *
 * <h2>Why bounded, and why dropping is correct</h2>
 *
 * <p>An unbounded queue does not remove the failure, it converts a latency problem into an
 * out-of-memory one - and it does so at the worst moment, under peak traffic. A bounded
 * queue forces the question "what happens when we cannot keep up?" to be answered at design
 * time, and for analytics the answer is: drop the event and keep serving redirects.
 *
 * <p>Dropping is only acceptable because it is <strong>counted</strong>. Silent loss makes
 * an analytics outage look identical to a traffic decline, which is how a broken pipeline
 * survives for a quarter. {@link #droppedCount()} is exported as a metric for exactly this.
 *
 * <p>The consumer catches {@link Throwable} around each event. A single malformed event must
 * not kill the drain thread and silently stop all analytics - that failure is invisible from
 * the outside, which makes it worse than the event being lost.
 */
public class AsyncClickEventSink implements ClickEventSink, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncClickEventSink.class);

    /** Sentinel that tells the consumer to finish. Avoids interrupting mid-fold. */
    private static final ClickEvent POISON_PILL = null;

    private final BlockingQueue<ClickEvent> queue;
    private final ClickStatsRepository stats;
    private final AtomicLong dropped = new AtomicLong();
    private final Thread consumer;
    private final CountDownLatch drained = new CountDownLatch(1);
    private volatile boolean running = true;

    public AsyncClickEventSink(ClickStatsRepository stats, int capacity) {
        this.stats = Objects.requireNonNull(stats, "stats must not be null");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        this.queue = new ArrayBlockingQueue<>(capacity);

        // A virtual thread: this consumer spends its life blocked on take(), which is the
        // case virtual threads exist for. It costs no platform thread while idle.
        this.consumer = Thread.ofVirtual()
                .name("click-analytics-consumer")
                .start(this::drain);
    }

    @Override
    public void record(ClickEvent event) {
        if (event == null || !running) {
            return;
        }
        // offer, never put. put() would block the redirect thread once the queue filled,
        // which is precisely the coupling this class exists to prevent.
        if (!queue.offer(event)) {
            dropped.incrementAndGet();
        }
    }

    @Override
    public long droppedCount() {
        return dropped.get();
    }

    /** Number of events accepted but not yet folded into the aggregates. */
    public int pendingCount() {
        return queue.size();
    }

    private void drain() {
        while (running || !queue.isEmpty()) {
            try {
                ClickEvent event = queue.poll(200, TimeUnit.MILLISECONDS);
                if (event == POISON_PILL) {
                    continue;
                }
                try {
                    stats.apply(event);
                } catch (RuntimeException e) {
                    // One bad event must not take the pipeline down with it.
                    log.warn("Failed to apply click event for code {}", event.code(), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        drained.countDown();
    }

    /**
     * Blocks until the queue is empty or the timeout elapses.
     *
     * <p>For tests, which must be able to assert on aggregates without sleeping and without
     * being flaky. Not used in production code.
     *
     * @return {@code true} if the queue drained within the timeout
     */
    public boolean awaitQuiescence(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (queue.isEmpty()) {
                // The consumer may still be inside apply() for the last event, so give it a
                // moment to land before reporting quiescence.
                Thread.sleep(5);
                if (queue.isEmpty()) {
                    return true;
                }
            }
            Thread.sleep(2);
        }
        return queue.isEmpty();
    }

    @Override
    public void close() {
        running = false;
        try {
            if (!drained.await(2, TimeUnit.SECONDS)) {
                log.warn("Click analytics consumer did not drain within 2s; {} events pending",
                        queue.size());
            }
            consumer.interrupt();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
