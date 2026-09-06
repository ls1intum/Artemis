package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.markDelegation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasToolSurface;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.WorkerCompletionDTO;
import de.tum.cit.aet.artemis.atlas.dto.WorkerResultDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/** Main-orchestrator tools that synchronously delegate semantic action batches to isolated workers. */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class OrchestratorDelegationToolsService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorDelegationToolsService.class);

    private static final String ORCHESTRATION_PIPELINE_ID = "ATLAS_ORCHESTRATION";

    private static final String CREATOR_PROMPT_PATH = "prompts/atlas/orchestrator_creator_worker_prompt.st";

    private static final String ASSIGNER_PROMPT_PATH = "prompts/atlas/orchestrator_assigner_worker_prompt.st";

    private static final String EDITOR_PROMPT_PATH = "prompts/atlas/orchestrator_editor_worker_prompt.st";

    private final AtlasPromptTemplateService templateService;

    private final AtlasAgentDelegationService delegationService;

    private final ToolCallbackProvider readTools;

    private final ToolCallbackProvider creatorTools;

    private final ToolCallbackProvider assignerTools;

    private final ToolCallbackProvider editorTools;

    private final ToolCallbackProvider terminalTools;

    private final String workerDeploymentName;

    private final String workerReasoningEffort;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    public OrchestratorDelegationToolsService(AtlasPromptTemplateService templateService, AtlasAgentDelegationService delegationService,
            @Qualifier("orchestratorReadToolCallbackProvider") AtlasToolSurface readTools, @Qualifier("creatorToolCallbackProvider") AtlasToolSurface creatorTools,
            @Qualifier("assignerToolCallbackProvider") AtlasToolSurface assignerTools, @Qualifier("editorToolCallbackProvider") AtlasToolSurface editorTools,
            @Qualifier("workerTerminalToolCallbackProvider") AtlasToolSurface terminalTools, AtlasOrchestratorProperties properties, LLMTokenUsageService llmTokenUsageService,
            UserRepository userRepository) {
        this.templateService = templateService;
        this.delegationService = delegationService;
        this.readTools = readTools.provider();
        this.creatorTools = creatorTools.provider();
        this.assignerTools = assignerTools.provider();
        this.editorTools = editorTools.provider();
        this.terminalTools = terminalTools.provider();
        this.workerDeploymentName = properties.workerModel();
        this.workerReasoningEffort = properties.workerReasoningEffort();
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
    }

    @Tool(description = "Delegate one semantic batch of competency creations to a stateless Creator worker. The worker can read course state and call createCompetency only.")
    public WorkerResultDTO delegateToCreator(@ToolParam(description = "complete, self-contained creation batch with evidence and expected outcome") String task,
            ToolContext toolContext) {
        return delegate(WorkerRole.CREATOR, task, toolContext, creatorTools);
    }

    @Tool(description = "Delegate one semantic batch of exercise link assignments or removals to a stateless Assigner worker. The worker can read course state and call assignment tools only.")
    public WorkerResultDTO delegateToAssigner(
            @ToolParam(description = "complete, self-contained exercise assignment batch with ids, weights, evidence, and expected outcome") String task, ToolContext toolContext) {
        return delegate(WorkerRole.ASSIGNER, task, toolContext, assignerTools);
    }

    @Tool(description = "Delegate one semantic batch of competency edits or deletions to a stateless Editor worker. The worker can read course state and call edit/delete tools only.")
    public WorkerResultDTO delegateToEditor(@ToolParam(description = "complete, self-contained edit/delete batch with evidence and expected outcome") String task,
            ToolContext toolContext) {
        return delegate(WorkerRole.EDITOR, task, toolContext, editorTools);
    }

    private WorkerResultDTO delegate(WorkerRole role, @Nullable String task, @Nullable ToolContext parentContext, ToolCallbackProvider roleTools) {
        if (task == null || task.isBlank()) {
            return new WorkerResultDTO(false, "Worker task must not be blank.", List.of());
        }
        Long courseId = OrchestratorToolHelpers.courseIdFromContext(parentContext);
        OrchestratorToolContextKeys.AppliedActionsBuffer buffer = OrchestratorToolHelpers.appliedActionsBufferFromContext(parentContext);
        if (courseId == null || buffer == null) {
            return new WorkerResultDTO(false, "Worker delegation context is incomplete.", List.of());
        }

        int actionStart = buffer.actions().size();
        AtomicReference<WorkerCompletionDTO> completionHolder = OrchestratorToolContextKeys.newWorkerCompletionHolder();
        Map<String, Object> workerContext = new HashMap<>();
        workerContext.put(OrchestratorToolContextKeys.COURSE_ID_KEY, courseId);
        workerContext.put(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, buffer);
        workerContext.put(OrchestratorToolContextKeys.WORKER_COMPLETION_KEY, completionHolder);
        workerContext.put(OrchestratorToolContextKeys.WORKER_READ_COUNT_KEY, new AtomicInteger());
        workerContext.put(OrchestratorToolContextKeys.WORKER_ACTION_START_KEY, actionStart);
        copyContextValue(parentContext, workerContext, OrchestratorToolContextKeys.LEARNING_OBJECT_ID_KEY);

        try {
            String systemPrompt = templateService.render(role.promptPath, Map.of());
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().deploymentName(workerDeploymentName).reasoningEffort(workerReasoningEffort);
            ChatResponse response = delegationService.delegateOrchestratorRound(systemPrompt,
                    task + "\n\nExecute this batch, then call completeWorkerTask exactly once with the outcome.", options, workerContext, readTools, roleTools, terminalTools);
            trackUsage(response, courseId, workerContext);
            WorkerCompletionDTO completion = completionHolder.get();
            if (completion == null) {
                return new WorkerResultDTO(false, role.displayName + " worker returned without calling completeWorkerTask.", actionSlice(buffer, actionStart));
            }
            return new WorkerResultDTO(completion.success(), completion.message(), actionSlice(buffer, actionStart));
        }
        catch (Exception ex) {
            log.warn("Atlas {} worker failed after applying {} action(s): {}", role.displayName, actionSlice(buffer, actionStart).size(), ex.getMessage(), ex);
            return new WorkerResultDTO(false, role.displayName + " worker failed while executing its batch.", actionSlice(buffer, actionStart));
        }
        finally {
            markDelegation(parentContext);
        }
    }

    private void trackUsage(ChatResponse response, long courseId, Map<String, Object> workerContext) {
        Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);
        Object exerciseValue = workerContext.get(OrchestratorToolContextKeys.LEARNING_OBJECT_ID_KEY);
        Long exerciseId = exerciseValue instanceof Number number ? number.longValue() : null;
        llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.ATLAS, ORCHESTRATION_PIPELINE_ID,
                builder -> builder.withCourse(courseId).withExercise(exerciseId).withUser(userId));
    }

    private static List<AppliedActionDTO> actionSlice(OrchestratorToolContextKeys.AppliedActionsBuffer buffer, int start) {
        synchronized (buffer.actions()) {
            int safeStart = Math.min(start, buffer.actions().size());
            return List.copyOf(buffer.actions().subList(safeStart, buffer.actions().size()));
        }
    }

    private static void copyContextValue(@Nullable ToolContext source, Map<String, Object> target, String key) {
        if (source != null && source.getContext() != null) {
            Object value = source.getContext().get(key);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private enum WorkerRole {

        CREATOR("Creator", CREATOR_PROMPT_PATH), ASSIGNER("Assigner", ASSIGNER_PROMPT_PATH), EDITOR("Editor", EDITOR_PROMPT_PATH);

        private final String displayName;

        private final String promptPath;

        WorkerRole(String displayName, String promptPath) {
            this.displayName = displayName;
            this.promptPath = promptPath;
        }
    }
}
