package de.tum.cit.aet.artemis.core.config;

/**
 * Exception thrown when the passkey configuration is invalid.
 * This exception is caught by {@link PasskeyConfigurationFailureAnalyzer} to provide
 * helpful error messages during application startup.
 */
public class PasskeyConfigurationException extends RuntimeException {

    private final String propertyName;

    private final String conflictingPropertyName;

    private final boolean expectedValue;

    public PasskeyConfigurationException(String message, String propertyName, String conflictingPropertyName, boolean expectedValue) {
        super(message);
        this.propertyName = propertyName;
        this.conflictingPropertyName = conflictingPropertyName;
        this.expectedValue = expectedValue;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getConflictingPropertyName() {
        return conflictingPropertyName;
    }

    public boolean getExpectedValue() {
        return expectedValue;
    }
}
