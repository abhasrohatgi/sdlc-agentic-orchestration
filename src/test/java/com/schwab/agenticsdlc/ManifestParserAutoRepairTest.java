package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.agent.ManifestParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ManifestParserAutoRepairTest {

    @Test
    public void testParseFromStringAutoRepairsTruncatedManifestJson() throws Exception {
        // Simulates a Phase 1 manifest JSON payload cut off at file #3 without closing brackets
        String truncatedJson = """
                {
                  "projectId": "url-shortner",
                  "files": [
                    {
                      "path": "pom.xml",
                      "fileType": "POM",
                      "purpose": "Build configuration"
                    },
                    {
                      "path": "src/main/java/com/urlshortner/Application.java",
                      "fileType": "CONFIG",
                      "purpose": "Main application class"
                    },
                    {
                      "path": "src/main/java/com/urlshortner/service/UrlService.java",
                      "fileType": "SERVICE_INTERFACE",
                      "purpose": "Service interface"
                    },
                    {
                      "path": "src/main/java/com/urlshortner/service/impl/UrlServiceImpl.java",
                      "fileType": "SERVICE_IMPL",
                      "purpose": "Truncated file entry
                """;

        ManifestParser.ProjectManifest manifest = ManifestParser.parseFromString(truncatedJson);
        assertNotNull(manifest);
        assertNotNull(manifest.getFiles());
        assertEquals(3, manifest.getFiles().size(), "Auto-repair must successfully recover the 3 valid file nodes");
        assertEquals("pom.xml", manifest.getFiles().get(0).getPath());
        assertEquals("src/main/java/com/urlshortner/Application.java", manifest.getFiles().get(1).getPath());
        assertEquals("src/main/java/com/urlshortner/service/UrlService.java", manifest.getFiles().get(2).getPath());
    }
}
