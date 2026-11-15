package de.tum.cit.aet.artemis.exercise.service;

/**
 * Receives the progress of deleting one exercise, so the caller can report it as part of its own operation. Every method does nothing by default.
 */
public interface DeletionProgressListener {

    /**
     * Called before the work of a step starts.
     *
     * @param stepKey the stable key of the step, for example {@code participations}
     */
    default void stepStarted(String stepKey) {
    }

    /**
     * Called before a step starts that only follows the point of no return of the deletion, for example the cleanup of the search index. A failure to report it must not skip
     * the work of the step, so implementations that can fail ignore the failure.
     *
     * @param stepKey the stable key of the step
     */
    default void stepStartedAfterDeletion(String stepKey) {
        stepStarted(stepKey);
    }

    /**
     * Called whenever an item of the current step is done.
     *
     * @param processed the items that are done
     * @param total     all items of the step
     */
    default void itemsProcessed(int processed, int total) {
    }

    /**
     * Creates a listener that ignores everything.
     *
     * @return a listener without any effect
     */
    static DeletionProgressListener none() {
        return new DeletionProgressListener() {
        };
    }
}
