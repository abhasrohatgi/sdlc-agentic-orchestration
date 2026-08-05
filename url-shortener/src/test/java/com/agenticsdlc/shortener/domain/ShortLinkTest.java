package com.agenticsdlc.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShortLinkTest {

    private static final ShortCode CODE = ShortCode.of("aB3xK9p");
    private static final URI TARGET = URI.create("https://example.com/page");
    private static final Instant CREATED = Instant.parse("2026-08-05T10:00:00Z");

    @Test
    @DisplayName("a permanent link never expires")
    void permanentNeverExpires() {
        ShortLink link = ShortLink.permanent(CODE, TARGET, CREATED, false);

        assertThat(link.expiry()).isEmpty();
        assertThat(link.isExpired(CREATED.plus(Duration.ofDays(3650)))).isFalse();
    }

    @Test
    @DisplayName("a link is live before its expiry instant")
    void liveBeforeExpiry() {
        ShortLink link = new ShortLink(CODE, TARGET, CREATED,
                CREATED.plus(Duration.ofHours(1)), false);

        assertThat(link.isExpired(CREATED.plus(Duration.ofMinutes(59)))).isFalse();
    }

    @Test
    @DisplayName("expiry is inclusive: a link is expired exactly at its expiry instant")
    void expiredAtTheBoundary() {
        // Deliberate choice. Treating the boundary as still-live would serve a link with a
        // stated one-hour lifetime for one hour plus an epsilon.
        Instant expiresAt = CREATED.plus(Duration.ofHours(1));
        ShortLink link = new ShortLink(CODE, TARGET, CREATED, expiresAt, false);

        assertThat(link.isExpired(expiresAt)).isTrue();
    }

    @Test
    @DisplayName("a link is expired after its expiry instant")
    void expiredAfter() {
        ShortLink link = new ShortLink(CODE, TARGET, CREATED,
                CREATED.plus(Duration.ofHours(1)), false);

        assertThat(link.isExpired(CREATED.plus(Duration.ofHours(2)))).isTrue();
    }

    @Test
    @DisplayName("rejects an expiry at or before creation, which could never be served")
    void rejectsNonFutureExpiry() {
        assertThatThrownBy(() -> new ShortLink(CODE, TARGET, CREATED, CREATED, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be after");

        assertThatThrownBy(() -> new ShortLink(CODE, TARGET, CREATED,
                CREATED.minusSeconds(1), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects null required fields")
    void rejectsNulls() {
        assertThatThrownBy(() -> new ShortLink(null, TARGET, CREATED, null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ShortLink(CODE, null, CREATED, null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ShortLink(CODE, TARGET, null, null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("records whether the code was caller-chosen")
    void tracksCustomAlias() {
        assertThat(new ShortLink(CODE, TARGET, CREATED, null, true).customAlias()).isTrue();
        assertThat(ShortLink.permanent(CODE, TARGET, CREATED, false).customAlias()).isFalse();
    }
}
