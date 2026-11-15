package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.SupportedVectorizer;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateCollectionSchema;
import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviatePropertyDefinition;
import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateReferenceDefinition;
import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateSchemas;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import io.weaviate.client6.v1.api.WeaviateApiException;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionConfig;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.ReferenceProperty;
import io.weaviate.client6.v1.api.collections.Tokenization;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.vectorindex.Hnsw;
import io.weaviate.client6.v1.api.collections.vectorindex.MultiVector;

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

    private final WeaviateConfigurationProperties properties;

    private final boolean isOpenApiDocsGeneration;

    /**
     * The {@code WeaviateClient} bean is created lazily because the enclosing
     * {@link WeaviateClientConfiguration} and this service are both {@code @Lazy}.
     * <p>
     * Note: {@code @Lazy} must <b>not</b> be placed on the {@code client} parameter
     * itself, because the Weaviate Java client exposes {@code collections} as a
     * <b>public field</b>. A CGLIB lazy-proxy only intercepts method calls — direct
     * field access bypasses the proxy and returns {@code null}, which causes a
     * {@link NullPointerException} in {@link #ensureCollectionExists}.
     * <p>
     * The client parameter is {@code Optional} because during OpenAPI docs generation
     * ({@code artemis.openapi-docs-generation=true}) no Weaviate server is available.
     * The {@link WeaviateClientConfiguration#weaviateClient()} bean method returns
     * {@code null} in that mode, so no {@code WeaviateClient} bean is registered.
     * Using {@code Optional} here prevents a {@code NoSuchBeanDefinitionException}
     * from cascading through the entire dependency tree and crashing the application.
     *
     * @throws IllegalStateException if the client is not available outside of OpenAPI docs generation
     */
    public WeaviateService(Optional<WeaviateClient> clientOptional, WeaviateConfigurationProperties properties, Environment environment) {
        this.isOpenApiDocsGeneration = Boolean.parseBoolean(environment.getProperty("artemis.openapi-docs-generation", "false"));
        if (clientOptional.isEmpty() && !isOpenApiDocsGeneration) {
            throw new IllegalStateException("WeaviateClient bean is required when Weaviate is enabled and not in OpenAPI docs generation mode");
        }
        this.client = clientOptional.orElse(null);
        this.properties = properties;
    }

    /**
     * Resolves the actual collection name by prepending the configured prefix.
     *
     * @param baseName the base collection name (e.g. "Exercises")
     * @return the prefixed collection name (e.g. "Artemis_Exercises" when prefix is "Artemis_")
     */
    private String resolveCollectionName(String baseName) {
        return properties.collectionPrefix() + baseName;
    }

    /**
     * Initializes the Weaviate collections when the service is first used.
     * Creates collections that don't exist yet.
     */
    @PostConstruct
    public void initializeCollections() {
        if (isOpenApiDocsGeneration) {
            log.info("OpenAPI docs generation mode: skipping Weaviate collection initialization");
            return;
        }

        log.info("Initializing Weaviate collections at {}://{}:{} (gRPC: {}) with vectorizer module: {}", properties.scheme(), properties.httpHost(), properties.httpPort(),
                properties.grpcPort(), properties.vectorizerModule());

        ensureAllCollectionsExist();

        log.info("Weaviate collection initialization complete");
    }

    /**
     * Ensures every collection in {@link WeaviateSchemas#ALL_SCHEMAS} exists, creating any that are missing.
     * <p>
     * Runs on every Weaviate-enabled node during startup (via {@link #initializeCollections()}) so the indexing
     * and search paths always have their target collections available. It is idempotent; the index rebuild
     * ({@link SearchableEntityIndexRebuildService}) calls it again to recreate the collections it deleted.
     */
    public void ensureAllCollectionsExist() {
        for (WeaviateCollectionSchema schema : WeaviateSchemas.ALL_SCHEMAS) {
            ensureCollectionExists(schema);
        }
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
                log.info("Collection '{}' already exists, skipping creation.", collectionName);
                reconcileExistingCollection(collectionName, schema);
                return;
            }

            log.info("Creating collection '{}' with vectorizer '{}'...", collectionName, properties.vectorizerModule());

            client.collections.create(collectionName, collection -> {
                // Artemis provides the vectors itself. With "text2vec-openai" every row carries one vector per chunk of its
                // embedding inputs (a multi-vector scored by MaxSim, i.e. by its best chunk); with "none" rows carry no vectors
                // and search is keyword-only.
                if (SupportedVectorizer.TEXT2VEC_OPENAI.configValue().equals(properties.vectorizerModule())) {
                    collection.vectorConfig(VectorConfig.selfProvided(vector -> vector
                            .vectorIndex(Hnsw.of(hnsw -> hnsw.multiVector(MultiVector.of(multiVector -> multiVector.aggregation(MultiVector.Aggregation.MAX_SIM)))))));
                    log.info("Configured collection '{}' with chunk vectors provided by Artemis (model '{}')", collectionName, properties.openAiEmbeddingModel());
                }
                else {
                    collection.vectorConfig(VectorConfig.selfProvided());
                    log.info("Configured collection '{}' without vectors (keyword search only)", collectionName);
                }

                // Enable null state indexing so that properties with null values can be used in filters
                // (e.g. release_date IS NULL, or filtering on exam properties that are null for non-exam exercises)
                collection.invertedIndex(idx -> idx.indexNulls(true));

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
            throw new WeaviateException("Failed to create Weaviate collection '" + collectionName + "': " + e.getMessage(), e);
        }
        catch (WeaviateApiException e) {
            // Multiple Spring contexts (or concurrent startups) may race between the
            // exists() check and the create() call, causing an "already exists" error.
            if (e.getMessage() != null
                    && (e.getMessage().contains("already exists") || e.getMessage().contains("CLASS_ALREADY_EXISTS") || e.getMessage().contains("TYPE_ADD_CLASS"))) {
                log.debug("Collection '{}' was created concurrently, ignoring", collectionName);
            }
            else {
                log.error("Failed to create collection '{}': {}", collectionName, e.getMessage(), e);
                throw new WeaviateException("Failed to create Weaviate collection '" + collectionName + "': " + e.getMessage(), e);
            }
        }
    }

    /**
     * Reconciles an existing Weaviate collection with the Java schema definition by adding any missing properties.
     * Whether its vectors still match the configuration is checked by {@link SearchableEntityIndexRebuildService}.
     * Fetches the collection config exactly once to avoid redundant REST calls.
     *
     * @param collectionName the fully-qualified collection name (with prefix)
     * @param schema         the expected schema definition
     */
    private void reconcileExistingCollection(String collectionName, WeaviateCollectionSchema schema) {
        try {
            Optional<CollectionConfig> existingConfig = client.collections.getConfig(collectionName);
            if (existingConfig.isEmpty()) {
                return;
            }

            addMissingProperties(collectionName, schema, existingConfig.get());
        }
        catch (Exception e) {
            log.warn("Could not reconcile existing collection '{}': {}", collectionName, e.getMessage());
        }
    }

    /**
     * Detects properties defined in the Java schema but missing from the existing Weaviate
     * collection, and adds them. This handles the case where new properties are added to
     * {@link WeaviateCollectionSchema} after the collection was originally created.
     */
    private void addMissingProperties(String collectionName, WeaviateCollectionSchema schema, CollectionConfig existingConfig) throws IOException {
        Set<String> existingPropertyNames = existingConfig.properties().stream().map(Property::propertyName).collect(Collectors.toSet());

        var collection = client.collections.use(collectionName);
        for (WeaviatePropertyDefinition prop : schema.properties()) {
            if (!existingPropertyNames.contains(prop.name())) {
                collection.config.addProperty(createProperty(prop));
                log.info("Added missing property '{}' to collection '{}'", prop.name(), collectionName);
            }
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
            case TEXT -> Property.text(definition.name(), property -> {
                var builder = property.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable());
                // Trigram tokenization indexes every 3-char sliding window of text values, enabling
                // BM25 to match partial words and typos (e.g. "strateg" → "strategy").
                if (definition.indexSearchable()) {
                    builder.tokenization(Tokenization.TRIGRAM);
                }
                return builder;
            });
            case TEXT_ARRAY ->
                Property.textArray(definition.name(), property -> property.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
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

    /**
     * Gets a handle for a collection owned by another service (e.g. the Iris ingestion pipeline's
     * {@code Lectures} / {@code LectureTranscriptions} collections), addressed by its EXACT name WITHOUT the
     * Artemis collection prefix. Use this only to READ external collections; Artemis's own collections must go
     * through {@link #getCollection(String)} so the prefix is applied.
     *
     * @param exactCollectionName the exact, unprefixed collection name
     * @return the collection handle (returned even if the collection does not exist; the failure surfaces on the
     *         first read)
     */
    public CollectionHandle<Map<String, Object>> getExternalCollection(String exactCollectionName) {
        return client.collections.use(exactCollectionName);
    }

    /**
     * Returns whether a collection with the given EXACT (unprefixed) name exists. Used to short-circuit reads of
     * external collections (e.g. the Iris ingestion collections) that may not exist on this instance, so callers can
     * treat an absent collection as empty rather than erroring per read.
     *
     * @param exactCollectionName the exact, unprefixed collection name
     * @return {@code true} if the collection exists
     */
    public boolean externalCollectionExists(String exactCollectionName) {
        try {
            return client.collections.exists(exactCollectionName);
        }
        catch (IOException exception) {
            throw new WeaviateException("Failed to check whether external collection '" + exactCollectionName + "' exists: " + exception.getMessage(), exception);
        }
    }
}
