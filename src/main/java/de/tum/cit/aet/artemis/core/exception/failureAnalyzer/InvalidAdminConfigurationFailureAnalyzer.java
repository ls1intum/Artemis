package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.InvalidAdminConfigurationException;

/**
 * FailureAnalyzer that provides helpful error messages when the internal admin configuration is invalid.
 * This analyzer catches {@link InvalidAdminConfigurationException} and formats it into a user-friendly message
 * with both a description of the problem and recommended actions to fix it.
 */
public class InvalidAdminConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<InvalidAdminConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidAdminConfigurationException cause) {
        String description = buildDescription(cause);
        String action = buildAction(cause);
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(InvalidAdminConfigurationException cause) {
        return String.format(
                "Invalid internal admin configuration detected:%n%n" + "Property '%s' (configuration path: %s) has value: '%s'%n%n" + "This violates the constraint: %s%n%n"
                        + "The internal admin user is required for server startup and must have valid credentials.",
                cause.getPropertyName(), cause.getPropertyPath(), cause.getCurrentValue(), cause.getConstraint());
    }

    private String buildAction(InvalidAdminConfigurationException cause) {
        return String.format(
                "Update your application configuration to fix the internal admin credentials:%n%n" + "Configuration locations (in order of precedence):%n"
                        + "  1. Environment variables: ARTEMIS_USER_MANAGEMENT_%s%n" + "  2. Command line arguments: --artemis.user-management.%s=<value>%n"
                        + "  3. application-*.yml files: artemis.user-management.%s%n%n" + "Required constraint: %s%n%n" + "Example configuration in application-artemis.yml:%n"
                        + "artemis:%n" + "  user-management:%n" + "    internal-admin:%n" + "      username: artemis_admin  # %s%n" + "      password: SecureP@ss123  # %s",

                cause.getPropertyPath().replace("artemis.user-management.", "").replace(".", "_").replace("-", "_").toUpperCase(),
                cause.getPropertyPath().replace("artemis.user-management.", ""), cause.getPropertyPath().replace("artemis.user-management.", ""), cause.getConstraint(),
                getConstraintForProperty("username"), getConstraintForProperty("password"));
    }

    private String getConstraintForProperty(String property) {
        return switch (property) {
            case "username" -> "must be 4-50 characters long, not null or blank";
            case "password" -> "must be at least 8 characters long, not null or blank";
            default -> "must satisfy application constraints";
        };
    }
}
