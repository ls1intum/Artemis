package de.tum.cit.aet.artemis.atlas.service;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/** Records the main orchestrator's verified decision and instructor-facing summary for this run. */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasOrchestratorTerminalToolService {

    /**
     * Completes the run after verification. Returning directly prevents a second model-generated summary.
     *
     * @param verified    whether all requested work was verified against the final index
     * @param message     concise instructor-facing summary, including unresolved work
     * @param toolContext course-scoped invocation context
     * @return the recorded summary
     */
    @Tool(description = "Required final step. After refreshing listCompetencyIndex, call verified=true only when all requested work is complete. Use verified=false and explain unresolved work otherwise. No tools may be called afterwards.", returnDirect = true)
    public String completeOrchestration(@ToolParam(description = "true only after the final index verification passes") boolean verified,
            @ToolParam(description = "concise instructor-facing result and any unresolved work") String message, ToolContext toolContext) {
        AtlasToolCallBudget budget = toolContext == null ? null : AtlasToolCallBudget.existingBudget(toolContext.getContext());
        if (budget == null) {
            throw new IllegalStateException("Orchestration context is missing; completion cannot be recorded.");
        }
        budget.complete(verified, message);
        return message;
    }
}
