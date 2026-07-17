package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single progress event streamed to the instructor over the websocket while an agentic whole-exercise generation/adaptation runs.
 * <p>
 * {@link Serializable} because it is retained (inside {@code JobTranscript}) in a distributed Hazelcast map for reconnect/replay.
 *
 * @param type                the event kind
 * @param message             a human-readable progress or result message (already localised-agnostic; the client decides presentation)
 * @param completionStatus    on a terminal {@code DONE} event, whether the run succeeded, needs review, or partially completed; otherwise {@code null}
 * @param verdict             on a terminal event with a verification result, the structured verdict (which gates passed/failed) so the client can render scannable chips; else
 *                                {@code null}
 * @param liveExerciseChanged on a terminal event, whether the live exercise repositories/problem statement were changed and an open editor should refresh; otherwise {@code null}
 * @param timestamp           the moment the event was produced
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A progress event streamed to the instructor while an agentic whole-exercise generation or adaptation runs")
public record ExerciseGenerationEventDTO(@Schema(description = "The event kind") Type type,
        @Schema(description = "Human-readable progress or result message") @Nullable String message,
        @Schema(description = "On a terminal DONE event, whether the run succeeded, needs review, or partially completed") @Nullable CompletionStatus completionStatus,
        @Schema(description = "On a terminal event, the structured verification verdict") @Nullable ExerciseGenerationVerdictDTO verdict,
        @Schema(description = "On a terminal event, whether the live exercise changed and open editors should refresh") @Nullable Boolean liveExerciseChanged,
        @Schema(description = "The moment the event was produced") Instant timestamp) implements Serializable {

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

    public static ExerciseGenerationEventDTO of(Type type, @Nullable String message) {
        return new ExerciseGenerationEventDTO(type, message, null, null, null, Instant.now());
    }

    public static ExerciseGenerationEventDTO done(@Nullable String message, CompletionStatus completionStatus, @Nullable ExerciseGenerationVerdictDTO verdict) {
        return done(message, completionStatus, verdict, false);
    }

    public static ExerciseGenerationEventDTO done(@Nullable String message, CompletionStatus completionStatus, @Nullable ExerciseGenerationVerdictDTO verdict,
            boolean liveExerciseChanged) {
        return new ExerciseGenerationEventDTO(Type.DONE, message, completionStatus, verdict, liveExerciseChanged, Instant.now());
    }
}
