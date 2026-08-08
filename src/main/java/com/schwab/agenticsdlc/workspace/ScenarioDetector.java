package com.schwab.agenticsdlc.workspace;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Automated Scenario Classifier.
 * Analyzes target workspace directory state and user prompt specification to auto-detect GREENFIELD, BROWNFIELD, or AMBIGUOUS scenarios.
 */
public class ScenarioDetector {

    private static final Set<String> BUILD_MANIFESTS = Set.of(
            "pom.xml", "build.gradle", "package.json", "requirements.txt",
            "pyproject.toml", "Cargo.toml", "go.mod", "Makefile", "CMakeLists.txt", "project_config.json"
    );

    /**
     * Auto-detect the SDLC scenario type based on workspace directory contents and requirement prompt.
     * @param workspaceDir Canonical path to project workspace directory.
     * @param userPrompt Input requirement prompt string.
     * @return Auto-detected ScenarioType (BROWNFIELD, AMBIGUOUS, or GREENFIELD).
     */
    public ScenarioType detectScenario(Path workspaceDir, String userPrompt) {
        // 1. Check for Brownfield (existing code or build manifests present)
        if (workspaceDir != null && Files.exists(workspaceDir) && Files.isDirectory(workspaceDir)) {
            if (hasExistingSourceCodeOrManifests(workspaceDir.toFile())) {
                return ScenarioType.BROWNFIELD;
            }
        }

        // 2. Check for Ambiguous requirement prompt
        if (userPrompt == null || userPrompt.isBlank() || isAmbiguousPrompt(userPrompt)) {
            return ScenarioType.AMBIGUOUS;
        }

        // 3. Otherwise Greenfield (clean/new workspace with explicit requirements)
        return ScenarioType.GREENFIELD;
    }

    private boolean hasExistingSourceCodeOrManifests(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Ignore standard VCS directories
                if (".git".equals(file.getName()) || ".idea".equals(file.getName()) || "target".equals(file.getName()) || "node_modules".equals(file.getName())) {
                    continue;
                }
                // Recurse into subdirectories if they contain source files
                if (hasExistingSourceCodeOrManifests(file)) {
                    return true;
                }
            } else if (file.isFile()) {
                String name = file.getName();
                // Check if build manifest exists (excluding empty project_config.json)
                if (BUILD_MANIFESTS.contains(name) && !"project_config.json".equals(name)) {
                    return true;
                }
                // Check for common source extensions
                if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".ts")
                        || name.endsWith(".js") || name.endsWith(".go") || name.endsWith(".rs") || name.endsWith(".cpp")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAmbiguousPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return true;
        }
        String trimmed = prompt.trim();
        if (trimmed.length() < 15) {
            return true;
        }

        // Check for gibberish / nonsensical character sequences (e.g. "hfjkdwgfkdhfg")
        if (isGibberishOrNonsense(trimmed)) {
            return true;
        }

        String lower = trimmed.toLowerCase();
        boolean hasActionKeyword = lower.contains("build") || lower.contains("create") || lower.contains("implement")
                || lower.contains("refactor") || lower.contains("add") || lower.contains("make") || lower.contains("develop")
                || lower.contains("api") || lower.contains("service") || lower.contains("app") || lower.contains("system");

        return !hasActionKeyword;
    }

    private boolean isGibberishOrNonsense(String text) {
        String cleaned = text.replaceAll("[^a-zA-Z]", "");
        if (cleaned.isBlank()) {
            return true;
        }

        // Count vowels (a, e, i, o, u, y)
        long vowelCount = cleaned.toLowerCase().chars()
                .filter(ch -> ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u' || ch == 'y')
                .count();

        double vowelRatio = (double) vowelCount / cleaned.length();

        // Standard English text has a vowel ratio between 0.25 and 0.55. Gibberish like "hfjkdwgfkdhfg" has < 0.15.
        if (cleaned.length() >= 8 && vowelRatio < 0.15) {
            return true;
        }

        return false;
    }
}
