package de.tum.cit.aet.artemis.hyperion.service.variants;

/**
 * Thrown by a provisioner whose OWN post-import cleanup failed: the import already persisted the exercise (plus,
 * for programming exercises, its repositories and build plans), a later step threw, and deleting that clone threw
 * as well. {@code provision()} never returns on this path, so the pipeline never sees the exercise — this
 * exception carries its id so the terminal transition can KEEP it instead of clearing the only pointer to a
 * surviving exercise.
 */
public class LeftoverVariantExerciseException extends RuntimeException {

    private final long exerciseId;

    public LeftoverVariantExerciseException(long exerciseId, String message, Throwable cause) {
        super(message, cause);
        this.exerciseId = exerciseId;
    }

    /**
     * @return the id of the exercise that survived the failed cleanup and has to be deleted manually
     */
    public long getExerciseId() {
        return exerciseId;
    }
}
