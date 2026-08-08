package com.schwab.agenticsdlc.llm;

/**
 * Stub implementation for the LlmClient class.
 * This class is responsible for interacting with the LLM to generate manifests and code.
 */
public class LlmClient {

    /**
     * Requests the manifest from the LLM.
     *
     * @param projectContext The project context/architecture plan.
     * @return The JSON manifest as a string.
     */
    public String requestManifest(String projectContext) {
        // Stub implementation
        return "{\"projectId\":\"example\",\"files\":[]}";
    }

    /**
     * Requests code generation for a specific file from the LLM.
     *
     * @param filePrompt The prompt for the specific file.
     * @return The raw Markdown response containing the Java code.
     */
    public String requestCode(String filePrompt) {
        // Stub implementation
        return "```java\n// Generated code\n```";
    }
}
