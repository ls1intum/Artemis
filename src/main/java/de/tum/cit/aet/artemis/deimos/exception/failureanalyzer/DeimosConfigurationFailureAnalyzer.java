package de.tum.cit.aet.artemis.deimos.exception.failureanalyzer;

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
                Deimos bounds the evidence it sends to its LLM preset and the size of a batch run, and every limit has to lie within its documented bounds.

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
