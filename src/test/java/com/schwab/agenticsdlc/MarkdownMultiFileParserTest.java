package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.agent.MarkdownMultiFileParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarkdownMultiFileParserTest {

    @Test
    public void testParseMultiFileMarkdownWithHeaders() {
        String markdown = """
                Here is the diagnostic fix for the build failure:

                ### File: pom.xml
                ```xml
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.urlshortner</groupId>
                    <artifactId>url-shortener-service</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                </project>
                ```

                ### File: src/main/java/com/urlshortner/Application.java
                ```java
                package com.urlshortner;

                public class Application {
                    public static void main(String[] args) {}
                }
                ```
                """;

        Map<String, String> result = MarkdownMultiFileParser.parseMultiFileMarkdown(markdown);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("pom.xml"));
        assertTrue(result.containsKey("src/main/java/com/urlshortner/Application.java"));

        assertTrue(result.get("pom.xml").contains("<artifactId>url-shortener-service</artifactId>"));
        assertTrue(result.get("src/main/java/com/urlshortner/Application.java").contains("package com.urlshortner;"));
    }

    @Test
    public void testParseMultiFileMarkdownWithVariousHeaderFormats() {
        String markdown = """
                Diagnostic fixes for compiler errors:

                ### 1. src/main/java/com/urlshortner/dto/CreateUrlRequest.java
                ```java
                package com.urlshortner.dto;
                public class CreateUrlRequest {}
                ```

                **src/main/java/com/urlshortner/exception/GlobalExceptionHandler.java**
                ```java
                package com.urlshortner.exception;
                import org.slf4j.Logger;
                public class GlobalExceptionHandler {}
                ```

                ### src/main/java/com/urlshortner/service/impl/UrlServiceImpl.java
                ```java
                package com.urlshortner.service.impl;
                public class UrlServiceImpl {}
                ```

                ```java // src/main/java/com/urlshortner/service/UrlService.java
                package com.urlshortner.service;
                public interface UrlService {}
                ```
                """;

        Map<String, String> result = MarkdownMultiFileParser.parseMultiFileMarkdown(markdown);
        assertNotNull(result);
        assertEquals(4, result.size());
        assertTrue(result.containsKey("src/main/java/com/urlshortner/dto/CreateUrlRequest.java"));
        assertTrue(result.containsKey("src/main/java/com/urlshortner/exception/GlobalExceptionHandler.java"));
        assertTrue(result.containsKey("src/main/java/com/urlshortner/service/impl/UrlServiceImpl.java"));
        assertTrue(result.containsKey("src/main/java/com/urlshortner/service/UrlService.java"));
    }

    @Test
    public void testParseMultiFileMarkdownJsonFallback() {
        String json = """
                ```json
                {
                  "pom.xml": "<project></project>"
                }
                ```
                """;

        Map<String, String> result = MarkdownMultiFileParser.parseMultiFileMarkdown(json);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("<project></project>", result.get("pom.xml"));
    }

    @Test
    public void testExtractDiagnosticReasoning() {
        String markdown = """
                ### Diagnostic Reasoning
                - Root Cause: UrlServiceImpl.java missing implements UrlService statement.
                - Fix Strategy: Added implements UrlService declaration.

                ### File: src/main/java/com/urlshortner/service/impl/UrlServiceImpl.java
                ```java
                package com.urlshortner.service.impl;
                public class UrlServiceImpl implements UrlService {}
                ```
                """;

        String reasoning = MarkdownMultiFileParser.extractDiagnosticReasoning(markdown);
        assertTrue(reasoning.contains("Root Cause: UrlServiceImpl.java missing implements UrlService statement."));
        assertTrue(reasoning.contains("Fix Strategy: Added implements UrlService declaration."));
    }
}
