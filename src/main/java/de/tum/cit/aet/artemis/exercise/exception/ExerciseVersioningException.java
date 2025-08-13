package de.tum.cit.aet.artemis.exercise.exception;

/**
 * Exception thrown when exercise versioning operations encounter unexpected states or failures.
 */
public class ExerciseVersioningException extends RuntimeException {

    public ExerciseVersioningException(String message) {
        super(message);
    }

    public ExerciseVersioningException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Exception thrown when attempting to create an initial version for an exercise that already has versions.
     */
    public static class DuplicateInitialVersionException extends ExerciseVersioningException {
        public DuplicateInitialVersionException(Long exerciseId) {
            super("Attempt to create duplicate initial version for exercise ID: " + exerciseId);
        }
    }

    /**
     * Exception thrown when versioning operations fail due to data extraction or persistence issues.
     */
    public static class VersionCreationException extends ExerciseVersioningException {
        public VersionCreationException(Long exerciseId, String operation, Throwable cause) {
            super("Failed to " + operation + " for exercise ID " + exerciseId, cause);
        }
    }

    /**
     * Exception thrown when trying to version an exercise without an ID.
     */
    public static class InvalidExerciseStateException extends ExerciseVersioningException {
        public InvalidExerciseStateException(String message) {
            super(message);
        }
    }
}