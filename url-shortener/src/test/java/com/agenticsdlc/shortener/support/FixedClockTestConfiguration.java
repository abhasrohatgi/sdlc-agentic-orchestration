package com.agenticsdlc.shortener.support;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application clock with one the test controls.
 *
 * <p>Marked {@link Primary} rather than overriding the bean definition, so it works without
 * enabling bean-definition overriding.
 */
@TestConfiguration
public class FixedClockTestConfiguration {

    public static final String START = "2026-08-05T10:00:00Z";

    @Bean
    @Primary
    public Clock testClock() {
        return MutableClock.at(START);
    }
}
