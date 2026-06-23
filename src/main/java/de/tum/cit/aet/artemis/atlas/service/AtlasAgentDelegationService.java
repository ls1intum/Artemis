package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasAgentProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/** Delegates messages to AI agents; extracted from {@code AtlasAgentService} to break a circular dependency with {@code AtlasAgentToolsService}. */
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
            AtlasAgentProperties properties) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.chatMemory = chatMemory;
        this.deploymentName = properties.chatModel();
        this.temperature = properties.temperature();
    }

    /** Delegates a message to an AI agent with the given prompt template and tool provider; returns the agent's response. */
    String delegateToAgent(String promptResourcePath, String message, Long courseId, String sessionId, boolean saveToMemory, @Nullable ToolCallbackProvider toolCallbackProvider) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not configured. Atlas Agent delegation is unavailable.");
        }

        String systemPrompt = templateService.render(promptResourcePath, Map.of());

        String systemPromptWithContext = systemPrompt + "\n\nCONTEXT FOR THIS REQUEST:\nCourse ID: " + courseId;

        ChatClient.Builder clientBuilder = chatClient.mutate();
        boolean withMemory = chatMemory != null && saveToMemory;
        if (withMemory) {
            clientBuilder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        ChatClient sessionClient = clientBuilder.build();

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().deploymentName(deploymentName).temperature(temperature);

        ChatClientRequestSpec promptSpec = sessionClient.prompt().system(systemPromptWithContext).user(message).options(options);

        if (withMemory) {
            // Spring AI 2.0.0-M6 removed the advisor-level conversation id (fix for GHSA-q62f-h9x2-gcqc):
            // the per-(course,user) session id must be supplied as a request-time advisor param instead.
            promptSpec = promptSpec.advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, sessionId));
        }
        if (toolCallbackProvider != null) {
            promptSpec = promptSpec.toolCallbacks(toolCallbackProvider);
        }

        String content = promptSpec.call().content();
        return content != null ? content : "";
    }
}
