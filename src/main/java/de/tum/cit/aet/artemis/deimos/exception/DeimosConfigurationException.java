package de.tum.cit.aet.artemis.deimos.exception;

import java.io.Serial;
import java.util.List;

import de.tum.cit.aet.artemis.deimos.exception.failureAnalyzer.DeimosConfigurationFailureAnalyzer;

/**
 * Exception thrown when the Deimos configuration is invalid or incomplete.
 * This exception is caught by {@link DeimosConfigurationFailureAnalyzer} to provide
 * helpful error messages during application startup.
 */
public class DeimosConfigurationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> missingOrInvalidProperties;

    public DeimosConfigurationException(String message, List<String> missingOrInvalidProperties) {
        super(message);
        this.missingOrInvalidProperties = List.copyOf(missingOrInvalidProperties);
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
