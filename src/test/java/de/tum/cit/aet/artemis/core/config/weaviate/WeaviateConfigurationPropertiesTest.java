package de.tum.cit.aet.artemis.core.config.weaviate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;

/**
 * Tests for Weaviate configuration properties
 */
class WeaviateConfigurationPropertiesTest {

    private static final String TEST_COLLECTION_PREFIX = "TestArtemis_";

    @Test
    void testConfigurationProperties() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "test-host", 9999, 60051, "https", TEST_COLLECTION_PREFIX);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.httpHost()).isEqualTo("test-host");
        assertThat(properties.httpPort()).isEqualTo(9999);
        assertThat(properties.grpcPort()).isEqualTo(60051);
        assertThat(properties.secure()).isTrue();
        assertThat(properties.scheme()).isEqualTo("https");
        assertThat(properties.collectionPrefix()).isEmpty();
    }

    @Test
    void testTypicalDefaults() {
        // Test typical default configuration values
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(false, "localhost", 8001, 50051, "http", TEST_COLLECTION_PREFIX);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.httpHost()).isEqualTo("localhost");
        assertThat(properties.httpPort()).isEqualTo(8001);
        assertThat(properties.grpcPort()).isEqualTo(50051);
        assertThat(properties.secure()).isFalse();
        assertThat(properties.scheme()).isEqualTo("http");
        assertThat(properties.collectionPrefix()).isEmpty();
    }

    @Test
    void testCollectionPrefix() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", "Test");
        assertThat(properties.collectionPrefix()).isEqualTo("Test");
    }

    @Test
    void testSchemeSecureValidation() {
        // Test valid combinations
        assertThat(new WeaviateConfigurationProperties(false, "localhost", 8001, 50051, "http", TEST_COLLECTION_PREFIX)).isNotNull();
        assertThat(new WeaviateConfigurationProperties(false, "localhost", 443, 50051, "https", TEST_COLLECTION_PREFIX)).isNotNull();

        // Test http scheme
        WeaviateConfigurationProperties httpProps = new WeaviateConfigurationProperties(false, "localhost", 8001, 50051, "http", TEST_COLLECTION_PREFIX);
        assertThat(httpProps.scheme()).isEqualTo("http");
        assertThat(httpProps.secure()).isFalse();

        // Test https scheme
        WeaviateConfigurationProperties httpsExplicit = new WeaviateConfigurationProperties(false, "localhost", 443, 50051, "https", TEST_COLLECTION_PREFIX);
        assertThat(httpsExplicit.scheme()).isEqualTo("https");
        assertThat(httpsExplicit.secure()).isTrue();
    }

    @Test
    void testSchemeValidation() {
        // Test valid schemes
        WeaviateConfigurationProperties httpProps = new WeaviateConfigurationProperties(false, "localhost", 8001, 50051, "http", TEST_COLLECTION_PREFIX);
        assertThat(httpProps.secure()).isFalse();

        WeaviateConfigurationProperties httpsProps = new WeaviateConfigurationProperties(false, "localhost", 443, 50051, "https", TEST_COLLECTION_PREFIX);
        assertThat(httpsProps.secure()).isTrue();

        // Test that secure() method correctly derives from scheme
        assertThat(new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", TEST_COLLECTION_PREFIX).secure()).isFalse();
        assertThat(new WeaviateConfigurationProperties(true, "localhost", 443, 50051, "https", TEST_COLLECTION_PREFIX).secure()).isTrue();
    }
}
