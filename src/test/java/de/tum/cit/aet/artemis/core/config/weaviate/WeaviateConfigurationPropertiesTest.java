package de.tum.cit.aet.artemis.core.config.weaviate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for Weaviate configuration properties
 */
class WeaviateConfigurationPropertiesTest {

    @Test
    void testConfigurationProperties() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "test-host", 9999, 60051, "https");

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.host()).isEqualTo("test-host");
        assertThat(properties.port()).isEqualTo(9999);
        assertThat(properties.grpcPort()).isEqualTo(60051);
        assertThat(properties.secure()).isTrue();
        assertThat(properties.scheme()).isEqualTo("https");
    }

    @Test
    void testDefaultValues() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties();

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.host()).isEqualTo("localhost");
        assertThat(properties.port()).isEqualTo(8001);
        assertThat(properties.grpcPort()).isEqualTo(50051);
        assertThat(properties.secure()).isFalse();
        assertThat(properties.scheme()).isEqualTo("http");
    }

    @Test
    void testSchemeSecureValidation() {
        // Test valid combinations
        assertThat(new WeaviateConfigurationProperties(false, "localhost", 8001, 50051, "http")).isNotNull();
        assertThat(new WeaviateConfigurationProperties(false, "localhost", 443, 50051, "https")).isNotNull();

        // Test scheme defaults
        WeaviateConfigurationProperties httpDefault = new WeaviateConfigurationProperties(false, "localhost", 8001, 50051, null);
        assertThat(httpDefault.scheme()).isEqualTo("http");
        assertThat(httpDefault.secure()).isFalse();

        WeaviateConfigurationProperties httpsExplicit = new WeaviateConfigurationProperties(false, "localhost", 443, 50051, "https");
        assertThat(httpsExplicit.scheme()).isEqualTo("https");
        assertThat(httpsExplicit.secure()).isTrue();
    }

    @Test
    void testSchemeValidation() {
        // Test valid schemes
        WeaviateConfigurationProperties httpProps = new WeaviateConfigurationProperties(false, "localhost", 8001, 50051, "http");
        assertThat(httpProps.secure()).isFalse();

        WeaviateConfigurationProperties httpsProps = new WeaviateConfigurationProperties(false, "localhost", 443, 50051, "https");
        assertThat(httpsProps.secure()).isTrue();

        // Test that secure() method correctly derives from scheme
        assertThat(new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http").secure()).isFalse();
        assertThat(new WeaviateConfigurationProperties(true, "localhost", 443, 50051, "https").secure()).isTrue();
    }
}
