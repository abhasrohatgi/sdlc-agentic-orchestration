package com.schwab.agenticsdlc;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Production Java tool class exposed to the Google GenAI / ADK agent.
 */
public class GreetingTools {

    /**
     * Tool method invoked by Gemini LLM automatic function calling.
     * @param name The target recipient name.
     * @return Formatted greeting string.
     */
    public static String getGreeting(String name) {
        LocalTime now = LocalTime.now();
        String timeOfDay;

        if (now.getHour() < 12) {
            timeOfDay = "Morning";
        } else if (now.getHour() < 18) {
            timeOfDay = "Afternoon";
        } else {
            timeOfDay = "Evening";
        }

        return String.format("Good %s, %s! Welcome to official Google ADK & GenAI SDK.", timeOfDay, name);
    }

    public static Tool createGreetingTool() {
        FunctionDeclaration greetingFunc = FunctionDeclaration.builder()
                .name("getGreeting")
                .description("Generates a time-based greeting for a given user name.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "name", Schema.builder().type("STRING").description("The recipient user name").build()
                        ))
                        .required(List.of("name"))
                        .build())
                .build();

        return Tool.builder()
                .functionDeclarations(List.of(greetingFunc))
                .build();
    }
}
