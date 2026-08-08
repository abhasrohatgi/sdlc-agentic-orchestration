package com.schwab.agenticsdlc.engine;

import com.schwab.agenticsdlc.agent.CodeEngineerAgent;
import com.schwab.agenticsdlc.agent.HumanGatekeeperAgent;
import com.schwab.agenticsdlc.agent.PlanGeneratorAgent;
import com.schwab.agenticsdlc.agent.QAValidatorAgent;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * Directed Multi-Agent Orchestration Engine.
 * Coordinates Plan Generation -> Human Gatekeeper Checkpoint -> Code Synthesis -> QA Self-Healing Loop.
 */
public class OrchestratorEngine {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorEngine.class);

    private final PlanGeneratorAgent planAgent;
    private final HumanGatekeeperAgent humanGatekeeperAgent;
    private final CodeEngineerAgent codeEngineerAgent;
    private final QAValidatorAgent qaValidatorAgent;

    public OrchestratorEngine() {
        this(new Scanner(System.in));
    }

    public OrchestratorEngine(Scanner scanner) {
        this.planAgent = new PlanGeneratorAgent();
        this.humanGatekeeperAgent = new HumanGatekeeperAgent(scanner);
        this.codeEngineerAgent = new CodeEngineerAgent();
        this.qaValidatorAgent = new QAValidatorAgent();
    }

    public OrchestratorEngine(PlanGeneratorAgent planAgent,
                               HumanGatekeeperAgent humanGatekeeperAgent,
                               CodeEngineerAgent codeEngineerAgent,
                               QAValidatorAgent qaValidatorAgent) {
        this.planAgent = planAgent;
        this.humanGatekeeperAgent = humanGatekeeperAgent;
        this.codeEngineerAgent = codeEngineerAgent;
        this.qaValidatorAgent = qaValidatorAgent;
    }

    public boolean executeOrchestrationPipeline(ProjectConfig projectConfig, String requirementPrompt) {
        com.schwab.agenticsdlc.workspace.ScenarioDetector scenarioDetector = new com.schwab.agenticsdlc.workspace.ScenarioDetector();
        com.schwab.agenticsdlc.workspace.ScenarioType detectedScenario = scenarioDetector.detectScenario(
                java.nio.file.Paths.get(projectConfig.getProjectPath()), requirementPrompt);
        projectConfig.setScenarioType(detectedScenario);

        logger.info("==================================================================");
        logger.info("   AGENTIC SDLC - MULTI-AGENT ORCHESTRATION PIPELINE START        ");
        logger.info("==================================================================");
        logger.info("Active Project:    {} ({})", projectConfig.getProjectName(), projectConfig.getProjectId());
        logger.info("Tech Stack:        {}", projectConfig.getLanguageStack());
        logger.info("Detected Scenario: {}", projectConfig.getScenarioType());
        logger.info("Location:          {}", projectConfig.getProjectPath());
        logger.info("------------------------------------------------------------------");

        com.schwab.agenticsdlc.telemetry.ExecutionTrajectoryLogger trajectoryLogger =
                new com.schwab.agenticsdlc.telemetry.ExecutionTrajectoryLogger(projectConfig);

        try {
            // Stage 1: LLM Plan Generation (.ai-plan/plan.md)
            logger.info("[STAGE 1]: Requirement Analysis & LLM Architectural Planning...");
            trajectoryLogger.recordStep("STAGE_1_REQUIREMENT_ANALYSIS", "PlanGeneratorAgent", "STARTED", "Generating architectural plan", null, null);
            Path planPath = planAgent.generatePlan(projectConfig, requirementPrompt);
            logger.info("[GATE_ARCH_APPROVED]: LLM Architectural plan saved at {}", planPath);
            trajectoryLogger.recordStep("STAGE_1_REQUIREMENT_ANALYSIS", "PlanGeneratorAgent", "COMPLETED", "Plan generated at " + planPath, null, java.util.List.of(".ai-plan/plan.md"));

            // Stage 2: Human Governance Checkpoint
            logger.info("[STAGE 2]: Human Governance Checkpoint...");
            trajectoryLogger.recordStep("STAGE_2_HUMAN_GOVERNANCE", "HumanGatekeeperAgent", "WAITING_APPROVAL", "Requesting human operator approval", null, null);
            boolean approved = humanGatekeeperAgent.requestHumanApproval(projectConfig);
            if (!approved) {
                logger.warn("[GATE_HUMAN_APPROVED FAILED]: Execution aborted by human operator.");
                trajectoryLogger.setFinalStatus("ABORTED_BY_USER");
                return false;
            }
            trajectoryLogger.recordStep("STAGE_2_HUMAN_GOVERNANCE", "HumanGatekeeperAgent", "APPROVED", "Plan approved by human operator", null, null);

            // Stage 3: LLM Code Synthesis
            logger.info("[STAGE 3]: Dynamic LLM Code Synthesis...");
            trajectoryLogger.recordStep("STAGE_3_CODE_SYNTHESIS", "CodeEngineerAgent", "STARTED", "Executing 3-wave topological synthesis", null, null);
            codeEngineerAgent.synthesizeCode(projectConfig, requirementPrompt);
            trajectoryLogger.recordStep("STAGE_3_CODE_SYNTHESIS", "CodeEngineerAgent", "COMPLETED", "Topological code synthesis complete", null, null);

            // Stage 4: QA Validation & Real Build Self-Healing Loop
            logger.info("[STAGE 4]: QA Validation & Real Build Verification...");
            trajectoryLogger.recordStep("STAGE_4_QA_VALIDATION", "QAValidatorAgent", "STARTED", "Starting empirical build & test verification", null, null);
            boolean qaSuccess = qaValidatorAgent.validateAndSelfHeal(projectConfig, codeEngineerAgent, requirementPrompt, trajectoryLogger);

            if (qaSuccess) {
                logger.info("==================================================================");
                logger.info("   AGENTIC SDLC - ORCHESTRATION PIPELINE PASSED SUCCESSFULLY!    ");
                logger.info("==================================================================");
                trajectoryLogger.setFinalStatus("PASSED");
                return true;
            } else {
                logger.error("==================================================================");
                logger.error("   AGENTIC SDLC - ORCHESTRATION PIPELINE FAILED QA VALIDATION.   ");
                logger.error("==================================================================");
                trajectoryLogger.setFinalStatus("FAILED_QA");
                return false;
            }

        } catch (IllegalArgumentException e) {
            logger.error("{}", e.getMessage());
            logger.error("[GATE_REQUIREMENT_VALIDATION FAILED]: Execution ABORTED. Please rerun with a valid software requirement.");
            trajectoryLogger.setFinalStatus("INVALID_REQUIREMENT");
            return false;
        } catch (Exception e) {
            logger.error("[OrchestratorEngine ERROR]: Unhandled exception in pipeline: {}", e.getMessage(), e);
            trajectoryLogger.setFinalStatus("ERRORED");
            return false;
        }
    }
}
