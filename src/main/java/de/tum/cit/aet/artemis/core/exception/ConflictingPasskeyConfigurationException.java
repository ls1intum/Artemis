package de.tum.cit.aet.artemis.core.exception;

import de.tum.cit.aet.artemis.core.exception.failureAnalyzer.PasskeyConfigurationFailureAnalyzer;

/**
 * Exception thrown when the passkey configuration is invalid.
 * This exception is caught by {@link PasskeyConfigurationFailureAnalyzer} to provide
 * helpful error messages during application startup.
 */
public class ConflictingPasskeyConfigurationException extends ConflictingEntriesConfigurationException {

    public ConflictingPasskeyConfigurationException(String message, String propertyName, String conflictingPropertyName) {
        super(message, propertyName, conflictingPropertyName);
    }
}
