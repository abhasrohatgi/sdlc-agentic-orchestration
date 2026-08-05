package com.agenticsdlc.shortener.port;

import com.agenticsdlc.shortener.domain.ClickEvent;

/**
 * Write side of click analytics.
 *
 * <p>The contract is deliberately weak, and the weakness is the point:
 *
 * <ul>
 *   <li><strong>It must never block.</strong> This is called from the redirect hot path.
 *       An implementation that waits on a queue, a lock or a database has put analytics
 *       latency in front of every user click.</li>
 *   <li><strong>It must never throw.</strong> Analytics failing is not a reason for a
 *       redirect to fail. A user clicking a link does not care that a counter could not be
 *       incremented.</li>
 *   <li><strong>Delivery is best-effort.</strong> Under sustained overload an implementation
 *       is expected to drop events rather than apply backpressure - but it must
 *       <em>count</em> what it dropped, so that "traffic fell" can be distinguished from
 *       "we stopped recording".</li>
 * </ul>
 *
 * <p>Analytics are an observation of the system, not part of it. Anything that inverts that
 * relationship is a bug.
 */
public interface ClickEventSink {

    /** Records a click. Non-blocking, never throws. */
    void record(ClickEvent event);

    /**
     * Number of events dropped because the sink could not keep up.
     *
     * <p>Silent dropping would make an analytics outage indistinguishable from a traffic
     * drop, so this is surfaced as a metric.
     */
    long droppedCount();
}
