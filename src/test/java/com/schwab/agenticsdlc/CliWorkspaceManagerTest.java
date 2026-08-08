package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.workspace.LanguageStack;
import com.schwab.agenticsdlc.workspace.ProjectConfig;
import com.schwab.agenticsdlc.workspace.ProjectType;
import com.schwab.agenticsdlc.workspace.WorkspaceFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CliWorkspaceManagerTest {

    @Test
    public void testWorkspaceCreation(@TempDir Path tempDir) throws Exception {
        WorkspaceFileManager fileManager = new WorkspaceFileManager(tempDir);
        ProjectConfig config = fileManager.createProject("sample-app", "Sample description", LanguageStack.JAVA, ProjectType.CUSTOM, "Sample requirement prompt");

        assertNotNull(config);
        assertEquals("sample-app", config.getProjectId());
        assertEquals("Sample description", config.getDescription());
    }
}
