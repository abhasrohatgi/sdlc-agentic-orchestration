package com.agenticsdlc.shortener.domain;

import java.time.Instant;

/**
 * Thrown when a link exists but has passed its expiry. Surfaces as HTTP 410 Gone.
 *
 * <p>Distinguished from {@link LinkNotFoundException} deliberately: "this code was real and
 * has expired" is materially different information from "this code never existed", both for
 * a human debugging a dead link and for a client deciding whether to retry.
 */
public class LinkExpiredException extends RuntimeException {

    private final ShortCode code;
    private final Instant expiredAt;

    public LinkExpiredException(ShortCode code, Instant expiredAt) {
        super("Link '" + code + "' expired at " + expiredAt);
        this.code = code;
        this.expiredAt = expiredAt;
    }

    public ShortCode code() {
        return code;
    }

    public Instant expiredAt() {
        return expiredAt;
    }
}
