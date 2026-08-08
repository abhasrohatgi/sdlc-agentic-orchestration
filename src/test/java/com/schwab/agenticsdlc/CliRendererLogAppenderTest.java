package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.cli.CliRendererLogAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CliRendererLogAppenderTest {

    @Test
    public void testCliRendererLogAppenderAppendsEvent() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = context.getLogger("com.schwab.agenticsdlc.agent.TestAgent");

        CliRendererLogAppender appender = new CliRendererLogAppender();
        appender.setContext(context);
        appender.start();

        assertTrue(appender.isStarted());

        LoggingEvent event = new LoggingEvent(
                "ch.qos.logback.classic.Logger",
                logger,
                Level.INFO,
                "Test log message formatted via CliRenderer",
                null,
                null
        );

        appender.doAppend(event);
        appender.stop();
    }
}
