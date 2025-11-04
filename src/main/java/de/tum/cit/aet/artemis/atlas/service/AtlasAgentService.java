package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.Nullable;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
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

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider toolCallbackProvider;

    private final ChatMemory chatMemory;

    private final AtlasAgentToolsService atlasAgentToolsService;

    private final String chatModel;

    public AtlasAgentService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ToolCallbackProvider toolCallbackProvider,
            @Nullable ChatMemory chatMemory, @Nullable AtlasAgentToolsService atlasAgentToolsService, @Value("${artemis.atlas.chat-model}") String chatModel) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatMemory = chatMemory;
        this.atlasAgentToolsService = atlasAgentToolsService;
        this.chatModel = chatModel;
    }

    /**
     * Process a chat message for the given course and return AI response with modification status.
     * Uses request-scoped state tracking to detect competency modifications.
     *
     * @param message   The user's message
     * @param courseId  The course ID for context
     * @param sessionId The session ID for chat memory
     * @return Result containing the AI response and competency modification flag
     */
    public CompletableFuture<AgentChatResult> processChatMessage(String message, Long courseId, String sessionId) {
        try {

            String resourcePath = "/prompts/atlas/agent_system_prompt.st";
            Map<String, String> variables = Map.of();
            String systemPrompt = templateService.render(resourcePath, variables);

            AzureOpenAiChatOptions options = AzureOpenAiChatOptions.builder().deploymentName(chatModel).build();

            ChatClientRequestSpec promptSpec = chatClient.prompt().system(systemPrompt).user(String.format("Course ID: %d\n\n%s", courseId, message)).options(options);

            // Add chat memory advisor
            if (chatMemory != null) {
                promptSpec = promptSpec.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
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
     * Check if the Atlas Agent service is available and properly configured.
     *
     * @return true if the service is ready, false otherwise
     */
    public boolean isAvailable() {
        try {
            return chatClient != null;
        }
        catch (Exception e) {
            return false;
        }
    }
}
