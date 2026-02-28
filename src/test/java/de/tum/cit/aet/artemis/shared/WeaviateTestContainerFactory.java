package de.tum.cit.aet.artemis.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.weaviate.WeaviateContainer;

/**
 * Provides a shared singleton {@link WeaviateContainer} for integration tests.
 * <p>
 * The container is lazily started on first access and reused across all test classes
 * in the same JVM. Testcontainers' Ryuk automatically stops the container when the JVM exits.
 * <p>
 * Returns {@code null} if Docker is unavailable, the {@code weaviate.server.version} system
 * property is not set, or the container fails to start. Tests should guard themselves
 * with {@code @EnabledIf} checks.
 *
 * @see de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest
 * @see de.tum.cit.aet.artemis.core.config.weaviate.WeaviateClientIntegrationTest
 */
public final class WeaviateTestContainerFactory {

    private static final Logger log = LoggerFactory.getLogger(WeaviateTestContainerFactory.class);

    private static WeaviateContainer instance;

    private static boolean initialized = false;

    private WeaviateTestContainerFactory() {
    }

    /**
     * Returns the shared Weaviate container, starting it on first call.
     *
     * @return the running container, or {@code null} if Docker is unavailable or startup fails
     */
    public static synchronized WeaviateContainer getContainer() {
        if (!initialized) {
            initialized = true;
            instance = tryStart();
        }
        return instance;
    }

    private static WeaviateContainer tryStart() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                log.info("Docker is not available, Weaviate integration tests will be skipped");
                return null;
            }
            String version = System.getProperty("weaviate.server.version");
            if (version == null || version.isBlank()) {
                log.info("weaviate.server.version system property not set, Weaviate integration tests will be skipped");
                return null;
            }
            String image = "cr.weaviate.io/semitechnologies/weaviate:" + version;
            WeaviateContainer container = new WeaviateContainer(image);
            container.start();
            log.info("Weaviate Testcontainer started successfully on ports HTTP={}, gRPC={}", container.getMappedPort(8080), container.getMappedPort(50051));
            return container;
        }
        catch (Exception e) {
            log.warn("Failed to start Weaviate Testcontainer, Weaviate integration tests will be skipped: {}", e.getMessage());
            return null;
        }
    }
}
