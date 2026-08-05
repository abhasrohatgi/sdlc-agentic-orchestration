package com.agenticsdlc.shortener.application;

import com.agenticsdlc.shortener.domain.ShortCode;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Request to create a short link.
 *
 * <p>A command object rather than a long parameter list, so that adding an option later
 * does not change every call site, and so that {@code null} for "no alias" cannot be
 * confused with {@code null} for "no expiry" at a call site.
 *
 * @param target      the URL to redirect to
 * @param customAlias caller-chosen code, or {@code null} to have one generated
 * @param timeToLive  how long the link should live, or {@code null} for no expiry
 */
public record CreateLinkCommand(URI target, ShortCode customAlias, Duration timeToLive) {

    public CreateLinkCommand {
        Objects.requireNonNull(target, "target must not be null");
        if (timeToLive != null && (timeToLive.isZero() || timeToLive.isNegative())) {
            throw new IllegalArgumentException(
                    "timeToLive must be positive, got " + timeToLive
                            + ". Use null for a link that never expires.");
        }
    }

    /** A link with a generated code and no expiry. */
    public static CreateLinkCommand of(URI target) {
        return new CreateLinkCommand(target, null, null);
    }

    public Optional<ShortCode> alias() {
        return Optional.ofNullable(customAlias);
    }

    public Optional<Duration> ttl() {
        return Optional.ofNullable(timeToLive);
    }
}
