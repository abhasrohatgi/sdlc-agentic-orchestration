package com.schwab.agenticsdlc.workspace;

import com.schwab.agenticsdlc.cli.CliMenuNavigator;
import com.schwab.agenticsdlc.cli.CliRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive Terminal CLI Workspace Manager.
 * Prompts the user to select an existing project or create a new Java project workspace.
 */
public class CliWorkspaceManager {

    private static final Logger logger = LoggerFactory.getLogger(CliWorkspaceManager.class);

    private final WorkspaceFileManager fileManager;
    private final Scanner scanner;

    public CliWorkspaceManager() {
        this(new WorkspaceFileManager(), new Scanner(System.in));
    }

    public CliWorkspaceManager(WorkspaceFileManager fileManager, Scanner scanner) {
        this.fileManager = fileManager;
        this.scanner = scanner;
    }

    public ProjectConfig runInteractiveMenu() {
        CliMenuNavigator menuNav = new CliMenuNavigator(scanner);
        List<CliMenuNavigator.MenuItem> options = List.of(
                new CliMenuNavigator.MenuItem("Select existing project", "Open a project workspace from workspaces/"),
                new CliMenuNavigator.MenuItem("Create new project", "Bootstrap a new Java Spring Boot workspace"),
                new CliMenuNavigator.MenuItem("Exit", "Close AgenticSDLC CLI")
        );

        int choice = menuNav.selectOption("AgenticSDLC — Workspace Manager", options, 0);
        switch (choice) {
            case 0 -> {
                return handleSelectExistingProject();
            }
            case 1 -> {
                return handleCreateNewProject();
            }
            default -> {
                CliRenderer.info("Goodbye!");
                return null;
            }
        }
    }

    public ProjectConfig handleSelectExistingProject() {
        List<ProjectConfig> projects = fileManager.discoverProjects();

        if (projects.isEmpty()) {
            CliRenderer.warn("No existing projects found in " + fileManager.getRootWorkspacesDir());
            CliRenderer.printPrompt("Create a new project instead? (y/n):");
            String choice = scanner.nextLine().trim();
            return ("y".equalsIgnoreCase(choice) || "yes".equalsIgnoreCase(choice))
                    ? handleCreateNewProject() : null;
        }

        List<CliMenuNavigator.MenuItem> items = projects.stream()
                .map(p -> new CliMenuNavigator.MenuItem(
                        p.getProjectName() + " (" + p.getProjectId() + ")",
                        p.getLanguageStack() + " • " + p.getProjectType() + " • " + p.getScenarioType() + " • " + p.getProjectPath()
                ))
                .toList();

        CliMenuNavigator menuNav = new CliMenuNavigator(scanner);
        int selected = menuNav.selectOption("Discovered Workspace Projects", items, 0);
        if (selected >= 0 && selected < projects.size()) {
            ProjectConfig selectedConfig = projects.get(selected);
            CliRenderer.success("Selected → " + selectedConfig.getProjectName() + " (" + selectedConfig.getProjectId() + ")");
            return selectedConfig;
        }
        return null;
    }

    public ProjectConfig handleCreateNewProject() {
        CliRenderer.printHeader("Create New Project Workspace");
        System.out.println();

        // Project name
        CliRenderer.printPrompt("Project name (e.g. URL Shortener, Analytics Service):");
        String name = scanner.nextLine().trim();
        while (name.isBlank()) {
            CliRenderer.warn("Project name cannot be empty.");
            CliRenderer.printPrompt("Project name:");
            name = scanner.nextLine().trim();
        }

        // Requirement / description
        CliRenderer.printPrompt("Requirement / description (e.g. Build a REST URL Shortener with Base62):");
        String description = scanner.nextLine().trim();
        if (description.isBlank()) {
            description = "AgenticSDLC managed software project";
        }

        LanguageStack languageStack = LanguageStack.JAVA;
        ProjectType projectType = ProjectType.CUSTOM;

        try {
            ProjectConfig config = fileManager.createProject(name, description, languageStack, projectType, description);

            CliRenderer.printSuccess(
                    "Project workspace created!",
                    config.getProjectId() + "  •  " + config.getLanguageStack()
            );
            CliRenderer.bullet("Location:", config.getProjectPath());
            CliRenderer.bullet("Scenario:", config.getScenarioType().toString());

            return config;
        } catch (IOException e) {
            CliRenderer.error("Failed to create project workspace: " + e.getMessage());
            logger.error("[CliWorkspaceManager]: Error creating project workspace: {}", e.getMessage(), e);
            return null;
        }
    }
}
