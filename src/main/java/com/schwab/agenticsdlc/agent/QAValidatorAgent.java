package com.schwab.agenticsdlc.agent;

import com.schwab.agenticsdlc.llm.LlmClientManager;
import com.schwab.agenticsdlc.workspace.LanguageStack;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * QA & Security Validator Agent.
 * Executes real process build runners (mvn test) with non-blocking stream consumers and LLM self-healing diagnostics.
 */
public class QAValidatorAgent {

    private static final Logger logger = LoggerFactory.getLogger(QAValidatorAgent.class);

    private static final int MAX_SELF_HEALING_RETRIES = 10;
    private static final long PROCESS_TIMEOUT_MINUTES = 3;
    private final LlmClientManager llmClientManager;
    private final HumanGatekeeperAgent humanGatekeeperAgent;

    public QAValidatorAgent() {
        this(new LlmClientManager(), new HumanGatekeeperAgent());
    }

    public QAValidatorAgent(LlmClientManager llmClientManager) {
        this(llmClientManager, new HumanGatekeeperAgent());
    }

    public QAValidatorAgent(LlmClientManager llmClientManager, HumanGatekeeperAgent humanGatekeeperAgent) {
        this.llmClientManager = llmClientManager;
        this.humanGatekeeperAgent = humanGatekeeperAgent;
    }

    public boolean validateAndSelfHeal(ProjectConfig projectConfig, CodeEngineerAgent codeEngineerAgent) {
        return validateAndSelfHeal(projectConfig, codeEngineerAgent, projectConfig.getDescription());
    }

    public boolean validateAndSelfHeal(ProjectConfig projectConfig, CodeEngineerAgent codeEngineerAgent, String requirementPrompt) {
        return validateAndSelfHeal(projectConfig, codeEngineerAgent, requirementPrompt, null);
    }

    public boolean validateAndSelfHeal(ProjectConfig projectConfig, CodeEngineerAgent codeEngineerAgent, String requirementPrompt,
                                       com.schwab.agenticsdlc.telemetry.ExecutionTrajectoryLogger trajectoryLogger) {
        Path projectDir = Paths.get(projectConfig.getProjectPath());
        LanguageStack stack = projectConfig.getLanguageStack();

        java.util.List<SelfHealingAttempt> attemptHistory = new java.util.ArrayList<>();

        for (int attempt = 1; attempt <= MAX_SELF_HEALING_RETRIES; attempt++) {
            logger.info("[QAValidatorAgent]: Execution Attempt {}/{} for project '{}' (Stack: {})...",
                    attempt, MAX_SELF_HEALING_RETRIES, projectConfig.getProjectId(), stack);

            TestExecutionResult result = runBuildAndTests(projectDir.toFile(), stack);

            if (result.isPassed()) {
                logger.info("[QAValidatorAgent SUCCESS]: All build and test suites passed cleanly!");
                logger.debug("--- BUILD OUTPUT ---\n{}", result.getOutput());
                writeAuditLog(projectConfig, true, attempt, "Compilation & unit tests passed cleanly.");
                if (trajectoryLogger != null) {
                    trajectoryLogger.recordStep("STAGE_4_QA_VALIDATION", "QAValidatorAgent", "PASSED", "Build and tests passed cleanly on attempt " + attempt, null, null);
                }
                return true;
            }

            logger.error("[QAValidatorAgent FAILURE]: Attempt {} failed with error code {}.", attempt, result.getExitCode());
            logger.error("--- STACK TRACE / BUILD LOG OUTPUT ---\n{}", result.getOutput());

            if (attempt < MAX_SELF_HEALING_RETRIES) {
                logger.info("[QAValidatorAgent SELF-HEALING]: Passing stack trace, error context, and {} past failed attempts to LLM...", attemptHistory.size());
                if (trajectoryLogger != null) {
                    trajectoryLogger.recordStep(
                            "STAGE_4_QA_SELF_HEALING_ATTEMPT_" + attempt,
                            "QAValidatorAgent",
                            "DIAGNOSING",
                            "Analyzing build failure for attempt " + attempt,
                            null,
                            null
                    );
                }

                try {
                    Map<String, String> currentFiles = loadWorkspaceFiles(projectDir);
                    Map<String, String> relevantFiles = extractErrorRelevantFiles(projectConfig, result.getOutput(), currentFiles);
                    Map<String, String> fixPayload = llmClientManager.generateFixDirective(projectConfig, result.getOutput(), relevantFiles, attemptHistory);

                    if (fixPayload != null && !fixPayload.isEmpty()) {
                        String reasoning = com.schwab.agenticsdlc.agent.MarkdownMultiFileParser.extractDiagnosticReasoning(
                                fixPayload.toString());
                        attemptHistory.add(new SelfHealingAttempt(attempt, reasoning, new java.util.ArrayList<>(fixPayload.keySet()), result.getOutput()));

                        if (trajectoryLogger != null) {
                            trajectoryLogger.recordStep(
                                    "STAGE_4_QA_PATCH_APPLIED",
                                    "CodeEngineerAgent",
                                    "PATCHED",
                                    "Applied self-healing patch for " + fixPayload.size() + " files on attempt " + attempt,
                                    reasoning,
                                    new java.util.ArrayList<>(fixPayload.keySet())
                            );
                        }

                        logger.info("[QAValidatorAgent SELF-HEALING]: Applying targeted fix patch for {} files...", fixPayload.size());
                        codeEngineerAgent.applyFiles(projectConfig, fixPayload);
                    } else {
                        logger.warn("[QAValidatorAgent SELF-HEALING]: LLM returned empty fix directive for attempt {}. Retrying next diagnostic pass...", attempt);
                    }
                } catch (Exception e) {
                    logger.error("Self-healing LLM fix application failed: {}", e.getMessage(), e);
                }
            }
        }

        logger.error("[QAValidatorAgent FAILED]: Self-healing retry limit exhausted.");
        if (humanGatekeeperAgent != null) {
            logger.info("[QAValidatorAgent CLARIFICATION]: Requesting human clarification/guidance to assist self-healing...");
            String clarification = humanGatekeeperAgent.requestClarification(
                    "Self-healing retries exhausted for project '" + projectConfig.getProjectId() + "'. Please provide guidance/clarification to assist build repair: "
            );
            if (clarification != null && !clarification.isBlank()) {
                logger.info("[QAValidatorAgent SELF-HEALING]: Retrying build synthesis with human clarification input...");
                try {
                    codeEngineerAgent.synthesizeCode(projectConfig, requirementPrompt + "\n\nHuman Clarification Guidance:\n" + clarification);
                    TestExecutionResult finalAttempt = runBuildAndTests(projectDir.toFile(), stack);
                    if (finalAttempt.isPassed()) {
                        logger.info("[QAValidatorAgent SUCCESS]: Build passed after applying human clarification guidance!");
                        return true;
                    }
                } catch (Exception e) {
                    logger.error("Failed to re-synthesize after human clarification: {}", e.getMessage(), e);
                }
            }
        }

        return false;
    }

    public TestExecutionResult runBuildAndTests(File workingDir, LanguageStack stack) {
        return executeProcess(workingDir, "mvn test");
    }

    private TestExecutionResult executeProcess(File workingDir, String commandLine) {
        StringBuilder output = new StringBuilder();
        int exitCode = -1;

        try {
            ProcessBuilder pb = new ProcessBuilder(commandLine.split("\\s+"));
            pb.directory(workingDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Asynchronous stream consumer thread to drain output without blocking
            Thread streamConsumer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (output) {
                            output.append(line).append("\n");
                        }
                    }
                } catch (Exception ignored) {}
            });
            streamConsumer.start();

            boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                output.append("\nProcess Execution Timeout (Exceeded ").append(PROCESS_TIMEOUT_MINUTES).append(" minutes)");
                return new TestExecutionResult(-1, output.toString());
            }

            streamConsumer.join(2000);
            exitCode = process.exitValue();
        } catch (Exception e) {
            output.append("Process Execution Error: ").append(e.getMessage());
        }

        return new TestExecutionResult(exitCode, output.toString());
    }

    private void writeAuditLog(ProjectConfig config, boolean success, int attempts, String notes) {
        try {
            Path projectDir = Paths.get(config.getProjectPath());
            Path auditFile = projectDir.resolve("audit_log.json");
            String json = String.format(
                    "{\n  \"projectId\": \"%s\",\n  \"projectName\": \"%s\",\n  \"status\": \"%s\",\n  \"attempts\": %d,\n  \"languageStack\": \"%s\",\n  \"scenarioType\": \"%s\",\n  \"timestamp\": \"%s\",\n  \"notes\": \"%s\"\n}\n",
                    config.getProjectId(),
                    config.getProjectName(),
                    success ? "PASSED" : "FAILED",
                    attempts,
                    config.getLanguageStack(),
                    config.getScenarioType(),
                    java.time.Instant.now().toString(),
                    notes
            );
            java.nio.file.Files.writeString(auditFile, json);
            logger.info("[QAValidatorAgent]: Telemetry saved to {}", auditFile);
        } catch (Exception e) {
            logger.warn("[QAValidatorAgent WARNING]: Failed to write audit log: {}", e.getMessage());
        }
    }

    private Map<String, String> loadWorkspaceFiles(Path projectDir) {
        Map<String, String> files = new java.util.HashMap<>();
        if (!java.nio.file.Files.exists(projectDir)) {
            return files;
        }
        try (var stream = java.nio.file.Files.walk(projectDir)) {
            stream.filter(java.nio.file.Files::isRegularFile)
                  .forEach(path -> {
                      Path relative = projectDir.relativize(path);
                      String relString = relative.toString().replace('\\', '/');
                      if (!relString.startsWith("target/") && !relString.startsWith(".git/") && !relString.equals("audit_log.json")) {
                          try {
                              String content = java.nio.file.Files.readString(path);
                              files.put(relString, content);
                          } catch (Exception ignored) {}
                      }
                  });
        } catch (Exception e) {
            logger.warn("[QAValidatorAgent WARNING]: Failed to scan workspace files for self-healing: {}", e.getMessage());
        }
        return files;
    }

    public Map<String, String> extractErrorRelevantFiles(ProjectConfig config, String stackTrace, Map<String, String> currentFiles) {
        if (currentFiles == null || currentFiles.isEmpty()) {
            return Map.of();
        }
        if (stackTrace == null || stackTrace.isBlank()) {
            return currentFiles;
        }

        // Step 1: 100% LLM-Driven Error Diagnosis
        java.util.List<String> failingPaths = llmClientManager.identifyFailingFiles(config, stackTrace, currentFiles.keySet());
        if (failingPaths == null || failingPaths.isEmpty()) {
            return currentFiles;
        }

        Map<String, String> filtered = new java.util.LinkedHashMap<>();

        // Always include pom.xml and plan.md if present
        if (currentFiles.containsKey("pom.xml")) {
            filtered.put("pom.xml", currentFiles.get("pom.xml"));
        }
        if (currentFiles.containsKey(".ai-plan/plan.md")) {
            filtered.put(".ai-plan/plan.md", currentFiles.get(".ai-plan/plan.md"));
        }

        for (String path : failingPaths) {
            if (currentFiles.containsKey(path)) {
                filtered.put(path, currentFiles.get(path));
            }
        }

        logger.info("[QAValidatorAgent]: Step 1 LLM Diagnosis selected {} failing files out of {} workspace files.",
                filtered.size(), currentFiles.size());
        return filtered;
    }

    public static class SelfHealingAttempt {
        private final int attemptIndex;
        private final String diagnosticReasoning;
        private final java.util.List<String> filesPatched;
        private final String buildErrorOutput;

        public SelfHealingAttempt(int attemptIndex, String diagnosticReasoning, java.util.List<String> filesPatched, String buildErrorOutput) {
            this.attemptIndex = attemptIndex;
            this.diagnosticReasoning = diagnosticReasoning;
            this.filesPatched = filesPatched;
            this.buildErrorOutput = buildErrorOutput;
        }

        public int getAttemptIndex() { return attemptIndex; }
        public String getDiagnosticReasoning() { return diagnosticReasoning; }
        public java.util.List<String> getFilesPatched() { return filesPatched; }
        public String getBuildErrorOutput() { return buildErrorOutput; }
    }

    public static class TestExecutionResult {
        private final int exitCode;
        private final String output;

        public TestExecutionResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public boolean isPassed() {
            return exitCode == 0;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getOutput() {
            return output;
        }
    }
}
