package de.tum.cit.aet.artemis.core.exception;

/**
 * Exception thrown when a module is not present.
 * This is an unchecked exception such that the caller does not have to catch it.
 */
public class ModuleNotPresentException extends RuntimeException {

    public ModuleNotPresentException(String profileName) {
        super("The called module is not present. Make sure the respective Spring profile '" + profileName + "' has been activated.");
    }
}
