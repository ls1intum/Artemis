package de.tum.cit.aet.artemis.globalsearch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Unit tests for {@link WeaviateHealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
class WeaviateHealthIndicatorTest {

    private static final String EXPECTED_ADDRESS = "http://localhost:8001";

    @Mock
    private WeaviateClient client;

    private WeaviateHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        WeaviateConfigurationProperties properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", "Artemis_", "none", null, null, null, null);
        healthIndicator = new WeaviateHealthIndicator(client, properties);
    }

    @Test
    void healthUp_whenWeaviateReportsReady() throws Exception {
        when(client.isReady()).thenReturn(true);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("Address", EXPECTED_ADDRESS);
    }

    @Test
    void healthDown_whenWeaviateReportsNotReady() throws Exception {
        when(client.isReady()).thenReturn(false);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("Address", EXPECTED_ADDRESS);
        assertThat(health.getDetails()).containsEntry("ready", false);
    }

    @Test
    void healthDown_whenIsReadyThrows() throws Exception {
        when(client.isReady()).thenThrow(new RuntimeException("Connection refused"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("Address", EXPECTED_ADDRESS);
        assertThat(health.getDetails()).containsKey("error");
    }
}
