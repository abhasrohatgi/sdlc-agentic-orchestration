package com.schwab.agenticsdlc.cli;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Custom Logback Appender that routes all SLF4J application logs directly through {@link CliRenderer}.
 *
 * <p>Guarantees that 100% of log statements across all agents (OrchestratorEngine, CodeEngineerAgent,
 * QAValidatorAgent, LlmClientManager, WorkspaceManager, etc.) receive unified ANSI color badges and formatting.</p>
 */
public class CliRendererLogAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null) {
            return;
        }

        String level = event.getLevel() != null ? event.getLevel().toString() : "INFO";
        String loggerName = event.getLoggerName();

        // Extract simple agent name / class name from logger string (e.g. com.schwab.agenticsdlc.agent.CodeEngineerAgent -> CodeEngineerAgent)
        String agentTag = loggerName;
        if (loggerName != null && loggerName.contains(".")) {
            agentTag = loggerName.substring(loggerName.lastIndexOf('.') + 1);
        }

        String message = event.getFormattedMessage();

        // Delegate to CliRenderer for unified ANSI terminal formatting
        CliRenderer.logEvent(level, agentTag, message);
    }
}
