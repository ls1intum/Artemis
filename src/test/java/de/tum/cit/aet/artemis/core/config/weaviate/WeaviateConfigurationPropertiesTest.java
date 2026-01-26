package de.tum.cit.aet.artemis.core.config.weaviate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for Weaviate configuration properties
 */
class WeaviateConfigurationPropertiesTest {

    @Test
    void testConfigurationProperties() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "test-host", 9999, 60051, true, "https");

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
        assertThat(properties.port()).isEqualTo(8080);
        assertThat(properties.grpcPort()).isEqualTo(50051);
        assertThat(properties.secure()).isFalse();
        assertThat(properties.scheme()).isEqualTo("http");
    }

    @Test
    void testSchemeSecureValidation() {
        // Test valid combinations
        assertThat(new WeaviateConfigurationProperties(false, "localhost", 8080, 50051, false, "http")).isNotNull();
        assertThat(new WeaviateConfigurationProperties(false, "localhost", 443, 50051, true, "https")).isNotNull();

        // Test scheme defaults based on secure flag
        WeaviateConfigurationProperties secureDefault = new WeaviateConfigurationProperties(false, "localhost", 443, 50051, true, null);
        assertThat(secureDefault.scheme()).isEqualTo("https");

        WeaviateConfigurationProperties nonSecureDefault = new WeaviateConfigurationProperties(false, "localhost", 8080, 50051, false, null);
        assertThat(nonSecureDefault.scheme()).isEqualTo("http");
    }

    @Test
    void testSchemeSecureInconsistency() {
        // Test secure=true but scheme=http (invalid)
        assertThatThrownBy(() -> new WeaviateConfigurationProperties(false, "localhost", 443, 50051, true, "http")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secure=true but scheme=http");

        // Test secure=false but scheme=https (invalid)
        assertThatThrownBy(() -> new WeaviateConfigurationProperties(false, "localhost", 8080, 50051, false, "https")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secure=false but scheme=https");
    }
}
