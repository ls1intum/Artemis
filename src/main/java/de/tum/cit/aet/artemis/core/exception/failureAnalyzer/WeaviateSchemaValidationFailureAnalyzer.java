package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.WeaviateSchemaValidationException;

/**
 * FailureAnalyzer that provides helpful error messages when Weaviate schema validation fails.
 * This analyzer catches {@link WeaviateSchemaValidationException} and formats it into a user-friendly message
 * with both a description of the problem and recommended actions to fix it.
 */
public class WeaviateSchemaValidationFailureAnalyzer extends AbstractFailureAnalyzer<WeaviateSchemaValidationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, WeaviateSchemaValidationException cause) {
        String description = buildDescription(cause);
        String action = buildAction(cause);
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(WeaviateSchemaValidationException cause) {
        return String.format("Weaviate schema validation failed!%n%n" + "The Artemis Weaviate schema definitions do not match the Iris repository schemas.%n"
                + "This could lead to data incompatibility issues between Artemis and Iris.%n%n" + "Validation errors found:%n%s", cause.getFormattedErrors());
    }

    private String buildAction(WeaviateSchemaValidationException cause) {
        return String.format("To resolve this issue, you have the following options:%n%n" + "Option 1: Update the Artemis schema definitions%n"
                + "    Location: de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateSchemas%n"
                + "    Sync with: https://github.com/ls1intum/edutelligence/tree/main/iris/src/iris/vector_database%n%n"
                + "Option 2: Bypass schema validation (NOT RECOMMENDED for production)%n" + "    Set the following property in your configuration:%n"
                + "    artemis.weaviate.schema-validation.strict: false%n%n" + "Option 3: Disable schema validation entirely%n"
                + "    Set the following property in your configuration:%n" + "    artemis.weaviate.schema-validation.enabled: false%n%n"
                + "WARNING: Options 2 and 3 may cause data compatibility issues with Iris.%n" + "Only use them if you understand the implications.");
    }
}
