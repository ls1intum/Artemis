package de.tum.cit.aet.artemis.core.exception;

import de.tum.cit.aet.artemis.core.exception.failureAnalyzer.InvalidAdminConfigurationFailureAnalyzer;

/**
 * Exception thrown when the internal admin configuration is invalid.
 * This exception is caught by {@link InvalidAdminConfigurationFailureAnalyzer} to provide
 * helpful error messages during application startup.
 */
public class InvalidAdminConfigurationException extends RuntimeException {

    private final String propertyName;

    private final String propertyPath;

    private final String currentValue;

    private final String constraint;

    public InvalidAdminConfigurationException(String message, String propertyName, String propertyPath, String currentValue, String constraint) {
        super(message);
        this.propertyName = propertyName;
        this.propertyPath = propertyPath;
        this.currentValue = currentValue;
        this.constraint = constraint;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getPropertyPath() {
        return propertyPath;
    }

    public String getCurrentValue() {
        return currentValue;
    }

    public String getConstraint() {
        return constraint;
    }
}
