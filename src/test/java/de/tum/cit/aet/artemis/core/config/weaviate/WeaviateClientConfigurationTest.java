package de.tum.cit.aet.artemis.core.config.weaviate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import de.tum.cit.aet.artemis.core.exception.WeaviateAuthenticationException;
import de.tum.cit.aet.artemis.core.exception.WeaviateConnectionException;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateClientConfiguration;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Tests for WeaviateClientConfiguration
 */
class WeaviateClientConfigurationTest {

    private static final String TEST_COLLECTION_PREFIX = "TestArtemis_";

    @Test
    void testWeaviateClientCreationSecure() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "test-host", 443, 50051, "https", TEST_COLLECTION_PREFIX,
                WeaviateConfigurationProperties.DEFAULT_VECTORIZER_MODULE, null, null, null, null);

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
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", TEST_COLLECTION_PREFIX,
                WeaviateConfigurationProperties.DEFAULT_VECTORIZER_MODULE, null, null, null, null);

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
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "invalid-host", 8001, 50051, "http", TEST_COLLECTION_PREFIX,
                WeaviateConfigurationProperties.DEFAULT_VECTORIZER_MODULE, null, null, null, null);

        WeaviateClientConfiguration config = new WeaviateClientConfiguration(properties);

        // Mock WeaviateClient static methods to throw exception
        try (MockedStatic<WeaviateClient> mockedClient = mockStatic(WeaviateClient.class)) {
            mockedClient.when(() -> WeaviateClient.connectToLocal(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("Connection failed"));

            assertThatThrownBy(() -> config.weaviateClient()).isInstanceOf(WeaviateConnectionException.class).hasMessageContaining("Failed to configure Weaviate client")
                    .hasRootCauseMessage("Connection failed");
        }
    }

    @Test
    void testWeaviateClientAuthenticationFailure() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", TEST_COLLECTION_PREFIX,
                WeaviateConfigurationProperties.DEFAULT_VECTORIZER_MODULE, null, null, null, null);

        WeaviateClientConfiguration config = new WeaviateClientConfiguration(properties);

        // Mock WeaviateClient static methods to throw HTTP 401 exception
        try (MockedStatic<WeaviateClient> mockedClient = mockStatic(WeaviateClient.class)) {
            mockedClient.when(() -> WeaviateClient.connectToLocal(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new RuntimeException("HTTP 401: GET /v1/meta: anonymous access not enabled"));

            assertThatThrownBy(() -> config.weaviateClient()).isInstanceOf(WeaviateAuthenticationException.class).hasMessageContaining("Weaviate authentication failed")
                    .hasRootCauseMessage("HTTP 401: GET /v1/meta: anonymous access not enabled");
        }
    }

    @Test
    void testWeaviateClientAuthenticationFailureNestedCause() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", TEST_COLLECTION_PREFIX,
                WeaviateConfigurationProperties.DEFAULT_VECTORIZER_MODULE, null, null, null, null);

        WeaviateClientConfiguration config = new WeaviateClientConfiguration(properties);

        // Mock WeaviateClient with nested cause containing HTTP 401
        try (MockedStatic<WeaviateClient> mockedClient = mockStatic(WeaviateClient.class)) {
            RuntimeException authCause = new RuntimeException("HTTP 401: anonymous access not enabled");
            RuntimeException wrapper = new RuntimeException("Client initialization failed", authCause);
            mockedClient.when(() -> WeaviateClient.connectToLocal(org.mockito.ArgumentMatchers.any())).thenThrow(wrapper);

            assertThatThrownBy(() -> config.weaviateClient()).isInstanceOf(WeaviateAuthenticationException.class);
        }
    }

}
