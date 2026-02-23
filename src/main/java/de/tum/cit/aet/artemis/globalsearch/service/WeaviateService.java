package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateCollectionSchema;
import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviatePropertyDefinition;
import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateReferenceDefinition;
import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateSchemas;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.ReferenceProperty;
import io.weaviate.client6.v1.api.collections.VectorConfig;

/**
 * Infrastructure service for Weaviate vector database.
 * Handles schema initialization and provides collection access for entity-specific services.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class WeaviateService {

    private static final Logger log = LoggerFactory.getLogger(WeaviateService.class);

    private final WeaviateClient client;

    private final String collectionPrefix;

    public WeaviateService(WeaviateClient client, WeaviateConfigurationProperties properties) {
        this.client = client;
        this.collectionPrefix = properties.collectionPrefix();
    }

    /**
     * Resolves the actual collection name by prepending the configured prefix.
     *
     * @param baseName the base collection name (e.g. "Exercises")
     * @return the prefixed collection name (e.g. "Artemis_Exercises" when prefix is "Artemis_")
     */
    private String resolveCollectionName(String baseName) {
        return collectionPrefix + baseName;
    }

    /**
     * Initializes the Weaviate collections when the service is first used.
     * Creates collections that don't exist yet.
     */
    @PostConstruct
    public void initializeCollections() {
        log.info("Initializing Weaviate collections...");

        for (WeaviateCollectionSchema schema : WeaviateSchemas.ALL_SCHEMAS) {
            ensureCollectionExists(schema);
        }

        log.info("Weaviate collection initialization complete");
    }

    /**
     * Ensures a collection exists, creating it if necessary.
     *
     * @param schema the schema definition
     */
    private void ensureCollectionExists(WeaviateCollectionSchema schema) {
        String collectionName = resolveCollectionName(schema.collectionName());

        try {
            if (client.collections.exists(collectionName)) {
                log.debug("Collection '{}' already exists", collectionName);
                return;
            }

            log.info("Creating collection '{}'...", collectionName);

            client.collections.create(collectionName, collection -> {
                // Explicitly disable vectorization; text2vec-transformers will be added in a separate PR
                // In a follow-up PR we will configure text2vec-transformers vectorizer for automatic embeddings
                collection.vectorConfig(VectorConfig.selfProvided());

                // Add properties
                for (WeaviatePropertyDefinition prop : schema.properties()) {
                    collection.properties(createProperty(prop));
                }

                // Add references
                for (WeaviateReferenceDefinition ref : schema.references()) {
                    collection.references(ReferenceProperty.to(ref.name(), resolveCollectionName(ref.targetCollection())));
                }

                return collection;
            });

            log.info("Successfully created collection '{}'", collectionName);
        }
        catch (IOException e) {
            log.error("Failed to create collection '{}': {}", collectionName, e.getMessage(), e);
            throw new WeaviateException("Failed to create collection: " + collectionName, e);
        }
    }

    /**
     * Creates a Weaviate property from a property definition.
     *
     * @param definition the property definition
     * @return the Weaviate property
     */
    private Property createProperty(WeaviatePropertyDefinition definition) {
        return switch (definition.dataType()) {
            // indexSearchable is only applicable to text properties; Weaviate ignores it for numeric types
            // See https://docs.weaviate.io/weaviate/config-refs/indexing/inverted-index
            case INT -> Property.integer(definition.name(), property -> property.indexFilterable(definition.indexFilterable()));
            case TEXT -> Property.text(definition.name(), property -> property.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case NUMBER -> Property.number(definition.name(), property -> property.indexFilterable(definition.indexFilterable()));
            case BOOLEAN -> Property.bool(definition.name(), property -> property.indexFilterable(definition.indexFilterable()));
            case DATE -> Property.date(definition.name(), property -> property.indexFilterable(definition.indexFilterable()));
            case UUID -> Property.uuid(definition.name(), property -> property.indexFilterable(definition.indexFilterable()));
            case BLOB -> Property.blob(definition.name());
        };
    }

    /**
     * Gets a collection handle for the specified collection name.
     *
     * @param collectionName the base collection name (prefix will be applied)
     * @return the collection handle
     */
    public CollectionHandle<Map<String, Object>> getCollection(String collectionName) {
        return client.collections.use(resolveCollectionName(collectionName));
    }
}
