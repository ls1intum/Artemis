package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.errorJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.hasFreshVerificationRead;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.isBlank;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.toJson;

import java.util.Map;
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
import de.tum.cit.aet.artemis.atlas.dto.OrchestrationCompletionDTO;

/** One-shot terminal tool for the main Atlas orchestration request. */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasOrchestratorTerminalToolService {

    private final ObjectMapper objectMapper;

    public AtlasOrchestratorTerminalToolService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Completes the orchestration request. Verified completion requires a competency-index read newer
     * than the latest completed worker delegation.
     *
     * @param verified    whether the refreshed course state satisfies the plan
     * @param message     concise overall outcome or unresolved failure reason
     * @param toolContext request-scoped verification markers and completion holder
     * @return acknowledgement JSON, or an error when the terminal contract is violated
     */
    @Tool(description = "Finish the orchestration exactly once. verified=true is accepted only after listCompetencyIndex was called after the latest worker delegation.")
    public String completeOrchestration(@ToolParam(description = "true only when a post-delegation competency-index refresh verifies the final state") boolean verified,
            @ToolParam(description = "concise final outcome or unresolved failure reason") String message, ToolContext toolContext) {
        if (isBlank(message)) {
            return errorJson(objectMapper, "message is required.");
        }
        AtomicReference<OrchestrationCompletionDTO> holder = completionHolder(toolContext);
        if (holder == null) {
            return errorJson(objectMapper, "No orchestration completion context available.");
        }
        if (verified && !hasFreshVerificationRead(toolContext)) {
            return errorJson(objectMapper, "Verified completion requires a competency-index read after the latest delegation.");
        }
        OrchestrationCompletionDTO completion = new OrchestrationCompletionDTO(verified, message);
        if (!holder.compareAndSet(null, completion)) {
            return errorJson(objectMapper, "Orchestration was already completed.");
        }
        return toJson(objectMapper, Map.of("completed", true, "verified", verified));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static AtomicReference<OrchestrationCompletionDTO> completionHolder(@Nullable ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(OrchestratorToolContextKeys.ORCHESTRATION_COMPLETION_KEY);
        return value instanceof AtomicReference<?> ? (AtomicReference<OrchestrationCompletionDTO>) value : null;
    }
}
