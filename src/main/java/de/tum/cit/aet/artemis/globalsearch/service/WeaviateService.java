package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.vectorizers.Text2VecOpenAiVectorizer;

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

    private final boolean isTestProfile;

    private final boolean isOpenApiDocsGeneration;

    private final WeaviateMigrationService migrationService;

    /**
     * The {@code WeaviateClient} bean is created lazily because the enclosing
     * {@link WeaviateClientConfiguration} and this service are both {@code @Lazy}.
     * <p>
     * Note: {@code @Lazy} must <b>not</b> be placed on the {@code client} parameter
     * itself, because the Weaviate Java client exposes {@code collections} as a
     * <b>public field</b>. A CGLIB lazy-proxy only intercepts method calls — direct
     * field access bypasses the proxy and returns {@code null}, which causes a
     * {@link NullPointerException} in {@link #ensureCollectionExists}.
     */
    public WeaviateService(WeaviateClient client, WeaviateConfigurationProperties properties, Environment environment, WeaviateMigrationService migrationService) {
        this.client = client;
        this.properties = properties;
        this.isTestProfile = environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST));
        this.isOpenApiDocsGeneration = Boolean.parseBoolean(environment.getProperty("artemis.openapi-docs-generation", "false"));
        this.migrationService = migrationService;
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

        for (WeaviateCollectionSchema schema : WeaviateSchemas.ALL_SCHEMAS) {
            ensureCollectionExists(schema);
        }

        migrationService.runPendingMigrations();

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
                log.info("Collection '{}' already exists, skipping creation.", collectionName);
                checkForConfigurationDrift(collectionName);
                return;
            }

            log.info("Creating collection '{}' with vectorizer '{}'...", collectionName, properties.vectorizerModule());

            client.collections.create(collectionName, collection -> {
                // Configure vectorizer based on deployment setup
                // - "none": Use self-provided vectors (respective weaviate instance can be started via docker/weaviate.yml)
                // - "text2vec-transformers": Automatic embeddings with embeddinggemma-300m (respective weaviate instance can be started via docker/weaviate-embeddings.yml)
                // - "text2vec-openai": OpenAI-compatible API embeddings, e.g. Ollama (weaviate started with docker/weaviate/openai.env)
                if (SupportedVectorizer.TEXT2VEC_OPENAI.configValue().equals(properties.vectorizerModule())) {
                    collection.vectorConfig(VectorConfig.text2vecOpenAi(builder -> {
                        if (StringUtils.hasText(properties.openAiBaseUrl())) {
                            builder.baseUrl(properties.openAiBaseUrl());
                        }
                        if (StringUtils.hasText(properties.openAiEmbeddingModel())) {
                            builder.model(properties.openAiEmbeddingModel());
                        }
                        return builder;
                    }));
                    log.info("Configured collection '{}' with text2vec-openai: baseUrl='{}', model='{}', apiKey configured={}", collectionName, properties.openAiBaseUrl(),
                            properties.openAiEmbeddingModel(), StringUtils.hasText(properties.gpuApiKey()));
                }
                else if (SupportedVectorizer.TEXT2VEC_TRANSFORMERS.configValue().equals(properties.vectorizerModule())) {
                    log.info("Configured collection '{}' with text2vec-transformers vectorizer", collectionName);
                    collection.vectorConfig(VectorConfig.text2vecTransformers());
                }
                else if (SupportedVectorizer.NONE.configValue().equals(properties.vectorizerModule())) {
                    log.info("Configured collection '{}' with self-provided vectors (no automatic embeddings)", collectionName);
                    collection.vectorConfig(VectorConfig.selfProvided());
                }
                else {
                    log.warn("Unknown vectorizer module '{}', defaulting to self-provided vectors", properties.vectorizerModule());
                    collection.vectorConfig(VectorConfig.selfProvided());
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
            // In test environments, multiple Spring contexts may share one Weaviate instance,
            // causing a race condition between the exists() check and create() call.
            if (isTestProfile && e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("Collection '{}' was created concurrently (test environment), ignoring", collectionName);
            }
            else {
                log.error("Failed to create collection '{}': {}", collectionName, e.getMessage(), e);
                throw new WeaviateException("Failed to create Weaviate collection '" + collectionName + "': " + e.getMessage(), e);
            }
        }
    }

    /**
     * Compares the existing collection's vectorizer configuration with the application config.
     * Logs a warning if there is a mismatch, guiding the developer to delete and recreate the collection.
     *
     * @param collectionName the fully-qualified collection name (with prefix)
     */
    private void checkForConfigurationDrift(String collectionName) {
        try {
            Optional<VectorConfig> defaultVector = getDefaultVectorConfig(collectionName);
            if (defaultVector.isEmpty()) {
                return;
            }

            List<String> mismatches = detectVectorizerMismatches(defaultVector.get());
            if (!mismatches.isEmpty()) {
                log.warn(
                        "Collection '{}' exists but its vectorizer configuration differs from the application config: {}. "
                                + "Delete the collection and restart Artemis to apply the new settings: curl -X DELETE \"http://{}:{}/v1/schema/{}\"",
                        collectionName, String.join("; ", mismatches), properties.httpHost(), properties.httpPort(), collectionName);
            }
        }
        catch (Exception e) {
            log.debug("Could not verify configuration of existing collection '{}': {}", collectionName, e.getMessage());
        }
    }

    /**
     * Retrieves the default vector configuration from an existing collection.
     *
     * @param collectionName the fully-qualified collection name
     * @return the default vector config, or empty if not available
     */
    private Optional<VectorConfig> getDefaultVectorConfig(String collectionName) throws IOException {
        Optional<CollectionConfig> existingConfig = client.collections.getConfig(collectionName);
        if (existingConfig.isEmpty()) {
            return Optional.empty();
        }

        Map<String, VectorConfig> vectors = existingConfig.get().vectors();
        if (vectors == null || vectors.isEmpty()) {
            return Optional.empty();
        }

        // Weaviate stores the default vector under the key "default"
        VectorConfig defaultVector = vectors.get("default");
        if (defaultVector == null) {
            log.warn("Collection '{}' has no 'default' vector key; found keys: {}. Skipping configuration drift check.", collectionName, vectors.keySet());
            return Optional.empty();
        }
        return Optional.of(defaultVector);
    }

    /**
     * Compares the existing vector configuration against the application's configured vectorizer
     * and returns a list of human-readable mismatch descriptions.
     *
     * @param existingVector the vector configuration currently stored in Weaviate
     * @return a list of mismatch descriptions (empty if configurations match)
     */
    private List<String> detectVectorizerMismatches(VectorConfig existingVector) {
        VectorConfig.Kind existingKind = existingVector._kind();
        VectorConfig.Kind expectedKind = SupportedVectorizer.fromConfigValue(properties.vectorizerModule()).vectorConfigKind();

        List<String> mismatches = new ArrayList<>();

        if (existingKind != expectedKind) {
            mismatches.add("vectorizer module: existing='%s', configured='%s'".formatted(existingKind.jsonValue(), properties.vectorizerModule()));
        }

        boolean shouldCompareOpenAiProperties = existingKind == VectorConfig.Kind.TEXT2VEC_OPENAI && expectedKind == VectorConfig.Kind.TEXT2VEC_OPENAI;
        if (shouldCompareOpenAiProperties) {
            Text2VecOpenAiVectorizer existingOpenAi = existingVector._as(VectorConfig.Kind.TEXT2VEC_OPENAI);
            if (!Objects.equals(existingOpenAi.baseUrl(), properties.openAiBaseUrl())) {
                mismatches.add("open-ai-base-url: existing='%s', configured='%s'".formatted(existingOpenAi.baseUrl(), properties.openAiBaseUrl()));
            }
            if (!Objects.equals(existingOpenAi.model(), properties.openAiEmbeddingModel())) {
                mismatches.add("open-ai-embedding-model: existing='%s', configured='%s'".formatted(existingOpenAi.model(), properties.openAiEmbeddingModel()));
            }
        }

        return mismatches;
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

    /**
     * Returns whether a text vectorizer is configured that can automatically
     * create embeddings from text. When this returns {@code false}, only keyword
     * (BM25) search should be used instead of hybrid search because hybrid search
     * requires a vectorizer to convert the query text into a vector.
     *
     * @return {@code true} if a text vectorizer is available, {@code false} otherwise
     */
    public boolean isVectorizerAvailable() {
        return SupportedVectorizer.TEXT2VEC_TRANSFORMERS.configValue().equals(properties.vectorizerModule())
                || SupportedVectorizer.TEXT2VEC_OPENAI.configValue().equals(properties.vectorizerModule());
    }
}
