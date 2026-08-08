package com.schwab.agenticsdlc.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-Precision Multi-File Markdown Parser for Self-Healing Fix Directives.
 *
 * <p>Parses LLM diagnostic responses containing multi-file fixes formatted in any Markdown variant:
 * <ul>
 *   <li>{@code ### File: src/main/java/com/example/Service.java}</li>
 *   <li>{@code ### src/main/java/com/example/Service.java}</li>
 *   <li>{@code **src/main/java/com/example/Service.java**}</li>
 *   <li>{@code ### 1. src/main/java/com/example/Service.java}</li>
 *   <li>{@code ```java // src/main/java/com/example/Service.java}</li>
 * </ul>
 */
public class MarkdownMultiFileParser {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownMultiFileParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Regex matching file path markers in Markdown headers/lists:
     * e.g. "### File: path/file.java", "### 1. path/file.java", "**path/file.java**", "File 1: path/file.java"
     */
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "([a-zA-Z0-9_./\\\\-]+?\\.(?:java|xml|yaml|yml|properties|json|md|txt))", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts the top-level "### Diagnostic Reasoning" section from the LLM self-healing response.
     *
     * @param markdownText Raw LLM response string
     * @return Extracted diagnostic reasoning string, or empty string if not present
     */
    public static String extractDiagnosticReasoning(String markdownText) {
        if (markdownText == null || markdownText.isBlank()) {
            return "";
        }
        int reasoningIdx = markdownText.indexOf("### Diagnostic Reasoning");
        if (reasoningIdx < 0) {
            return "";
        }
        String sub = markdownText.substring(reasoningIdx + "### Diagnostic Reasoning".length());
        int nextHeader = sub.indexOf("### File:");
        if (nextHeader < 0) {
            nextHeader = sub.indexOf("```");
        }
        if (nextHeader >= 0) {
            sub = sub.substring(0, nextHeader);
        }
        return sub.trim();
    }

    /**
     * Parses a Markdown text string containing one or more file code blocks.
     *
     * @param markdownText Raw LLM response string
     * @return Map of relative file path -> extracted raw file content
     */
    public static Map<String, String> parseMultiFileMarkdown(String markdownText) {
        Map<String, String> files = new LinkedHashMap<>();
        if (markdownText == null || markdownText.isBlank()) {
            return files;
        }

        String[] lines = markdownText.split("\n");
        String pendingFilePath = null;
        String currentFilePath = null;
        StringBuilder currentCode = null;
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!inCodeBlock) {
                // Check if line contains a file path before a code block
                Matcher matcher = PATH_PATTERN.matcher(trimmed);
                if (matcher.find()) {
                    pendingFilePath = matcher.group(1);
                }
            }

            // Check for code fence start/end
            if (trimmed.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    // Check if fence line itself contains a file path comment (e.g. ```java // src/main/... )
                    Matcher fenceMatcher = PATH_PATTERN.matcher(trimmed);
                    if (fenceMatcher.find()) {
                        currentFilePath = fenceMatcher.group(1);
                    } else if (pendingFilePath != null) {
                        currentFilePath = pendingFilePath;
                    }
                    currentCode = new StringBuilder();
                } else {
                    inCodeBlock = false;
                    if (currentFilePath != null && currentCode != null) {
                        String cleanContent = sanitizeExtractedCode(currentCode.toString());
                        if (!cleanContent.isBlank()) {
                            files.put(currentFilePath, cleanContent);
                            logger.info("[MarkdownMultiFileParser]: Extracted file fix for '{}' ({} chars).",
                                    currentFilePath, cleanContent.length());
                        }
                    }
                    currentFilePath = null;
                    pendingFilePath = null;
                    currentCode = null;
                }
                continue;
            }

            if (inCodeBlock && currentCode != null) {
                // Check if first line of code block contains a file path comment (e.g. // File: src/main/... or <!-- pom.xml -->)
                if (currentFilePath == null && currentCode.length() == 0) {
                    Matcher commentMatcher = PATH_PATTERN.matcher(trimmed);
                    if (commentMatcher.find()) {
                        currentFilePath = commentMatcher.group(1);
                    }
                }
                currentCode.append(line).append("\n");
            }
        }

        // Secondary Fallback: Try JSON map parsing if Markdown blocks were absent
        if (files.isEmpty()) {
            try {
                int firstBrace = markdownText.indexOf('{');
                int lastBrace = markdownText.lastIndexOf('}');
                if (firstBrace >= 0 && lastBrace > firstBrace) {
                    String jsonPart = markdownText.substring(firstBrace, lastBrace + 1);
                    Map<String, String> jsonMap = objectMapper.readValue(jsonPart, new TypeReference<Map<String, String>>() {});
                    if (jsonMap != null && !jsonMap.isEmpty()) {
                        logger.info("[MarkdownMultiFileParser]: Extracted {} files via JSON fallback parser.", jsonMap.size());
                        return jsonMap;
                    }
                }
            } catch (Exception ignored) {}
        }

        return files;
    }

    private static String sanitizeExtractedCode(String raw) {
        String[] lines = raw.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // If first line is a comment declaring the file path, strip it from code body
            if (i == 0 && (line.trim().startsWith("//") || line.trim().startsWith("<!--") || line.trim().startsWith("#"))) {
                Matcher m = PATH_PATTERN.matcher(line);
                if (m.find()) {
                    continue;
                }
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }
}
