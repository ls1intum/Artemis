package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJobPhase;

/**
 * Websocket event published on "/user/topic/hyperion/variant-generation/jobs/{jobId}" via
 * HyperionWebsocketService. Events are fire-and-forget; the Hazelcast job record is
 * authoritative and the client re-syncs from REST on reconnect.
 *
 * @param type              event kind
 * @param phase             current phase (steps in the wizard are derived from VariantJobPhase — single source
 *                              of truth via the OpenAPI client)
 * @param attempt           repair attempt counter, e.g. "Building solution repository — attempt 2/3"
 * @param maxAttempts       attempt budget
 * @param detail            type-specific sub-label ("Validating quiz questions") or failure detail
 * @param variantExerciseId set on DONE — the client fetches the created exercise
 * @param warnings          set on DONE when the terminal state is DRAFT_WITH_WARNINGS
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantGenerationEventDTO(Type type, VariantJobPhase phase, Integer attempt, Integer maxAttempts, String detail, Long variantExerciseId, List<String> warnings)
        implements Serializable {

    /**
     * Event kinds. STEP_OUTPUT signals that a phase recorded a step output and carries only its short summary
     * in {@code detail}; the open modal fetches the full panel body from the job-detail endpoint.
     */
    public enum Type {
        PHASE_CHANGED, PROGRESS, ATTEMPT, STEP_OUTPUT, DONE, FAILED, CANCELLED
    }

    /**
     * @param phase the new phase
     * @return a PHASE_CHANGED event
     */
    public static VariantGenerationEventDTO phaseChanged(VariantJobPhase phase) {
        return new VariantGenerationEventDTO(Type.PHASE_CHANGED, phase, null, null, null, null, null);
    }

    /**
     * @param phase       current phase
     * @param attempt     current attempt (1-based)
     * @param maxAttempts attempt budget
     * @param detail      type-specific sub-label
     * @return an ATTEMPT event
     */
    public static VariantGenerationEventDTO attempt(VariantJobPhase phase, int attempt, int maxAttempts, String detail) {
        return new VariantGenerationEventDTO(Type.ATTEMPT, phase, attempt, maxAttempts, detail, null, null);
    }

    /**
     * @param phase  current phase
     * @param detail type-specific progress sub-label
     * @return a PROGRESS event
     */
    public static VariantGenerationEventDTO progress(VariantJobPhase phase, String detail) {
        return new VariantGenerationEventDTO(Type.PROGRESS, phase, null, null, detail, null, null);
    }

    /**
     * @param phase   the phase the output belongs to
     * @param summary the step-output summary (panel header)
     * @return a STEP_OUTPUT event
     */
    public static VariantGenerationEventDTO stepOutput(VariantJobPhase phase, String summary) {
        return new VariantGenerationEventDTO(Type.STEP_OUTPUT, phase, null, null, summary, null, null);
    }

    /**
     * @param terminalPhase     COMPLETED or DRAFT_WITH_WARNINGS
     * @param variantExerciseId the created exercise
     * @param warnings          non-empty for DRAFT_WITH_WARNINGS
     * @return a DONE event
     */
    public static VariantGenerationEventDTO done(VariantJobPhase terminalPhase, Long variantExerciseId, List<String> warnings) {
        return new VariantGenerationEventDTO(Type.DONE, terminalPhase, null, null, null, variantExerciseId, warnings);
    }

    /**
     * @param detail failure description including the phase the job failed in
     * @return a FAILED event
     */
    public static VariantGenerationEventDTO failed(String detail) {
        return new VariantGenerationEventDTO(Type.FAILED, VariantJobPhase.FAILED, null, null, detail, null, null);
    }

    /**
     * @return a CANCELLED event
     */
    public static VariantGenerationEventDTO cancelled() {
        return new VariantGenerationEventDTO(Type.CANCELLED, VariantJobPhase.CANCELLED, null, null, null, null, null);
    }
}
