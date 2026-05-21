package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import de.tum.cit.aet.artemis.core.exception.failureAnalyzer.ConflictingPasskeyConfigurationFailureAnalyzer;

/**
 * Exception thrown when the passkey configuration is invalid.
 * This exception is caught by {@link ConflictingPasskeyConfigurationFailureAnalyzer} to provide
 * helpful error messages during application startup.
 */
public class ConflictingPasskeyConfigurationException extends ConflictingEntriesConfigurationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConflictingPasskeyConfigurationException(String message, String propertyName, String conflictingPropertyName) {
        super(message, propertyName, conflictingPropertyName);
    }
}
