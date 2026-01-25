package de.tum.cit.aet.artemis.core.service.weaviate;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionHandle;

/**
 * Service for interacting with Weaviate vector database
 */
@Service
@ConditionalOnProperty(prefix = "artemis.weaviate", name = "enabled", havingValue = "true")
public class WeaviateService {

    private static final Logger log = LoggerFactory.getLogger(WeaviateService.class);

    private final WeaviateClient weaviateClient;

    public WeaviateService(WeaviateClient weaviateClient) {
        this.weaviateClient = weaviateClient;
    }

    /**
     * Check if a collection exists in Weaviate
     *
     * @param collectionName the name of the collection
     * @return true if collection exists, false otherwise
     */
    public boolean collectionExists(String collectionName) {
        try {
            return weaviateClient.collections.exists(collectionName);
        }
        catch (IOException e) {
            log.error("Failed to check if collection {} exists", collectionName, e);
            return false;
        }
    }

    /**
     * Get a collection handle for interacting with a specific collection
     *
     * @param collectionName the name of the collection
     * @return CollectionHandle for the specified collection
     */
    public CollectionHandle<Map<String, Object>> getCollection(String collectionName) {
        return weaviateClient.collections.use(collectionName);
    }

    /**
     * Check if Weaviate connection is healthy
     *
     * @return true if Weaviate is healthy, false otherwise
     */
    public boolean isHealthy() {
        try {
            // Check if we can list collections
            weaviateClient.collections.list();
            return true;
        }
        catch (Exception e) {
            log.warn("Weaviate health check failed: {}", e.getMessage());
            return false;
        }
    }
}
