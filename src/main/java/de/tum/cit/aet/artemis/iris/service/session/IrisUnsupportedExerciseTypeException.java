package de.tum.cit.aet.artemis.iris.service.session;

/**
 * Exception thrown when an unsupported exercise type is encountered in Iris operations.
 */
public class IrisUnsupportedExerciseTypeException extends RuntimeException {

    public IrisUnsupportedExerciseTypeException(String message) {
        super(message);
    }
}
