package de.tum.cit.aet.artemis.exercise.service;

/**
 * The work of an asynchronous deletion.
 */
@FunctionalInterface
public interface DeletionJob {

    /**
     * Deletes the entity and reports the progress to the tracker. Any exception ends the deletion as failed.
     *
     * @param tracker reports the progress to the clients
     * @throws Exception if the deletion fails
     */
    void run(DeletionProgressTracker tracker) throws Exception;
}
