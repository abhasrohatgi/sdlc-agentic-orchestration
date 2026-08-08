package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.agent.CodeEngineerAgent;
import com.schwab.agenticsdlc.agent.HumanGatekeeperAgent;
import com.schwab.agenticsdlc.agent.QAValidatorAgent;
import com.schwab.agenticsdlc.llm.LlmClientManager;
import com.schwab.agenticsdlc.workspace.LanguageStack;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.ProjectType;
import com.schwab.agenticsdlc.workspace.ScenarioType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QAValidatorAgentTest {

    @Test
    public void testSelfHealingDoesNotTriggerFullReSynthesisOnEmptyFixPayload(@TempDir Path tempDir) {
        LlmClientManager llmClientManager = mock(LlmClientManager.class);
        CodeEngineerAgent codeEngineerAgent = mock(CodeEngineerAgent.class);
        HumanGatekeeperAgent humanGatekeeperAgent = mock(HumanGatekeeperAgent.class);

        ProjectConfig config = new ProjectConfig(
                "test-project", "Test Project", "Test Description",
                LanguageStack.JAVA, ProjectType.MICROSERVICE, ScenarioType.GREENFIELD,
                tempDir.toString()
        );

        // Mock LLM returning empty fix directive
        when(llmClientManager.generateFixDirective(any(), anyString(), any())).thenReturn(Map.of());

        QAValidatorAgent qaAgent = new QAValidatorAgent(llmClientManager, humanGatekeeperAgent);

        // Execute validation (will fail mvn test in tempDir)
        boolean result = qaAgent.validateAndSelfHeal(config, codeEngineerAgent, "Test requirement");

        // ASSERT: CodeEngineerAgent.synthesizeCode MUST NEVER be called during self-healing retry passes
        verify(codeEngineerAgent, never()).synthesizeCode(eq(config), anyString());
    }

    @Test
    public void testSelfHealingAppliesTargetedFixesWhenPayloadReturned(@TempDir Path tempDir) {
        LlmClientManager llmClientManager = mock(LlmClientManager.class);
        CodeEngineerAgent codeEngineerAgent = mock(CodeEngineerAgent.class);
        HumanGatekeeperAgent humanGatekeeperAgent = mock(HumanGatekeeperAgent.class);

        ProjectConfig config = new ProjectConfig(
                "test-project", "Test Project", "Test Description",
                LanguageStack.JAVA, ProjectType.MICROSERVICE, ScenarioType.GREENFIELD,
                tempDir.toString()
        );

        Map<String, String> patchMap = Map.of("pom.xml", "<project></project>");
        when(llmClientManager.generateFixDirective(any(), anyString(), any(), any())).thenReturn(patchMap);

        QAValidatorAgent qaAgent = new QAValidatorAgent(llmClientManager, humanGatekeeperAgent);

        qaAgent.validateAndSelfHeal(config, codeEngineerAgent, "Test requirement");

        // ASSERT: applyFiles was called with targeted patch, synthesizeCode was NEVER called
        verify(codeEngineerAgent, times(9)).applyFiles(eq(config), eq(patchMap));
        verify(codeEngineerAgent, never()).synthesizeCode(eq(config), anyString());
    }

    @Test
    public void testExtractErrorRelevantFiles() {
        LlmClientManager llmClientManager = mock(LlmClientManager.class);
        QAValidatorAgent qaAgent = new QAValidatorAgent(llmClientManager, mock(HumanGatekeeperAgent.class));

        ProjectConfig config = new ProjectConfig(
                "test-project", "Test Project", "Test Description",
                LanguageStack.JAVA, ProjectType.MICROSERVICE, ScenarioType.GREENFIELD,
                "/tmp/test"
        );

        Map<String, String> currentFiles = Map.of(
                "pom.xml", "<project></project>",
                "src/main/java/com/urlshortner/dto/CreateUrlRequest.java", "class CreateUrlRequest {}",
                "src/main/java/com/urlshortner/exception/GlobalExceptionHandler.java", "class GlobalExceptionHandler {}",
                "src/main/java/com/urlshortner/service/impl/UrlServiceImpl.java", "class UrlServiceImpl {}",
                "src/main/java/com/urlshortner/controller/UrlController.java", "class UrlController {}",
                "src/main/java/com/urlshortner/repository/UrlRepository.java", "interface UrlRepository {}"
        );

        String stackTrace = """
                [ERROR] /workspaces/url-shortner/src/main/java/com/urlshortner/dto/CreateUrlRequest.java:[26,21] annotation value not of an allowable type
                [ERROR] /workspaces/url-shortner/src/main/java/com/urlshortner/exception/GlobalExceptionHandler.java:[5,15] package org.slf does not exist
                """;

        when(llmClientManager.identifyFailingFiles(eq(config), eq(stackTrace), any()))
                .thenReturn(java.util.List.of(
                        "src/main/java/com/urlshortner/dto/CreateUrlRequest.java",
                        "src/main/java/com/urlshortner/exception/GlobalExceptionHandler.java"
                ));

        Map<String, String> relevant = qaAgent.extractErrorRelevantFiles(config, stackTrace, currentFiles);

        // Should include pom.xml + 2 LLM identified failing files = 3 files total
        assertEquals(3, relevant.size());
        assertTrue(relevant.containsKey("pom.xml"));
        assertTrue(relevant.containsKey("src/main/java/com/urlshortner/dto/CreateUrlRequest.java"));
        assertTrue(relevant.containsKey("src/main/java/com/urlshortner/exception/GlobalExceptionHandler.java"));
    }
}
