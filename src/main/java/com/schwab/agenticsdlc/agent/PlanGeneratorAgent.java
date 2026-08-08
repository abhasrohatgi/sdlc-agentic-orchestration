package com.schwab.agenticsdlc.agent;

import com.schwab.agenticsdlc.llm.LlmClientManager;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Requirement Specialist & Software Architect Agent.
 * Uses LlmClientManager to dynamically generate workstations/<project-id>/.ai-plan/plan.md via Google GenAI LLM.
 */
public class PlanGeneratorAgent {

    private static final Logger logger = LoggerFactory.getLogger(PlanGeneratorAgent.class);

    private final LlmClientManager llmClientManager;

    public PlanGeneratorAgent() {
        this(new LlmClientManager());
    }

    public PlanGeneratorAgent(LlmClientManager llmClientManager) {
        this.llmClientManager = llmClientManager;
    }

    public Path generatePlan(ProjectConfig projectConfig, String requirementPrompt) throws IOException {
        Path projectDirPath = Paths.get(projectConfig.getProjectPath());
        Path aiPlanDir = projectDirPath.resolve(".ai-plan");
        Files.createDirectories(aiPlanDir);

        Path planFilePath = aiPlanDir.resolve("plan.md");

        String existingPlan = "";
        if (Files.exists(planFilePath)) {
            existingPlan = Files.readString(planFilePath);
        }

        String existingCodebaseSummary = scanExistingCodebaseSummary(projectDirPath.toFile());

        // Dynamically generate architectural plan via LLM (incorporating Brownfield context if existing code/plan present)
        String planContent = llmClientManager.generateArchitecturalPlan(projectConfig, requirementPrompt, existingPlan, existingCodebaseSummary);

        Files.writeString(planFilePath, planContent);
        logger.info("[PlanGeneratorAgent]: Dynamically generated LLM architectural plan at {}", planFilePath);
        return planFilePath;
    }

    private String scanExistingCodebaseSummary(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        collectFilesSummary(dir, dir, sb, 0);
        return sb.toString();
    }

    private void collectFilesSummary(File rootDir, File currentDir, StringBuilder sb, int depth) {
        if (depth > 5 || currentDir == null) return;
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory()) {
                if (name.startsWith(".") || "target".equals(name) || "node_modules".equals(name) || "build".equals(name)) {
                    continue;
                }
                collectFilesSummary(rootDir, file, sb, depth + 1);
            } else if (file.isFile()) {
                if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".ts")
                        || name.endsWith(".js") || name.endsWith(".go") || name.endsWith(".rs")
                        || "pom.xml".equals(name) || "package.json".equals(name) || "requirements.txt".equals(name)) {
                    String relative = rootDir.toURI().relativize(file.toURI()).getPath();
                    sb.append("File: ").append(relative).append("\n");
                    try {
                        String content = Files.readString(file.toPath());
                        String[] lines = content.split("\r?\n");
                        int maxLines = Math.min(lines.length, 25);
                        for (int i = 0; i < maxLines; i++) {
                            sb.append("  ").append(lines[i]).append("\n");
                        }
                        sb.append("\n");
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
