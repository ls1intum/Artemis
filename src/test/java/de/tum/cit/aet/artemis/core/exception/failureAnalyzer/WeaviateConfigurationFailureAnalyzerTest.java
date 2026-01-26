package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.WeaviateConfigurationException;

/**
 * Tests for WeaviateConfigurationFailureAnalyzer
 */
class WeaviateConfigurationFailureAnalyzerTest {

    private final WeaviateConfigurationFailureAnalyzer analyzer = new WeaviateConfigurationFailureAnalyzer();

    @Test
    void testAnalyzeWithMissingProperties() {
        List<String> missingProperties = Arrays.asList("host", "port", "grpcPort");
        WeaviateConfigurationException exception = new WeaviateConfigurationException("Invalid configuration", missingProperties);
        RuntimeException rootFailure = new RuntimeException("Root cause", exception);

        FailureAnalysis analysis = analyzer.analyze(rootFailure, exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Invalid Weaviate configuration detected");
        assertThat(analysis.getDescription()).contains("host");
        assertThat(analysis.getDescription()).contains("port");
        assertThat(analysis.getDescription()).contains("grpcPort");

        assertThat(analysis.getAction()).contains("Update your application configuration");
        assertThat(analysis.getAction()).contains("enabled: true");
        assertThat(analysis.getAction()).contains("enabled: false");

        assertThat(analysis.getCause()).isEqualTo(exception);
    }

    @Test
    void testAnalyzeWithSingleMissingProperty() {
        List<String> missingProperties = Arrays.asList("host");
        WeaviateConfigurationException exception = new WeaviateConfigurationException("Missing host", missingProperties);
        RuntimeException rootFailure = new RuntimeException("Root cause", exception);

        FailureAnalysis analysis = analyzer.analyze(rootFailure, exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Invalid Weaviate configuration detected");
        assertThat(analysis.getDescription()).contains("host");
        assertThat(analysis.getAction()).contains("Update your application configuration");
        assertThat(analysis.getAction()).contains("enabled: true");
        assertThat(analysis.getAction()).contains("enabled: false");
        assertThat(analysis.getCause()).isEqualTo(exception);
    }

    @Test
    void testAnalyzeWithEmptyMissingProperties() {
        List<String> missingProperties = Arrays.asList();
        WeaviateConfigurationException exception = new WeaviateConfigurationException("General error", missingProperties);
        RuntimeException rootFailure = new RuntimeException("Root cause", exception);

        FailureAnalysis analysis = analyzer.analyze(rootFailure, exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Invalid Weaviate configuration detected");
        assertThat(analysis.getAction()).contains("Update your application configuration");
        assertThat(analysis.getAction()).contains("enabled: true");
        assertThat(analysis.getAction()).contains("enabled: false");
        assertThat(analysis.getCause()).isEqualTo(exception);
    }
}
