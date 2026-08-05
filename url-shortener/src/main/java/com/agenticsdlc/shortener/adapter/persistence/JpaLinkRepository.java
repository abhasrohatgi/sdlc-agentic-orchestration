package com.agenticsdlc.shortener.adapter.persistence;

import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import com.agenticsdlc.shortener.port.LinkRepository;
import jakarta.persistence.EntityExistsException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed {@link LinkRepository}.
 *
 * <p>Passes the same contract test as {@link InMemoryLinkRepository}; that equivalence is
 * the point of having the port at all.
 */
public class JpaLinkRepository implements LinkRepository {

    private final SpringDataLinkRepository delegate;

    public JpaLinkRepository(SpringDataLinkRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implemented as insert-and-catch rather than check-then-insert. A prior
     * {@code existsById} call would leave a window in which two concurrent requests both
     * observe a free code and both proceed, and the second would overwrite the first with no
     * error - silently repointing a link somebody is already using.
     *
     * <p>Letting the primary key constraint arbitrate pushes the decision to the one place
     * that can make it atomically. The exception is the expected outcome of a lost race, not
     * a fault, so it is translated to {@code false} rather than propagated.
     *
     * <p>Two details make this work, and both were found by the contract test rather than
     * by reasoning:
     *
     * <ul>
     *   <li>{@link LinkEntity} implements {@code Persistable} so that {@code save} routes to
     *       {@code persist} rather than {@code merge}. With a caller-assigned primary key,
     *       Spring Data's default treats every instance as existing and merges, which
     *       overwrites instead of failing.</li>
     *   <li>There is deliberately <strong>no</strong> {@code @Transactional} here. If this
     *       method opened a transaction, the failed insert would mark it rollback-only, and
     *       returning {@code false} would then blow up at commit with
     *       {@code UnexpectedRollbackException}. Letting the repository's own transaction
     *       own the write means the failure rolls back in isolation and the caller gets a
     *       clean {@code false}.</li>
     * </ul>
     */
    @Override
    public boolean saveIfAbsent(ShortLink link) {
        Objects.requireNonNull(link, "link must not be null");
        try {
            delegate.saveAndFlush(LinkEntity.newFrom(link));
            return true;
        } catch (DataIntegrityViolationException | EntityExistsException e) {
            // EntityExistsException can escape untranslated when persist() detects the
            // conflict before flush, so both are treated as "the code was taken".
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShortLink> findByCode(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return delegate.findById(code.value()).map(LinkEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean deleteByCode(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        if (!delegate.existsById(code.value())) {
            return false;
        }
        delegate.deleteById(code.value());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return delegate.existsById(code.value());
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }
}
