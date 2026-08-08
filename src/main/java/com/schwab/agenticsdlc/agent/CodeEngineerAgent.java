package com.schwab.agenticsdlc.agent;

import com.schwab.agenticsdlc.llm.LlmClientManager;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.WorkspaceFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Code Engineer Agent — Two-Phase, Two-Wave Code Generation Orchestrator.
 *
 * <h2>Architecture</h2>
 *
 * <p>Implements a split-phase, two-wave synthesis strategy that eliminates JSON parse
 * failures and maximises LLM throughput:
 *
 * <h3>Phase 1 — File Manifest Generation (JSON only)</h3>
 * <p>Requests a lightweight JSON manifest (file paths, types, purposes — <em>no source code</em>)
 * from the LLM using {@code responseMimeType("application/json")}. Parsed into
 * {@link ManifestParser.ProjectManifest} / {@link ManifestParser.FileNode} DTOs.
 *
 * <h3>Phase 2 — Two-Wave Parallel Code Synthesis (Markdown only)</h3>
 * <p>Files are split into two execution waves:
 * <ul>
 *   <li><b>Wave 1 (Sequential)</b>: Contract / schema files ({@code POM}, {@code CONFIG},
 *       {@code INTERFACE}, {@code ENTITY}, {@code DTO}, {@code EXCEPTION}) are generated
 *       first in order. Their outputs are accumulated as bounded context for Wave 2.</li>
 *   <li><b>Wave 2 (Parallel)</b>: Implementation files ({@code SERVICE_IMPL},
 *       {@code CONTROLLER}, {@code REPOSITORY}, {@code TEST}, etc.) are generated
 *       concurrently using a bounded {@link ExecutorService} with
 *       {@value #PHASE2_PARALLELISM} threads.</li>
 * </ul>
 * <p>Each LLM call returns ONLY a raw code block wrapped in the correct Markdown fence
 * ({@code ```java}, {@code ```xml}, {@code ```yaml}, etc.). Never JSON.
 * {@link MarkdownCodeExtractor} strips the fence and the clean content is written to disk.
 */
public class CodeEngineerAgent {

    private static final Logger logger = LoggerFactory.getLogger(CodeEngineerAgent.class);

    /** Number of parallel threads for Wave 2 code synthesis. */
    private static final int PHASE2_PARALLELISM = 5;

    /**
     * File types treated as "contracts" — generated sequentially in Wave 1 and
     * accumulated as context for Wave 2.
     */
    private static final Set<String> WAVE1_TYPES = Set.of(
            "POM", "CONFIG", "INTERFACE", "SERVICE_INTERFACE",
            "ENTITY", "DTO", "EXCEPTION", "ENUM", "REPOSITORY", "MODEL"
    );

    private static final Set<String> WAVE3_TYPES = Set.of(
            "TEST", "UNIT_TEST", "INTEGRATION_TEST"
    );

    private final LlmClientManager llmClientManager;
    private final WorkspaceFileManager workspaceFileManager;

    public CodeEngineerAgent() {
        this(new LlmClientManager(), new WorkspaceFileManager());
    }

    public CodeEngineerAgent(LlmClientManager llmClientManager) {
        this(llmClientManager, new WorkspaceFileManager());
    }

    public CodeEngineerAgent(LlmClientManager llmClientManager, WorkspaceFileManager workspaceFileManager) {
        this.llmClientManager = llmClientManager;
        this.workspaceFileManager = workspaceFileManager;
    }

    // ─── Primary Entry Point ─────────────────────────────────────────────────

    /**
     * Orchestrates the 2-phase, 3-wave topological code synthesis pipeline with Active Symbol Graph.
     *
     * @param projectConfig     project metadata
     * @param requirementPrompt the user's requirement prompt
     */
    public void synthesizeCode(ProjectConfig projectConfig, String requirementPrompt) {
        logger.info("[CodeEngineerAgent]: Starting 2-Phase LLM code synthesis for project '{}' ({})...",
                projectConfig.getProjectName(), projectConfig.getProjectId());

        String planContent = loadPlanContent(projectConfig);

        // ── Phase 1: Manifest Generation ──────────────────────────────────
        logger.info("[CodeEngineerAgent PHASE-1]: Requesting file manifest from LLM...");
        ManifestParser.ProjectManifest manifest;
        try {
            String manifestJson = llmClientManager.generateFileManifest(projectConfig, planContent, requirementPrompt);
            manifest = ManifestParser.parseFromString(manifestJson);
            logger.info("[CodeEngineerAgent PHASE-1]: Manifest parsed — {} files declared.", manifest.getFiles().size());
        } catch (Exception e) {
            logger.error("[CodeEngineerAgent PHASE-1 FAILED]: Manifest generation failed: {}. Falling back to single-call synthesis.", e.getMessage());
            Map<String, String> files = llmClientManager.generateCodeFiles(projectConfig, planContent, requirementPrompt);
            applyFiles(projectConfig, files);
            return;
        }

        // ── Phase 2: Three-Wave Topological Synthesis with Active Symbol Graph ─
        List<ManifestParser.FileNode> allFiles = manifest.getFiles();

        List<ManifestParser.FileNode> wave1Files = new ArrayList<>();
        List<ManifestParser.FileNode> wave2Files = new ArrayList<>();
        List<ManifestParser.FileNode> wave3Files = new ArrayList<>();

        for (ManifestParser.FileNode fn : allFiles) {
            String type = fn.getFileType() != null ? fn.getFileType().toUpperCase() : "";
            if (WAVE1_TYPES.contains(type)) {
                wave1Files.add(fn);
            } else if (WAVE3_TYPES.contains(type) || fn.getPath().toLowerCase().contains("test")) {
                wave3Files.add(fn);
            } else {
                wave2Files.add(fn);
            }
        }

        logger.info("[CodeEngineerAgent PHASE-2 TOPOLOGICAL WAVES]: Wave 1 (Contracts/Schemas): {} files, Wave 2 (Implementations/Controllers): {} files, Wave 3 (Unit/Integration Tests): {} files.",
                wave1Files.size(), wave2Files.size(), wave3Files.size());

        // Active Workspace Symbol & Contract Graph
        Map<String, String> activeSymbolGraph = new ConcurrentHashMap<>();
        AtomicInteger synthesized = new AtomicInteger(0);
        int totalFiles = allFiles.size();

        // ── Wave 1: Sequential Contracts / Schemas / Models ────────────────
        logger.info("[CodeEngineerAgent WAVE-1]: Synthesizing {} contract/schema files sequentially...", wave1Files.size());
        for (int i = 0; i < wave1Files.size(); i++) {
            ManifestParser.FileNode fn = wave1Files.get(i);
            logger.info("[CodeEngineerAgent WAVE-1]: [{}/{}] Synthesizing '{}' ({})...",
                    i + 1, wave1Files.size(), fn.getPath(), fn.getFileType());

            try {
                String raw = llmClientManager.generateSingleFileCode(projectConfig, planContent, fn, activeSymbolGraph);
                String clean = MarkdownCodeExtractor.extractCode(raw);
                writeFile(projectConfig, fn.getPath(), clean);
                synthesized.incrementAndGet();

                // Accumulate generated source code into Active Symbol Graph
                activeSymbolGraph.put(fn.getPath(), truncateLines(clean, 80));
            } catch (Exception e) {
                logger.error("[CodeEngineerAgent WAVE-1]: Failed to synthesize '{}': {}. Skipping.",
                        fn.getPath(), e.getMessage());
            }
        }

        // ── Wave 2: Parallel Implementations & Controllers ────────────────
        if (!wave2Files.isEmpty()) {
            logger.info("[CodeEngineerAgent WAVE-2]: Synthesizing {} implementation files in parallel ({} threads)...",
                    wave2Files.size(), PHASE2_PARALLELISM);

            final Map<String, String> wave1SymbolSnapshot = Map.copyOf(activeSymbolGraph);

            ExecutorService executor = Executors.newFixedThreadPool(PHASE2_PARALLELISM,
                    r -> {
                        Thread t = new Thread(r, "CodeEngineer-Wave2");
                        t.setDaemon(true);
                        return t;
                    });

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < wave2Files.size(); i++) {
                final ManifestParser.FileNode fn = wave2Files.get(i);
                final int idx = i + 1;

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    logger.info("[CodeEngineerAgent WAVE-2]: [{}/{}] Synthesizing '{}' ({})...",
                            idx, wave2Files.size(), fn.getPath(), fn.getFileType());
                    try {
                        String raw = llmClientManager.generateSingleFileCode(
                                projectConfig, planContent, fn, wave1SymbolSnapshot);
                        String clean = MarkdownCodeExtractor.extractCode(raw);
                        writeFile(projectConfig, fn.getPath(), clean);
                        synthesized.incrementAndGet();
                        activeSymbolGraph.put(fn.getPath(), truncateLines(clean, 80));
                    } catch (Exception e) {
                        logger.error("[CodeEngineerAgent WAVE-2]: Failed to synthesize '{}': {}. Skipping.",
                                fn.getPath(), e.getMessage());
                    }
                }, executor);

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }

        // ── Wave 3: Parallel Unit & Integration Tests ──────────────────────
        if (!wave3Files.isEmpty()) {
            logger.info("[CodeEngineerAgent WAVE-3]: Synthesizing {} test files in parallel ({} threads)...",
                    wave3Files.size(), PHASE2_PARALLELISM);

            final Map<String, String> wave1And2SymbolSnapshot = Map.copyOf(activeSymbolGraph);

            ExecutorService executor = Executors.newFixedThreadPool(PHASE2_PARALLELISM,
                    r -> {
                        Thread t = new Thread(r, "CodeEngineer-Wave3");
                        t.setDaemon(true);
                        return t;
                    });

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < wave3Files.size(); i++) {
                final ManifestParser.FileNode fn = wave3Files.get(i);
                final int idx = i + 1;

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    logger.info("[CodeEngineerAgent WAVE-3]: [{}/{}] Synthesizing test '{}' ({})...",
                            idx, wave3Files.size(), fn.getPath(), fn.getFileType());
                    try {
                        String raw = llmClientManager.generateSingleFileCode(
                                projectConfig, planContent, fn, wave1And2SymbolSnapshot);
                        String clean = MarkdownCodeExtractor.extractCode(raw);
                        writeFile(projectConfig, fn.getPath(), clean);
                        synthesized.incrementAndGet();
                    } catch (Exception e) {
                        logger.error("[CodeEngineerAgent WAVE-3]: Failed to synthesize test '{}': {}. Skipping.",
                                fn.getPath(), e.getMessage());
                    }
                }, executor);

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }

        logger.info("[CodeEngineerAgent]: Topological synthesis complete — {}/{} files written to workspace '{}'.",
                synthesized.get(), totalFiles, projectConfig.getProjectPath());
    }

    // ─── Direct File Patch (Self-Healing) ────────────────────────────────────

    /**
     * Applies a pre-computed map of {@code filePath -> fileContent} directly to the workspace.
     * Used by the QA self-healing loop when the LLM returns targeted fix directives.
     *
     * @param projectConfig the project metadata
     * @param files         map of relative file path → file content
     */
    public void applyFiles(ProjectConfig projectConfig, Map<String, String> files) {
        Path projectRoot = Paths.get(projectConfig.getProjectPath());
        int written = 0;
        int skipped = 0;

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String relativePath = entry.getKey();
            String content = entry.getValue();

            if (relativePath == null || relativePath.isBlank() || content == null) {
                skipped++;
                continue;
            }

            // Guard against path traversal attempts
            Path target = projectRoot.resolve(relativePath).normalize();
            if (!target.startsWith(projectRoot)) {
                logger.warn("[CodeEngineerAgent]: Skipping suspicious path outside project root: {}", relativePath);
                skipped++;
                continue;
            }

            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, content, java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                        java.nio.file.StandardOpenOption.SYNC);
                logger.debug("[CodeEngineerAgent]: Wrote -> {}", target);
                written++;
            } catch (IOException e) {
                logger.error("[CodeEngineerAgent]: Failed to write '{}': {}", relativePath, e.getMessage());
                skipped++;
            }
        }

        logger.info("[CodeEngineerAgent]: applyFiles — wrote {}/{} files ({} skipped).",
                written, files.size(), skipped);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void writeFile(ProjectConfig projectConfig, String relativePath, String content) throws IOException {
        Path projectRoot = Paths.get(projectConfig.getProjectPath());
        Path target = projectRoot.resolve(relativePath).normalize();

        if (!target.startsWith(projectRoot)) {
            throw new SecurityException("Attempted path traversal: " + relativePath);
        }

        Files.createDirectories(target.getParent());
        Files.writeString(target, content, java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.SYNC);
        logger.debug("[CodeEngineerAgent]: Wrote -> {}", target);
    }

    private String loadPlanContent(ProjectConfig projectConfig) {
        Path planFile = Paths.get(projectConfig.getProjectPath()).resolve(".ai-plan/plan.md");
        if (Files.exists(planFile)) {
            try {
                return Files.readString(planFile);
            } catch (IOException e) {
                logger.warn("[CodeEngineerAgent]: Could not read plan at {}: {}", planFile, e.getMessage());
            }
        }
        return "";
    }

    /**
     * Truncates content to at most {@code maxLines} lines for use as bounded context.
     */
    private String truncateLines(String content, int maxLines) {
        String[] lines = content.split("\n");
        if (lines.length <= maxLines) return content;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }
}
