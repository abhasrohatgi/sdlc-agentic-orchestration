package com.schwab.agenticsdlc.workspace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Metadata configuration for an AgenticSDLC managed workspace project.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectConfig {
    private String projectId;
    private String projectName;
    private String description;
    private LanguageStack languageStack;
    private ProjectType projectType;
    private ScenarioType scenarioType;
    private String createdAt;
    private String projectPath;

    public ProjectConfig() {
    }

    public ProjectConfig(String projectId, String projectName, String description,
                         LanguageStack languageStack, ProjectType projectType,
                         ScenarioType scenarioType, String projectPath) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.description = description;
        this.languageStack = languageStack != null ? languageStack : LanguageStack.JAVA;
        this.projectType = projectType != null ? projectType : ProjectType.CUSTOM;
        this.scenarioType = scenarioType != null ? scenarioType : ScenarioType.GREENFIELD;
        this.createdAt = Instant.now().toString();
        this.projectPath = projectPath;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LanguageStack getLanguageStack() {
        return languageStack;
    }

    public void setLanguageStack(LanguageStack languageStack) {
        this.languageStack = languageStack;
    }

    public ProjectType getProjectType() {
        return projectType;
    }

    public void setProjectType(ProjectType projectType) {
        this.projectType = projectType;
    }

    public ScenarioType getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(ScenarioType scenarioType) {
        this.scenarioType = scenarioType;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (Stack: %s, Type: %s, Scenario: %s) - %s (Location: %s)",
                projectId, projectName, languageStack, projectType, scenarioType, description, projectPath);
    }
}
