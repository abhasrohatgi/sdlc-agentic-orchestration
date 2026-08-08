package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.agent.CodeEngineerAgent;
import com.schwab.agenticsdlc.llm.LlmClientManager;
import com.schwab.agenticsdlc.workspace.LanguageStack;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.ProjectType;
import com.schwab.agenticsdlc.workspace.ScenarioType;
import com.schwab.agenticsdlc.workspace.WorkspaceFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class CodeEngineerAgentTest {

    @Test
    public void testTopologicalSynthesisFlow(@TempDir Path tempDir) throws Exception {
        LlmClientManager llmClientManager = mock(LlmClientManager.class);
        WorkspaceFileManager workspaceFileManager = mock(WorkspaceFileManager.class);

        ProjectConfig config = new ProjectConfig(
                "url-shortner", "URL Shortener", "Build a REST URL shortener",
                LanguageStack.JAVA, ProjectType.CUSTOM, ScenarioType.GREENFIELD,
                tempDir.toString()
        );

        String sampleManifestJson = """
                {
                  "projectId": "url-shortner",
                  "files": [
                    { "path": "pom.xml", "fileType": "POM", "purpose": "Build configuration" },
                    { "path": "src/main/java/com/urlshortner/dto/CreateUrlRequest.java", "fileType": "DTO", "purpose": "Request DTO" },
                    { "path": "src/main/java/com/urlshortner/service/UrlService.java", "fileType": "INTERFACE", "purpose": "Service interface" },
                    { "path": "src/main/java/com/urlshortner/service/impl/UrlServiceImpl.java", "fileType": "SERVICE_IMPL", "purpose": "Service implementation" },
                    { "path": "src/test/java/com/urlshortner/controller/UrlControllerTest.java", "fileType": "TEST", "purpose": "Controller test" }
                  ]
                }
                """;

        when(llmClientManager.generateFileManifest(eq(config), any(), any()))
                .thenReturn(sampleManifestJson);

        when(llmClientManager.generateSingleFileCode(eq(config), any(), any(), any()))
                .thenReturn("```java\npackage com.urlshortner;\npublic class Sample {}\n```");

        CodeEngineerAgent engineerAgent = new CodeEngineerAgent(llmClientManager, workspaceFileManager);
        engineerAgent.synthesizeCode(config, "Build a REST URL shortener");

        // Verify that generateSingleFileCode was called for all 5 files across 3 topological waves
        verify(llmClientManager, times(5)).generateSingleFileCode(eq(config), any(), any(), any());
    }
}
