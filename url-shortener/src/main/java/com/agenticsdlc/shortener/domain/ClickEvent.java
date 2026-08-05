package com.agenticsdlc.shortener.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A single click on a short link.
 *
 * <p>Deliberately records very little. Referrer and user agent are what an operator actually
 * needs to answer "where is this traffic coming from"; anything more identifying would make
 * this a tracking dataset with retention and consent obligations attached, which is a large
 * commitment to take on by accident.
 *
 * <p>Notably absent: the client IP address. It is the obvious thing to add and it is
 * personal data under GDPR even when hashed with a fixed salt, since the address space is
 * small enough to brute-force the hash. Geolocation, if wanted, belongs behind a coarse
 * country lookup performed at ingest and discarded, not by storing the address.
 *
 * @param code        the link that was clicked
 * @param occurredAt  when
 * @param referrer    the {@code Referer} header, or {@code null} if absent
 * @param userAgent   the {@code User-Agent} header, or {@code null} if absent
 */
public record ClickEvent(ShortCode code, Instant occurredAt, String referrer, String userAgent) {

    /** Caps stored header values so a hostile client cannot inflate memory one click at a time. */
    public static final int MAX_HEADER_LENGTH = 256;

    public ClickEvent {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        referrer = truncate(referrer);
        userAgent = truncate(userAgent);
    }

    public Optional<String> referrerValue() {
        return Optional.ofNullable(referrer);
    }

    public Optional<String> userAgentValue() {
        return Optional.ofNullable(userAgent);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= MAX_HEADER_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_HEADER_LENGTH);
    }
}
