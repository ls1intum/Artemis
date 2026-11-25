package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasLLMEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasLlmPresets;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;

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
 * {@code chat} preset;</li>
 * <li>the orchestrator and worker flows supply an already-assembled system prompt, use the {@code orchestrator}
 * and {@code worker} presets, run with memory OFF so each round is a fresh call, and record each provider
 * response before usage is combined.</li>
 * </ul>
 * Extracted from {@code AtlasAgentService} to break a circular dependency with
 * {@code AtlasAgentToolsService}.
 */
@Lazy
@Service
@Conditional(AtlasLLMEnabled.class)
public class AtlasAgentDelegationService {

    private final ChatClient chatClient;

    private final ChatClient orchestratorClient;

    private final ChatClient workerClient;

    private final AtlasPromptTemplateService templateService;

    @Nullable
    private final ChatMemory chatMemory;

    private final ToolCallingAdvisor autonomousToolCallingAdvisor;

    /**
     * Creates the delegation harness with the chat clients of the Atlas presets.
     *
     * @param llmClientService builds the chat clients of the presets
     * @param presets          the Atlas preset assignments
     * @param templateService  renders system-prompt templates from the classpath
     * @param chatMemory       the conversation memory store, or {@code null} when memory is disabled
     */
    @Autowired
    public AtlasAgentDelegationService(LlmClientService llmClientService, AtlasLlmPresets presets, AtlasPromptTemplateService templateService, @Nullable ChatMemory chatMemory) {
        this(llmClientService.chatClient(presets.chatPreset()), llmClientService.chatClient(presets.orchestratorPreset()), llmClientService.chatClient(presets.workerPreset()),
                templateService, chatMemory);
    }

    /**
     * Creates the delegation harness with explicit chat clients.
     *
     * @param chatClient         client of the interactive chat agent
     * @param orchestratorClient client of the autonomous orchestrator
     * @param workerClient       client of the stateless workers
     * @param templateService    renders system-prompt templates from the classpath
     * @param chatMemory         the conversation memory store, or {@code null} when memory is disabled
     */
    public AtlasAgentDelegationService(ChatClient chatClient, ChatClient orchestratorClient, ChatClient workerClient, AtlasPromptTemplateService templateService,
            @Nullable ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.orchestratorClient = orchestratorClient;
        this.workerClient = workerClient;
        this.templateService = templateService;
        this.chatMemory = chatMemory;
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
        String systemPrompt = templateService.render(promptResourcePath, Map.of());
        String systemPromptWithContext = systemPrompt + "\n\nCONTEXT FOR THIS REQUEST:\nCourse ID: " + courseId;

        // The model cannot supply or override this course scope when using shared content read tools.
        Map<String, Object> toolContext = Map.of(OrchestratorToolContextKeys.COURSE_ID_KEY, courseId);
        ChatResponse chatResponse = invoke(chatClient, systemPromptWithContext, message, saveToMemory, sessionId, toolContext, null, toolCallbackProvider);
        return extractContent(chatResponse);
    }

    /**
     * Delegate an autonomous orchestrator round: invoke the {@code orchestrator} preset with an already-assembled
     * system prompt, the mutable tool context, and the supplied tool providers, with chat memory OFF (each round is a
     * fresh call). Returns the raw {@link ChatResponse} for completion checks. Individual responses are recorded through
     * the callback.
     *
     * @param systemPrompt          the fully-assembled system prompt (no breadcrumb is appended here)
     * @param userMessage           the user-role message
     * @param toolContextMap        mutable map stashed into the request's tool context; mutations performed
     *                                  inside tool bodies are visible to the caller after this method returns
     * @param usageRecorder         records each model response before tool execution and usage accumulation
     * @param toolCallbackProviders the tool providers to expose to the model (read + planning)
     * @return the raw {@link ChatResponse} (never {@code null})
     */
    ChatResponse delegateOrchestratorRound(String systemPrompt, String userMessage, Map<String, Object> toolContextMap, Consumer<ChatResponse> usageRecorder,
            ToolCallbackProvider... toolCallbackProviders) {
        return invoke(orchestratorClient, systemPrompt, userMessage, false, null, toolContextMap, usageRecorder, toolCallbackProviders);
    }

    /**
     * Delegate one stateless worker round with the {@code worker} preset; otherwise like
     * {@link #delegateOrchestratorRound}.
     *
     * @param systemPrompt          the fully-assembled system prompt
     * @param userMessage           the user-role message
     * @param toolContextMap        mutable tool context of the worker
     * @param usageRecorder         records each model response before tool execution and usage accumulation
     * @param toolCallbackProviders the tool providers to expose to the worker
     * @return the raw {@link ChatResponse} (never {@code null})
     */
    ChatResponse delegateWorkerRound(String systemPrompt, String userMessage, Map<String, Object> toolContextMap, Consumer<ChatResponse> usageRecorder,
            ToolCallbackProvider... toolCallbackProviders) {
        return invoke(workerClient, systemPrompt, userMessage, false, null, toolContextMap, usageRecorder, toolCallbackProviders);
    }

    /**
     * Single shared invocation path: builds the (optionally memory-advised) client, assembles the
     * request spec with options / tool context / tool callbacks, and returns the {@link ChatResponse}.
     */
    private ChatResponse invoke(ChatClient baseClient, String systemPrompt, String userMessage, boolean saveToMemory, @Nullable String sessionId,
            @Nullable Map<String, Object> toolContextMap, @Nullable Consumer<ChatResponse> usageRecorder, @Nullable ToolCallbackProvider... toolCallbackProviders) {
        boolean autonomous = usageRecorder != null;
        ChatClient.Builder clientBuilder = baseClient.mutate();
        boolean withMemory = chatMemory != null && saveToMemory;
        if (withMemory) {
            clientBuilder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        ChatClient sessionClient = clientBuilder.build();

        ChatClientRequestSpec promptSpec = sessionClient.prompt().system(systemPrompt).user(userMessage);

        AtlasToolCallBudget budget = null;
        if (autonomous) {
            if (toolContextMap == null) {
                throw new IllegalArgumentException("Autonomous Atlas rounds require a tool context.");
            }
            budget = AtlasToolCallBudget.budgetForContext(toolContextMap);
            promptSpec = promptSpec.system(systemPrompt + "\n\n" + budget.instructions());
            promptSpec = promptSpec.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false));
            promptSpec = promptSpec.advisors(autonomousToolCallingAdvisor);
            promptSpec = promptSpec.advisors(new AtlasTokenUsageAdvisor(usageRecorder, autonomousToolCallingAdvisor.getOrder()));
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
                    promptSpec = promptSpec.tools(autonomous ? AtlasToolCallBudget.decorate(provider, budget) : provider);
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
