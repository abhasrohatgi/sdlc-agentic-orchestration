package com.agenticsdlc.shortener.config;

import com.agenticsdlc.shortener.adapter.codegen.Base62CodeGenerator;
import com.agenticsdlc.shortener.adapter.persistence.InMemoryLinkRepository;
import com.agenticsdlc.shortener.adapter.persistence.JpaLinkRepository;
import com.agenticsdlc.shortener.adapter.persistence.SpringDataLinkRepository;
import com.agenticsdlc.shortener.application.LinkService;
import com.agenticsdlc.shortener.port.CodeGenerator;
import com.agenticsdlc.shortener.port.LinkRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the hexagon.
 *
 * <p>All Spring knowledge about the application lives here. The domain, the application
 * service and the adapters are plain classes with constructor injection, which is what keeps
 * them testable without an application context and makes the port boundaries real rather
 * than annotated.
 */
@Configuration
public class ShortenerConfiguration {

    /**
     * The system clock.
     *
     * <p>Exposed as a bean so tests can substitute a fixed one. No code in this service
     * calls {@code Instant.now()} directly; expiry behaviour must be assertable without
     * sleeping.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public CodeGenerator codeGenerator() {
        return new Base62CodeGenerator();
    }

    /**
     * JPA-backed storage. The default.
     *
     * <p>Chosen as the default because it exercises the more realistic path - a real
     * transaction, a real primary-key constraint arbitrating concurrent alias claims - and
     * because H2 runs in-process, so it costs a reviewer nothing.
     */
    @Bean
    @ConditionalOnProperty(name = "shortener.repository", havingValue = "jpa", matchIfMissing = true)
    public LinkRepository jpaLinkRepository(SpringDataLinkRepository delegate) {
        return new JpaLinkRepository(delegate);
    }

    /**
     * In-process storage, selected with {@code shortener.repository=in-memory}.
     *
     * <p>A peer adapter, not a test double - it passes the same contract test as the JPA one.
     */
    @Bean
    @ConditionalOnProperty(name = "shortener.repository", havingValue = "in-memory")
    public LinkRepository inMemoryLinkRepository() {
        return new InMemoryLinkRepository();
    }

    @Bean
    public LinkService linkService(LinkRepository repository, CodeGenerator codeGenerator,
                                   Clock clock) {
        return new LinkService(repository, codeGenerator, clock);
    }
}
