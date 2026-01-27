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
        String host = "localhost";
        int port = 8080;
        int grpcPort = 50051;
        boolean secure = false;

        WeaviateConnectionException exception = new WeaviateConnectionException(message, cause, host, port, grpcPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getHost()).isEqualTo(host);
        assertThat(exception.getPort()).isEqualTo(port);
        assertThat(exception.getGrpcPort()).isEqualTo(grpcPort);
        assertThat(exception.isSecure()).isFalse();
    }

    @Test
    void testExceptionCreationSecure() {
        String message = "Secure connection failed";
        Throwable cause = new RuntimeException("TLS error");
        String host = "weaviate.example.com";
        int port = 443;
        int grpcPort = 50051;
        boolean secure = true;

        WeaviateConnectionException exception = new WeaviateConnectionException(message, cause, host, port, grpcPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getHost()).isEqualTo(host);
        assertThat(exception.getPort()).isEqualTo(port);
        assertThat(exception.getGrpcPort()).isEqualTo(grpcPort);
        assertThat(exception.isSecure()).isTrue();
    }

    @Test
    void testExceptionWithNullCause() {
        String message = "Connection timeout";
        String host = "unreachable-host";
        int port = 8080;
        int grpcPort = 50051;
        boolean secure = false;

        WeaviateConnectionException exception = new WeaviateConnectionException(message, null, host, port, grpcPort, secure);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getHost()).isEqualTo(host);
        assertThat(exception.getPort()).isEqualTo(port);
        assertThat(exception.getGrpcPort()).isEqualTo(grpcPort);
        assertThat(exception.isSecure()).isFalse();
    }
}
