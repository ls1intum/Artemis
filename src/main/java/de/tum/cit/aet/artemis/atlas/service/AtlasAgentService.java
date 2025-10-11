package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallbackProvider;
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

    private final ToolCallbackProvider toolCallbackProvider;

    private final ChatMemory chatMemory;

    public AtlasAgentService(@Autowired(required = false) ChatClient chatClient, AtlasPromptTemplateService templateService,
            @Autowired(required = false) ToolCallbackProvider toolCallbackProvider, @Autowired(required = false) ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatMemory = chatMemory;
    }

    /**
     * Process a chat message for the given course and return AI response.
     * Uses session-based memory to maintain conversation context across popup close/reopen events.
     *
     * @param message   The user's message
     * @param courseId  The course ID for context
     * @param sessionId The session ID for maintaining conversation memory
     * @return AI response
     */
    public CompletableFuture<String> processChatMessage(String message, Long courseId, String sessionId) {
        try {
            log.debug("Processing chat message for course {} with session {} (messageLength={} chars)", courseId, sessionId, message.length());

            // Load system prompt from external template
            String resourcePath = "/prompts/atlas/agent_system_prompt.st";
            Map<String, String> variables = Map.of(); // No variables needed for this template
            String systemPrompt = templateService.render(resourcePath, variables);

            var options = AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").temperature(1.0).build();
            log.info("Atlas Agent using deployment name: {} for course {} with session {}", options.getDeploymentName(), courseId, sessionId);

            var promptSpec = chatClient.prompt().system(systemPrompt).user(String.format("Course ID: %d\n\n%s", courseId, message)).options(options);

            // Add chat memory advisor for conversation history (temporary session-based caching)
            if (chatMemory != null) {
                promptSpec = promptSpec.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
            }

            // Add tools if available
            // Note: Use toolCallbacks() for ToolCallbackProvider, not tools() which expects tool objects
            if (toolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(toolCallbackProvider);
            }

            String response = promptSpec.call().content();

            log.info("Successfully processed chat message for course {} with session {}", courseId, sessionId);
            return CompletableFuture.completedFuture(response != null && !response.trim().isEmpty() ? response : "I apologize, but I couldn't generate a response.");

        }
        catch (Exception e) {
            log.error("Error processing chat message for course {} with session {}: {}", courseId, sessionId, e.getMessage(), e);
            return CompletableFuture.completedFuture("I apologize, but I'm having trouble processing your request right now. Please try again later.");
        }
    }

    /**
     * Retrieve conversation history for a given session.
     *
     * @param sessionId The session ID to retrieve history for
     * @return List of messages from the conversation history
     */
    public List<Message> getConversationHistory(String sessionId) {
        try {
            if (chatMemory == null) {
                log.debug("ChatMemory not available, returning empty history");
                return List.of();
            }
            List<Message> messages = chatMemory.get(sessionId);
            log.debug("Retrieved {} messages from history for session {}", messages.size(), sessionId);
            return messages;
        }
        catch (Exception e) {
            log.error("Error retrieving conversation history for session {}: {}", sessionId, e.getMessage(), e);
            return List.of();
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
