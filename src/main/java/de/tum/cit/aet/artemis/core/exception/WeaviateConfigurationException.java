package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;
import java.util.List;

import de.tum.cit.aet.artemis.core.exception.failureAnalyzer.WeaviateConfigurationFailureAnalyzer;

/**
 * Exception thrown when the Weaviate configuration is invalid or incomplete.
 * This exception is caught by {@link WeaviateConfigurationFailureAnalyzer} to provide
 * helpful error messages during application startup.
 */
public class WeaviateConfigurationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> missingOrInvalidProperties;

    public WeaviateConfigurationException(String message, List<String> missingOrInvalidProperties) {
        super(message);
        this.missingOrInvalidProperties = missingOrInvalidProperties;
    }

    /**
     * Gets the list of missing or invalid configuration properties.
     *
     * @return a list of property names that are missing or have invalid values
     */
    public List<String> getMissingOrInvalidProperties() {
        return missingOrInvalidProperties;
    }
}
