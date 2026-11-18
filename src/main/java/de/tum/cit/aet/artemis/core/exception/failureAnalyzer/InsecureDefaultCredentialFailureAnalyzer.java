package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import java.util.Locale;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.InsecureDefaultCredentialException;

/**
 * FailureAnalyzer that turns an {@link InsecureDefaultCredentialException} into an actionable startup
 * error, so an operator sees what to change instead of a stack trace.
 */
public class InsecureDefaultCredentialFailureAnalyzer extends AbstractFailureAnalyzer<InsecureDefaultCredentialException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InsecureDefaultCredentialException cause) {
        // @formatter:off
        String description = ("""
                Artemis refused to start because a security-critical property is not safe for production.

                Property: %s
                Problem:  %s

                Values that Artemis ships as examples are published in the public repository. Leaving one in \
                place in production is equivalent to having no secret at all.""")
                .formatted(cause.getPropertyPath(), cause.getReason());

        String action = ("""
                %s

                Set the value outside version control, for example:
                  - an environment variable, e.g. %s
                  - a command line argument, e.g. --%s=<value>
                  - an application-prod.yml kept next to the deployed WAR and excluded from git

                This check only runs under the 'prod' profile, so local development and tests are unaffected. \
                It is a guard against shipping an example value, not a policy you should work around.""")
                .formatted(cause.getRemediation(), toEnvironmentVariableName(cause.getPropertyPath()), cause.getPropertyPath());
        // @formatter:on

        return new FailureAnalysis(description, action, cause);
    }

    /**
     * Converts a property path into the relaxed-binding environment variable name Spring Boot accepts,
     * e.g. {@code jhipster.security.authentication.jwt.base64-secret} becomes
     * {@code JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64SECRET}.
     *
     * @param propertyPath the configuration property path
     * @return the corresponding environment variable name
     */
    private static String toEnvironmentVariableName(String propertyPath) {
        return propertyPath.replace("-", "").replace(".", "_").toUpperCase(Locale.ROOT);
    }
}
