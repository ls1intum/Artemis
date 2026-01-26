package de.tum.cit.aet.artemis.core.config.weaviate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import de.tum.cit.aet.artemis.core.exception.WeaviateConnectionException;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Tests for WeaviateClientConfiguration
 */
class WeaviateClientConfigurationTest {

    @Test
    void testWeaviateClientCreationSecure() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties();
        properties.setEnabled(true);
        properties.setHost("test-host");
        properties.setPort(443);
        properties.setGrpcPort(50051);
        properties.setSecure(true);
        properties.setScheme("https");

        WeaviateClientConfiguration config = new WeaviateClientConfiguration(properties);

        // Mock WeaviateClient static methods to avoid actual connection
        try (MockedStatic<WeaviateClient> mockedClient = mockStatic(WeaviateClient.class)) {
            WeaviateClient mockClient = org.mockito.Mockito.mock(WeaviateClient.class);
            mockedClient.when(() -> WeaviateClient.connectToCustom(org.mockito.ArgumentMatchers.any())).thenReturn(mockClient);

            WeaviateClient client = config.weaviateClient();
            assertThat(client).isNotNull();
        }
    }

    @Test
    void testWeaviateClientCreationNonSecure() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(8080);
        properties.setGrpcPort(50051);
        properties.setSecure(false);
        properties.setScheme("http");

        WeaviateClientConfiguration config = new WeaviateClientConfiguration(properties);

        // Mock WeaviateClient static methods to avoid actual connection
        try (MockedStatic<WeaviateClient> mockedClient = mockStatic(WeaviateClient.class)) {
            WeaviateClient mockClient = org.mockito.Mockito.mock(WeaviateClient.class);
            mockedClient.when(() -> WeaviateClient.connectToLocal(org.mockito.ArgumentMatchers.any())).thenReturn(mockClient);

            WeaviateClient client = config.weaviateClient();
            assertThat(client).isNotNull();
        }
    }

    @Test
    void testWeaviateClientConnectionFailure() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties();
        properties.setEnabled(true);
        properties.setHost("invalid-host");
        properties.setPort(8080);
        properties.setGrpcPort(50051);
        properties.setSecure(false);
        properties.setScheme("http");

        WeaviateClientConfiguration config = new WeaviateClientConfiguration(properties);

        // Mock WeaviateClient static methods to throw exception
        try (MockedStatic<WeaviateClient> mockedClient = mockStatic(WeaviateClient.class)) {
            mockedClient.when(() -> WeaviateClient.connectToLocal(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("Connection failed"));

            assertThatThrownBy(() -> config.weaviateClient()).isInstanceOf(WeaviateConnectionException.class).hasMessageContaining("Failed to connect to Weaviate")
                    .hasRootCauseMessage("Connection failed");
        }
    }

    @Test
    void testConstructor() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties();
        WeaviateClientConfiguration config = new WeaviateClientConfiguration(properties);
        assertThat(config).isNotNull();
    }
}
