package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final AtlasPromptTemplateService templateService;

    public AtlasAgentService(@Autowired(required = false) ChatClient chatClient, AtlasPromptTemplateService templateService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
    }

    /**
     * Process a chat message for the given course and return AI response.
     *
     * @param message  The user's message
     * @param courseId The course ID for context
     * @return AI response
     */
    public CompletableFuture<String> processChatMessage(String message, Long courseId) {
        try {
            log.debug("Processing chat message for course {} (messageLength={} chars)", courseId, message.length());

            // Load system prompt from external template
            String resourcePath = "/prompts/atlas/agent_system_prompt.st";
            Map<String, String> variables = Map.of(); // No variables needed for this template
            String systemPrompt = templateService.render(resourcePath, variables);

            String response = chatClient.prompt().system(systemPrompt).user(String.format("Course ID: %d\n\n%s", courseId, message))
                    .options(AzureOpenAiChatOptions.builder().temperature(1.0).build()).call().content();

            log.info("Successfully processed chat message for course {}", courseId);
            return CompletableFuture.completedFuture(response != null && !response.trim().isEmpty() ? response : "I apologize, but I couldn't generate a response.");

        }
        catch (Exception e) {
            log.error("Error processing chat message for course {}: {}", courseId, e.getMessage(), e);
            return CompletableFuture.completedFuture("I apologize, but I'm having trouble processing your request right now. Please try again later.");
        }
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
