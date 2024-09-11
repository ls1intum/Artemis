package de.tum.cit.aet.artemis.service.hestia.structural;

/**
 * Exception used for the generation of solution entries for structural test cases
 * It is thrown if there was an error while generating the solution entries
 */
public class StructuralSolutionEntryGenerationException extends Exception {

    private static final String MESSAGE_PREFIX = "Unable to generate structural solution entries: ";

    public StructuralSolutionEntryGenerationException(String message) {
        super(MESSAGE_PREFIX + message);
    }

    public StructuralSolutionEntryGenerationException(String message, Throwable cause) {
        super(MESSAGE_PREFIX + message, cause);
    }
}
