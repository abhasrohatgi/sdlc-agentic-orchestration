package com.agenticsdlc.shortener.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agenticsdlc.shortener.adapter.analytics.InMemoryClickStatsRepository;
import com.agenticsdlc.shortener.adapter.codegen.Base62CodeGenerator;
import com.agenticsdlc.shortener.adapter.persistence.InMemoryLinkRepository;
import com.agenticsdlc.shortener.adapter.safety.SsrfAwareUrlSafetyChecker;
import com.agenticsdlc.shortener.domain.AliasAlreadyTakenException;
import com.agenticsdlc.shortener.domain.InvalidTargetException;
import com.agenticsdlc.shortener.domain.LinkExpiredException;
import com.agenticsdlc.shortener.domain.LinkNotFoundException;
import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import com.agenticsdlc.shortener.port.CodeGenerator;
import com.agenticsdlc.shortener.support.MutableClock;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkServiceTest {

    private static final URI TARGET = URI.create("https://example.com/page");

    private InMemoryLinkRepository repository;
    private InMemoryClickStatsRepository clickStats;
    private MutableClock clock;
    private LinkService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLinkRepository();
        clock = MutableClock.at("2026-08-05T10:00:00Z");
        clickStats = new InMemoryClickStatsRepository();
        service = new LinkService(repository, new Base62CodeGenerator(),
                new SsrfAwareUrlSafetyChecker(), clickStats, clock);
    }

    @Nested
    @DisplayName("creating links")
    class Creating {

        @Test
        @DisplayName("generates a code when no alias is requested")
        void generatesCode() {
            ShortLink link = service.create(CreateLinkCommand.of(TARGET));

            assertThat(link.code().value()).hasSize(Base62CodeGenerator.CODE_LENGTH);
            assertThat(link.customAlias()).isFalse();
            assertThat(link.target()).isEqualTo(TARGET);
            assertThat(link.createdAt()).isEqualTo(clock.instant());
            assertThat(link.expiresAt()).isNull();
        }

        @Test
        @DisplayName("honours a requested custom alias")
        void usesCustomAlias() {
            ShortLink link = service.create(
                    new CreateLinkCommand(TARGET, ShortCode.of("q3-report"), null));

            assertThat(link.code()).isEqualTo(ShortCode.of("q3-report"));
            assertThat(link.customAlias()).isTrue();
        }

        @Test
        @DisplayName("rejects a custom alias that is already taken instead of substituting one")
        void rejectsTakenAlias() {
            ShortCode alias = ShortCode.of("q3-report");
            service.create(new CreateLinkCommand(TARGET, alias, null));

            assertThatThrownBy(() -> service.create(new CreateLinkCommand(
                    URI.create("https://other.example"), alias, null)))
                    .isInstanceOf(AliasAlreadyTakenException.class);

            // The original must be untouched.
            assertThat(service.resolve(alias).target()).isEqualTo(TARGET);
        }

        @Test
        @DisplayName("computes expiry from the clock plus the requested lifetime")
        void computesExpiry() {
            ShortLink link = service.create(
                    new CreateLinkCommand(TARGET, null, Duration.ofHours(2)));

            assertThat(link.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(2)));
        }

        @Test
        @DisplayName("retries with a fresh code when a generated code loses a race")
        void retriesOnGeneratedCodeCollision() {
            // A generator that hands out a code already claimed by an alias, then a free one.
            // Without the retry, creation would fail for a reason the caller cannot act on.
            Deque<String> codes = new ArrayDeque<>(java.util.List.of("taken1", "free001"));
            CodeGenerator colliding = () -> ShortCode.of(codes.poll());

            LinkService retrying = new LinkService(repository, colliding,
                    new SsrfAwareUrlSafetyChecker(), clickStats, clock);
            repository.saveIfAbsent(ShortLink.permanent(
                    ShortCode.of("taken1"), TARGET, clock.instant(), true));

            ShortLink created = retrying.create(CreateLinkCommand.of(TARGET));

            assertThat(created.code()).isEqualTo(ShortCode.of("free001"));
        }

        @Test
        @DisplayName("fails loudly when no free code can be obtained, rather than looping")
        void failsAfterExhaustingAttempts() {
            CodeGenerator alwaysSame = () -> ShortCode.of("stuck00");
            LinkService stuck = new LinkService(repository, alwaysSame,
                    new SsrfAwareUrlSafetyChecker(), clickStats, clock);
            repository.saveIfAbsent(ShortLink.permanent(
                    ShortCode.of("stuck00"), TARGET, clock.instant(), true));

            assertThatThrownBy(() -> stuck.create(CreateLinkCommand.of(TARGET)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("attempts");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "javascript:alert(1)",
                // Base64 rather than raw markup: a data: URL containing '<' is not a legal
                // URI at all, so URI.create would reject it before the service ever saw it,
                // and the test would pass for the wrong reason.
                "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
                "file:///etc/passwd",
                "ftp://example.com/file"
        })
        @DisplayName("rejects non-http(s) schemes that would weaponise the redirect")
        void rejectsDangerousSchemes(String raw) {
            URI target = URI.create(raw);
            assertThatThrownBy(() -> service.create(CreateLinkCommand.of(target)))
                    .isInstanceOf(InvalidTargetException.class);
        }

        @Test
        @DisplayName("rejects a relative URL")
        void rejectsRelativeUrl() {
            assertThatThrownBy(() -> service.create(
                    CreateLinkCommand.of(URI.create("/just/a/path"))))
                    .isInstanceOf(InvalidTargetException.class)
                    .hasMessageContaining("absolute");
        }

        @Test
        @DisplayName("rejects a non-positive time to live at the command boundary")
        void rejectsNonPositiveTtl() {
            assertThatThrownBy(() -> new CreateLinkCommand(TARGET, null, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CreateLinkCommand(TARGET, null, Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("resolving links")
    class Resolving {

        @Test
        @DisplayName("returns the target for a live link")
        void resolvesLive() {
            ShortLink created = service.create(CreateLinkCommand.of(TARGET));

            assertThat(service.resolve(created.code()).target()).isEqualTo(TARGET);
        }

        @Test
        @DisplayName("reports an unknown code as not found")
        void unknownCode() {
            assertThatThrownBy(() -> service.resolve(ShortCode.of("nothere")))
                    .isInstanceOf(LinkNotFoundException.class);
        }

        @Test
        @DisplayName("reports an expired link as expired, not as missing")
        void expiredIsDistinctFromMissing() {
            ShortLink created = service.create(
                    new CreateLinkCommand(TARGET, null, Duration.ofMinutes(30)));
            clock.advance(Duration.ofMinutes(31));

            assertThatThrownBy(() -> service.resolve(created.code()))
                    .isInstanceOf(LinkExpiredException.class);
        }

        @Test
        @DisplayName("reading an expired link does not delete it")
        void resolvingDoesNotDelete() {
            // Making a read destructive would race with any concurrent read of the same code
            // and would make the metadata endpoint's behaviour depend on who looked first.
            ShortLink created = service.create(
                    new CreateLinkCommand(TARGET, null, Duration.ofMinutes(30)));
            clock.advance(Duration.ofMinutes(31));

            assertThatThrownBy(() -> service.resolve(created.code()))
                    .isInstanceOf(LinkExpiredException.class);

            assertThat(repository.existsByCode(created.code())).isTrue();
        }
    }

    @Nested
    @DisplayName("metadata and deletion")
    class MetadataAndDeletion {

        @Test
        @DisplayName("metadata is available for an expired link")
        void metadataForExpired() {
            ShortLink created = service.create(
                    new CreateLinkCommand(TARGET, null, Duration.ofMinutes(30)));
            clock.advance(Duration.ofMinutes(31));

            // An operator investigating a dead link needs to see it.
            assertThat(service.get(created.code()).code()).isEqualTo(created.code());
        }

        @Test
        @DisplayName("deleting removes the link")
        void deletes() {
            ShortLink created = service.create(CreateLinkCommand.of(TARGET));

            service.delete(created.code());

            assertThatThrownBy(() -> service.resolve(created.code()))
                    .isInstanceOf(LinkNotFoundException.class);
        }

        @Test
        @DisplayName("deleting an unknown code reports not found")
        void deleteUnknown() {
            assertThatThrownBy(() -> service.delete(ShortCode.of("nothere")))
                    .isInstanceOf(LinkNotFoundException.class);
        }
    }
}
