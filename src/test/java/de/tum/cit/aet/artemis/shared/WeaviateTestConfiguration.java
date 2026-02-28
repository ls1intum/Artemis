package de.tum.cit.aet.artemis.shared;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.weaviate.WeaviateContainer;

/**
 * Centralized configuration for Weaviate integration tests.
 * <p>
 * Provides a consistent collection prefix and property registration for all test base classes.
 * The underscore suffix in the prefix serves as a separator between the prefix and collection name,
 * matching the production pattern (e.g., "Artemis_").
 */
public final class WeaviateTestConfiguration {

    /**
     * Collection prefix used for all Weaviate collections in tests.
     * The trailing underscore separates the prefix from the collection name.
     */
    public static final String COLLECTION_PREFIX = "Test_";

    private WeaviateTestConfiguration() {
        // Utility class
    }

    /**
     * Registers Weaviate-related dynamic properties for Spring test contexts.
     * <p>
     * This method should be called from {@code @DynamicPropertySource} methods in test base classes.
     * It configures the Weaviate connection properties based on the running container.
     *
     * @param registry  the dynamic property registry to add properties to
     * @param container the Weaviate test container (may be null if Docker is unavailable)
     */
    public static void registerWeaviateProperties(DynamicPropertyRegistry registry, WeaviateContainer container) {
        if (container != null && container.isRunning()) {
            registry.add("artemis.weaviate.enabled", () -> true);
            registry.add("artemis.weaviate.http-host", container::getHost);
            registry.add("artemis.weaviate.http-port", () -> container.getMappedPort(8080));
            registry.add("artemis.weaviate.grpc-port", () -> container.getMappedPort(50051));
            registry.add("artemis.weaviate.scheme", () -> "http");
            registry.add("artemis.weaviate.collection-prefix", () -> COLLECTION_PREFIX);
        }
    }
}
