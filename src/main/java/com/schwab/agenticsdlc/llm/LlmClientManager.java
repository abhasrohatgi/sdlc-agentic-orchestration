package com.schwab.agenticsdlc.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.ScenarioType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Pure 100% LLM Client Manager.
 * Communicates directly with Gemini LLM (gemini-2.5-flash) via official com.google.genai.Client.
 * Zero hardcoded templates, zero static string generation fallbacks. All planning, file discovery, code synthesis, and fix directives are 100% LLM-driven.
 */
public class LlmClientManager {

    private static final Logger logger = LoggerFactory.getLogger(LlmClientManager.class);

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String applicationCredentials;
    private final String modelName;
    private final String llmBaseUrl;

    public LlmClientManager() {
        this.objectMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);

        // Universal Provider-Agnostic LLM Configuration
        String key = System.getenv("LLM_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getenv("GOOGLE_API_KEY"); // Default GenAI SDK fallback
        }
        this.apiKey = (key != null && !key.isBlank()) ? key.trim() : null;

        String creds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (creds == null || creds.isBlank()) {
            creds = System.getProperty("GOOGLE_APPLICATION_CREDENTIALS");
        }
        this.applicationCredentials = (creds != null && !creds.isBlank()) ? creds.trim() : null;

        String baseUrl = System.getenv("LLM_BASE_URL");
        this.llmBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : null;

        String model = System.getenv("LLM_MODEL");
        if (model == null || model.isBlank()) {
            model = System.getProperty("llm.model");
        }
        this.modelName = (model != null && !model.isBlank()) ? model.trim() : "gemini-3.6-flash";
    }

    public String getModelName() {
        return modelName;
    }

    /**
     * Verifies LLM API credentials and connectivity at application startup.
     * @return true if LLM is accessible, false otherwise
     */
    public boolean verifyLlmConnection() {
        logger.info("------------------------------------------------------------------");
        logger.info("[GATE_LLM_HEALTH_CHECK]: Verifying LLM API Access...");
        logger.info("  Configured Target Model: {}", modelName);
        if (llmBaseUrl != null) {
            logger.info("  Configured Base Endpoint: {}", llmBaseUrl);
        }

        if ((apiKey == null || apiKey.isBlank()) && (applicationCredentials == null || applicationCredentials.isBlank())) {
            logger.warn("[LLM_HEALTH_CHECK WARNING]: Neither LLM_API_KEY (or GOOGLE_API_KEY) nor GOOGLE_APPLICATION_CREDENTIALS environment variable is set.");
            logger.warn("  -> To enable live LLM generation, set your universal credentials via:");
            logger.warn("     export LLM_API_KEY=\"your-api-key\"");
            logger.warn("     export LLM_MODEL=\"your-model-id\"      # e.g. gemini-2.5-flash");
            logger.warn("     export LLM_BASE_URL=\"https://...\"      # (optional custom endpoint)");
            return false;
        }

        try {
            Client client = getClient();
            GenerateContentResponse response = client.models.generateContent(modelName, "ping", null);
            if (response != null && response.text() != null) {
                logger.info("[LLM_HEALTH_CHECK SUCCESS]: LLM connection verified successfully!");
                return true;
            }
        } catch (Exception e) {
            logger.error("[LLM_HEALTH_CHECK FAILED]: Connection probe failed: {}", e.getMessage());
            logger.error("  -> Please verify your LLM_API_KEY, LLM_MODEL, or GOOGLE_APPLICATION_CREDENTIALS credentials.");
            return false;
        }

        return false;
    }

    /**
     * Generates architectural execution plan dynamically via Gemini LLM.
     */
    public String generateArchitecturalPlan(ProjectConfig config, String userPrompt) {
        return generateArchitecturalPlan(config, userPrompt, "", "");
    }

    public String generateArchitecturalPlan(ProjectConfig config, String userPrompt, String existingPlan, String existingCodebaseSummary) {
        if (userPrompt != null && isGibberish(userPrompt)) {
            throw new IllegalArgumentException("[GATE_REQUIREMENT_VALIDATION FAILED]: The requirement prompt '" + userPrompt + "' is invalid or nonsensical gibberish. Please provide a clear software specification.");
        }

        boolean isBrownfield = (config.getScenarioType() == ScenarioType.BROWNFIELD)
                || (existingPlan != null && !existingPlan.isBlank())
                || (existingCodebaseSummary != null && !existingCodebaseSummary.isBlank());

        String systemInstruction;
        if (isBrownfield) {
            systemInstruction = loadPromptTemplate("plan_generator_brownfield_system.prompt");
        } else {
            systemInstruction = loadPromptTemplate("plan_generator_greenfield_system.prompt");
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(String.format("Generate architectural plan for project '%s' (ID: %s, Stack: %s, Category: %s, Detected Scenario: %s).\nUser Requirement Prompt: %s\n\n",
                config.getProjectName(), config.getProjectId(), config.getLanguageStack(), config.getProjectType(),
                isBrownfield ? "BROWNFIELD" : config.getScenarioType(), userPrompt));

        if (existingPlan != null && !existingPlan.isBlank()) {
            promptBuilder.append("--- BASELINE ARCHITECTURAL PLAN (.ai-plan/plan.md) ---\n").append(existingPlan).append("\n\n");
        }

        if (existingCodebaseSummary != null && !existingCodebaseSummary.isBlank()) {
            promptBuilder.append("--- EXISTING WORKSPACE CODEBASE SNAPSHOT ---\n").append(existingCodebaseSummary).append("\n\n");
        }

        String prompt = promptBuilder.toString();

        try {
            GenerateContentConfig contentConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.builder().parts(List.of(Part.builder().text(systemInstruction).build())).build())
                    .temperature(0.1f)
                    .build();

            GenerateContentResponse response = executeWithQuotaRetry(() -> getClient().models.generateContent(modelName, prompt, contentConfig));
            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new RuntimeException("LLM returned an empty architectural plan for prompt: " + userPrompt);
            }

            String responseText = response.text().trim();
            if (responseText.contains("ERROR_REJECTED_GIBBERISH_REQUIREMENT")) {
                throw new IllegalArgumentException("[GATE_REQUIREMENT_VALIDATION FAILED]: The requirement prompt '" + userPrompt + "' was rejected as invalid or nonsensical. Please provide a clear software specification.");
            }

            return responseText;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("LLM Architectural Plan Generation Failed: " + e.getMessage(), e);
        }
    }

    private boolean isGibberish(String text) {
        String cleaned = text.replaceAll("[^a-zA-Z]", "");
        if (cleaned.isBlank()) {
            return true;
        }
        long vowelCount = cleaned.toLowerCase().chars()
                .filter(ch -> ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u' || ch == 'y')
                .count();
        double vowelRatio = (double) vowelCount / cleaned.length();
        return (cleaned.length() >= 8 && vowelRatio < 0.15);
    }

    private String getSanitizedPackageRoot(ProjectConfig config) {
        if (config == null || config.getProjectId() == null) {
            return "app";
        }
        String sanitized = config.getProjectId().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return sanitized.isBlank() ? "app" : sanitized;
    }

    public String loadPromptTemplate(String fileName) {
        String resourcePath = "prompts/" + fileName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            logger.error("[LlmClientManager ERROR]: Failed to load prompt resource '{}': {}", resourcePath, e.getMessage());
        }
        throw new IllegalStateException("Failed to load required LLM prompt template resource: " + resourcePath);
    }

    // ─── Phase 1: Manifest Generation ────────────────────────────────────────

    /**
     * Phase 1 of the 2-phase code generation strategy.
     * Asks the LLM to return ONLY a structured JSON manifest listing all files to be
     * generated — with NO source code embedded. Uses {@code responseMimeType("application/json")}
     * to guarantee structurally valid JSON output.
     *
     * <p>This eliminates the root cause of JSON parse failures: embedding raw Java source
     * (which contains {@code #}, unescaped {@code "}, backticks, and raw newlines) inside
     * JSON string values.
     *
     * @param config       the project metadata
     * @param planContent  content of .ai-plan/plan.md
     * @param userPrompt   the requirement prompt
     * @return Raw JSON string of the file manifest
     */
    public String generateFileManifest(ProjectConfig config, String planContent, String userPrompt) {
        String pkgRoot = getSanitizedPackageRoot(config);
        String systemInstruction = loadPromptTemplate("manifest_generation_system.prompt").replace("%1$s", pkgRoot);

        String prompt = String.format(
                "Generate the file manifest for project '%s' (ID: %s, Stack: %s, Category: %s).\n" +
                "User Requirement: %s\n\nArchitectural Plan:\n%s",
                config.getProjectName(), config.getProjectId(),
                config.getLanguageStack(), config.getProjectType(),
                userPrompt, planContent);

        try {
            GenerateContentConfig contentConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.builder().parts(List.of(Part.builder().text(systemInstruction).build())).build())
                    .responseMimeType("application/json")
                    .temperature(0.1f)
                    .maxOutputTokens(8192)
                    .build();

            GenerateContentResponse response = executeWithQuotaRetry(
                    () -> getClient().models.generateContent(modelName, prompt, contentConfig));

            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new RuntimeException("LLM returned empty manifest for project: " + config.getProjectId());
            }

            logger.info("[LlmClientManager PHASE-1]: File manifest received ({} chars).", response.text().length());
            return response.text();
        } catch (Exception e) {
            throw new RuntimeException("Phase 1 manifest generation failed: " + e.getMessage(), e);
        }
    }

    // ─── Phase 2: Per-File Code Generation ───────────────────────────────────

    /**
     * Phase 2 of the 2-phase code generation strategy.
     * Generates the source code for a SINGLE file in isolation. The LLM is instructed to
     * return ONLY raw Java code wrapped in a {@code ```java} Markdown block — never JSON.
     *
     * <p>The {@link com.schwab.agenticsdlc.agent.MarkdownCodeExtractor} then strips the
     * fence, and the clean code is written directly to disk.
     *
     * @param config            the project metadata
     * @param planContent       content of .ai-plan/plan.md
     * @param fileNode          the target file descriptor from the Phase 1 manifest
     * @param interfaceContext  map of previously generated interface/model files (for consistency)
     * @return Raw Markdown string containing exactly one {@code ```java ... ```} block
     */
    public String generateSingleFileCode(ProjectConfig config, String planContent,
                                         com.schwab.agenticsdlc.agent.ManifestParser.FileNode fileNode,
                                         java.util.Map<String, String> interfaceContext) {
        String pkgRoot = getSanitizedPackageRoot(config);

        // Build bounded interface context summary (avoid unbounded prompt growth)
        StringBuilder contextSummary = new StringBuilder();
        if (interfaceContext != null && !interfaceContext.isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : interfaceContext.entrySet()) {
                contextSummary.append("// === ").append(entry.getKey()).append(" ===\n");
                // Include first 60 lines only to bound prompt size
                String[] lines = entry.getValue().split("\n");
                int limit = Math.min(lines.length, 60);
                for (int i = 0; i < limit; i++) {
                    contextSummary.append(lines[i]).append("\n");
                }
                contextSummary.append("\n");
            }
        }

        String planSummary = planContent.length() > 2000
                ? planContent.substring(0, 2000) + "\n...[truncated]"
                : planContent;

        String contextSummaryStr = contextSummary.length() > 0 ? contextSummary.toString() : "(none yet)";

        String template = loadPromptTemplate("single_file_code_system.prompt");
        String systemInstruction = template
                .replace("%1$s", pkgRoot != null ? pkgRoot : "")
                .replace("%2$s", config.getProjectName() != null ? config.getProjectName() : "")
                .replace("%3$s", planSummary != null ? planSummary : "")
                .replace("%4$s", contextSummaryStr)
                .replace("%5$s", fileNode.getPath() != null ? fileNode.getPath() : "")
                .replace("%6$s", fileNode.getPurpose() != null ? fileNode.getPurpose() : "")
                .replace("%7$s", fileNode.getFileType() != null ? fileNode.getFileType() : "");

        String prompt = String.format(
                "Generate the Java source code for: %s\nPurpose: %s\nFileType: %s",
                fileNode.getPath(), fileNode.getPurpose(), fileNode.getFileType());

        try {
            // CRITICAL: NO responseMimeType here — plain text / markdown only
            GenerateContentConfig contentConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.builder().parts(List.of(Part.builder().text(systemInstruction).build())).build())
                    .temperature(0.1f)
                    .maxOutputTokens(8192)
                    .build();

            GenerateContentResponse response = executeWithQuotaRetry(
                    () -> getClient().models.generateContent(modelName, prompt, contentConfig));

            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new RuntimeException("LLM returned empty code for file: " + fileNode.getPath());
            }

            logger.info("[LlmClientManager PHASE-2]: Received code for '{}' ({} chars).",
                    fileNode.getPath(), response.text().length());
            return response.text();
        } catch (Exception e) {
            throw new RuntimeException("Phase 2 code generation failed for '" + fileNode.getPath() + "': " + e.getMessage(), e);
        }
    }

    // ─── Legacy Single-Call Fallback ─────────────────────────────────────────

    /**
     * Legacy single-call code generation (kept as graceful fallback).
     * Generates all project files in one LLM call as a JSON map.
     * <p><b>Known limitation</b>: Subject to JSON parse failures when LLM embeds raw source
     * code containing unescaped characters inside JSON string values. Prefer the 2-phase
     * approach ({@link #generateFileManifest} + {@link #generateSingleFileCode}).
     */
    public Map<String, String> generateCodeFiles(ProjectConfig config, String planContent, String userPrompt) {
        String pkgRoot = getSanitizedPackageRoot(config);
        String template = loadPromptTemplate("code_engineer_system.prompt");
        String systemInstruction = String.format(template, pkgRoot, pkgRoot, pkgRoot);

        String prompt = String.format("Synthesize complete, production-grade source code files, unit tests, and API integration test suites for project '%s' (Stack: %s, Category: %s).\nRequirement: %s\nArchitectural Plan:\n%s\n\nReturn the response as a valid JSON object enclosed within triple backticks (```json ... ```). Ensure all field names are enclosed in double quotes and the JSON is strictly valid.",
                config.getProjectName(), config.getLanguageStack(), config.getProjectType(), userPrompt, planContent);

        try {
            GenerateContentConfig contentConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.builder().parts(List.of(Part.builder().text(systemInstruction).build())).build())
                    .responseMimeType("application/json")
                    .temperature(0.1f)
                    .maxOutputTokens(8192)
                    .build();

            GenerateContentResponse response = executeWithQuotaRetry(() -> getClient().models.generateContent(modelName, prompt, contentConfig));
            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new RuntimeException("LLM returned empty code payload for prompt: " + userPrompt);
            }

            // Extract and sanitize JSON response
            String jsonText = extractJsonText(response.text());
            jsonText = enforceDoubleQuotes(jsonText); // Enforce double quotes around field names
            return parseJsonMap(jsonText);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate code files via Gemini LLM: " + e.getMessage(), e);
        }
    }

    /**
     * Enforces double quotes around field names in a JSON string.
     * @param jsonText The raw JSON string.
     * @return The sanitized JSON string with double-quoted field names.
     */
    private String enforceDoubleQuotes(String jsonText) {
        // Use regex to find and replace unquoted field names
        return jsonText.replaceAll("(?<!\\\\)\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b(?=\\s*:\s*)", "\"$1\"");
    }

    public List<String> identifyFailingFiles(ProjectConfig config, String stackTrace, java.util.Set<String> allFilePaths) {
        if (allFilePaths == null || allFilePaths.isEmpty()) {
            return List.of();
        }
        if (stackTrace == null || stackTrace.isBlank()) {
            return new java.util.ArrayList<>(allFilePaths);
        }

        String systemInstruction = loadPromptTemplate("error_diagnosis_system.prompt");

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(String.format("Build failure log for project '%s':\n%s\n\n", config.getProjectId(), stackTrace));
        promptBuilder.append("--- AVAILABLE WORKSPACE FILE PATHS ---\n");
        for (String path : allFilePaths) {
            promptBuilder.append("- ").append(path).append("\n");
        }
        promptBuilder.append("\nIdentify the exact relative file paths from the list above causing or involved in this failure. Return ONLY a JSON array of strings.");

        try {
            GenerateContentConfig contentConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.builder().parts(List.of(Part.builder().text(systemInstruction).build())).build())
                    .responseMimeType("application/json")
                    .temperature(0.1f)
                    .maxOutputTokens(1024)
                    .build();

            GenerateContentResponse response = executeWithQuotaRetry(() -> getClient().models.generateContent(modelName, promptBuilder.toString(), contentConfig));
            if (response != null && response.text() != null && !response.text().isBlank()) {
                String jsonPart = extractJsonText(response.text());
                List<String> files = objectMapper.readValue(jsonPart, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                if (files != null && !files.isEmpty()) {
                    // Systemic Guardrail: If build/compiler error occurs, guarantee pom.xml is included in diagnostic set
                    String lowerTrace = stackTrace != null ? stackTrace.toLowerCase() : "";
                    if ((lowerTrace.contains("compiler-plugin") || lowerTrace.contains("fatal error compiling") ||
                         lowerTrace.contains("exceptionininitializererror") || lowerTrace.contains("mojoexecutionexception") ||
                         lowerTrace.contains("annotationprocessor")) && allFilePaths.contains("pom.xml") && !files.contains("pom.xml")) {
                        files.add(0, "pom.xml");
                    }
                    logger.info("[LlmClientManager STEP-1 DIAGNOSTIC]: LLM identified {} failing files: {}", files.size(), files);
                    return files;
                }
            }
        } catch (Exception e) {
            logger.warn("[LlmClientManager STEP-1 WARNING]: LLM failing file diagnosis failed: {}. Falling back to full file set.", e.getMessage());
        }

        return new java.util.ArrayList<>(allFilePaths);
    }

    public Map<String, String> generateFixDirective(ProjectConfig config, String stackTrace, Map<String, String> currentFiles) {
        return generateFixDirective(config, stackTrace, currentFiles, List.of());
    }

    public Map<String, String> generateFixDirective(ProjectConfig config, String stackTrace, Map<String, String> currentFiles,
                                                   List<com.schwab.agenticsdlc.agent.QAValidatorAgent.SelfHealingAttempt> attemptHistory) {
        String pkgRoot = getSanitizedPackageRoot(config);
        String systemInstruction = loadPromptTemplate("fix_directive_system.prompt").replace("%1$s", pkgRoot);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(String.format("The build/test execution for project '%s' failed with the following stack trace:\n%s\n\n",
                config.getProjectId(), stackTrace));

        // Inject Previous Failed Self-Healing Attempts Context
        if (attemptHistory != null && !attemptHistory.isEmpty()) {
            promptBuilder.append("=== PREVIOUS FAILED SELF-HEALING ATTEMPTS (DO NOT REPEAT THESE STRATEGIES) ===\n");
            promptBuilder.append("The following fix strategies were ALREADY attempted on previous passes and STILL FAILED. Do NOT repeat or make minor variations of these strategies:\n\n");
            for (com.schwab.agenticsdlc.agent.QAValidatorAgent.SelfHealingAttempt attempt : attemptHistory) {
                promptBuilder.append("--- Attempt ").append(attempt.getAttemptIndex()).append(" (FAILED) ---\n");
                if (attempt.getDiagnosticReasoning() != null && !attempt.getDiagnosticReasoning().isBlank()) {
                    promptBuilder.append("LLM Strategy Tried: ").append(attempt.getDiagnosticReasoning()).append("\n");
                }
                if (attempt.getFilesPatched() != null && !attempt.getFilesPatched().isEmpty()) {
                    promptBuilder.append("Files Modified: ").append(attempt.getFilesPatched()).append("\n");
                }
                if (attempt.getBuildErrorOutput() != null && !attempt.getBuildErrorOutput().isBlank()) {
                    String errorSummary = attempt.getBuildErrorOutput().length() > 500
                            ? attempt.getBuildErrorOutput().substring(0, 500) + "... [truncated]"
                            : attempt.getBuildErrorOutput();
                    promptBuilder.append("Resulting Build Error: ").append(errorSummary.trim()).append("\n");
                }
                promptBuilder.append("\n");
            }
            promptBuilder.append("CRITICAL ANTI-LOOPING DIRECTIVE: The strategies above ALREADY FAILED. You MUST pivot to a fundamentally DIFFERENT architectural or configuration fix strategy!\n\n");
        }

        if (currentFiles != null && !currentFiles.isEmpty()) {
            promptBuilder.append("--- EXISTING WORKSPACE CODEBASE SNAPSHOT ---\n");
            // Systemic Prioritization: Always place pom.xml / build descriptor at the VERY TOP of LLM context window
            Map<String, String> sortedFiles = new java.util.LinkedHashMap<>();
            if (currentFiles.containsKey("pom.xml")) {
                sortedFiles.put("pom.xml", currentFiles.get("pom.xml"));
            }
            for (Map.Entry<String, String> entry : currentFiles.entrySet()) {
                if (!entry.getKey().equals("pom.xml")) {
                    sortedFiles.put(entry.getKey(), entry.getValue());
                }
            }

            for (Map.Entry<String, String> entry : sortedFiles.entrySet()) {
                if (entry.getKey().equals("pom.xml")) {
                    promptBuilder.append("// === PRIMARY BUILD DESCRIPTOR (pom.xml) ===\n");
                } else {
                    promptBuilder.append("// === File: ").append(entry.getKey()).append(" ===\n");
                }
                promptBuilder.append(entry.getValue()).append("\n\n");
            }
        }

        promptBuilder.append("Analyze the stack trace and existing workspace files above. Return file fix directives using Markdown file headers (### File: path/to/file.ext) and Markdown code blocks.");

        String prompt = promptBuilder.toString();

        try {
            // CRITICAL: Plain text/Markdown mode for file code synthesis (no responseMimeType("application/json"))
            GenerateContentConfig contentConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.builder().parts(List.of(Part.builder().text(systemInstruction).build())).build())
                    .temperature(0.1f)
                    .maxOutputTokens(8192)
                    .build();

            GenerateContentResponse response = executeWithQuotaRetry(() -> getClient().models.generateContent(modelName, prompt, contentConfig));
            if (response == null || response.text() == null || response.text().isBlank()) {
                return Map.of();
            }

            String reasoning = com.schwab.agenticsdlc.agent.MarkdownMultiFileParser.extractDiagnosticReasoning(response.text());
            if (!reasoning.isBlank()) {
                logger.info("[LlmClientManager DIAGNOSTIC REASONING]:\n{}", reasoning);
                com.schwab.agenticsdlc.cli.CliRenderer.logEvent("INFO", "QAValidatorAgent", "⚡ LLM Diagnostic Reasoning:\n" + reasoning);
            }

            Map<String, String> fixes = com.schwab.agenticsdlc.agent.MarkdownMultiFileParser.parseMultiFileMarkdown(response.text());
            if (fixes != null && !fixes.isEmpty()) {
                logger.info("[LlmClientManager SELF-HEALING]: Parsed {} file fix directives via MarkdownMultiFileParser.", fixes.size());
                return fixes;
            }
            return Map.of();
        } catch (Exception e) {
            logger.warn("[LlmClientManager WARNING]: Failed to generate fix directive via LLM: {}", e.getMessage());
            return Map.of();
        }
    }

    @FunctionalInterface
    private interface LlmCallSupplier<T> {
        T get() throws Exception;
    }

    private GenerateContentResponse executeWithQuotaRetry(LlmCallSupplier<GenerateContentResponse> call) throws Exception {
        int maxRetries = 10;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                return call.get();
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                if ((errorMsg.contains("429") || errorMsg.contains("Quota exceeded") || errorMsg.contains("Too Many Requests")) && i < maxRetries) {
                    long sleepMs = 45000;
                    if (errorMsg.contains("Please retry in ")) {
                        try {
                            String retrySub = errorMsg.substring(errorMsg.indexOf("Please retry in ") + 16);
                            int sIndex = retrySub.indexOf("s");
                            if (sIndex > 0) {
                                double seconds = Double.parseDouble(retrySub.substring(0, sIndex));
                                sleepMs = (long) ((seconds + 2.0) * 1000);
                            }
                        } catch (Exception ignored) {}
                    }
                    logger.warn("[LlmClientManager RATE-LIMIT]: 429 Quota Exceeded. Sleeping {} ms before retry {}/{}...", sleepMs, i, maxRetries);
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Rate limit retries exhausted.");
    }

    private Client getClient() {
        if (llmBaseUrl != null && !llmBaseUrl.isBlank()) {
            logger.debug("[LlmClientManager]: Using Custom LLM Base URL Endpoint at {}", llmBaseUrl);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return Client.builder().apiKey(apiKey).build();
        }
        if (applicationCredentials != null && !applicationCredentials.isBlank()) {
            logger.info("[LlmClientManager]: Using Google Application Credentials Service Account JSON at {}", applicationCredentials);
            String projectId = extractProjectIdFromServiceAccountJson(applicationCredentials);
            String location = System.getenv("GOOGLE_CLOUD_LOCATION");
            if (location == null || location.isBlank()) {
                location = "us-central1";
            }
            if (projectId != null && !projectId.isBlank()) {
                logger.info("[LlmClientManager]: Initializing Vertex AI Client (Project: {}, Location: {})", projectId, location);
                return Client.builder().vertexAI(true).project(projectId).location(location).build();
            }
        }
        try {
            return Client.builder().build();
        } catch (Exception e) {
            throw new IllegalStateException("LLM Client initialization failed. Please set LLM_API_KEY (or GOOGLE_APPLICATION_CREDENTIALS) environment variable.", e);
        }
    }

    private String extractProjectIdFromServiceAccountJson(String filePath) {
        try {
            java.io.File jsonFile = new java.io.File(filePath);
            if (jsonFile.exists()) {
                com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(jsonFile);
                if (rootNode.has("project_id")) {
                    return rootNode.get("project_id").asText();
                }
            }
        } catch (Exception e) {
            logger.warn("[LlmClientManager WARNING]: Could not parse project_id from {}: {}", filePath, e.getMessage());
        }
        return System.getenv("GOOGLE_CLOUD_PROJECT");
    }

    private Map<String, String> parseJsonMap(String jsonText) throws Exception {
        try {
            return objectMapper.readValue(jsonText, new TypeReference<Map<String, String>>() {});
        } catch (Exception originalException) {
            logger.warn("[LlmClientManager WARNING]: Standard JSON parsing failed ({}). Attempting JSON auto-repair...", originalException.getMessage());
            String repairedJson = repairTruncatedJson(jsonText);
            try {
                return objectMapper.readValue(repairedJson, new TypeReference<Map<String, String>>() {});
            } catch (Exception repairException) {
                logger.warn("[LlmClientManager WARNING]: JSON auto-repair failed. Executing resilient key-value fallback parser...");
                try {
                    Map<String, String> fallbackResult = parseJsonMapWithFallbackExtractor(jsonText);
                    if (fallbackResult != null && !fallbackResult.isEmpty()) {
                        logger.info("[LlmClientManager SUCCESS]: Resilient key-value fallback parser recovered {} files.", fallbackResult.size());
                        return fallbackResult;
                    }
                } catch (Exception fallbackEx) {
                    logger.error("[LlmClientManager ERROR]: Fallback key-value extractor failed: {}", fallbackEx.getMessage());
                }
                throw originalException;
            }
        }
    }

    /**
     * Parses JSON content with enhanced error handling to address malformed or invalid JSON.
     * @param jsonContent The raw JSON string to parse.
     * @return A parsed Map representation of the JSON content.
     * @throws RuntimeException if parsing fails.
     */
    private Map<String, Object> parseJsonWithFallback(String jsonContent) {
        try {
            return objectMapper.readValue(jsonContent, new TypeReference<>() {});
        } catch (Exception e) {
            logger.warn("[LlmClientManager WARNING]: Standard JSON parsing failed: {}", e.getMessage());
            logger.warn("Attempting JSON auto-repair...");

            // Attempt to sanitize the JSON content
            String sanitizedJson = jsonContent.replace("`", "\""); // Replace problematic backticks with quotes

            try {
                return objectMapper.readValue(sanitizedJson, new TypeReference<>() {});
            } catch (Exception repairException) {
                logger.warn("[LlmClientManager WARNING]: JSON auto-repair failed: {}", repairException.getMessage());
                logger.warn("Executing resilient key-value fallback parser...");

                // Fallback to a simple key-value parser
                try {
                    Map<String, Object> fallbackMap = new java.util.HashMap<>();
                    String[] entries = sanitizedJson.split(",");
                    for (String entry : entries) {
                        String[] keyValue = entry.split(":");
                        if (keyValue.length == 2) {
                            fallbackMap.put(keyValue[0].trim(), keyValue[1].trim());
                        }
                    }
                    return fallbackMap;
                } catch (Exception fallbackException) {
                    throw new RuntimeException("Failed to parse JSON content after multiple attempts: " + fallbackException.getMessage(), fallbackException);
                }
            }
        }
    }

    private Map<String, String> parseJsonMapWithFallbackExtractor(String text) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        java.util.regex.Pattern keyPattern = java.util.regex.Pattern.compile("\"([a-zA-Z0-9_./\\\\-]+?\\.[a-zA-Z0-9]+)\"\\s*:\\s*\"");
        java.util.regex.Matcher matcher = keyPattern.matcher(text);

        List<String> keys = new java.util.ArrayList<>();
        List<Integer> keyMatchStarts = new java.util.ArrayList<>();
        List<Integer> valueStarts = new java.util.ArrayList<>();

        while (matcher.find()) {
            keys.add(matcher.group(1));
            keyMatchStarts.add(matcher.start());
            valueStarts.add(matcher.end());
        }

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int start = valueStarts.get(i);
            int end;

            if (i < keys.size() - 1) {
                int nextKeyStart = keyMatchStarts.get(i + 1);
                end = text.lastIndexOf('"', nextKeyStart - 1);
                while (end > start && text.charAt(end - 1) == '\\') {
                    end = text.lastIndexOf('"', end - 2);
                }
                if (end <= start) {
                    end = nextKeyStart;
                    while (end > start && (text.charAt(end - 1) == ',' || text.charAt(end - 1) == '"' || Character.isWhitespace(text.charAt(end - 1)))) {
                        end--;
                    }
                }
            } else {
                int lastBrace = text.lastIndexOf('}');
                end = (lastBrace > start) ? lastBrace : text.length();
                while (end > start && (text.charAt(end - 1) == '}' || text.charAt(end - 1) == '"' || Character.isWhitespace(text.charAt(end - 1)))) {
                    end--;
                }
            }

            if (end > start) {
                String rawVal = text.substring(start, end);
                String cleaned = cleanAndUnescapeValue(rawVal);
                result.put(key, cleaned);
            }
        }
        return result;
    }

    private String cleanAndUnescapeValue(String raw) {
        if (raw == null) return "";
        String val = raw.trim();
        if (val.startsWith("\"")) {
            val = val.substring(1);
        }
        if (val.endsWith("\"")) {
            val = val.substring(0, val.length() - 1);
        }
        return val.replace("\\\"", "\"")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\\\", "\\");
    }

    private String repairTruncatedJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return "{}";
        }
        String trimmed = jsonText.trim();
        int firstBrace = trimmed.indexOf('{');
        if (firstBrace >= 0) {
            trimmed = trimmed.substring(firstBrace);
        } else {
            trimmed = "{" + trimmed;
        }

        // Count unescaped double quotes
        int quotes = 0;
        boolean escaped = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && !escaped) {
                escaped = true;
            } else {
                if (c == '"' && !escaped) {
                    quotes++;
                }
                escaped = false;
            }
        }

        // If string ended mid-escape, strip trailing backslash
        if (escaped || trimmed.endsWith("\\")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
            quotes++; // adjusted quote count after stripping trailing backslash
        }

        // If string ended mid-quote, append a closing quote
        if (quotes % 2 != 0) {
            trimmed = trimmed + "\"";
        }

        // Close root object if missing closing brace
        if (!trimmed.endsWith("}")) {
            trimmed = trimmed + "}";
        }

        return trimmed;
    }

    private String extractJsonText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "{}";
        }
        String text = rawText.trim();
        if (text.contains("```json")) {
            int start = text.indexOf("```json") + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                text = text.substring(start, end).trim();
            }
        } else if (text.contains("```")) {
            int start = text.indexOf("```") + 3;
            int end = text.indexOf("```", start);
            if (end > start) {
                text = text.substring(start, end).trim();
            }
        }

        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1).trim();
        } else if (firstBrace >= 0) {
            return text.substring(firstBrace).trim();
        }

        return text;
    }
}
