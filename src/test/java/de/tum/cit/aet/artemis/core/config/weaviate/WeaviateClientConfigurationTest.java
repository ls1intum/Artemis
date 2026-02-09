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
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "test-host", 443, 50051, "https", "");

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
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", "");

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
    void testWeaviateClientConfigurationFailure() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "invalid-host", 8001, 50051, "http", "");

        WeaviateClientConfiguration config = new WeaviateClientConfiguration(properties);

        // Mock WeaviateClient static methods to throw exception
        try (MockedStatic<WeaviateClient> mockedClient = mockStatic(WeaviateClient.class)) {
            mockedClient.when(() -> WeaviateClient.connectToLocal(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("Connection failed"));

            assertThatThrownBy(() -> config.weaviateClient()).isInstanceOf(WeaviateConnectionException.class).hasMessageContaining("Failed to configure Weaviate client")
                    .hasRootCauseMessage("Connection failed");
        }
    }

}
