package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJobPhase;

/**
 * Websocket event published on "/user/topic/hyperion/variant-generation/jobs/{jobId}" via
 * HyperionWebsocketService (plan Section 5.2). Events are fire-and-forget; the Hazelcast job record is
 * authoritative and the client re-syncs from REST on reconnect (Section 5.4, "State handling").
 *
 * @param type              event kind
 * @param phase             current phase (steps in the wizard are derived from VariantJobPhase — single source
 *                              of truth via the OpenAPI client, Section 5.2)
 * @param attempt           repair attempt counter, e.g. "Building solution repository — attempt 2/3"
 * @param maxAttempts       attempt budget
 * @param detail            type-specific sub-label ("Validating quiz questions") or failure detail
 * @param variantExerciseId set on DONE — the client fetches the created exercise (Section 5.3, point 2)
 * @param warnings          set on DONE when the terminal state is DRAFT_WITH_WARNINGS
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantGenerationEventDTO(Type type, VariantJobPhase phase, Integer attempt, Integer maxAttempts, String detail, Long variantExerciseId, List<String> warnings)
        implements Serializable {

    /**
     * Event kinds per plan Section 5.2. STEP_OUTPUT carries the freshly recorded step output so open modals
     * can populate the expandable panel without a REST round-trip (Section 2.4).
     */
    public enum Type {
        PHASE_CHANGED, PROGRESS, ATTEMPT, STEP_OUTPUT, DONE, FAILED, CANCELLED
    }

    // TODO (Sonnet): STEP_OUTPUT needs the payload — either add a StepOutputDTO component here (nullable, only set
    // for STEP_OUTPUT) or let the client fetch the job-detail endpoint on STEP_OUTPUT; pick the inline payload
    // (plan Section 2.4 wants live panel updates during the run) and keep it truncated server-side.

    // TODO (Sonnet): Static factories per type (phaseChanged(...), attempt(...), done(...), failed(...)) so the
    // job service publishes consistently shaped events (single writer, see ExerciseVariantJobService TODOs).
}
