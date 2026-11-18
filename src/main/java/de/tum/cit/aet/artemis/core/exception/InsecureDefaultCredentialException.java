package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.core.exception.failureAnalyzer.InsecureDefaultCredentialFailureAnalyzer;

/**
 * Exception thrown at startup when a security-critical property still holds a value that Artemis ships
 * as an example default (or is otherwise unusable in production), and the application is running under
 * the {@code prod} profile.
 * <p>
 * These values are published in the Artemis repository, so leaving one in place in production is
 * equivalent to having no secret at all: a known JWT signing key allows anyone to forge a token for any
 * user with any authority, and a known admin password or build-agent git password grants direct access.
 * <p>
 * This exception is caught by {@link InsecureDefaultCredentialFailureAnalyzer} to produce an actionable
 * startup error instead of an opaque stack trace. It deliberately fails the boot rather than logging a
 * warning: a warning in a startup log is routinely missed, and the whole point of the check is that the
 * unsafe state must not be able to reach a running production system.
 */
public class InsecureDefaultCredentialException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String propertyPath;

    private final String reason;

    private final String remediation;

    /**
     * @param propertyPath the full configuration path of the offending property, e.g.
     *                         {@code jhipster.security.authentication.jwt.base64-secret}
     * @param reason       what is wrong with the configured value, phrased for an operator. Must never
     *                         include the value itself.
     * @param remediation  concrete instructions for producing an acceptable value
     */
    public InsecureDefaultCredentialException(@NonNull String propertyPath, @NonNull String reason, @NonNull String remediation) {
        super("Insecure production configuration for '%s': %s".formatted(propertyPath, reason));
        this.propertyPath = propertyPath;
        this.reason = reason;
        this.remediation = remediation;
    }

    public String getPropertyPath() {
        return propertyPath;
    }

    public String getReason() {
        return reason;
    }

    public String getRemediation() {
        return remediation;
    }
}
