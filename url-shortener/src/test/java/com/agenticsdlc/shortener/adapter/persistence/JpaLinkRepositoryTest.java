package com.agenticsdlc.shortener.adapter.persistence;

import com.agenticsdlc.shortener.port.LinkRepository;
import com.agenticsdlc.shortener.port.LinkRepositoryContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Runs the {@link LinkRepositoryContract} against the JPA adapter on in-process H2.
 *
 * <p>Docker is not available in this environment, so Testcontainers is not an option.
 * H2 is sufficient here because the contract exercises primary-key semantics and basic CRUD
 * rather than PostgreSQL-specific behaviour.
 *
 * <p>{@code @DataJpaTest} rolls each test back, but the concurrency test in the contract
 * runs work on other threads which therefore cannot see the test's transaction. Auto-commit
 * is enabled and the table is cleared explicitly instead.
 */
@DataJpaTest(showSql = false)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:contract;DB_CLOSE_DELAY=-1"
})
@DisplayName("JpaLinkRepository honours the LinkRepository contract")
class JpaLinkRepositoryTest extends LinkRepositoryContract {

    @Autowired
    private SpringDataLinkRepository delegate;

    private LinkRepository repository;

    @BeforeEach
    void setUp() {
        delegate.deleteAll();
        delegate.flush();
        repository = new JpaLinkRepository(delegate);
    }

    @Override
    protected LinkRepository repository() {
        return repository;
    }
}
