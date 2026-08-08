package com.schwab.agenticsdlc.agent;

import com.schwab.agenticsdlc.workspace.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * Human Gatekeeper Agent.
 * Enforces Governance Checkpoint (GATE_HUMAN_APPROVED) by displaying .ai-plan/plan.md and pausing for explicit approval.
 */
public class HumanGatekeeperAgent {

    private static final Logger logger = LoggerFactory.getLogger(HumanGatekeeperAgent.class);

    private final Scanner scanner;

    public HumanGatekeeperAgent() {
        this(new Scanner(System.in));
    }

    public HumanGatekeeperAgent(Scanner scanner) {
        this.scanner = scanner;
    }

    public boolean requestHumanApproval(ProjectConfig projectConfig) throws IOException {
        Path planPath = Paths.get(projectConfig.getProjectPath()).resolve(".ai-plan/plan.md");
        if (!Files.exists(planPath)) {
            logger.error("[HumanGatekeeperAgent ERROR]: Architectural plan not found at {}", planPath);
            return false;
        }

        String planContent = Files.readString(planPath);

        System.out.println("\n==================================================================");
        System.out.println("     HUMAN GOVERNANCE CHECKPOINT: PLAN REVIEW & APPROVAL           ");
        System.out.println("==================================================================");
        logger.info("Target Project: {} ({})", projectConfig.getProjectName(), projectConfig.getProjectId());
        logger.info("Plan Location:  {}", planPath);
        System.out.println("------------------------------------------------------------------");
        System.out.println(planContent);
        System.out.println("==================================================================");
        System.out.print("Do you approve this architectural plan to proceed with code generation? (y/n): ");
        System.out.flush();

        if (!scanner.hasNextLine()) {
            return false;
        }

        String response = scanner.nextLine().trim();
        boolean approved = "y".equalsIgnoreCase(response) || "yes".equalsIgnoreCase(response);

        if (approved) {
            logger.info("[HumanGatekeeperAgent SUCCESS]: Plan APPROVED by human operator. Proceeding to Code Engineer Agent.");
        } else {
            logger.warn("[HumanGatekeeperAgent REJECTED]: Plan REJECTED by human operator. Code synthesis aborted.");
        }

        return approved;
    }

    public String requestClarification(String promptMessage) {
        System.out.println("\n==================================================================");
        System.out.println("     HUMAN GOVERNANCE CHECKPOINT: CLARIFICATION / GUIDANCE        ");
        System.out.println("==================================================================");
        System.out.print(promptMessage);
        System.out.flush();

        if (!scanner.hasNextLine()) {
            return null;
        }

        return scanner.nextLine().trim();
    }
}
