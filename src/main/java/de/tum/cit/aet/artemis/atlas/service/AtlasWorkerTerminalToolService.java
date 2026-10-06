package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.errorJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.isBlank;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.markWorkerCompletion;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.markWorkerToolActivity;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.toJson;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasLLMEnabled;
import de.tum.cit.aet.artemis.atlas.dto.WorkerCompletionDTO;

/** One-shot terminal tool shared by the stateless Creator, Assigner, and Editor workers. */
@Lazy
@Service
@Conditional(AtlasLLMEnabled.class)
public class AtlasWorkerTerminalToolService {

    private final JsonMapper objectMapper;

    public AtlasWorkerTerminalToolService(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Completes a worker request after it has inspected course state or received a mutation outcome. A read that
     * returned an error is sufficient evidence only for {@code success=false}, so a worker blocked by a stale id or an
     * extraction failure can report that specific blocker instead of being treated as never having completed.
     *
     * @param success     whether the assigned semantic batch was completed
     * @param message     concise outcome or actionable failure reason
     * @param toolContext request-scoped terminal holder and evidence counters
     * @return acknowledgement JSON, or an error when the terminal contract is violated
     */
    @Tool(description = "Finish this worker task exactly once. Set success=false when any requested action could not be completed, and explain the blocker in message.")
    public String completeWorkerTask(@ToolParam(description = "true only when the complete assigned batch succeeded") boolean success,
            @ToolParam(description = "concise outcome or actionable failure reason") String message, ToolContext toolContext) {
        long completionSequence = markWorkerToolActivity(toolContext);
        if (isBlank(message)) {
            return errorJson(objectMapper, "message is required.");
        }
        AtomicReference<WorkerCompletionDTO> holder = completionHolder(toolContext);
        if (holder == null) {
            return errorJson(objectMapper, "No worker completion context available.");
        }
        if (!hasSuccessEvidence(toolContext)) {
            boolean hasReadOutcome = hasPositiveCount(toolContext, OrchestratorToolContextKeys.WORKER_READ_OUTCOME_COUNT_KEY);
            if (!hasReadOutcome) {
                return errorJson(objectMapper, "Inspect course state or receive a mutation outcome before completing the worker task.");
            }
            if (success) {
                return errorJson(objectMapper, "success=true requires a successful course-state read or a mutation outcome; report the failed read with success=false.");
            }
        }
        WorkerCompletionDTO completion = new WorkerCompletionDTO(success, message);
        if (!holder.compareAndSet(null, completion)) {
            return errorJson(objectMapper, "Worker task was already completed.");
        }
        markWorkerCompletion(toolContext, completionSequence);
        return toJson(objectMapper, Map.of("completed", true, "success", success));
    }

    /**
     * Evidence that can back any completion: a successful read, a completed mutation outcome (including errors and
     * no-ops), or an applied action. Failed reads are deliberately excluded; they only back a {@code success=false}
     * completion that reports the read failure as the blocker.
     */
    private static boolean hasSuccessEvidence(@Nullable ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return false;
        }
        OrchestratorToolContextKeys.AppliedActionsBuffer buffer = OrchestratorToolHelpers.appliedActionsBufferFromContext(toolContext);
        Object startValue = toolContext.getContext().get(OrchestratorToolContextKeys.WORKER_ACTION_START_KEY);
        int start = startValue instanceof Number number ? number.intValue() : 0;
        return hasPositiveCount(toolContext, OrchestratorToolContextKeys.WORKER_READ_COUNT_KEY)
                || hasPositiveCount(toolContext, OrchestratorToolContextKeys.WORKER_MUTATION_OUTCOME_COUNT_KEY) || buffer != null && buffer.actions().size() > start;
    }

    private static boolean hasPositiveCount(@Nullable ToolContext toolContext, String key) {
        return toolContext != null && toolContext.getContext() != null && toolContext.getContext().get(key) instanceof AtomicInteger count && count.get() > 0;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static AtomicReference<WorkerCompletionDTO> completionHolder(@Nullable ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(OrchestratorToolContextKeys.WORKER_COMPLETION_KEY);
        return value instanceof AtomicReference<?> ? (AtomicReference<WorkerCompletionDTO>) value : null;
    }
}
