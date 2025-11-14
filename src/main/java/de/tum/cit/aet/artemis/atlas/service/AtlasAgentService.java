package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.Nullable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentHistoryMessageDTO;

/**
 * Service for Atlas Agent functionality with Azure OpenAI integration.
 * Handles chat interactions and competency-related AI assistance.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentService {

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider toolCallbackProvider;

    private final ChatMemory chatMemory;

    private static final ThreadLocal<Boolean> competencyCreatedInCurrentRequest = ThreadLocal.withInitial(() -> false);

    public AtlasAgentService(@Qualifier("atlasChatClient") @Nullable ChatClient atlasChatClient, AtlasPromptTemplateService templateService,
            @Nullable ToolCallbackProvider toolCallbackProvider, @Nullable ChatMemory chatMemory) {
        this.chatClient = atlasChatClient;
        this.templateService = templateService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatMemory = chatMemory;
    }

    /**
     * Process a chat message for the given course and return AI response with modification status.
     * Uses ThreadLocal state tracking to detect competency modifications.
     *
     * @param message   The user's message
     * @param courseId  The course ID for context
     * @param sessionId The session ID for chat memory
     * @return Result containing the AI response and competency modification flag
     */
    public CompletableFuture<AgentChatResult> processChatMessage(String message, Long courseId, String sessionId) {
        if (chatClient == null) {
            return CompletableFuture.completedFuture(new AgentChatResult("Atlas Agent is not available. Please contact your administrator.", false));
        }

        try {
            // Reset the ThreadLocal flag at the start of each request
            competencyCreatedInCurrentRequest.set(false);

            String resourcePath = "/prompts/atlas/agent_system_prompt.st";
            Map<String, String> variables = Map.of();
            String systemPrompt = templateService.render(resourcePath, variables);

            // Add course ID to system prompt instead of user message to avoid storing it in chat history
            String enhancedSystemPrompt = String.format("%s\n\nContext: You are assisting with Course ID: %d", systemPrompt, courseId);

            ChatClientRequestSpec promptSpec = chatClient.prompt().system(enhancedSystemPrompt).user(message);

            // Add chat memory advisor using persistent JDBC-based memory
            if (chatMemory != null) {
                promptSpec = promptSpec.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
            }

            // Add tools
            if (toolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(toolCallbackProvider);
            }

            // Execute the chat (tools are executed internally by Spring AI)
            String response = promptSpec.call().chatResponse().getResult().getOutput().getText();

            // Check if competency was created during this request
            boolean competenciesModified = competencyCreatedInCurrentRequest.get();

            String finalResponse = response != null && !response.trim().isEmpty() ? response : "I apologize, but I couldn't generate a response.";

            return CompletableFuture.completedFuture(new AgentChatResult(finalResponse, competenciesModified));

        }
        catch (Exception e) {
            return CompletableFuture.completedFuture(new AgentChatResult("I apologize, but I'm having trouble processing your request right now. Please try again later.", false));
        }
        finally {
            // Clean up ThreadLocal to prevent memory leaks
            competencyCreatedInCurrentRequest.remove();
        }
    }

    /**
     * Marks that a competency was created during the current request.
     * This method is called by tool methods (e.g., createCompetency) to signal
     * that a competency modification occurred during tool execution.
     */
    public static void markCompetencyCreated() {
        competencyCreatedInCurrentRequest.set(true);
    }

    /**
     * Retrieves the conversation history for a given session as DTOs.
     *
     * @param sessionId The session/conversation ID
     * @return List of conversation history messages as DTOs
     */
    public List<AtlasAgentHistoryMessageDTO> getConversationHistoryAsDTO(String sessionId) {
        try {
            if (chatMemory == null) {
                return List.of();
            }
            List<Message> messages = chatMemory.get(sessionId);

            if (messages.isEmpty()) {
                return List.of();
            }
            return messages.stream().map(message -> {
                boolean isUser = message.getMessageType() == MessageType.USER;
                return new AtlasAgentHistoryMessageDTO(message.getText(), isUser);
            }).toList();
        }
        catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Check if the Atlas Agent service is available and properly configured.
     *
     * @return true if the service is ready, false otherwise
     */
    public boolean isAvailable() {
        return chatClient != null && chatMemory != null;
    }

    /**
     * Generates a unique session ID for a user's conversation in a specific course.
     * This ensures each user has their own isolated chat history per course.
     * Centralized generation prevents security risks from client-controlled session IDs.
     *
     * @param courseId the course ID
     * @param userId   the user ID
     * @return the generated session ID in format "course_{courseId}_user_{userId}"
     */
    public String generateSessionId(Long courseId, Long userId) {
        return String.format("course_%d_user_%d", courseId, userId);
    }
}
