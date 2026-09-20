package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasAgentProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasResponsesApiConfiguration;
import de.tum.cit.aet.artemis.atlas.config.AtlasResponsesApiConfiguration.AtlasResponsesChatClient;

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

    @Nullable
    private final ChatClient responsesChatClient;

    private final boolean responsesApiEnabled;

    private final ToolCallingAdvisor autonomousToolCallingAdvisor;

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
        this(chatClient, templateService, chatMemory, properties, null, false);
    }

    /**
     * Creates the Spring-managed delegation harness with the optional autonomous Responses client.
     *
     * @param chatClient             shared ChatClient used by interactive Atlas flows
     * @param templateService        classpath prompt renderer
     * @param chatMemory             optional interactive conversation memory
     * @param properties             interactive chat model configuration
     * @param orchestratorProperties autonomous model and feature-switch configuration
     * @param responsesChatClient    optional holder for the isolated Responses ChatClient
     */
    @Autowired
    public AtlasAgentDelegationService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ChatMemory chatMemory,
            AtlasAgentProperties properties, AtlasOrchestratorProperties orchestratorProperties,
            @Qualifier(AtlasResponsesApiConfiguration.ATLAS_RESPONSES_CHAT_CLIENT) @Nullable AtlasResponsesChatClient responsesChatClient) {
        this(chatClient, templateService, chatMemory, properties, responsesChatClient == null ? null : responsesChatClient.chatClient(),
                orchestratorProperties.responsesApiEnabled());
    }

    private AtlasAgentDelegationService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ChatMemory chatMemory,
            AtlasAgentProperties properties, @Nullable ChatClient responsesChatClient, boolean responsesApiEnabled) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.chatMemory = chatMemory;
        this.deploymentName = properties.chatModel();
        this.temperature = properties.temperature();
        this.responsesChatClient = responsesChatClient;
        this.responsesApiEnabled = responsesApiEnabled;
        this.autonomousToolCallingAdvisor = ToolCallingAdvisor.builder()
                .toolCallingManager(DefaultToolCallingManager.builder().unlimitedCallsPerTool().unlimitedTotalToolCalls().resolutionFallbackEnabled(false).build()).build();
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
        ChatResponse chatResponse = invoke(chatClient, systemPromptWithContext, message, options, saveToMemory, sessionId, null, false, toolCallbackProvider);
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
        ChatClient selectedClient = responsesApiEnabled ? responsesChatClient : chatClient;
        if (selectedClient == null) {
            String clientName = responsesApiEnabled ? "Atlas Responses ChatClient" : "ChatClient";
            throw new IllegalStateException(clientName + " is not configured. Atlas Agent delegation is unavailable.");
        }
        return invoke(selectedClient, systemPrompt, userMessage, options, false, null, toolContextMap, true, toolCallbackProviders);
    }

    /** @return whether the configured autonomous transport is available */
    public boolean isOrchestratorAvailable() {
        return (responsesApiEnabled ? responsesChatClient : chatClient) != null;
    }

    /**
     * Single shared invocation path: builds the (optionally memory-advised) client, assembles the
     * request spec with options / tool context / tool callbacks, and returns the {@link ChatResponse}.
     */
    private ChatResponse invoke(ChatClient baseClient, String systemPrompt, String userMessage, OpenAiChatOptions.Builder options, boolean saveToMemory, @Nullable String sessionId,
            @Nullable Map<String, Object> toolContextMap, boolean autonomous, @Nullable ToolCallbackProvider... toolCallbackProviders) {
        ChatClient.Builder clientBuilder = baseClient.mutate();
        boolean withMemory = chatMemory != null && saveToMemory;
        if (withMemory) {
            clientBuilder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        ChatClient sessionClient = clientBuilder.build();

        ChatClientRequestSpec promptSpec = sessionClient.prompt().system(systemPrompt).user(userMessage).options(options);

        AtlasToolCallBudget budget = null;
        if (autonomous) {
            if (toolContextMap == null) {
                throw new IllegalArgumentException("Autonomous Atlas rounds require a tool context.");
            }
            budget = AtlasToolCallBudget.budgetForContext(toolContextMap);
            promptSpec = promptSpec.system(systemPrompt + "\n\n" + budget.instructions());
            promptSpec = promptSpec.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false));
            promptSpec = promptSpec.advisors(autonomousToolCallingAdvisor);
        }

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
                    promptSpec = promptSpec.toolCallbacks(autonomous ? AtlasToolCallBudget.decorate(provider, budget) : provider);
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
