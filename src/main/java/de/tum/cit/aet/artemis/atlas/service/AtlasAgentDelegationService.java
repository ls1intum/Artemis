package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasAgentProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Shared LLM delegation harness for every Atlas agent flow.
 * <p>
 * Both the interactive chat ({@link AtlasAgentService} and its tool-driven sub-agent delegations in
 * {@link AtlasAgentToolsService}) and the autonomous {@link CompetencyOrchestrationService} need to
 * ask the LLM for a response with a specific system prompt, an optional set of tool callbacks, and
 * optionally mutable state carried in a {@link org.springframework.ai.chat.model.ToolContext}. This
 * service is the single call site so the memory semantics, system-prompt breadcrumb, and client
 * mutation rules stay in one place:
 * <ul>
 * <li>the chat flow renders its system prompt from a template, appends a course-id breadcrumb, and
 * — when a {@link ChatMemory} bean is configured and {@code saveToMemory} is {@code true} — wires in
 * a {@link MessageChatMemoryAdvisor} so the conversation history replays on every round, using the
 * chat-model deployment / temperature from {@link AtlasAgentProperties};</li>
 * <li>the orchestrator flow supplies an already-assembled system prompt and its own
 * {@link OpenAiChatOptions} (orchestrator deployment / temperature / reasoning effort), runs with
 * memory OFF so each round is a fresh call, and needs the raw {@link ChatResponse} back so it can
 * track token usage.</li>
 * </ul>
 * Extracted from {@code AtlasAgentService} to break a circular dependency with
 * {@code AtlasAgentToolsService}.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentDelegationService {

    @Nullable
    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    @Nullable
    private final ChatMemory chatMemory;

    private final String deploymentName;

    private final double temperature;

    /**
     * Creates the delegation harness.
     *
     * @param chatClient      the configured chat client, or {@code null} when no AI model is available
     * @param templateService renders system-prompt templates from the classpath
     * @param chatMemory      the conversation memory store, or {@code null} when memory is disabled
     * @param properties      chat-agent model configuration (deployment name and temperature)
     */
    public AtlasAgentDelegationService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ChatMemory chatMemory,
            AtlasAgentProperties properties) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.chatMemory = chatMemory;
        this.deploymentName = properties.chatModel();
        this.temperature = properties.temperature();
    }

    /**
     * Delegate a message to a chat agent: render the system prompt from {@code promptResourcePath},
     * append the course-id breadcrumb, optionally attach the chat-memory advisor, and invoke the LLM
     * with the chat-model deployment / temperature. Returns the assistant's text response.
     *
     * @param promptResourcePath   classpath path to the system-prompt template (rendered with no variables)
     * @param message              the user-role message
     * @param courseId             the course id appended as a breadcrumb to the system prompt
     * @param sessionId            the conversation id for the memory advisor
     * @param saveToMemory         whether to attach the {@link MessageChatMemoryAdvisor} (chat: {@code true})
     * @param toolCallbackProvider optional provider of {@code @Tool}-annotated callbacks
     * @return the assistant's text response (never {@code null}; empty string when the model returned no content)
     */
    String delegateToAgent(String promptResourcePath, String message, Long courseId, String sessionId, boolean saveToMemory, @Nullable ToolCallbackProvider toolCallbackProvider) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not configured. Atlas Agent delegation is unavailable.");
        }

        String systemPrompt = templateService.render(promptResourcePath, Map.of());
        String systemPromptWithContext = systemPrompt + "\n\nCONTEXT FOR THIS REQUEST:\nCourse ID: " + courseId;

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().deploymentName(deploymentName).temperature(temperature);
        ChatResponse chatResponse = invoke(systemPromptWithContext, message, options, saveToMemory, sessionId, null, toolCallbackProvider);
        return extractContent(chatResponse);
    }

    /**
     * Delegate an autonomous orchestrator round: invoke the LLM with an already-assembled system
     * prompt, the orchestrator's own {@link OpenAiChatOptions}, the mutable tool context, and the
     * supplied tool providers, with chat memory OFF (each round is a fresh call). Returns the raw
     * {@link ChatResponse} so the caller can track token usage and extract the summary itself.
     *
     * @param systemPrompt          the fully-assembled system prompt (no breadcrumb is appended here)
     * @param userMessage           the user-role message
     * @param options               the orchestrator chat options (deployment / temperature / reasoning effort)
     * @param toolContextMap        mutable map stashed into the request's tool context; mutations performed
     *                                  inside tool bodies are visible to the caller after this method returns
     * @param toolCallbackProviders the tool providers to expose to the model (read + planning)
     * @return the raw {@link ChatResponse} (never {@code null})
     */
    ChatResponse delegateOrchestratorRound(String systemPrompt, String userMessage, OpenAiChatOptions.Builder options, Map<String, Object> toolContextMap,
            ToolCallbackProvider... toolCallbackProviders) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not configured. Atlas Agent delegation is unavailable.");
        }
        return invoke(systemPrompt, userMessage, options, false, null, toolContextMap, toolCallbackProviders);
    }

    /**
     * Single shared invocation path: builds the (optionally memory-advised) client, assembles the
     * request spec with options / tool context / tool callbacks, and returns the {@link ChatResponse}.
     */
    private ChatResponse invoke(String systemPrompt, String userMessage, OpenAiChatOptions.Builder options, boolean saveToMemory, @Nullable String sessionId,
            @Nullable Map<String, Object> toolContextMap, @Nullable ToolCallbackProvider... toolCallbackProviders) {
        ChatClient.Builder clientBuilder = chatClient.mutate();
        boolean withMemory = chatMemory != null && saveToMemory;
        if (withMemory) {
            clientBuilder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        ChatClient sessionClient = clientBuilder.build();

        ChatClientRequestSpec promptSpec = sessionClient.prompt().system(systemPrompt).user(userMessage).options(options);

        if (withMemory) {
            // Spring AI 2.0.0-M6 removed the advisor-level conversation id (fix for GHSA-q62f-h9x2-gcqc):
            // the per-(course,user) session id must be supplied as a request-time advisor param instead.
            promptSpec = promptSpec.advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, sessionId));
        }
        if (toolContextMap != null) {
            promptSpec = promptSpec.toolContext(toolContextMap);
        }
        if (toolCallbackProviders != null) {
            for (ToolCallbackProvider provider : toolCallbackProviders) {
                if (provider != null) {
                    promptSpec = promptSpec.toolCallbacks(provider);
                }
            }
        }
        return promptSpec.call().chatResponse();
    }

    /**
     * Extract the assistant text from a {@link ChatResponse}, returning an empty string when the
     * model produced no content.
     *
     * @param chatResponse the chat response (may be {@code null})
     * @return the assistant text, or an empty string
     */
    static String extractContent(@Nullable ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
