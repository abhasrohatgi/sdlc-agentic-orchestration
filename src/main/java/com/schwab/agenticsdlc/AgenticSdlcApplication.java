package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.cli.CliRenderer;
import com.schwab.agenticsdlc.engine.OrchestratorEngine;
import com.schwab.agenticsdlc.llm.LlmClientManager;
import com.schwab.agenticsdlc.workspace.CliWorkspaceManager;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * AgenticSDLC Main CLI Entry Point.
 */
public class AgenticSdlcApplication {

    private static final Logger logger = LoggerFactory.getLogger(AgenticSdlcApplication.class);

    private static final String[] PIPELINE_STAGES = {"PLAN", "REVIEW", "CODEGEN", "BUILD", "DONE"};

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // ── Banner ────────────────────────────────────────────────────────
        CliRenderer.printBanner();

        // ── LLM Health Check ──────────────────────────────────────────────
        LlmClientManager llmClientManager = new LlmClientManager();
        CliRenderer.info("Verifying LLM connection...");
        boolean llmAvailable = llmClientManager.verifyLlmConnection();

        if (!llmAvailable) {
            CliRenderer.printLlmCheckFailed();
            logger.error("[GATE_LLM_HEALTH_CHECK FAILED]: Execution ABORTED due to unverified LLM API access.");
            return;
        }
        CliRenderer.printLlmCheckPassed();

        // ── Workspace Manager ─────────────────────────────────────────────
        CliWorkspaceManager workspaceManager = new CliWorkspaceManager(
                new com.schwab.agenticsdlc.workspace.WorkspaceFileManager(), scanner);
        ProjectConfig activeProject = workspaceManager.runInteractiveMenu();

        if (activeProject == null) {
            CliRenderer.info("No active project workspace selected. Exiting.");
            return;
        }

        // ── Active Project Box ────────────────────────────────────────────
        CliRenderer.printProjectBox(
                activeProject.getProjectName(),
                activeProject.getProjectId(),
                activeProject.getLanguageStack().toString(),
                activeProject.getProjectType().toString(),
                activeProject.getScenarioType().toString(),
                activeProject.getProjectPath()
        );

        // ── Launch Antigravity-Style Interactive REPL Shell ───────────────
        com.schwab.agenticsdlc.cli.AgenticSdlcShell shell = new com.schwab.agenticsdlc.cli.AgenticSdlcShell(
                scanner,
                new com.schwab.agenticsdlc.workspace.WorkspaceFileManager(),
                activeProject
        );
        shell.runShell();

        scanner.close();
    }
}
