package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single progress event streamed to the instructor over the websocket while an agentic whole-exercise generation/adaptation runs.
 * <p>
 * {@link Serializable} because it is retained (inside {@code JobTranscript}) in a distributed Hazelcast map for reconnect/replay.
 *
 * @param type             the event kind
 * @param message          a human-readable progress or result message (already localised-agnostic; the client decides presentation)
 * @param completionStatus on a terminal {@code DONE} event, whether the run succeeded, partially completed, or failed; otherwise {@code null}
 * @param verdict          on a terminal event with a verification result, the structured verdict (which gates passed/failed) so the client can render scannable chips; else
 *                             {@code null}
 * @param timestamp        the moment the event was produced
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExerciseGenerationEventDTO(Type type, @Nullable String message, @Nullable CompletionStatus completionStatus, @Nullable Verdict verdict, Instant timestamp)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The structured outcome of the differential verification, mirrored from {@code VerificationResult} so the client can show which gates passed without parsing prose.
     *
     * @param accepted       whether the exercise was accepted
     * @param solutionPassed whether the solution passed all its tests
     * @param templateFailed whether the template compiled but (correctly) failed the tests
     * @param testCount      the number of tests discovered
     * @param reasons        human-readable explanations of any failed gate (empty when accepted)
     */
    public record Verdict(boolean accepted, boolean solutionPassed, boolean templateFailed, int testCount, List<String> reasons) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

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
        /**
         * The run did not fully converge but produced usable work: the best-effort draft was saved (clearly distinguished from a verified
         * exercise) and the verification findings were turned into review comments the instructor must resolve before the exercise is used. The
         * UI should present "draft generated, N issues to review" rather than a plain failure.
         */
        NEEDS_REVIEW,
        /** The run finished without saving (e.g. nothing usable was produced, or recovery itself failed); the exercise was left untouched. */
        PARTIAL
    }

    /**
     * @param type    the event kind
     * @param message the message
     * @return an event stamped with the current time
     */
    public static ExerciseGenerationEventDTO of(Type type, @Nullable String message) {
        return new ExerciseGenerationEventDTO(type, message, null, null, Instant.now());
    }

    /**
     * @param message          the message
     * @param completionStatus the terminal status
     * @param verdict          the structured verification verdict, or {@code null}
     * @return a terminal {@code DONE} event stamped with the current time, carrying the structured verdict
     */
    public static ExerciseGenerationEventDTO done(@Nullable String message, CompletionStatus completionStatus, @Nullable Verdict verdict) {
        return new ExerciseGenerationEventDTO(Type.DONE, message, completionStatus, verdict, Instant.now());
    }
}
