package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.WeaviateConnectionException;

/**
 * Tests for WeaviateConnectionFailureAnalyzer
 */
class WeaviateConnectionFailureAnalyzerTest {

    private final WeaviateConnectionFailureAnalyzer analyzer = new WeaviateConnectionFailureAnalyzer();

    @Test
    void testAnalyzeNonSecureConnection() {
        RuntimeException cause = new RuntimeException("Connection refused");
        WeaviateConnectionException exception = new WeaviateConnectionException("Failed to connect", cause, "localhost", 8080, 50051, false);
        RuntimeException rootFailure = new RuntimeException("Root cause", exception);

        FailureAnalysis analysis = analyzer.analyze(rootFailure, exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Failed to connect to Weaviate vector database");
        assertThat(analysis.getDescription()).contains("Host: localhost");
        assertThat(analysis.getDescription()).contains("HTTP Port: 8080 (http://localhost:8080)");
        assertThat(analysis.getDescription()).contains("gRPC Port: 50051");
        assertThat(analysis.getDescription()).contains("Connection refused");

        assertThat(analysis.getAction()).contains("Please verify the following");
        assertThat(analysis.getAction()).contains("VERIFY WEAVIATE IS RUNNING");
        assertThat(analysis.getAction()).contains("VERIFY NETWORK CONNECTIVITY");
        assertThat(analysis.getAction()).contains("curl -v http://localhost:8080/v1/.well-known/ready");
        assertThat(analysis.getAction()).contains("nc -zv localhost 8080");
        assertThat(analysis.getAction()).contains("nc -zv localhost 50051");
        assertThat(analysis.getAction()).contains("DISABLE WEAVIATE");

        assertThat(analysis.getCause()).isEqualTo(exception);
    }

    @Test
    void testAnalyzeSecureConnection() {
        RuntimeException cause = new RuntimeException("SSL handshake failed");
        WeaviateConnectionException exception = new WeaviateConnectionException("TLS connection failed", cause, "weaviate.example.com", 443, 50051, true);
        RuntimeException rootFailure = new RuntimeException("Root cause", exception);

        FailureAnalysis analysis = analyzer.analyze(rootFailure, exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Failed to connect to Weaviate vector database");
        assertThat(analysis.getDescription()).contains("Host: weaviate.example.com");
        assertThat(analysis.getDescription()).contains("HTTP Port: 443 (https://weaviate.example.com:443)");
        assertThat(analysis.getDescription()).contains("gRPC Port: 50051");
        assertThat(analysis.getDescription()).contains("SSL handshake failed");

        assertThat(analysis.getAction()).contains("curl -v https://weaviate.example.com:443/v1/.well-known/ready");
        assertThat(analysis.getAction()).contains("nc -zv weaviate.example.com 443");
        assertThat(analysis.getAction()).contains("nc -zv weaviate.example.com 50051");
        assertThat(analysis.getCause()).isEqualTo(exception);
    }

    @Test
    void testAnalyzeWithNullCause() {
        WeaviateConnectionException exception = new WeaviateConnectionException("Generic connection error", null, "test-host", 8080, 50051, false);
        RuntimeException rootFailure = new RuntimeException("Root cause", exception);

        FailureAnalysis analysis = analyzer.analyze(rootFailure, exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Failed to connect to Weaviate vector database");
        assertThat(analysis.getDescription()).contains("Host: test-host");
        assertThat(analysis.getDescription()).contains("Generic connection error");
        assertThat(analysis.getAction()).contains("Please verify the following");
        assertThat(analysis.getCause()).isEqualTo(exception);
    }
}
