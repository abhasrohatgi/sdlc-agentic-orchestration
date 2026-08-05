package com.agenticsdlc.shortener.port;

import com.agenticsdlc.shortener.domain.ClickEvent;
import com.agenticsdlc.shortener.domain.LinkStats;
import com.agenticsdlc.shortener.domain.ShortCode;

/**
 * Read and aggregate side of click analytics.
 *
 * <p>Separate from {@link ClickEventSink} because the two have opposite requirements: the
 * sink is on the redirect hot path and must never block, while this is queried
 * interactively and may do real work. Splitting them keeps the hot-path contract honest and
 * lets the aggregation move to a different store without touching the redirect path.
 */
public interface ClickStatsRepository {

    /** Folds a click into the aggregates. Called from the sink's consumer, never inline. */
    void apply(ClickEvent event);

    /** Statistics for a link, or {@link LinkStats#empty} if it has never been clicked. */
    LinkStats statsFor(ShortCode code);

    /** Discards statistics for a link, so a deleted link does not leave orphaned counters. */
    void forget(ShortCode code);
}
