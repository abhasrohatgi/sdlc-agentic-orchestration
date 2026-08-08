package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.agent.CodeEngineerAgent;
import com.schwab.agenticsdlc.agent.PlanGeneratorAgent;
import com.schwab.agenticsdlc.engine.OrchestratorEngine;
import com.schwab.agenticsdlc.workspace.LanguageStack;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.ProjectType;
import com.schwab.agenticsdlc.workspace.WorkspaceFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class AgenticSdlcPipelineTest {

    @TempDir
    Path tempWorkspacesDir;

    private WorkspaceFileManager fileManager;

    @BeforeEach
    void setUp() {
        fileManager = new WorkspaceFileManager(tempWorkspacesDir);
    }

    @Test
    @DisplayName("Verify Default AGENTS.md creation inside new project workspace")
    void testDefaultAgentsMdCreation() throws IOException {
        ProjectConfig config = fileManager.createProject("Gov App", "Governance project", LanguageStack.JAVA, ProjectType.CLI_TOOL, "Build governance app");

        Path agentsMdPath = tempWorkspacesDir.resolve("gov-app/AGENTS.md");
        assertTrue(Files.exists(agentsMdPath), "AGENTS.md must exist by default at project root");

        String content = Files.readString(agentsMdPath);
        assertTrue(content.contains("Project Behavioral Protocol & Guidelines"));
        assertTrue(content.contains("Mandatory Plan-First & Human Approval Protocol"));
    }

    @Test
    @DisplayName("Verify OrchestratorEngine aborts when human operator rejects plan")
    void testOrchestratorEngineEndToEndRejected() throws IOException {
        ProjectConfig config = fileManager.createProject("Rejected Service", "Rejection test", LanguageStack.JAVA, ProjectType.CUSTOM, "Build a rejected service");

        Scanner scanner = new Scanner(new ByteArrayInputStream("n\n".getBytes()));

        OrchestratorEngine engine = new OrchestratorEngine(scanner);
        boolean result = engine.executeOrchestrationPipeline(config, "Build a rejected service");

        assertFalse(result, "Pipeline execution must return false when human operator rejects plan");
    }
}
