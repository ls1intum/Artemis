package de.tum.cit.aet.artemis.core.exception;

import java.util.List;

/**
 * Exception thrown when Weaviate schema validation fails.
 * This exception contains details about the schema mismatches found between
 * the Artemis schema definitions and the Iris repository schemas.
 */
public class WeaviateSchemaValidationException extends RuntimeException {

    private final List<String> validationErrors;

    private final boolean strictMode;

    /**
     * Creates a new WeaviateSchemaValidationException.
     *
     * @param message          the error message
     * @param validationErrors the list of specific validation errors found
     * @param strictMode       whether strict mode was enabled
     */
    public WeaviateSchemaValidationException(String message, List<String> validationErrors, boolean strictMode) {
        super(message);
        this.validationErrors = validationErrors;
        this.strictMode = strictMode;
    }

    /**
     * Gets the list of validation errors.
     *
     * @return the validation errors
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }

    /**
     * Checks if strict mode was enabled when this exception was thrown.
     *
     * @return true if strict mode was enabled
     */
    public boolean isStrictMode() {
        return strictMode;
    }

    /**
     * Gets a formatted string of all validation errors.
     *
     * @return formatted error details
     */
    public String getFormattedErrors() {
        if (validationErrors == null || validationErrors.isEmpty()) {
            return "No specific errors recorded";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < validationErrors.size(); i++) {
            sb.append(i + 1).append(". ").append(validationErrors.get(i));
            if (i < validationErrors.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
