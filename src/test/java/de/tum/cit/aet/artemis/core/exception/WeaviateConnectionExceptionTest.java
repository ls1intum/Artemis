package de.tum.cit.aet.artemis.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for WeaviateConnectionException
 */
class WeaviateConnectionExceptionTest {

    @Test
    void testExceptionCreation() {
        String message = "Connection failed";
        Throwable cause = new RuntimeException("Network error");
        String httpHost = "localhost";
        int httpPort = 8080;
        int grpcPort = 50051;
        boolean secure = false;

        WeaviateConnectionException exception = new WeaviateConnectionException(message, cause, httpHost, httpPort, grpcPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getHttpHost()).isEqualTo(httpHost);
        assertThat(exception.getHttpPort()).isEqualTo(httpPort);
        assertThat(exception.getGrpcPort()).isEqualTo(grpcPort);
        assertThat(exception.isSecure()).isFalse();
    }

    @Test
    void testExceptionCreationSecure() {
        String message = "Secure connection failed";
        Throwable cause = new RuntimeException("TLS error");
        String httpHost = "weaviate.example.com";
        int httpPort = 443;
        int grpcPort = 50051;
        boolean secure = true;

        WeaviateConnectionException exception = new WeaviateConnectionException(message, cause, httpHost, httpPort, grpcPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getHttpHost()).isEqualTo(httpHost);
        assertThat(exception.getHttpPort()).isEqualTo(httpPort);
        assertThat(exception.getGrpcPort()).isEqualTo(grpcPort);
        assertThat(exception.isSecure()).isTrue();
    }

    @Test
    void testExceptionWithNullCause() {
        String message = "Connection timeout";
        String httpHost = "unreachable-host";
        int httpPort = 8080;
        int grpcPort = 50051;
        boolean secure = false;

        WeaviateConnectionException exception = new WeaviateConnectionException(message, null, httpHost, httpPort, grpcPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getHttpHost()).isEqualTo(httpHost);
        assertThat(exception.getHttpPort()).isEqualTo(httpPort);
        assertThat(exception.getGrpcPort()).isEqualTo(grpcPort);
        assertThat(exception.isSecure()).isFalse();
    }
}
