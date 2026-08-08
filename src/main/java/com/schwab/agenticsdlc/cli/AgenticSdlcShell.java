package com.schwab.agenticsdlc.cli;

import com.schwab.agenticsdlc.engine.OrchestratorEngine;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.WorkspaceFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * Antigravity-Style Modern Slash-Command Interactive Shell (REPL).
 * Supports persistent slash commands (/plan, /run, /status, /diff, /help, /exit)
 * without requiring JVM restart between SDLC workflow executions.
 */
public class AgenticSdlcShell {

    private static final Logger logger = LoggerFactory.getLogger(AgenticSdlcShell.class);

    private final Scanner scanner;
    private final WorkspaceFileManager workspaceFileManager;
    private final CliMenuNavigator menuNavigator;
    private ProjectConfig activeProject;

    public AgenticSdlcShell(Scanner scanner, WorkspaceFileManager workspaceFileManager, ProjectConfig activeProject) {
        this.scanner = scanner;
        this.workspaceFileManager = workspaceFileManager;
        this.activeProject = activeProject;
        this.menuNavigator = new CliMenuNavigator(scanner);
    }

    public void runShell() {
        CliRenderer.printHeader("AgenticSDLC Interactive Shell");
        System.out.println(CliRenderer.BRIGHT_BLACK + "  Type " + CliRenderer.BRIGHT_CYAN + "/help" + CliRenderer.BRIGHT_BLACK + " for command catalog or " + CliRenderer.BRIGHT_CYAN + "/run" + CliRenderer.BRIGHT_BLACK + " to start multi-agent pipeline." + CliRenderer.RESET);
        System.out.println();

        boolean running = true;
        while (running) {
            String promptLabel = activeProject != null
                    ? "agentic [" + activeProject.getProjectId() + "]> "
                    : "agentic> ";

            System.out.print(CliRenderer.BRIGHT_CYAN + CliRenderer.BOLD + promptLabel + CliRenderer.RESET);
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isBlank()) {
                continue;
            }

            String cmd = input.split("\\s+")[0].toLowerCase();
            switch (cmd) {
                case "/help", "help" -> printHelp();
                case "/plan", "plan" -> showPlan();
                case "/status", "status" -> showStatus();
                case "/diff", "diff" -> showDiff();
                case "/run", "run" -> executeRun(input);
                case "/exit", "exit", "quit" -> {
                    CliRenderer.info("Exiting AgenticSDLC Interactive Shell. Goodbye!");
                    running = false;
                }
                default -> {
                    if (input.startsWith("/")) {
                        CliRenderer.warn("Unknown slash command: '" + cmd + "'. Type /help for available commands.");
                    } else {
                        // Treat direct text entry as requirement prompt to run pipeline
                        executeRun("/run " + input);
                    }
                }
            }
        }
    }

    private void printHelp() {
        System.out.println();
        CliRenderer.printHeader("AgenticSDLC Slash Command Catalog");
        CliRenderer.bullet("/run [prompt]", "Executes multi-agent SDLC pipeline on active workspace");
        CliRenderer.bullet("/plan", "Inspects current architectural plan (.ai-plan/plan.md)");
        CliRenderer.bullet("/status", "Displays active project metadata, scenario, and audit history");
        CliRenderer.bullet("/diff", "Displays workspace file patch diffs applied by agents");
        CliRenderer.bullet("/help", "Displays this slash command catalog");
        CliRenderer.bullet("/exit", "Exits interactive shell");
        System.out.println();
    }

    private void showPlan() {
        if (activeProject == null) {
            CliRenderer.warn("No active project workspace selected.");
            return;
        }
        Path planFile = Paths.get(activeProject.getProjectPath()).resolve(".ai-plan/plan.md");
        if (Files.exists(planFile)) {
            try {
                String content = Files.readString(planFile);
                CliRenderer.printHeader("Architectural Plan (.ai-plan/plan.md)");
                System.out.println(CliRenderer.formatMarkdown(content));
                System.out.println();
            } catch (Exception e) {
                CliRenderer.error("Could not read plan file: " + e.getMessage());
            }
        } else {
            CliRenderer.warn("No architectural plan found at " + planFile);
        }
    }

    private void showStatus() {
        if (activeProject == null) {
            CliRenderer.warn("No active project workspace selected.");
            return;
        }
        CliRenderer.printProjectBox(
                activeProject.getProjectName(),
                activeProject.getProjectId(),
                activeProject.getLanguageStack().toString(),
                activeProject.getProjectType().toString(),
                activeProject.getScenarioType().toString(),
                activeProject.getProjectPath()
        );
        Path auditFile = Paths.get(activeProject.getProjectPath()).resolve("audit_log.json");
        if (Files.exists(auditFile)) {
            try {
                String auditJson = Files.readString(auditFile);
                CliRenderer.info("Recent Audit Telemetry (audit_log.json):");
                for (String line : auditJson.split("\n")) {
                    System.out.println("    " + CliRenderer.BRIGHT_BLACK + line + CliRenderer.RESET);
                }
            } catch (Exception ignored) {}
        }
    }

    private void showDiff() {
        if (activeProject == null) {
            CliRenderer.warn("No active project workspace selected.");
            return;
        }
        Path planFile = Paths.get(activeProject.getProjectPath()).resolve(".ai-plan/plan.md");
        if (Files.exists(planFile)) {
            try {
                String content = Files.readString(planFile);
                if (content.contains("[SELF-HEALING REPAIR LOG]")) {
                    String diffSection = content.substring(content.indexOf("[SELF-HEALING REPAIR LOG]"));
                    CliRenderer.printDiffPreview(".ai-plan/plan.md", diffSection);
                    return;
                }
            } catch (Exception ignored) {}
        }
        CliRenderer.info("No active diffs recorded in plan log.");
    }

    private void executeRun(String input) {
        if (activeProject == null) {
            CliRenderer.warn("No active project workspace selected.");
            return;
        }

        String requirementPrompt = input.length() > 4 ? input.substring(4).trim() : "";
        if (requirementPrompt.isBlank()) {
            requirementPrompt = activeProject.getDescription();
        }

        System.out.println();
        CliRenderer.info("Executing SDLC Pipeline with requirement: \"" + requirementPrompt + "\"");
        
        OrchestratorEngine engine = new OrchestratorEngine(scanner);
        boolean success = engine.executeOrchestrationPipeline(activeProject, requirementPrompt);

        if (success) {
            CliRenderer.printPipelineSuccess(activeProject.getProjectName(), activeProject.getProjectPath());
        } else {
            CliRenderer.printPipelineFailed("Pipeline execution failed or was rejected by operator.");
        }
    }
}
