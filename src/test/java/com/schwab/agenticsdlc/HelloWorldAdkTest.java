package com.schwab.agenticsdlc;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HelloWorldAdkTest {

    @Test
    @DisplayName("Verify GreetingTools getGreeting execution")
    void testGreetingToolsExecution() {
        String output = GreetingTools.getGreeting("Bob");
        assertNotNull(output);
        assertTrue(output.contains("Bob"));
        assertTrue(output.contains("Welcome to official Google ADK & GenAI SDK"));
    }

    @Test
    @DisplayName("Verify official Google GenAI Tool and GenerateContentConfig construction")
    void testGenAiToolAndConfigConstruction() {
        Tool greetingTool = GreetingTools.createGreetingTool();
        assertNotNull(greetingTool);
        assertTrue(greetingTool.functionDeclarations().isPresent());
        assertEquals(1, greetingTool.functionDeclarations().get().size());
        assertEquals("getGreeting", greetingTool.functionDeclarations().get().get(0).name().orElse(""));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(List.of(greetingTool))
                .build();

        assertNotNull(config);
        assertTrue(config.tools().isPresent());
        assertEquals(1, config.tools().get().size());
    }
}
