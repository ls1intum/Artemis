package de.tum.cit.aet.artemis.deimos.exception.failureAnalyzer;

import java.util.stream.Collectors;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.deimos.exception.DeimosConfigurationException;

/**
 * FailureAnalyzer that provides helpful error messages when the Deimos configuration is invalid.
 * This analyzer catches {@link DeimosConfigurationException} and formats it into a user-friendly message
 * with both a description of the problem and recommended actions to fix it.
 */
public class DeimosConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<DeimosConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, DeimosConfigurationException cause) {
        String description = buildDescription(cause);
        String action = buildAction(cause);
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(DeimosConfigurationException cause) {
        String propertyList = formatPropertyList(cause);
        return """
                Invalid Deimos configuration detected:

                Deimos is enabled (artemis.deimos.enabled=true) but the following properties are missing or invalid:
                %s""".formatted(propertyList);
    }

    private String buildAction(DeimosConfigurationException cause) {
        String propertyList = formatPropertyList(cause);
        return """
                Deimos sends student source code to the configured LLM endpoint, so it has no default endpoint.
                You must point it at an endpoint you operate and are permitted to send student data to.

                Update your application configuration (e.g., environment variables, application-prod.yml or application-local.yml):

                Option 1: Provide valid Deimos configuration for the following properties:
                %s

                Option 2: Disable Deimos if not needed:
                    artemis:
                      deimos:
                        enabled: false
                """.formatted(propertyList);
    }

    private String formatPropertyList(DeimosConfigurationException cause) {
        return cause.getMissingOrInvalidProperties().stream().map(property -> "    - " + property).collect(Collectors.joining("\n"));
    }
}
