package de.tum.cit.aet.artemis.exercise.service;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports the steps of a deletion to a {@link DeletionProgressTracker}, weighted by the expected effort of every step. A reporter without a tracker ignores everything, so the
 * code that deletes an entity is the same whether somebody follows the progress or not (a course that is deleted reports its own progress).
 */
public class DeletionStepReporter implements DeletionProgressListener {

    private static final Logger log = LoggerFactory.getLogger(DeletionStepReporter.class);

    @Nullable
    private final DeletionProgressTracker tracker;

    private final Map<String, Double> stepWeights;

    private String currentStep;

    /**
     * Creates a reporter that tells the tracker about the steps.
     *
     * @param tracker     the tracker of the running deletion
     * @param stepWeights the weight of every step by its key
     */
    public DeletionStepReporter(DeletionProgressTracker tracker, Map<String, Double> stepWeights) {
        this.tracker = tracker;
        this.stepWeights = stepWeights;
    }

    private DeletionStepReporter() {
        this.tracker = null;
        this.stepWeights = Map.of();
    }

    /**
     * Creates a reporter that ignores everything.
     *
     * @return a reporter without any effect
     */
    public static DeletionStepReporter none() {
        return new DeletionStepReporter();
    }

    @Override
    public void stepStarted(String stepKey) {
        finishCurrentStep();
        currentStep = stepKey;
        if (tracker != null) {
            tracker.startStep(stepKey);
        }
    }

    @Override
    public void stepStartedAfterDeletion(String stepKey) {
        try {
            stepStarted(stepKey);
        }
        catch (RuntimeException e) {
            // The entity is deleted already, so a progress store that is not available must not fail the deletion or skip the cleanup that follows.
            log.warn("Could not report the step {} of a deletion that is done", stepKey, e);
        }
    }

    /**
     * Claims the exercise that is deleted next, if somebody follows the progress. See {@link DeletionProgressTracker#claimExercise(long)}.
     *
     * @param exerciseId the id of the exercise that is deleted next
     */
    public void claimExercise(long exerciseId) {
        if (tracker != null) {
            tracker.claimExercise(exerciseId);
        }
    }

    @Override
    public void itemsProcessed(int processed, int total) {
        if (tracker != null && EntityDeletionWeights.STEP_PARTICIPATIONS.equals(currentStep) && total > 0) {
            tracker.reportItems(processed, total, null, stepWeights.getOrDefault(currentStep, 0.0) * processed / total);
        }
    }

    /**
     * Reports how many exercises of an exam or an exercise group are deleted.
     *
     * @param exercisesDeleted the exercises that are completely deleted
     * @param totalExercises   all exercises that are deleted
     * @param currentTitle     the title of the exercise that is deleted now, or null
     * @param partialWeight    the part of the weight of the step that is done
     */
    public void exercisesProcessed(int exercisesDeleted, int totalExercises, @Nullable String currentTitle, double partialWeight) {
        if (tracker != null) {
            tracker.reportItems(exercisesDeleted, totalExercises, currentTitle, partialWeight);
        }
    }

    /**
     * Marks the current step as done, so its weight counts for the progress of the next steps.
     */
    public void finishCurrentStep() {
        if (currentStep != null && tracker != null) {
            tracker.completeStep(stepWeights.getOrDefault(currentStep, 0.0));
        }
        currentStep = null;
    }
}
