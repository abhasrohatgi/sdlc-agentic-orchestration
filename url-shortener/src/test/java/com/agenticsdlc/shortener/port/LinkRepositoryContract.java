package com.agenticsdlc.shortener.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The behaviour every {@link LinkRepository} adapter must exhibit.
 *
 * <p>Written once and run against every adapter by subclassing. This is the whole
 * justification for having a port: two implementations passing <em>the same</em> suite is
 * evidence that the abstraction holds. Two separately written suites would only be evidence
 * that both authors believed their own implementation.
 *
 * <p>Subclasses supply an empty repository from {@link #repository()}.
 */
public abstract class LinkRepositoryContract {

    protected static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    protected static final URI TARGET = URI.create("https://example.com/page");

    /** @return a repository containing no links */
    protected abstract LinkRepository repository();

    protected static ShortLink link(String code) {
        return ShortLink.permanent(ShortCode.of(code), TARGET, NOW, false);
    }

    @Test
    @DisplayName("a saved link can be found by its code")
    void saveThenFind() {
        LinkRepository repo = repository();
        ShortLink saved = link("aB3xK9p");

        assertThat(repo.saveIfAbsent(saved)).isTrue();
        assertThat(repo.findByCode(saved.code())).contains(saved);
    }

    @Test
    @DisplayName("finding an unknown code returns empty rather than null or an exception")
    void findUnknown() {
        assertThat(repository().findByCode(ShortCode.of("nothere"))).isEmpty();
    }

    @Test
    @DisplayName("saveIfAbsent refuses to overwrite an existing code")
    void saveIfAbsentDoesNotOverwrite() {
        LinkRepository repo = repository();
        ShortLink first = link("dupcode");
        ShortLink second = new ShortLink(
                ShortCode.of("dupcode"), URI.create("https://elsewhere.example"), NOW, null, true);

        assertThat(repo.saveIfAbsent(first)).isTrue();
        assertThat(repo.saveIfAbsent(second)).isFalse();

        // The original must survive. Silently repointing a link somebody is already using
        // is the worst possible outcome here.
        assertThat(repo.findByCode(first.code())).contains(first);
    }

    @Test
    @DisplayName("all fields survive a round trip, including a null expiry")
    void roundTripsAllFields() {
        LinkRepository repo = repository();
        ShortLink expiring = new ShortLink(ShortCode.of("expiry1"), TARGET, NOW,
                NOW.plus(Duration.ofHours(2)), true);
        ShortLink permanent = ShortLink.permanent(ShortCode.of("forever"), TARGET, NOW, false);

        repo.saveIfAbsent(expiring);
        repo.saveIfAbsent(permanent);

        assertThat(repo.findByCode(expiring.code())).contains(expiring);
        assertThat(repo.findByCode(permanent.code())).contains(permanent);
        assertThat(repo.findByCode(permanent.code()).orElseThrow().expiresAt()).isNull();
    }

    @Test
    @DisplayName("deleting a stored link reports success and removes it")
    void deleteExisting() {
        LinkRepository repo = repository();
        ShortLink saved = link("todelete");
        repo.saveIfAbsent(saved);

        assertThat(repo.deleteByCode(saved.code())).isTrue();
        assertThat(repo.findByCode(saved.code())).isEmpty();
        assertThat(repo.existsByCode(saved.code())).isFalse();
    }

    @Test
    @DisplayName("deleting an unknown code reports failure rather than throwing")
    void deleteUnknown() {
        assertThat(repository().deleteByCode(ShortCode.of("nothere"))).isFalse();
    }

    @Test
    @DisplayName("a deleted code becomes available again")
    void codeIsReusableAfterDeletion() {
        LinkRepository repo = repository();
        repo.saveIfAbsent(link("reusable"));
        repo.deleteByCode(ShortCode.of("reusable"));

        assertThat(repo.saveIfAbsent(link("reusable"))).isTrue();
    }

    @Test
    @DisplayName("existsByCode reflects what is stored")
    void exists() {
        LinkRepository repo = repository();
        assertThat(repo.existsByCode(ShortCode.of("aB3xK9p"))).isFalse();

        repo.saveIfAbsent(link("aB3xK9p"));
        assertThat(repo.existsByCode(ShortCode.of("aB3xK9p"))).isTrue();
    }

    @Test
    @DisplayName("expired links remain stored; expiry is the caller's judgement, not storage's")
    void expiredLinksAreStillStored() {
        // The repository must not filter by expiry. The service needs to distinguish
        // "expired" (410) from "never existed" (404), which it cannot do if storage has
        // already hidden the row.
        LinkRepository repo = repository();
        ShortLink expired = new ShortLink(ShortCode.of("expired"), TARGET, NOW,
                NOW.plus(Duration.ofSeconds(1)), false);

        repo.saveIfAbsent(expired);

        assertThat(repo.findByCode(expired.code())).contains(expired);
        assertThat(expired.isExpired(NOW.plus(Duration.ofHours(1)))).isTrue();
    }

    @Test
    @DisplayName("count reflects stored links")
    void counts() {
        LinkRepository repo = repository();
        assertThat(repo.count()).isZero();

        repo.saveIfAbsent(link("first00"));
        repo.saveIfAbsent(link("second0"));
        assertThat(repo.count()).isEqualTo(2);

        repo.deleteByCode(ShortCode.of("first00"));
        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("exactly one of many concurrent claims on the same code wins")
    void saveIfAbsentIsAtomicUnderConcurrency() throws Exception {
        // This is the property the port exists to guarantee, and the reason saveIfAbsent is
        // a test-and-set rather than exists()-then-save(). A check-then-act implementation
        // passes every other test in this class and fails this one.
        LinkRepository repo = repository();
        int contenders = 16;
        ShortCode contested = ShortCode.of("hotcode");

        try (ExecutorService pool = Executors.newFixedThreadPool(contenders)) {
            List<Callable<Boolean>> claims = java.util.stream.IntStream.range(0, contenders)
                    .<Callable<Boolean>>mapToObj(i -> () -> repo.saveIfAbsent(new ShortLink(
                            contested, URI.create("https://example.com/" + i), NOW, null, true)))
                    .toList();

            List<Future<Boolean>> results = pool.invokeAll(claims);

            long winners = 0;
            for (Future<Boolean> r : results) {
                if (r.get()) {
                    winners++;
                }
            }
            assertThat(winners).as("exactly one concurrent claim should succeed").isEqualTo(1);
        }

        assertThat(repo.count()).isEqualTo(1);
    }
}
