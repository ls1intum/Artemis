package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the run is doing at the moment one progress event was produced: which substep it is inside, how far the agent loop has got, and the run's aggregate work so far.
 * <p>
 * It exists because a model call is synchronous and can take minutes. Without an event emitted <em>before</em> that call, the wall clock a run spends waiting is invisible and an
 * instructor cannot tell a working run from a hung one. {@code waitingOnModel} is true on exactly the event emitted immediately before a provider call and false on every later
 * event, so the client can say "waiting on the model since …" and stop saying it as soon as anything else happens.
 * <p>
 * The three counters are monotonic over the whole run, so the client can render an aggregate without replaying the transcript.
 * <p>
 * Purely observational: no scheduling decision, gate, or verdict reads these numbers.
 * <p>
 * {@link Serializable} because it is carried inside {@link ExerciseGenerationEventDTO}, which is retained in a distributed Hazelcast map for reconnect/replay.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "What a generation run is doing right now, so a long synchronous model call is visible instead of silent")
public record ExerciseGenerationActivityDTO(
        @Schema(description = "Machine-readable substep the run is inside (concept, spec, artifacts, statement), or null when no substep applies") @Nullable String step,
        @Schema(description = "The 1-based authoring attempt", requiredMode = Schema.RequiredMode.REQUIRED) int attempt,
        @Schema(description = "The 1-based agent turn inside the current agent session", requiredMode = Schema.RequiredMode.REQUIRED) int turn,
        @Schema(description = "Whether this event was emitted immediately before a provider call the run is now waiting on", requiredMode = Schema.RequiredMode.REQUIRED) boolean waitingOnModel,
        @Schema(description = "How many provider calls the run has completed so far", requiredMode = Schema.RequiredMode.REQUIRED) int modelCalls,
        @Schema(description = "How many tool calls the run has executed so far", requiredMode = Schema.RequiredMode.REQUIRED) int toolCalls,
        @Schema(description = "How many successful file writes, edits, and deletions the run has made so far", requiredMode = Schema.RequiredMode.REQUIRED) int filesWritten)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
