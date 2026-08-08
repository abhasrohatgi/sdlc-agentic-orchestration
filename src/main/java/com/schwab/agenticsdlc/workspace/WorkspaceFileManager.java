package com.schwab.agenticsdlc.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Sandboxed Workspace File Manager.
 * Manages multi-language project workspace creation, fast $O(1)$ master registry discovery in /workspaces, and path traversal security.
 */
public class WorkspaceFileManager {

    private final Path rootWorkspacesDir;
    private final ObjectMapper objectMapper;
    private final ScenarioDetector scenarioDetector;
    private final ProjectRegistry projectRegistry;

    public WorkspaceFileManager() {
        this(Paths.get("workspaces"));
    }

    public WorkspaceFileManager(Path rootWorkspacesDir) {
        this.rootWorkspacesDir = rootWorkspacesDir.toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.scenarioDetector = new ScenarioDetector();

        ensureRootDirectoryExists();
        this.projectRegistry = new ProjectRegistry(this.rootWorkspacesDir);
    }

    public Path getRootWorkspacesDir() {
        return rootWorkspacesDir;
    }

    public ProjectRegistry getProjectRegistry() {
        return projectRegistry;
    }

    private void ensureRootDirectoryExists() {
        try {
            if (!Files.exists(rootWorkspacesDir)) {
                Files.createDirectories(rootWorkspacesDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize root workspaces directory at " + rootWorkspacesDir, e);
        }
    }

    /**
     * Fast $O(1)$ discovery of all registered AgenticSDLC project workspaces via master registry file (projects_registry.json).
     */
    public List<ProjectConfig> discoverProjects() {
        return projectRegistry.getProjects();
    }

    /**
     * Overloaded backward-compatible project creation method.
     */
    public ProjectConfig createProject(String projectName, String description, ScenarioType scenarioType) throws IOException {
        return createProject(projectName, description, LanguageStack.JAVA, ProjectType.MICROSERVICE, scenarioType, null);
    }

    /**
     * Full project creation method supporting multi-language stack, project type, and scenario auto-detection.
     */
    public ProjectConfig createProject(String projectName, String description,
                                        LanguageStack languageStack, ProjectType projectType,
                                        String promptInput) throws IOException {
        return createProject(projectName, description, languageStack, projectType, null, promptInput);
    }

    private ProjectConfig createProject(String projectName, String description,
                                         LanguageStack languageStack, ProjectType projectType,
                                         ScenarioType explicitScenario, String promptInput) throws IOException {
        String projectId = slugify(projectName);
        Path projectDirPath = rootWorkspacesDir.resolve(projectId).normalize();

        // 1. Initialize language stack directory structure
        initializeLanguageDirectoryLayout(projectDirPath, languageStack);

        // 2. Auto-detect scenario if explicit scenario is not provided
        ScenarioType finalScenario = explicitScenario != null
                ? explicitScenario
                : scenarioDetector.detectScenario(projectDirPath, promptInput != null ? promptInput : description);

        // 3. Build ProjectConfig metadata
        ProjectConfig config = new ProjectConfig(
                projectId,
                projectName,
                description,
                languageStack != null ? languageStack : LanguageStack.JAVA,
                projectType != null ? projectType : ProjectType.CUSTOM,
                finalScenario,
                projectDirPath.toString()
        );

        // 4. Save isolated project_config.json
        Path configFilePath = projectDirPath.resolve("project_config.json");
        objectMapper.writeValue(configFilePath.toFile(), config);

        // 5. Save default AGENTS.md governance file at project root
        String agentsMdContent = DefaultAgentsMdTemplate.getTemplateContent(projectName, config.getLanguageStack(), config.getProjectType());
        Files.writeString(projectDirPath.resolve("AGENTS.md"), agentsMdContent,
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.SYNC);

        // 6. Update master index at workspaces/projects_registry.json
        projectRegistry.registerProject(config);

        return config;
    }

    private void initializeLanguageDirectoryLayout(Path projectDirPath, LanguageStack languageStack) throws IOException {
        Files.createDirectories(projectDirPath.resolve("src/main/java"));
        Files.createDirectories(projectDirPath.resolve("src/test/java"));
        Files.createDirectories(projectDirPath.resolve("src/main/resources"));
    }

    /**
     * Path traversal security guardrail.
     * Guarantees that any child file path stays strictly within the target workspace directory.
     */
    public Path sanitizePath(Path workspaceDir, String relativePath) {
        Path targetPath = workspaceDir.resolve(relativePath).normalize();
        if (!targetPath.startsWith(workspaceDir.normalize())) {
            throw new SecurityException("Path traversal violation detected: " + relativePath + " escapes workspace " + workspaceDir);
        }
        return targetPath;
    }

    /**
     * Writes content to a file at the specified path.
     *
     * @param filePath The relative path of the file to write.
     * @param content  The content to write to the file.
     * @throws IOException If an I/O error occurs.
     */
    public void writeFile(String filePath, String content) throws IOException {
        Path fullPath = rootWorkspacesDir.resolve(filePath).normalize();
        Files.createDirectories(fullPath.getParent()); // Ensure parent directories exist
        Files.writeString(fullPath, content,
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.SYNC);
    }

    private String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "project-" + System.currentTimeMillis();
        }
        return text.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
