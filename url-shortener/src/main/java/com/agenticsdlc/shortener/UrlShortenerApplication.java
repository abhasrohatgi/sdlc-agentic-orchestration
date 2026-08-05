package com.agenticsdlc.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the URL shortener service.
 *
 * <p>This service is a deliverable in its own right, but it is also the <em>target
 * codebase</em> that the agentic SDLC orchestrator plans against, modifies, tests and gates.
 * Its hexagonal structure exists so that brownfield reasoning has real seams to find.
 */
@SpringBootApplication
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
