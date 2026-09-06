package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.errorJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.isBlank;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.WorkerCompletionDTO;

/** One-shot terminal tool shared by the stateless Creator, Assigner, and Editor workers. */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasWorkerTerminalToolService {

    private final ObjectMapper objectMapper;

    public AtlasWorkerTerminalToolService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Completes a worker request after it has inspected course state or applied an action.
     *
     * @param success     whether the assigned semantic batch was completed
     * @param message     concise outcome or actionable failure reason
     * @param toolContext request-scoped terminal holder and evidence counters
     * @return acknowledgement JSON, or an error when the terminal contract is violated
     */
    @Tool(description = "Finish this worker task exactly once. Set success=false when any requested action could not be completed, and explain the blocker in message.")
    public String completeWorkerTask(@ToolParam(description = "true only when the complete assigned batch succeeded") boolean success,
            @ToolParam(description = "concise outcome or actionable failure reason") String message, ToolContext toolContext) {
        if (isBlank(message)) {
            return errorJson(objectMapper, "message is required.");
        }
        AtomicReference<WorkerCompletionDTO> holder = completionHolder(toolContext);
        if (holder == null) {
            return errorJson(objectMapper, "No worker completion context available.");
        }
        if (!hasWorkerEvidence(toolContext)) {
            return errorJson(objectMapper, "Inspect course state or apply an action before completing the worker task.");
        }
        WorkerCompletionDTO completion = new WorkerCompletionDTO(success, message);
        if (!holder.compareAndSet(null, completion)) {
            return errorJson(objectMapper, "Worker task was already completed.");
        }
        return toJson(objectMapper, Map.of("completed", true, "success", success));
    }

    private static boolean hasWorkerEvidence(@Nullable ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return false;
        }
        Object readValue = toolContext.getContext().get(OrchestratorToolContextKeys.WORKER_READ_COUNT_KEY);
        boolean hasRead = readValue instanceof AtomicInteger readCount && readCount.get() > 0;
        OrchestratorToolContextKeys.AppliedActionsBuffer buffer = OrchestratorToolHelpers.appliedActionsBufferFromContext(toolContext);
        Object startValue = toolContext.getContext().get(OrchestratorToolContextKeys.WORKER_ACTION_START_KEY);
        int start = startValue instanceof Number number ? number.intValue() : 0;
        return hasRead || buffer != null && buffer.actions().size() > start;
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
