package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.config.ConfigurationValidator;
import de.tum.cit.aet.artemis.core.exception.WeaviateAuthenticationException;

/**
 * FailureAnalyzer that provides helpful error messages when authentication to Weaviate fails (HTTP 401).
 * This analyzer catches {@link WeaviateAuthenticationException} and formats it into a user-friendly message
 * with guidance on configuring API key authentication.
 */
public class WeaviateAuthenticationFailureAnalyzer extends AbstractFailureAnalyzer<WeaviateAuthenticationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, WeaviateAuthenticationException cause) {
        String description = buildDescription(cause);
        String action = buildAction();
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(WeaviateAuthenticationException cause) {
        String scheme = cause.isSecure() ? ConfigurationValidator.HTTPS_SCHEME : ConfigurationValidator.HTTP_SCHEME;
        return String.format(
                "Authentication to Weaviate failed (HTTP 401).%n%n" + "Connection details:%n" + "    Host: %s%n" + "    HTTP Port: %d (%s://%s:%d)%n%n"
                        + "The Weaviate server requires API key authentication, but the Artemis configuration does not provide a valid API key.%n%n" + "Error: %s%n",
                cause.getHttpHost(), cause.getHttpPort(), scheme, cause.getHttpHost(), cause.getHttpPort(),
                cause.getCause() != null ? cause.getCause().getMessage() : cause.getMessage());
    }

    private String buildAction() {
        return """
                Please verify the following:

                1. CONFIGURE API KEY IN ARTEMIS:
                   Add the api-key property to your Artemis configuration (application-local.yml):
                       artemis:
                         weaviate:
                           api-key: <your-api-key>    # Must match AUTHENTICATION_APIKEY_ALLOWED_KEYS on the Weaviate server

                2. VERIFY WEAVIATE SERVER AUTHENTICATION SETTINGS:
                   In your Weaviate environment file (e.g. docker/weaviate/default.env), ensure:
                       AUTHENTICATION_APIKEY_ENABLED=true
                       AUTHENTICATION_APIKEY_ALLOWED_KEYS=<your-api-key>
                       AUTHENTICATION_APIKEY_USERS=<your-user>
                   The api-key value in Artemis must match one of the keys in AUTHENTICATION_APIKEY_ALLOWED_KEYS.
                   Note: The values shown above are placeholders. Replace them with your actual credentials.
                   For local development, the defaults in docker/weaviate/default.env can be used as-is.

                3. ALTERNATIVE - DISABLE AUTHENTICATION ON WEAVIATE SERVER:
                   If you don't need authentication, set in your Weaviate environment file:
                       AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED=true
                   and comment out the AUTHENTICATION_APIKEY_* lines. Then restart Weaviate.

                4. DISABLE WEAVIATE (if not needed):
                   artemis:
                     weaviate:
                       enabled: false
                """;
    }
}
