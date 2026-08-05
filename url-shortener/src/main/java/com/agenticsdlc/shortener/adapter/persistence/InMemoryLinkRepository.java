package com.agenticsdlc.shortener.adapter.persistence;

import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import com.agenticsdlc.shortener.port.LinkRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process {@link LinkRepository} backed by a concurrent map.
 *
 * <p>Not a test double. This is a real adapter, selectable at runtime with
 * {@code shortener.repository=in-memory}, and it is what makes the service runnable with no
 * database at all. It passes the same contract test as the JPA adapter.
 *
 * <p>Atomicity comes from {@link ConcurrentHashMap#putIfAbsent}, which gives
 * {@code saveIfAbsent} its test-and-set semantics without a lock.
 */
public class InMemoryLinkRepository implements LinkRepository {

    private final Map<ShortCode, ShortLink> links = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(ShortLink link) {
        Objects.requireNonNull(link, "link must not be null");
        return links.putIfAbsent(link.code(), link) == null;
    }

    @Override
    public Optional<ShortLink> findByCode(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return Optional.ofNullable(links.get(code));
    }

    @Override
    public boolean deleteByCode(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return links.remove(code) != null;
    }

    @Override
    public boolean existsByCode(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return links.containsKey(code);
    }

    @Override
    public long count() {
        return links.size();
    }

    /** Removes everything. Intended for tests; not part of the port. */
    public void clear() {
        links.clear();
    }
}
