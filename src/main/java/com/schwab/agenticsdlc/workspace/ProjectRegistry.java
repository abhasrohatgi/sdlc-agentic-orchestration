package com.schwab.agenticsdlc.workspace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Master Workspace Project Registry.
 * Maintains a fast $O(1)$ centralized index file at workspaces/projects_registry.json with self-healing capabilities.
 */
public class ProjectRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ProjectRegistry.class);

    private final Path registryFilePath;
    private final Path rootWorkspacesDir;
    private final ObjectMapper objectMapper;
    private final List<ProjectConfig> projectsCache;

    public ProjectRegistry(Path rootWorkspacesDir) {
        this.rootWorkspacesDir = rootWorkspacesDir.toAbsolutePath().normalize();
        this.registryFilePath = this.rootWorkspacesDir.resolve("projects_registry.json").normalize();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.projectsCache = new ArrayList<>();

        loadOrRebuildRegistry();
    }

    public Path getRegistryFilePath() {
        return registryFilePath;
    }

    public synchronized List<ProjectConfig> getProjects() {
        return Collections.unmodifiableList(new ArrayList<>(projectsCache));
    }

    public synchronized Optional<ProjectConfig> getProjectById(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        return projectsCache.stream()
                .filter(p -> projectId.equalsIgnoreCase(p.getProjectId()))
                .findFirst();
    }

    public synchronized void registerProject(ProjectConfig config) throws IOException {
        // Remove existing entry if updating
        projectsCache.removeIf(p -> p.getProjectId().equalsIgnoreCase(config.getProjectId()));
        projectsCache.add(config);
        saveRegistry();
    }

    public synchronized void loadOrRebuildRegistry() {
        projectsCache.clear();
        if (Files.exists(registryFilePath) && Files.isRegularFile(registryFilePath)) {
            try {
                List<ProjectConfig> loaded = objectMapper.readValue(registryFilePath.toFile(), new TypeReference<List<ProjectConfig>>() {});
                if (loaded != null) {
                    boolean modified = loaded.removeIf(p -> p.getProjectPath() == null || !Files.exists(Path.of(p.getProjectPath())));
                    projectsCache.addAll(loaded);
                    if (modified) {
                        saveRegistry();
                    }
                    return;
                }
            } catch (IOException e) {
                logger.warn("Failed to read master registry at {}. Rebuilding index...", registryFilePath);
            }
        }

        // Self-healing fallback: Scan subdirectories and rebuild master registry index
        rebuildRegistry();
    }

    public synchronized void rebuildRegistry() {
        projectsCache.clear();
        File rootDir = rootWorkspacesDir.toFile();
        File[] subDirs = rootDir.listFiles(File::isDirectory);

        if (subDirs != null) {
            for (File dir : subDirs) {
                File configFile = new File(dir, "project_config.json");
                if (configFile.exists() && configFile.isFile()) {
                    try {
                        ProjectConfig config = objectMapper.readValue(configFile, ProjectConfig.class);
                        projectsCache.add(config);
                    } catch (IOException e) {
                        logger.warn("Failed to parse project config at {}: {}", configFile, e.getMessage());
                    }
                }
            }
        }

        try {
            saveRegistry();
        } catch (IOException e) {
            logger.warn("Failed to persist rebuilt registry to {}: {}", registryFilePath, e.getMessage());
        }
    }

    private void saveRegistry() throws IOException {
        if (!Files.exists(rootWorkspacesDir)) {
            Files.createDirectories(rootWorkspacesDir);
        }
        objectMapper.writeValue(registryFilePath.toFile(), projectsCache);
    }
}
