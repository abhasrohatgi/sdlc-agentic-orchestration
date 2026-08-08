package com.schwab.agenticsdlc;

import com.schwab.agenticsdlc.llm.LlmClientManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LlmClientManagerJsonTest {

    @Test
    public void testExtractJsonTextWithLeadingExclamationAndText() throws Exception {
        LlmClientManager manager = new LlmClientManager();
        Method extractMethod = LlmClientManager.class.getDeclaredMethod("extractJsonText", String.class);
        extractMethod.setAccessible(true);

        String rawLlmResponse = "! Note: Here are the synthesized files:\n" +
                "{\n" +
                "  \"pom.xml\": \"<project></project>\",\n" +
                "  \"src/main/java/com/weather/Application.java\": \"package com.weather;\"\n" +
                "}";

        String extracted = (String) extractMethod.invoke(manager, rawLlmResponse);
        assertTrue(extracted.startsWith("{"));
        assertTrue(extracted.endsWith("}"));

        Method parseMethod = LlmClientManager.class.getDeclaredMethod("parseJsonMap", String.class);
        parseMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) parseMethod.invoke(manager, extracted);
        assertEquals(2, map.size());
        assertEquals("<project></project>", map.get("pom.xml"));
    }

    @Test
    public void testExtractJsonTextWithMarkdownBlocksAndPreamble() throws Exception {
        LlmClientManager manager = new LlmClientManager();
        Method extractMethod = LlmClientManager.class.getDeclaredMethod("extractJsonText", String.class);
        extractMethod.setAccessible(true);

        String rawLlmResponse = "! Important instructions:\n```json\n" +
                "{\n" +
                "  \"README.md\": \"# Weather API\"\n" +
                "}\n```";

        String extracted = (String) extractMethod.invoke(manager, rawLlmResponse);
        assertTrue(extracted.startsWith("{"));
        assertTrue(extracted.endsWith("}"));
    }

    @Test
    public void testParseJsonMapWithUnescapedQuotesAndHashCharacter() throws Exception {
        LlmClientManager manager = new LlmClientManager();
        Method parseMethod = LlmClientManager.class.getDeclaredMethod("parseJsonMap", String.class);
        parseMethod.setAccessible(true);

        String malformedLlmJson = "{\n" +
                "  \"pom.xml\": \"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project>...</project>\",\n" +
                "  \"README.md\": \"# Weather API Microservice\n## Installation\nrun \"mvn test\" now\",\n" +
                "  \"src/main/java/com/weather/App.java\": \"package com.weather;\npublic class App {}\"\n" +
                "}";

        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) parseMethod.invoke(manager, malformedLlmJson);
        assertNotNull(map);
        assertTrue(map.containsKey("pom.xml"));
        assertTrue(map.containsKey("README.md"));
        assertTrue(map.containsKey("src/main/java/com/weather/App.java"));
    }

    @Test
    public void testLoadPromptTemplateFromResources() {
        LlmClientManager manager = new LlmClientManager();
        String greenfieldPrompt = manager.loadPromptTemplate("plan_generator_greenfield_system.prompt");
        assertNotNull(greenfieldPrompt, "plan_generator_greenfield_system.prompt should be loaded");
        assertTrue(greenfieldPrompt.contains("software architect"), "Should contain software architect instruction");

        String brownfieldPrompt = manager.loadPromptTemplate("plan_generator_brownfield_system.prompt");
        assertNotNull(brownfieldPrompt, "plan_generator_brownfield_system.prompt should be loaded");
        assertTrue(brownfieldPrompt.contains("BROWNFIELD INCREMENTAL EVOLUTION"), "Should contain brownfield instruction");

        String codePrompt = manager.loadPromptTemplate("code_engineer_system.prompt");
        assertNotNull(codePrompt, "code_engineer_system.prompt should be loaded from resources");
        assertTrue(codePrompt.contains("DUAL-TIER TEST SUITE"), "Should contain dual-tier test suite instruction");

        String fixPrompt = manager.loadPromptTemplate("fix_directive_system.prompt");
        assertNotNull(fixPrompt, "fix_directive_system.prompt should be loaded from resources");
        assertTrue(fixPrompt.contains("MANDATORY PLAN SYNCHRONIZATION"), "Should contain plan synchronization instruction");
    }
}
