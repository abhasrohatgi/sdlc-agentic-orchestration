package com.agenticsdlc.shortener.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A short link: the mapping from a {@link ShortCode} to a target URL, plus the metadata that
 * governs whether it should still be served.
 *
 * <p>Immutable. Expiry is evaluated against a caller-supplied instant rather than read from
 * a clock inside the domain, so that expiry behaviour is testable without sleeping and
 * without a static clock.
 *
 * @param code        the public identifier
 * @param target      where the code redirects to
 * @param createdAt   when the link was created
 * @param expiresAt   when the link stops being served, or {@code null} if it never expires
 * @param customAlias whether the code was chosen by the caller rather than generated
 */
public record ShortLink(
        ShortCode code,
        URI target,
        Instant createdAt,
        Instant expiresAt,
        boolean customAlias) {

    public ShortLink {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            // A link that expires at or before the moment it was created can never be
            // served, so accepting one would only produce a confusing 410 later.
            throw new IllegalArgumentException(
                    "expiresAt (" + expiresAt + ") must be after createdAt (" + createdAt + ")");
        }
    }

    /** The expiry instant, if this link has one. */
    public Optional<Instant> expiry() {
        return Optional.ofNullable(expiresAt);
    }

    /**
     * Whether this link has expired as of {@code now}.
     *
     * <p>Expiry is inclusive of the boundary: a link with {@code expiresAt == now} is
     * expired. Choosing the inclusive side means a stated lifetime of one hour is never
     * served for one hour plus an epsilon.
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** Creates a link that never expires. */
    public static ShortLink permanent(ShortCode code, URI target, Instant createdAt,
                                      boolean customAlias) {
        return new ShortLink(code, target, createdAt, null, customAlias);
    }
}
