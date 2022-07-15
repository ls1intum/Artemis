package de.tum.in.www1.artemis.service.hestia.behavioral;

/**
 * Exception used for the generation of solution entries for behavioral test cases
 * It is thrown if there was an error while generating the solution entries
 */
public class BehavioralSolutionEntryGenerationException extends Exception {

    private static final String MESSAGE_PREFIX = "Unable to generate behavioral solution entries: ";

    public BehavioralSolutionEntryGenerationException(String message) {
        super(MESSAGE_PREFIX + message);
    }

    public BehavioralSolutionEntryGenerationException(String message, Throwable cause) {
        super(MESSAGE_PREFIX + message, cause);
    }
}
