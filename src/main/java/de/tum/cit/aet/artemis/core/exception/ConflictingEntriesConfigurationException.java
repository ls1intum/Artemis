package de.tum.cit.aet.artemis.core.exception;

/**
 * Base exception for configuration-related errors detected during application startup.
 * This exception tracks property names involved in configuration conflicts to enable
 * detailed error reporting through failure analyzers.
 */
public class ConflictingEntriesConfigurationException extends RuntimeException {

    private final String propertyName;

    private final String conflictingPropertyName;

    public ConflictingEntriesConfigurationException(String message, String propertyName, String conflictingPropertyName) {
        super(message);
        this.propertyName = propertyName;
        this.conflictingPropertyName = conflictingPropertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getConflictingPropertyName() {
        return conflictingPropertyName;
    }
}
