package com.agenticsdlc.orchestrator.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the agentic SDLC orchestrator.
 *
 * <p>This module is the delivery layer only - REST control surface, run-event persistence,
 * the Mermaid dashboard and the {@code sdlc} CLI. The engine itself lives in
 * {@code orchestrator-core}, which is framework-free by build-enforced invariant so that
 * the whole reducer can be tested with a fake clock and no application context.
 */
@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
