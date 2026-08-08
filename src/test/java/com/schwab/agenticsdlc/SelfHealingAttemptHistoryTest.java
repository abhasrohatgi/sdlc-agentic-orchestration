package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.agent.QAValidatorAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SelfHealingAttemptHistoryTest {

    @Test
    public void testSelfHealingAttemptDTO() {
        QAValidatorAgent.SelfHealingAttempt attempt = new QAValidatorAgent.SelfHealingAttempt(
                1,
                "Upgrade lombok to 1.18.34",
                List.of("pom.xml"),
                "Compilation error: TypeTag"
        );

        assertEquals(1, attempt.getAttemptIndex());
        assertEquals("Upgrade lombok to 1.18.34", attempt.getDiagnosticReasoning());
        assertNotNull(attempt.getFilesPatched());
        assertEquals(1, attempt.getFilesPatched().size());
        assertTrue(attempt.getFilesPatched().contains("pom.xml"));
        assertEquals("Compilation error: TypeTag", attempt.getBuildErrorOutput());
    }
}
