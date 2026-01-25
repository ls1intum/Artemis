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
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid Weaviate configuration detected:%n%n");
        sb.append("Weaviate is enabled (artemis.weaviate.enabled=true) but the following properties are missing or invalid:%n");
        for (String property : cause.getMissingOrInvalidProperties()) {
            sb.append("    - ").append(property).append("%n");
        }
        return String.format(sb.toString());
    }

    private String buildAction(WeaviateConfigurationException cause) {
        return String.format("Update your application configuration (e.g., application.yml or environment variables):%n%n" + "Option 1: Provide valid Weaviate configuration:%n"
                + "    artemis:%n" + "      weaviate:%n" + "        enabled: true%n" + "        host: your-weaviate-host%n" + "        port: 8080%n"
                + "        grpc-port: 50051%n%n" + "Option 2: Disable Weaviate if not needed:%n" + "    artemis:%n" + "      weaviate:%n" + "        enabled: false%n");
    }
}
