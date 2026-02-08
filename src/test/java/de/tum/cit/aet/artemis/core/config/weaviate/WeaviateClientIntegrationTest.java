package de.tum.cit.aet.artemis.core.config.weaviate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.weaviate.WeaviateContainer;

import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Integration tests for Weaviate client connectivity using Testcontainers.
 * <p>
 * These tests verify that the Weaviate client can connect to a real Weaviate instance
 * and perform basic operations. No Spring context is loaded to keep overhead minimal.
 * <p>
 * Requires Docker to be available; skipped otherwise.
 * <p>
 * The Weaviate server version is defined in {@code .env} ({@code WEAVIATE_SERVER_VERSION})
 * and must be compatible with the Java client version.
 *
 * @see <a href="https://docs.weaviate.io/weaviate/release-notes">Weaviate client/server compatibility matrix</a>
 */
@EnabledIf("isDockerAvailable")
class WeaviateClientIntegrationTest {

    // Injected by test.gradle from WEAVIATE_SERVER_VERSION in .env
    private static final String WEAVIATE_IMAGE = "cr.weaviate.io/semitechnologies/weaviate:" + System.getProperty("weaviate.server.version");

    private static WeaviateContainer weaviate;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void beforeAll() {
        weaviate = new WeaviateContainer(WEAVIATE_IMAGE);
        weaviate.start();
    }

    @AfterAll
    static void afterAll() {
        if (weaviate != null) {
            weaviate.stop();
        }
    }

    @Test
    void testClientConnectsAndIsReady() throws Exception {
        String host = weaviate.getHost();
        int httpPort = weaviate.getMappedPort(8080);
        int grpcPort = weaviate.getMappedPort(50051);

        var properties = new WeaviateConfigurationProperties(true, host, httpPort, grpcPort, "http");
        var config = new WeaviateClientConfiguration(properties);

        try (WeaviateClient client = config.weaviateClient()) {
            assertThat(client).isNotNull();
            assertThat(client.isReady()).isTrue();
        }
    }

    @Test
    void testDirectLocalConnection() throws Exception {
        String host = weaviate.getHost();
        int httpPort = weaviate.getMappedPort(8080);
        int grpcPort = weaviate.getMappedPort(50051);

        try (WeaviateClient client = WeaviateClient.connectToLocal(config -> config.host(host).port(httpPort).grpcPort(grpcPort))) {
            assertThat(client).isNotNull();
            assertThat(client.isReady()).isTrue();
        }
    }

    @Test
    void testBasicCollectionOperations() throws Exception {
        String host = weaviate.getHost();
        int httpPort = weaviate.getMappedPort(8080);
        int grpcPort = weaviate.getMappedPort(50051);

        try (WeaviateClient client = WeaviateClient.connectToLocal(config -> config.host(host).port(httpPort).grpcPort(grpcPort))) {
            String collectionName = "TestCollection";

            // Create a collection
            client.collections.create(collectionName);

            // Verify it exists
            boolean exists = client.collections.exists(collectionName);
            assertThat(exists).isTrue();

            // Delete the collection
            client.collections.delete(collectionName);

            // Verify it no longer exists
            exists = client.collections.exists(collectionName);
            assertThat(exists).isFalse();
        }
    }
}
