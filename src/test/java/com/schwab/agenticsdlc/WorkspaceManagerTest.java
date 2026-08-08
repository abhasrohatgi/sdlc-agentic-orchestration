package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.workspace.CliWorkspaceManager;
import com.schwab.agenticsdlc.workspace.LanguageStack;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.ProjectRegistry;
import com.schwab.agenticsdlc.workspace.ProjectType;
import com.schwab.agenticsdlc.workspace.ScenarioDetector;
import com.schwab.agenticsdlc.workspace.ScenarioType;
import com.schwab.agenticsdlc.workspace.WorkspaceFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceManagerTest {

    @TempDir
    Path tempWorkspacesDir;

    private WorkspaceFileManager fileManager;
    private ScenarioDetector scenarioDetector;

    @BeforeEach
    void setUp() {
        fileManager = new WorkspaceFileManager(tempWorkspacesDir);
        scenarioDetector = new ScenarioDetector();
    }

    @Test
    @DisplayName("Verify Master Registry File (projects_registry.json) creation and O(1) discovery")
    void testMasterRegistryFileCreation() throws IOException {
        ProjectConfig config = fileManager.createProject("Master Test App", "Testing master registry", LanguageStack.JAVA, ProjectType.MICROSERVICE, "Build a master test app");

        Path registryFile = tempWorkspacesDir.resolve("projects_registry.json");
        assertTrue(Files.exists(registryFile), "Master registry file projects_registry.json must exist at root of workspaces/");

        ProjectRegistry registry = fileManager.getProjectRegistry();
        List<ProjectConfig> projects = registry.getProjects();
        assertEquals(1, projects.size());
        assertEquals("master-test-app", projects.get(0).getProjectId());
    }

    @Test
    @DisplayName("Verify ProjectRegistry Self-Healing Index Rebuild capability")
    void testMasterRegistrySelfHealing() throws IOException {
        fileManager.createProject("App Alpha", "First app", LanguageStack.JAVA, ProjectType.CLI_TOOL, "Build app alpha");
        fileManager.createProject("App Beta", "Second app", LanguageStack.JAVA, ProjectType.MICROSERVICE, "Build app beta");

        Path registryFile = tempWorkspacesDir.resolve("projects_registry.json");
        assertTrue(Files.exists(registryFile));

        // Delete master registry file to simulate corruption / accidental deletion
        Files.delete(registryFile);
        assertFalse(Files.exists(registryFile));

        // Trigger self-healing rebuild
        ProjectRegistry registry = new ProjectRegistry(tempWorkspacesDir);
        assertTrue(Files.exists(registryFile), "Master registry file must be automatically rebuilt by self-healing logic");

        List<ProjectConfig> rebuiltProjects = registry.getProjects();
        assertEquals(2, rebuiltProjects.size());
    }

    @Test
    @DisplayName("Verify Java project workspace layout generation")
    void testJavaProjectLayout() throws IOException {
        ProjectConfig config = fileManager.createProject("Java Service", "Build a high performance REST microservice in Java", LanguageStack.JAVA, ProjectType.MICROSERVICE, "Build a REST microservice in Java");

        assertNotNull(config);
        assertEquals(LanguageStack.JAVA, config.getLanguageStack());
        assertEquals(ProjectType.MICROSERVICE, config.getProjectType());
        assertEquals(ScenarioType.GREENFIELD, config.getScenarioType());

        Path projectPath = tempWorkspacesDir.resolve("java-service");
        assertTrue(Files.isDirectory(projectPath.resolve("src/main/java")));
        assertTrue(Files.isDirectory(projectPath.resolve("src/test/java")));
    }

    @Test
    @DisplayName("Verify Automated Scenario Detection - BROWNFIELD when existing code is present")
    void testScenarioDetectionBrownfield() throws IOException {
        Path existingProjectDir = tempWorkspacesDir.resolve("existing-java-app");
        Files.createDirectories(existingProjectDir.resolve("src/main/java"));
        Files.writeString(existingProjectDir.resolve("pom.xml"), "<project></project>\n");
        Files.writeString(existingProjectDir.resolve("src/main/java/App.java"), "public class App {}\n");

        ScenarioType detected = scenarioDetector.detectScenario(existingProjectDir, "Build an endpoint");
        assertEquals(ScenarioType.BROWNFIELD, detected);
    }

    @Test
    @DisplayName("Verify Automated Scenario Detection - AMBIGUOUS when prompt is underspecified")
    void testScenarioDetectionAmbiguous() {
        ScenarioType detected = scenarioDetector.detectScenario(tempWorkspacesDir, "short prompt");
        assertEquals(ScenarioType.AMBIGUOUS, detected);
    }

    @Test
    @DisplayName("Verify Automated Scenario Detection - GREENFIELD for clean workspace with explicit requirements")
    void testScenarioDetectionGreenfield() {
        ScenarioType detected = scenarioDetector.detectScenario(tempWorkspacesDir, "Build a full REST microservice for URL shortener using Base62 encoding");
        assertEquals(ScenarioType.GREENFIELD, detected);
    }

    @Test
    @DisplayName("Verify Path Traversal Sanitization Security Guardrail")
    void testPathSanitizerGuardrail() throws IOException {
        ProjectConfig config = fileManager.createProject("Secure App", "Security test", LanguageStack.JAVA, ProjectType.CUSTOM, "Build a secure app");
        Path workspaceDir = Paths.get(config.getProjectPath());

        Path validPath = fileManager.sanitizePath(workspaceDir, "src/main/java/App.java");
        assertNotNull(validPath);

        assertThrows(SecurityException.class, () -> {
            fileManager.sanitizePath(workspaceDir, "../../etc/passwd");
        });
    }

    @Test
    @DisplayName("Verify Interactive CLI Menu project creation flow with Java Stack")
    void testCliWorkspaceManagerCreateFlow() {
        String simulatedInput = "2\nJava Web App\nBuild a Web App in Java\n";
        Scanner simulatedScanner = new Scanner(new ByteArrayInputStream(simulatedInput.getBytes()));

        CliWorkspaceManager cliManager = new CliWorkspaceManager(fileManager, simulatedScanner);
        ProjectConfig config = cliManager.runInteractiveMenu();

        assertNotNull(config);
        assertEquals("java-web-app", config.getProjectId());
        assertEquals(LanguageStack.JAVA, config.getLanguageStack());
        assertEquals(ScenarioType.GREENFIELD, config.getScenarioType());
    }
}
