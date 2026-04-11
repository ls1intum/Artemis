package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Service for delegating messages to AI agents.
 * Extracted from {@link AtlasAgentService} to break the circular dependency between
 * {@link AtlasAgentService} and {@link AtlasAgentToolsService}.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentDelegationService {

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ChatMemory chatMemory;

    private final String deploymentName;

    private final double temperature;

    public AtlasAgentDelegationService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ChatMemory chatMemory,
            @Value("${artemis.atlas.chat-model:gpt-4o}") String deploymentName, @Value("${artemis.atlas.chat-temperature:0.2}") double temperature) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.chatMemory = chatMemory;
        this.deploymentName = deploymentName;
        this.temperature = temperature;
    }

    /**
     * Delegate message processing to an AI agent with the given prompt template and tool provider.
     *
     * @param promptResourcePath   the classpath resource path of the system prompt template
     * @param message              the user's message
     * @param courseId             the course ID for context
     * @param sessionId            the session ID for chat memory
     * @param saveToMemory         whether to add message to chat memory
     * @param toolCallbackProvider the tool callback provider for this agent (may be null)
     * @return the agent's response
     */
    String delegateToAgent(String promptResourcePath, String message, Long courseId, String sessionId, boolean saveToMemory, @Nullable ToolCallbackProvider toolCallbackProvider) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not configured. Atlas Agent delegation is unavailable.");
        }

        String systemPrompt = templateService.render(promptResourcePath, Map.of());

        String systemPromptWithContext = systemPrompt + "\n\nCONTEXT FOR THIS REQUEST:\nCourse ID: " + courseId;

        ChatClient.Builder clientBuilder = chatClient.mutate();
        if (chatMemory != null && saveToMemory) {
            clientBuilder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
        }
        ChatClient sessionClient = clientBuilder.build();

        ToolCallingChatOptions options = AzureOpenAiChatOptions.builder().deploymentName(deploymentName).temperature(temperature).build();

        ChatClientRequestSpec promptSpec = sessionClient.prompt().system(systemPromptWithContext).user(message).options(options);

        if (toolCallbackProvider != null) {
            promptSpec = promptSpec.toolCallbacks(toolCallbackProvider);
        }

        String content = promptSpec.call().content();
        return content != null ? content : "";
    }
}
