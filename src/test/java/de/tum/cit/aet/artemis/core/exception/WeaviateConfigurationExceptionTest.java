package de.tum.cit.aet.artemis.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for WeaviateConfigurationException
 */
class WeaviateConfigurationExceptionTest {

    @Test
    void testExceptionCreation() {
        List<String> missingProperties = Arrays.asList("host", "port");
        String message = "Configuration is invalid";

        WeaviateConfigurationException exception = new WeaviateConfigurationException(message, missingProperties);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getMissingOrInvalidProperties()).isEqualTo(missingProperties);
    }

    @Test
    void testExceptionWithEmptyProperties() {
        List<String> emptyProperties = Arrays.asList();
        String message = "Empty configuration";

        WeaviateConfigurationException exception = new WeaviateConfigurationException(message, emptyProperties);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getMissingOrInvalidProperties()).isEmpty();
    }

    @Test
    void testExceptionWithSingleProperty() {
        List<String> singleProperty = Arrays.asList("grpcPort");
        String message = "Invalid gRPC port";

        WeaviateConfigurationException exception = new WeaviateConfigurationException(message, singleProperty);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getMissingOrInvalidProperties()).hasSize(1);
        assertThat(exception.getMissingOrInvalidProperties().getFirst()).isEqualTo("grpcPort");
    }
}
