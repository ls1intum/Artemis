package de.tum.cit.aet.artemis.core.config.weaviate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests for Weaviate configuration properties
 */
@SpringBootTest
@TestPropertySource(properties = { "artemis.weaviate.enabled=true", "artemis.weaviate.host=test-host", "artemis.weaviate.port=9999", "artemis.weaviate.grpc-port=60051",
        "artemis.weaviate.secure=true", "artemis.weaviate.scheme=https" })
class WeaviateConfigurationPropertiesTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private WeaviateConfigurationProperties properties;

    @Test
    void testConfigurationPropertiesFromTestProperties() {
        // Test that the properties from @TestPropertySource are correctly loaded
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getHost()).isEqualTo("test-host");
        assertThat(properties.getPort()).isEqualTo(9999);
        assertThat(properties.getGrpcPort()).isEqualTo(60051);
        assertThat(properties.isSecure()).isTrue();
        assertThat(properties.getScheme()).isEqualTo("https");
    }

    @Test
    void testDefaultValues() {
        WeaviateConfigurationProperties defaultProperties = new WeaviateConfigurationProperties();

        assertThat(defaultProperties.isEnabled()).isFalse();
        assertThat(defaultProperties.getHost()).isEqualTo("localhost");
        assertThat(defaultProperties.getPort()).isEqualTo(8080);
        assertThat(defaultProperties.getGrpcPort()).isEqualTo(50051);
        assertThat(defaultProperties.isSecure()).isFalse();
        assertThat(defaultProperties.getScheme()).isEqualTo("http");
    }
}
