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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Service for delegating messages to AI agents.
 * Extracted from {@link AtlasAgentService} to break the circular dependency between
 * {@link AtlasAgentService} and {@link AtlasAgentToolsService}.
 * Holds the sub-agent tool callback providers so they don't need to be duplicated across callers.
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

    private final ToolCallbackProvider competencyExpertToolCallbackProvider;

    private final ToolCallbackProvider competencyMapperToolCallbackProvider;

    private final ToolCallbackProvider exerciseMapperToolCallbackProvider;

    public AtlasAgentDelegationService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ChatMemory chatMemory,
            @Value("${atlas.chat-model:gpt-4o}") String deploymentName, @Value("${atlas.chat-temperature:0.2}") double temperature,
            @Nullable @Qualifier("competencyExpertToolCallbackProvider") ToolCallbackProvider competencyExpertToolCallbackProvider,
            @Nullable @Qualifier("competencyMapperToolCallbackProvider") ToolCallbackProvider competencyMapperToolCallbackProvider,
            @Nullable @Qualifier("exerciseMapperToolCallbackProvider") ToolCallbackProvider exerciseMapperToolCallbackProvider) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.chatMemory = chatMemory;
        this.deploymentName = deploymentName;
        this.temperature = temperature;
        this.competencyExpertToolCallbackProvider = competencyExpertToolCallbackProvider;
        this.competencyMapperToolCallbackProvider = competencyMapperToolCallbackProvider;
        this.exerciseMapperToolCallbackProvider = exerciseMapperToolCallbackProvider;
    }

    ToolCallbackProvider getSubAgentToolCallbackProvider(AtlasAgentService.AgentType agentType) {
        return switch (agentType) {
            case COMPETENCY_EXPERT -> competencyExpertToolCallbackProvider;
            case COMPETENCY_MAPPER -> competencyMapperToolCallbackProvider;
            case EXERCISE_MAPPER -> exerciseMapperToolCallbackProvider;
            case MAIN_AGENT -> throw new IllegalArgumentException("Main agent provider is not managed by the delegation service");
        };
    }

    /**
     * Delegate message processing to a sub-agent identified by its type.
     *
     * @param agentType    the sub-agent type (must not be MAIN_AGENT)
     * @param message      the user's message
     * @param courseId     the course ID for context
     * @param sessionId    the session ID for chat memory
     * @param saveToMemory whether to add message to chat memory
     * @return the agent's response
     */
    String delegateToSubAgent(AtlasAgentService.AgentType agentType, String message, Long courseId, String sessionId, boolean saveToMemory) {
        return delegateToAgent(AtlasAgentService.getPromptResourcePath(agentType), message, courseId, sessionId, saveToMemory, getSubAgentToolCallbackProvider(agentType));
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

        return promptSpec.call().content();
    }
}
