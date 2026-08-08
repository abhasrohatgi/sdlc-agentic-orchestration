package com.schwab.agenticsdlc.telemetry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Step-by-Step Agent Execution Trajectory Logger for Enterprise Observability.
 *
 * <p>Persists a detailed, chronological JSON log of every agent action, decision, timing,
 * and diagnostic reasoning to {@code workspaces/<project_id>/execution_trajectory.json}.</p>
 */
public class ExecutionTrajectoryLogger {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionTrajectoryLogger.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final ProjectConfig projectConfig;
    private final String startTime;
    private final List<TrajectoryStep> steps = new ArrayList<>();
    private String finalStatus = "RUNNING";

    public ExecutionTrajectoryLogger(ProjectConfig projectConfig) {
        this.projectConfig = projectConfig;
        this.startTime = Instant.now().toString();
        recordStep("PIPELINE_INIT", "OrchestratorEngine", "STARTED", "Initialized orchestration pipeline for project '" + projectConfig.getProjectId() + "'", null, null);
    }

    public synchronized void recordStep(String stage, String agent, String status, String details, String diagnosticReasoning, List<String> filesModified) {
        TrajectoryStep step = new TrajectoryStep();
        step.setStepIndex(steps.size() + 1);
        step.setStage(stage);
        step.setAgent(agent);
        step.setStatus(status);
        step.setTimestamp(Instant.now().toString());
        step.setDetails(details);
        step.setDiagnosticReasoning(diagnosticReasoning);
        step.setFilesModified(filesModified);

        steps.add(step);
        flushTrajectoryFile();
    }

    public synchronized void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
        recordStep("PIPELINE_COMPLETE", "OrchestratorEngine", finalStatus, "Orchestration pipeline finished with status: " + finalStatus, null, null);
        flushTrajectoryFile();
    }

    private void flushTrajectoryFile() {
        if (projectConfig == null || projectConfig.getProjectPath() == null) {
            return;
        }

        try {
            Path projectDir = Paths.get(projectConfig.getProjectPath());
            Path trajectoryFile = projectDir.resolve("execution_trajectory.json");

            TrajectoryReport report = new TrajectoryStepReport(
                    projectConfig.getProjectId(),
                    projectConfig.getProjectName(),
                    projectConfig.getLanguageStack().toString(),
                    projectConfig.getScenarioType() != null ? projectConfig.getScenarioType().toString() : "GREENFIELD",
                    startTime,
                    Instant.now().toString(),
                    finalStatus,
                    new ArrayList<>(steps)
            );

            String json = objectMapper.writeValueAsString(report);
            Files.createDirectories(trajectoryFile.getParent());
            Files.writeString(trajectoryFile, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);

            logger.debug("[ExecutionTrajectoryLogger]: Flushed execution_trajectory.json -> {}", trajectoryFile);
        } catch (IOException e) {
            logger.warn("[ExecutionTrajectoryLogger WARNING]: Failed to write trajectory log: {}", e.getMessage());
        }
    }

    public static class TrajectoryStep {
        private int stepIndex;
        private String stage;
        private String agent;
        private String status;
        private String timestamp;
        private String details;
        private String diagnosticReasoning;
        private List<String> filesModified;

        public int getStepIndex() { return stepIndex; }
        public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }

        public String getStage() { return stage; }
        public void setStage(String stage) { this.stage = stage; }

        public String getAgent() { return agent; }
        public void setAgent(String agent) { this.agent = agent; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public String getDiagnosticReasoning() { return diagnosticReasoning; }
        public void setDiagnosticReasoning(String diagnosticReasoning) { this.diagnosticReasoning = diagnosticReasoning; }

        public List<String> getFilesModified() { return filesModified; }
        public void setFilesModified(List<String> filesModified) { this.filesModified = filesModified; }
    }

    public interface TrajectoryReport {}

    public static class TrajectoryStepReport implements TrajectoryReport {
        private final String projectId;
        private final String projectName;
        private final String languageStack;
        private final String scenarioType;
        private final String startTime;
        private final String endTime;
        private final String finalStatus;
        private final List<TrajectoryStep> steps;

        public TrajectoryStepReport(String projectId, String projectName, String languageStack, String scenarioType,
                                    String startTime, String endTime, String finalStatus, List<TrajectoryStep> steps) {
            this.projectId = projectId;
            this.projectName = projectName;
            this.languageStack = languageStack;
            this.scenarioType = scenarioType;
            this.startTime = startTime;
            this.endTime = endTime;
            this.finalStatus = finalStatus;
            this.steps = steps;
        }

        public String getProjectId() { return projectId; }
        public String getProjectName() { return projectName; }
        public String getLanguageStack() { return languageStack; }
        public String getScenarioType() { return scenarioType; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public String getFinalStatus() { return finalStatus; }
        public List<TrajectoryStep> getSteps() { return steps; }
    }
}
