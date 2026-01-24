package de.tum.cit.aet.artemis.core.config.weaviate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;

/**
 * Tests for Weaviate configuration properties
 */
@SpringBootTest
@EnableConfigurationProperties(WeaviateConfigurationProperties.class)
@TestPropertySource(properties = { "artemis.weaviate.enabled=true", "artemis.weaviate.host=test-host", "artemis.weaviate.port=9999", "artemis.weaviate.grpc-port=60051",
        "artemis.weaviate.secure=true", "artemis.weaviate.scheme=https" })
class WeaviateConfigurationPropertiesTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    void testConfigurationProperties() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties();
        properties.setEnabled(true);
        properties.setHost("test-host");
        properties.setPort(9999);
        properties.setGrpcPort(60051);
        properties.setSecure(true);
        properties.setScheme("https");

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getHost()).isEqualTo("test-host");
        assertThat(properties.getPort()).isEqualTo(9999);
        assertThat(properties.getGrpcPort()).isEqualTo(60051);
        assertThat(properties.isSecure()).isTrue();
        assertThat(properties.getScheme()).isEqualTo("https");
    }

    @Test
    void testDefaultValues() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getHost()).isEqualTo("localhost");
        assertThat(properties.getPort()).isEqualTo(8080);
        assertThat(properties.getGrpcPort()).isEqualTo(50051);
        assertThat(properties.isSecure()).isFalse();
        assertThat(properties.getScheme()).isEqualTo("http");
    }
}
