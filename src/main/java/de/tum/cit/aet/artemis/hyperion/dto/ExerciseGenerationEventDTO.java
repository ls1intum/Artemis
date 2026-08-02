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
 *
 * @param type                   the event kind
 * @param message                a human-readable progress or result message
 * @param completionStatus       on a terminal {@code DONE} event, whether the run succeeded, needs review, or partially completed; otherwise {@code null}
 * @param verdict                on a terminal event with a verification result, the structured verdict (which gates passed/failed) so the client can render scannable chips; else
 *                                   {@code null}
 * @param liveExerciseChanged    on a terminal event, whether the live exercise repositories/problem statement were changed and an open editor should refresh; otherwise
 *                                   {@code null}
 * @param savedRepositoryCommits exact commit hashes saved by repository name on a successful terminal event; otherwise {@code null}
 * @param savedExerciseVersionId the id of the exact {@code ExerciseVersion} row saved by this run, on a successful terminal event; {@code null} when no new version was
 *                                   recorded (e.g. a no-op run)
 * @param terminationReason      on a terminal event, why the generation run ended, as a closed machine-readable value; {@code null} otherwise. Orthogonal to
 *                                   {@link #completionStatus()}: this says why the attempt loop stopped producing candidates, that says what became of the result
 * @param repairRound            on a repair-round progress event, that round's finding bookkeeping; {@code null} on every other event
 * @param timestamp              the moment the event was produced
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A progress event streamed to the instructor while an agentic whole-exercise generation or adaptation runs")
public record ExerciseGenerationEventDTO(@Schema(description = "The event kind", requiredMode = Schema.RequiredMode.REQUIRED) Type type,
        @Schema(description = "Human-readable progress or result message") @Nullable String message,
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
        /** The session has started. */
        STARTED,
        /** A progress update (e.g. a tool call or a verification step). */
        PROGRESS,
        /** A terminal event: the run finished (see {@link #completionStatus()}). */
        DONE,
        /** The run was cancelled by the instructor. */
        CANCELLED,
        /** A terminal error event. */
        ERROR
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum CompletionStatus {
        /** The exercise was verified and saved. */
        SUCCESS,
        /** The mechanically verified exercise was saved, but automated quality findings require instructor review. */
        NEEDS_REVIEW,
        /** Saving did not complete; live changes may exist and require manual review. */
        PARTIAL
    }

    /**
     * Why one generation or adaptation run stopped producing candidates.
     * <p>
     * A closed set rather than prose, because this is the number that decides whether the repair loop converges or merely runs out of budget, and a substring match against a log
     * line cannot answer that. Every exit of the attempt loop maps to exactly one value; the run-level controls the loop cannot see (wall clock, token budget) refine
     * {@link #CANCELLED} into their own value where the caller knows better.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum TerminationReason {
        /** The candidate was mechanically verified and its quality review found nothing blocking: the loop stopped because it was finished. */
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
        /** Concept exploration completed, but no candidate satisfied the instructor brief and learning-fit review. */
        NO_ADMISSIBLE_CONCEPT,
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
        return new ExerciseGenerationEventDTO(type, message, null, null, null, null, null, null, null, Instant.now());
    }

    /** A progress event carrying one repair round's finding bookkeeping alongside its human-readable line. */
    public static ExerciseGenerationEventDTO repairRound(@Nullable String message, ExerciseGenerationRepairRoundDTO repairRound) {
        return new ExerciseGenerationEventDTO(Type.PROGRESS, message, null, null, null, null, null, null, repairRound, Instant.now());
    }

    /**
     * This event with {@code terminationReason} set, so terminal events can be stamped where the reason is known without threading it through every factory overload.
     *
     * @param terminationReason why the run ended; {@code null} leaves the event unchanged
     * @return a copy carrying the reason, or {@code this} when there is nothing to add
     */
    public ExerciseGenerationEventDTO withTerminationReason(@Nullable TerminationReason terminationReason) {
        if (terminationReason == null || terminationReason == this.terminationReason) {
            return this;
        }
        return new ExerciseGenerationEventDTO(type, message, completionStatus, verdict, liveExerciseChanged, savedRepositoryCommits, savedExerciseVersionId, terminationReason,
                repairRound, timestamp);
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
        return new ExerciseGenerationEventDTO(Type.DONE, message, completionStatus, verdict, liveExerciseChanged,
                savedRepositoryCommits == null ? null : Map.copyOf(savedRepositoryCommits), savedExerciseVersionId, null, null, Instant.now());
    }
}
