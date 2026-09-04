package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationActivityDTO;

/**
 * The live position and running totals of one generation run, so every progress event can say what the run is doing rather than only what it just finished.
 * <p>
 * One instance is owned by one run (the run's progress sink creates it and hands it out via {@link AgentActivitySink#activityTracker()}), so nothing is static and no state can
 * leak from one run into the next. It is deliberately a plain mutable holder: a generation run executes on a single worker thread, which owns every mutation below. The only value
 * that crosses a thread boundary is the immutable {@link ExerciseGenerationActivityDTO} a snapshot produces, safely published through the distributed transcript map.
 */
public final class GenerationActivityTracker {

    /** The substep of context-isolated concept discovery, which runs before the staged authoring phases and therefore has no {@link GenerationStage} of its own. */
    public static final String CONCEPT_STEP = "concept";

    @Nullable
    private String step;

    private int attempt;

    private int turn;

    private int modelCalls;

    private int toolCalls;

    private int filesWritten;

    /** Whether anything has been recorded yet; while false a snapshot is {@code null} so events from before the agent loop carry no invented activity context. */
    private boolean started;

    /**
     * The substep the run is inside.
     *
     * @param step the substep key, or {@code null} for the stretches (verification, review, repair) where no substep applies
     */
    public void step(@Nullable String step) {
        this.step = step;
        this.started = true;
    }

    /**
     * Starts an authoring attempt, which resets the turn counter because a turn is scoped to one agent session.
     *
     * @param attempt the 1-based authoring attempt
     */
    public void attempt(int attempt) {
        this.attempt = attempt;
        this.turn = 0;
        this.started = true;
    }

    /**
     * Records the turn the agent loop has reached.
     *
     * @param turn the 1-based agent turn inside the current agent session
     */
    public void turn(int turn) {
        this.turn = turn;
        this.started = true;
    }

    /** One completed provider call. */
    public void recordModelCall() {
        this.modelCalls++;
        this.started = true;
    }

    /** One tool call the agent loop dispatched this turn. */
    public void recordToolCall() {
        this.toolCalls++;
        this.started = true;
    }

    /** One successful {@code write_file}, {@code edit_file}, or {@code delete_file}. */
    public void recordFileWritten() {
        this.filesWritten++;
        this.started = true;
    }

    /**
     * The current activity for an ordinary event.
     *
     * @return the activity, or {@code null} while the run has recorded nothing at all — an event from before any agent work genuinely has no activity context, and inventing one
     *         would make an idle run look like a working one
     */
    @Nullable
    public ExerciseGenerationActivityDTO snapshot() {
        return started ? activity(false) : null;
    }

    /**
     * The current activity for the event emitted immediately before a provider call, which is always inside a turn and therefore always present.
     *
     * @return the activity with {@code waitingOnModel} set
     */
    public ExerciseGenerationActivityDTO waitingOnModel() {
        this.started = true;
        return activity(true);
    }

    private ExerciseGenerationActivityDTO activity(boolean waitingOnModel) {
        return new ExerciseGenerationActivityDTO(step, attempt, turn, waitingOnModel, modelCalls, toolCalls, filesWritten);
    }
}
