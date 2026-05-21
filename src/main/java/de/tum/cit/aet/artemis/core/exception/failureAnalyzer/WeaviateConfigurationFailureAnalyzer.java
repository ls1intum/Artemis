package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import java.util.stream.Collectors;

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
        String propertyList = formatPropertyList(cause);
        return """
                Invalid Weaviate configuration detected:

                Weaviate is enabled (artemis.weaviate.enabled=true) but the following properties are missing or invalid:
                %s""".formatted(propertyList);
    }

    private String buildAction(WeaviateConfigurationException cause) {
        String propertyList = formatPropertyList(cause);
        return """
                Update your application configuration (e.g., environment variables, application-dev.yml or application-local.yml):

                Option 1: Provide valid Weaviate configuration for the following properties:
                %s

                Option 2: Disable Weaviate if not needed:
                    artemis:
                      weaviate:
                        enabled: false
                """.formatted(propertyList);
    }

    private String formatPropertyList(WeaviateConfigurationException cause) {
        return cause.getMissingOrInvalidProperties().stream().map(property -> "    - " + property).collect(Collectors.joining("\n"));
    }
}
