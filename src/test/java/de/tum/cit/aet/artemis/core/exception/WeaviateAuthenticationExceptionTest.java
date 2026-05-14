package de.tum.cit.aet.artemis.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for WeaviateAuthenticationException
 */
class WeaviateAuthenticationExceptionTest {

    @Test
    void testExceptionCreation() {
        String message = "Authentication failed";
        Throwable cause = new RuntimeException("HTTP 401: anonymous access not enabled");
        String httpHost = "localhost";
        int httpPort = 8001;
        boolean secure = false;

        WeaviateAuthenticationException exception = new WeaviateAuthenticationException(message, cause, httpHost, httpPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getHttpHost()).isEqualTo(httpHost);
        assertThat(exception.getHttpPort()).isEqualTo(httpPort);
        assertThat(exception.isSecure()).isFalse();
    }

    @Test
    void testExceptionCreationSecure() {
        String message = "Secure auth failed";
        Throwable cause = new RuntimeException("HTTP 401: invalid API key");
        String httpHost = "weaviate.example.com";
        int httpPort = 443;
        boolean secure = true;

        WeaviateAuthenticationException exception = new WeaviateAuthenticationException(message, cause, httpHost, httpPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getHttpHost()).isEqualTo(httpHost);
        assertThat(exception.getHttpPort()).isEqualTo(httpPort);
        assertThat(exception.isSecure()).isTrue();
    }

    @Test
    void testExceptionWithNullCause() {
        String message = "Auth failed";
        String httpHost = "localhost";
        int httpPort = 8001;
        boolean secure = false;

        WeaviateAuthenticationException exception = new WeaviateAuthenticationException(message, null, httpHost, httpPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getHttpHost()).isEqualTo(httpHost);
        assertThat(exception.getHttpPort()).isEqualTo(httpPort);
        assertThat(exception.isSecure()).isFalse();
    }
}
