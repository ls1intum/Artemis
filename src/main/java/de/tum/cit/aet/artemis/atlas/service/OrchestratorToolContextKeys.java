package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.OrchestrationCompletionDTO;
import de.tum.cit.aet.artemis.atlas.dto.WorkerCompletionDTO;

/**
 * Keys under which the orchestrator tool services stash mutable state in the Spring AI
 * {@link org.springframework.ai.chat.model.ToolContext}, plus the per-run audit buffer those tools
 * append to. A single place for every string so typos fail fast at compile time instead of producing
 * silent no-ops at runtime.
 * <p>
 * The container ({@link CompetencyOrchestrationService}) owns the lifetime of the tool-context map:
 * it writes {@link #COURSE_ID_KEY} and an {@link AppliedActionsBuffer} under {@link #APPLIED_ACTIONS_KEY}
 * before the LLM round and reads the collected actions back after the round returns. Tool-context
 * parameters are stripped from the JSON schema Spring AI exposes to the model, so the LLM can neither
 * forge the course id nor tamper with the audit log.
 */
public final class OrchestratorToolContextKeys {

    /**
     * Tool-context key carrying the current course id. Every read and write tool rejects the call if
     * this is missing so the model cannot operate without a course scope.
     */
    public static final String COURSE_ID_KEY = "courseId";

    /**
     * Tool-context key carrying the per-run {@link AppliedActionsBuffer}. The buffer wraps a
     * {@link java.util.Collections#synchronizedList synchronized list} so concurrent tool calls
     * (Spring AI's parallel-tool-call roadmap) cannot race on append.
     */
    public static final String APPLIED_ACTIONS_KEY = "appliedActions";

    /** Tool-context key carrying the exercise that anchored the current orchestration request. */
    public static final String LEARNING_OBJECT_ID_KEY = "learningObjectId";

    /** Request-scoped one-shot holder populated by {@code completeOrchestration}. */
    public static final String ORCHESTRATION_COMPLETION_KEY = "orchestrationCompletion";

    /** Worker-scoped one-shot holder populated by {@code completeWorkerTask}. */
    public static final String WORKER_COMPLETION_KEY = "workerCompletion";

    /** Monotonic request sequence shared by delegation and verification markers. */
    public static final String TOOL_SEQUENCE_KEY = "toolSequence";

    /** Sequence of the most recent successful tool-driven competency-index read. */
    public static final String LAST_INDEX_READ_SEQUENCE_KEY = "lastIndexReadSequence";

    /** Sequence of the most recently completed worker delegation. */
    public static final String LAST_DELEGATION_SEQUENCE_KEY = "lastDelegationSequence";

    /** Count of successful course-scoped read calls made by the current worker. */
    public static final String WORKER_READ_COUNT_KEY = "workerReadCount";

    /** Applied-action list offset captured immediately before the current worker starts. */
    public static final String WORKER_ACTION_START_KEY = "workerActionStart";

    /**
     * Hard cap on the number of write tool calls per orchestrator run, shared across every write
     * tool ({@code createCompetency}, {@code editCompetency}, {@code assignExerciseToCompetency},
     * {@code unassignExerciseFromCompetency}, {@code deleteCompetency}). Mirrors the limit declared
     * in the system prompt; enforced through {@link AppliedActionsBuffer#tryReserveSlot(int)} so a
     * hallucinating model cannot spend more than this many writes regardless of what the prompt says.
     */
    public static final int MAX_WRITE_CALLS = 32;

    private OrchestratorToolContextKeys() {
    }

    static AtomicReference<OrchestrationCompletionDTO> newOrchestrationCompletionHolder() {
        return new AtomicReference<>();
    }

    static AtomicReference<WorkerCompletionDTO> newWorkerCompletionHolder() {
        return new AtomicReference<>();
    }

    static AtomicLong newSequenceMarker() {
        return new AtomicLong();
    }

    /**
     * Typed wrapper for the per-run applied-actions list passed through Spring AI's
     * {@link org.springframework.ai.chat.model.ToolContext}. The contained list MUST be a
     * synchronized list — Spring AI's roadmap includes parallel tool-call execution, and
     * {@link OrchestratorToolHelpers#appendAction} is the only path that mutates it. The
     * {@code reservedSlots} counter enforces {@link #MAX_WRITE_CALLS} atomically via
     * {@link #tryReserveSlot(int)} so concurrent tool callbacks cannot both pass the cap and exceed
     * it (a {@code size()}-then-{@code add()} pre-check would be racy).
     *
     * @param actions       the synchronized list accumulating one entry per successful mutation
     * @param reservedSlots atomic counter reserving slots against {@link #MAX_WRITE_CALLS}
     */
    public record AppliedActionsBuffer(List<AppliedActionDTO> actions, AtomicInteger reservedSlots) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Convenience constructor seeding a fresh reservation counter.
         *
         * @param actions the synchronized list accumulating applied actions
         */
        public AppliedActionsBuffer(List<AppliedActionDTO> actions) {
            this(actions, new AtomicInteger());
        }

        /**
         * Atomically reserves one slot against {@code cap}, returning {@code false} when the cap is
         * already exhausted. The counter is incremented once per attempted write (before
         * persistence) and never decremented, so a hallucinating model cannot retry past the cap by
         * failing a mutation.
         *
         * @param cap the maximum number of write tool calls allowed for the run
         * @return {@code true} if a slot was reserved, {@code false} once the cap is reached
         */
        public boolean tryReserveSlot(int cap) {
            while (true) {
                int current = reservedSlots.get();
                if (current >= cap) {
                    return false;
                }
                if (reservedSlots.compareAndSet(current, current + 1)) {
                    return true;
                }
            }
        }
    }
}
