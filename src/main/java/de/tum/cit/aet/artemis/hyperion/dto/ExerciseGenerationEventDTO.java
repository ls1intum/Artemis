package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single progress event streamed to the instructor over the websocket while an agentic whole-exercise generation/adaptation runs.
 * <p>
 * {@link Serializable} because it is retained (inside {@code JobTranscript}) in a distributed Hazelcast map for reconnect/replay.
 * <p>
 * Everything except {@code type} and {@code timestamp} is populated only on the events it describes and is {@code null} elsewhere. {@link TerminationReason} is orthogonal to
 * {@link CompletionStatus}: the first says why the attempt loop stopped producing candidates, the second what became of the result.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A progress event streamed to the instructor while an agentic whole-exercise generation or adaptation runs")
public record ExerciseGenerationEventDTO(@Schema(description = "The event kind", requiredMode = Schema.RequiredMode.REQUIRED) Type type,
        @Schema(description = "Human-readable progress or result message") @Nullable String message,
        @Schema(description = "Stable instructor-facing phase of the generation journey") @Nullable Phase phase,
        @Schema(description = "On a terminal DONE event, whether the run succeeded, needs review, or partially completed") @Nullable CompletionStatus completionStatus,
        @Schema(description = "On a terminal event, the structured verification verdict") @Nullable ExerciseGenerationVerdictDTO verdict,
        @Schema(description = "On a terminal event, whether the live exercise changed and open editors should refresh") @Nullable Boolean liveExerciseChanged,
        @Schema(description = "Exact saved commit hashes keyed by repository name") @Nullable Map<String, String> savedRepositoryCommits,
        @Schema(description = "The exact saved exercise version id, on a successful terminal event") @Nullable Long savedExerciseVersionId,
        @Schema(description = "On a terminal event, why the generation run ended") @Nullable TerminationReason terminationReason,
        @Schema(description = "On a repair-round progress event, that round's finding bookkeeping") @Nullable ExerciseGenerationRepairRoundDTO repairRound,
        @Schema(description = "The moment the event was produced", requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Type {
        STARTED, PROGRESS, DONE, CANCELLED, ERROR
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Phase {
        PREPARING, DESIGNING, SPECIFYING, AUTHORING, VERIFYING, REVIEWING, REPAIRING, SAVING
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum CompletionStatus {
        /** The exercise was verified and saved with no blocking quality findings. */
        SUCCESS,
        /** The mechanically verified exercise was saved, but automated quality findings require instructor review. */
        NEEDS_REVIEW,
        /** Saving did not complete; live changes may exist and require manual review. */
        PARTIAL
    }

    /**
     * Why one generation or adaptation run stopped producing candidates. Every exit of the attempt loop maps to exactly one value; the run-level controls the loop cannot see
     * (wall clock, token budget) refine {@link #CANCELLED} into their own value.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum TerminationReason {
        /** The candidate was mechanically verified and its quality review found nothing blocking. */
        CONVERGED,
        /** Every semantic repair round the run was allowed had been spent while blocking findings remained. */
        REPAIR_BUDGET_EXHAUSTED,
        /** The last authoring attempt of the run had been made. */
        ATTEMPT_CAP_REACHED,
        /** The bounded mechanical repair phase ran out before any candidate ever built and graded. */
        MECHANICAL_REPAIR_EXHAUSTED,
        /** A semantic repair broke mechanical verification and the single narrow correction granted afterwards did not restore it. */
        POST_REPAIR_CORRECTION_EXHAUSTED,
        /** Blocking findings remained with repair budget still unspent, because none of them maps to a repairable surface. */
        NO_SCHEDULABLE_SURFACE,
        /** The quality review never returned a usable verdict, even after its one retry, so no repair could be scheduled from it. */
        REVIEW_UNAVAILABLE,
        /** The agent submitted the previously rejected candidate unchanged, so re-verifying it could only repeat the same verdict. */
        UNCHANGED_CANDIDATE_RESUBMITTED,
        /** A semantic repair introduced a new blocker or failed to remove any existing blocker, so the previous reviewed checkpoint was retained. */
        REPAIR_DID_NOT_IMPROVE,
        /** Concept exploration completed, but no candidate satisfied the instructor brief and learning-fit review, and none was usable as a fallback either. */
        NO_ADMISSIBLE_CONCEPT,
        /**
         * Concept exploration admitted no candidate, so the run proceeded with the least-rejected one and stopped with the reviewer's objections attached. Refines
         * {@link #NO_SCHEDULABLE_SURFACE}: the contested artifact is the exercise idea rather than any file the repair loop could edit.
         */
        CONCEPT_ADMITTED_WITH_FINDINGS,
        /** The run was stopped cooperatively (instructor cancellation, lost job ownership, or an unclassified stop signal). */
        CANCELLED,
        /** The run exceeded its configured wall-clock budget. */
        DEADLINE_EXCEEDED,
        /** The run exceeded its configured provider token budget. */
        TOKEN_BUDGET_EXHAUSTED,
        /** The agent loop itself ended in an error before a verdict could be reached. */
        AGENT_ERROR,
        /** The sandbox or build environment could not host the run, so no attempt was made. */
        ENVIRONMENT_UNAVAILABLE,
        /** The run failed with an unexpected exception. */
        RUN_FAILED,
        /** The job never reached the attempt loop (superseded, expired, or its exercise was no longer generatable). */
        NOT_STARTED
    }

    public static ExerciseGenerationEventDTO of(Type type, @Nullable String message) {
        return new ExerciseGenerationEventDTO(type, message, null, null, null, null, null, null, null, null, Instant.now());
    }

    public static ExerciseGenerationEventDTO phase(Phase phase, String message) {
        return new ExerciseGenerationEventDTO(Type.PROGRESS, message, phase, null, null, null, null, null, null, null, Instant.now());
    }

    public static ExerciseGenerationEventDTO repairRound(@Nullable String message, ExerciseGenerationRepairRoundDTO repairRound) {
        return new ExerciseGenerationEventDTO(Type.PROGRESS, message, Phase.REPAIRING, null, null, null, null, null, null, repairRound, Instant.now());
    }

    /**
     * This event with {@code terminationReason} set, so a terminal event can be stamped where the reason is known.
     *
     * @param terminationReason why the run ended; {@code null} leaves the event unchanged
     * @return a copy carrying the reason, or {@code this} when there is nothing to add
     */
    public ExerciseGenerationEventDTO withTerminationReason(@Nullable TerminationReason terminationReason) {
        if (terminationReason == null || terminationReason == this.terminationReason) {
            return this;
        }
        return new ExerciseGenerationEventDTO(type, message, phase, completionStatus, verdict, liveExerciseChanged, savedRepositoryCommits, savedExerciseVersionId,
                terminationReason, repairRound, timestamp);
    }

    public static ExerciseGenerationEventDTO done(@Nullable String message, CompletionStatus completionStatus, @Nullable ExerciseGenerationVerdictDTO verdict) {
        return done(message, completionStatus, verdict, false);
    }

    public static ExerciseGenerationEventDTO done(@Nullable String message, CompletionStatus completionStatus, @Nullable ExerciseGenerationVerdictDTO verdict,
            boolean liveExerciseChanged) {
        return done(message, completionStatus, verdict, liveExerciseChanged, null);
    }

    public static ExerciseGenerationEventDTO done(@Nullable String message, CompletionStatus completionStatus, @Nullable ExerciseGenerationVerdictDTO verdict,
            boolean liveExerciseChanged, @Nullable Map<String, String> savedRepositoryCommits) {
        return done(message, completionStatus, verdict, liveExerciseChanged, savedRepositoryCommits, null);
    }

    public static ExerciseGenerationEventDTO done(@Nullable String message, CompletionStatus completionStatus, @Nullable ExerciseGenerationVerdictDTO verdict,
            boolean liveExerciseChanged, @Nullable Map<String, String> savedRepositoryCommits, @Nullable Long savedExerciseVersionId) {
        return new ExerciseGenerationEventDTO(Type.DONE, message, Phase.SAVING, completionStatus, verdict, liveExerciseChanged,
                savedRepositoryCommits == null ? null : Map.copyOf(savedRepositoryCommits), savedExerciseVersionId, null, null, Instant.now());
    }
}
