package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.WeaviateConfigurationException;

/**
 * FailureAnalyzer that provides helpful error messages when the Weaviate configuration is invalid.
 * This analyzer catches {@link WeaviateConfigurationException} and formats it into a user-friendly message
 * with both a description of the problem and recommended actions to fix it.
 */
public class WeaviateConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<WeaviateConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, WeaviateConfigurationException cause) {
        String description = buildDescription(cause);
        String action = buildAction(cause);
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(WeaviateConfigurationException cause) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Invalid Weaviate configuration detected:%n%n");
        stringBuilder.append("Weaviate is enabled (artemis.weaviate.enabled=true) but the following properties are missing or invalid:%n");
        for (String property : cause.getMissingOrInvalidProperties()) {
            stringBuilder.append("    - ").append(property).append("%n");
        }
        return String.format(stringBuilder.toString());
    }

    private String buildAction(WeaviateConfigurationException cause) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Update your application configuration (e.g., environment variables, application-dev.yml or application-local.yml):%n%n");
        stringBuilder.append("Option 1: Provide valid Weaviate configuration for the following properties:%n");
        for (String property : cause.getMissingOrInvalidProperties()) {
            stringBuilder.append("    - ").append(property).append("%n");
        }
        stringBuilder.append("%n");
        stringBuilder.append("Option 2: Disable Weaviate if not needed:%n");
        stringBuilder.append("    artemis:%n");
        stringBuilder.append("      weaviate:%n");
        stringBuilder.append("        enabled: false%n");
        return String.format(stringBuilder.toString());
    }
}
