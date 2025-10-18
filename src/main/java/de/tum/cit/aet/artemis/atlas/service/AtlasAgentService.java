package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.Nullable;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.tool.ToolCallbackProvider;
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

    private final AtlasAgentToolsService atlasAgentToolsService;

    public AtlasAgentService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ToolCallbackProvider toolCallbackProvider,
            @Nullable ChatMemory chatMemory, @Nullable AtlasAgentToolsService atlasAgentToolsService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatMemory = chatMemory;
        this.atlasAgentToolsService = atlasAgentToolsService;
    }

    /**
     * Process a chat message for the given course and return AI response with modification status.
     * Uses request-scoped state tracking to detect competency modifications.
     * Supports conversation context through sessionId with persistent memory.
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
            // Load system prompt from external template
            String resourcePath = "/prompts/atlas/agent_system_prompt.st";
            Map<String, String> variables = Map.of();
            String systemPrompt = templateService.render(resourcePath, variables);

            // Add course ID to system prompt instead of user message to avoid storing it in chat history
            String enhancedSystemPrompt = String.format("%s\n\nContext: You are assisting with Course ID: %d", systemPrompt, courseId);

            AzureOpenAiChatOptions options = AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").temperature(1.0).build();

            ChatClientRequestSpec promptSpec = chatClient.prompt().system(enhancedSystemPrompt).user(message).options(options);

            // Add chat memory advisor using persistent JDBC-based memory
            if (chatMemory != null) {
                promptSpec = promptSpec.advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, sessionId));
            }

            // Add tools
            if (toolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(toolCallbackProvider);
            }

            // Execute the chat (tools are executed internally by Spring AI)
            String response = promptSpec.call().content();

            // Check if createCompetency was called by examining the service state
            boolean competenciesModified = atlasAgentToolsService != null && atlasAgentToolsService.wasCompetencyCreated();

            String finalResponse = response != null && !response.trim().isEmpty() ? response : "I apologize, but I couldn't generate a response.";

            return CompletableFuture.completedFuture(new AgentChatResult(finalResponse, competenciesModified));

        }
        catch (Exception e) {
            return CompletableFuture.completedFuture(new AgentChatResult("I apologize, but I'm having trouble processing your request right now. Please try again later.", false));
        }
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
        try {
            return chatClient != null && chatMemory != null;
        }
        catch (Exception e) {
            return false;
        }
    }
}
