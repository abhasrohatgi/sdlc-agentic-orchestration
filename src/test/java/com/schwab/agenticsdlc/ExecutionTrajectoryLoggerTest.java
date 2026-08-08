package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.telemetry.ExecutionTrajectoryLogger;
import com.schwab.agenticsdlc.workspace.LanguageStack;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.ProjectType;
import com.schwab.agenticsdlc.workspace.ScenarioType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExecutionTrajectoryLoggerTest {

    @Test
    public void testTrajectoryLoggingFlushesJsonFile(@TempDir Path tempDir) throws IOException {
        ProjectConfig config = new ProjectConfig(
                "test-traj-proj",
                "Test Trajectory Project",
                "com.test",
                LanguageStack.JAVA,
                ProjectType.CUSTOM,
                ScenarioType.GREENFIELD,
                tempDir.toString()
        );

        ExecutionTrajectoryLogger logger = new ExecutionTrajectoryLogger(config);
        logger.recordStep("STAGE_1_PLAN", "PlanGeneratorAgent", "COMPLETED", "Generated plan.md", null, List.of(".ai-plan/plan.md"));
        logger.recordStep("STAGE_2_GOVERNANCE", "HumanGatekeeperAgent", "APPROVED", "Approved by human", null, null);
        logger.setFinalStatus("PASSED");

        Path trajectoryFile = tempDir.resolve("execution_trajectory.json");
        assertTrue(Files.exists(trajectoryFile), "execution_trajectory.json must exist in project workspace");

        String content = Files.readString(trajectoryFile);
        assertFalse(content.isBlank(), "execution_trajectory.json content must not be blank");
        assertTrue(content.contains("\"projectId\" : \"test-traj-proj\""));
        assertTrue(content.contains("\"stage\" : \"STAGE_1_PLAN\""));
        assertTrue(content.contains("\"agent\" : \"PlanGeneratorAgent\""));
        assertTrue(content.contains("\"finalStatus\" : \"PASSED\""));
    }
}
