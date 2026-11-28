package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.session.SessionService;
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
 * service is the single call site for memory, the system-prompt breadcrumb, and tool setup:
 * <ul>
 * <li>the chat flow renders its system prompt from a template, appends a course-id breadcrumb, and
 * — when a {@link SessionService} bean is configured and {@code saveToMemory} is {@code true} — reads
 * recent conversation text once before invoking the {@code chat} preset. The tool advisor keeps the
 * provider's live tool exchange and metadata for the duration of the request;</li>
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
    private final SessionService sessionService;

    private final ToolCallingAdvisor autonomousToolCallingAdvisor;

    /**
     * Creates the delegation harness with the chat clients of the Atlas presets.
     *
     * @param llmClientService builds the chat clients of the presets
     * @param presets          the Atlas preset assignments
     * @param templateService  renders system-prompt templates from the classpath
     * @param sessionService   the conversation memory store, or {@code null} when memory is disabled
     */
    @Autowired
    public AtlasAgentDelegationService(LlmClientService llmClientService, AtlasLlmPresets presets, AtlasPromptTemplateService templateService,
            @Nullable SessionService sessionService) {
        this(llmClientService.chatClient(presets.chatPreset()), llmClientService.chatClient(presets.orchestratorPreset()), llmClientService.chatClient(presets.workerPreset()),
                templateService, sessionService);
    }

    /**
     * Creates the delegation harness with explicit chat clients.
     *
     * @param chatClient         client of the interactive chat agent
     * @param orchestratorClient client of the autonomous orchestrator
     * @param workerClient       client of the stateless workers
     * @param templateService    renders system-prompt templates from the classpath
     * @param sessionService     the conversation memory store, or {@code null} when memory is disabled
     */
    public AtlasAgentDelegationService(ChatClient chatClient, ChatClient orchestratorClient, ChatClient workerClient, AtlasPromptTemplateService templateService,
            @Nullable SessionService sessionService) {
        this.chatClient = chatClient;
        this.orchestratorClient = orchestratorClient;
        this.workerClient = workerClient;
        this.templateService = templateService;
        this.sessionService = sessionService;
        this.autonomousToolCallingAdvisor = ToolCallingAdvisor.builder()
                .toolCallingManager(DefaultToolCallingManager.builder().unlimitedCallsPerTool().unlimitedTotalToolCalls().resolutionFallbackEnabled(false).build()).build();
    }

    /**
     * Delegate a message to a chat agent: render the system prompt from {@code promptResourcePath},
     * append the course-id breadcrumb, optionally load recent conversation text, and invoke the LLM.
     * Returns the assistant's text response.
     *
     * @param promptResourcePath   classpath path to the system-prompt template (rendered with no variables)
     * @param message              the user-role message
     * @param courseId             the course id appended as a breadcrumb to the system prompt
     * @param sessionId            the conversation id for session memory
     * @param saveToMemory         whether to read and store conversation text (chat: {@code true})
     * @param toolCallbackProvider optional provider of {@code @Tool}-annotated callbacks
     * @return the assistant's text response (never {@code null}; empty string when the model returned no content)
     */
    String delegateToAgent(String promptResourcePath, String message, Long courseId, String sessionId, boolean saveToMemory, @Nullable ToolCallbackProvider toolCallbackProvider) {
        String systemPrompt = templateService.render(promptResourcePath, Map.of());
        String systemPromptWithContext = systemPrompt + "\n\nCONTEXT FOR THIS REQUEST:\nCourse ID: " + courseId;

        // The model cannot supply or override this course scope when using shared content read tools.
        Map<String, Object> toolContext = Map.of(OrchestratorToolContextKeys.COURSE_ID_KEY, courseId);
        SessionService memory = saveToMemory ? sessionService : null;
        List<Message> history = memory != null ? AtlasChatSessions.startTurn(memory, sessionId, message) : List.of();
        ChatResponse chatResponse = invoke(chatClient, systemPromptWithContext, message, history, toolContext, null, toolCallbackProvider);
        String reply = extractContent(chatResponse);
        if (memory != null && !reply.isBlank()) {
            memory.appendMessage(sessionId, new AssistantMessage(reply));
        }
        return reply;
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
        return invoke(orchestratorClient, systemPrompt, userMessage, List.of(), toolContextMap, usageRecorder, toolCallbackProviders);
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
        return invoke(workerClient, systemPrompt, userMessage, List.of(), toolContextMap, usageRecorder, toolCallbackProviders);
    }

    /**
     * Assembles a request with the supplied history and tools, then returns the {@link ChatResponse}.
     */
    private ChatResponse invoke(ChatClient client, String systemPrompt, String userMessage, List<Message> history, @Nullable Map<String, Object> toolContextMap,
            @Nullable Consumer<ChatResponse> usageRecorder, @Nullable ToolCallbackProvider... toolCallbackProviders) {
        boolean autonomous = usageRecorder != null;
        ChatClientRequestSpec promptSpec = client.prompt().system(systemPrompt).messages(history).user(userMessage);

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
