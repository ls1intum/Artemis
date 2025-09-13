package de.tum.cit.aet.artemis.atlas.service;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Service for Atlas Agent functionality with Azure OpenAI integration.
 * Handles chat interactions and competency-related AI assistance.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentService {

    private static final Logger log = LoggerFactory.getLogger(AtlasAgentService.class);

    private final ChatClient chatClient;

    public AtlasAgentService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Process a chat message for the given course and return AI response.
     *
     * @param message  The user's message
     * @param courseId The course ID for context
     * @return AI response
     */
    public CompletableFuture<String> processChatMessage(String message, Long courseId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing chat message for course {}: {}", courseId, message.substring(0, Math.min(message.length(), 50)));

                String systemPrompt = """
                        You are an AI assistant that helps instructors work with Atlas competency management and Artemis course management.

                        Your role is to:
                        1. Help instructors discover and map competencies to course content using Atlas
                        2. Facilitate understanding of course competencies and their relationships
                        3. Provide clear, structured suggestions with explanations
                        4. Answer questions about competency frameworks and learning objectives

                        Guidelines:
                        - Always provide clear, concise responses
                        - Focus on educational competency management
                        - Be helpful and professional in all interactions
                        - If you cannot help with a specific request, explain why clearly

                        You are specifically designed to work with the Atlas competency management system within Artemis.
                        """;

                String response = chatClient.prompt().system(systemPrompt).user(String.format("Course ID: %d\n\n%s", courseId, message))
                        .options(AzureOpenAiChatOptions.builder().temperature(0.7).maxTokens(800).build()).call().content();

                log.info("Successfully processed chat message for course {}", courseId);
                return response != null && !response.trim().isEmpty() ? response : "I apologize, but I couldn't generate a response.";

            }
            catch (Exception e) {
                log.error("Error processing chat message for course {}: {}", courseId, e.getMessage(), e);
                return "I apologize, but I'm having trouble processing your request right now. Please try again later.";
            }
        });
    }

    /**
     * Check if the Atlas Agent service is available and properly configured.
     *
     * @return true if the service is ready, false otherwise
     */
    public boolean isAvailable() {
        try {
            return chatClient != null;
        }
        catch (Exception e) {
            log.warn("Atlas Agent service availability check failed: {}", e.getMessage());
            return false;
        }
    }
}
