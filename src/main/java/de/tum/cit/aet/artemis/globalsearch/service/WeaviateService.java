package de.tum.cit.aet.artemis.globalsearch.service;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    private final String collectionPrefix;

    private final String vectorizerModule;

    private final boolean weaviateEnabled;

    private final String weaviateHost;

    private final int weaviateHttpPort;

    private final int weaviateGrpcPort;

    private final String weaviateScheme;

    private final String weaviateApiEmbeddingModel;

    private final String weaviateApiBaseUrl;

    private final String weaviateApiKey;

    private final boolean isTestProfile;

    public WeaviateService(WeaviateClient client, Environment environment, @Value("${artemis.weaviate.enabled:false}") boolean weaviateEnabled,
            @Value("${artemis.weaviate.http-host:#{null}}") String weaviateHost,
            @Value("${artemis.weaviate.http-port:" + WeaviateConfigurationProperties.DEFAULT_HTTP_PORT + "}") int weaviateHttpPort,
            @Value("${artemis.weaviate.grpc-port:" + WeaviateConfigurationProperties.DEFAULT_GRPC_PORT + "}") int weaviateGrpcPort,
            @Value("${artemis.weaviate.scheme:#{null}}") String weaviateScheme,
            @Value("${artemis.weaviate.collection-prefix:" + WeaviateConfigurationProperties.DEFAULT_COLLECTION_PREFIX + "}") String collectionPrefix,
            @Value("${artemis.weaviate.vectorizer-module:none}") String vectorizerModule,
            @Value("${artemis.weaviate.api-embedding-model:#{null}}") String weaviateApiEmbeddingModel,
            @Value("${artemis.weaviate.api-base-url:#{null}}") String weaviateApiBaseUrl, @Value("${artemis.weaviate.api-key:#{null}}") String weaviateApiKey) {
        this.client = client;
        this.weaviateEnabled = weaviateEnabled;
        this.weaviateHost = weaviateHost;
        this.weaviateHttpPort = weaviateHttpPort;
        this.weaviateGrpcPort = weaviateGrpcPort;
        this.weaviateScheme = weaviateScheme;
        this.collectionPrefix = collectionPrefix;
        this.vectorizerModule = vectorizerModule;
        this.weaviateApiEmbeddingModel = weaviateApiEmbeddingModel;
        this.weaviateApiBaseUrl = weaviateApiBaseUrl;
        this.weaviateApiKey = weaviateApiKey;
        this.isTestProfile = environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST));
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
        if (!weaviateEnabled) {
            log.debug("Skipping Weaviate collection initialization because integration is disabled");
            return;
        }

        log.info("Initializing Weaviate collections at {}://{}:{} (gRPC: {}) with vectorizer module: {}", weaviateScheme, weaviateHost, weaviateHttpPort, weaviateGrpcPort,
                vectorizerModule);

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
                log.info("Collection '{}' already exists, skipping creation.", collectionName);
                checkForConfigurationDrift(collectionName);
                return;
            }

            log.info("Creating collection '{}' with vectorizer '{}'...", collectionName, vectorizerModule);

            client.collections.create(collectionName, collection -> {
                // Configure vectorizer based on deployment setup
                // - "none": Use self-provided vectors (respective weaviate instance can be started via docker/weaviate.yml)
                // - "text2vec-transformers": Automatic embeddings with embeddinggemma-300m (respective weaviate instance can be started via docker/weaviate-embeddings.yml)
                // - "text2vec-openai": OpenAI-compatible API embeddings, e.g. Ollama (weaviate started with docker/weaviate/openai.env)
                if (VectorConfig.Kind.TEXT2VEC_OPENAI.jsonValue().equals(vectorizerModule)) {
                    collection.vectorConfig(VectorConfig.text2vecOpenAi(builder -> {
                        if (StringUtils.hasText(weaviateApiBaseUrl)) {
                            builder.baseUrl(weaviateApiBaseUrl);
                        }
                        if (StringUtils.hasText(weaviateApiEmbeddingModel)) {
                            builder.model(weaviateApiEmbeddingModel);
                        }
                        return builder;
                    }));
                    log.info("Configured collection '{}' with text2vec-openai: baseUrl='{}', model='{}', apiKey configured={}", collectionName, weaviateApiBaseUrl,
                            weaviateApiEmbeddingModel, StringUtils.hasText(weaviateApiKey));
                }
                else if (VectorConfig.Kind.TEXT2VEC_TRANSFORMERS.jsonValue().equals(vectorizerModule)) {
                    log.info("Configured collection '{}' with text2vec-transformers vectorizer", collectionName);
                    collection.vectorConfig(VectorConfig.text2vecTransformers());
                }
                else if (VectorConfig.Kind.NONE.jsonValue().equals(vectorizerModule)) {
                    log.info("Configured collection '{}' with self-provided vectors (no automatic embeddings)", collectionName);
                    collection.vectorConfig(VectorConfig.selfProvided());
                }
                else {
                    log.warn("Unknown vectorizer module '{}', defaulting to self-provided vectors", vectorizerModule);
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
            Optional<CollectionConfig> existingConfig = client.collections.getConfig(collectionName);
            if (existingConfig.isEmpty()) {
                return;
            }

            Map<String, VectorConfig> vectors = existingConfig.get().vectors();
            if (vectors == null || vectors.isEmpty()) {
                return;
            }

            // Weaviate stores the default vector under the key "default"
            VectorConfig existingVector = vectors.get("default");
            if (existingVector == null) {
                log.warn("Collection '{}' has no 'default' vector key; found keys: {}. Skipping configuration drift check.", collectionName, vectors.keySet());
                return;
            }
            VectorConfig.Kind existingKind = existingVector._kind();

            // Determine the expected kind from the configured vectorizer module
            VectorConfig.Kind expectedKind = switch (vectorizerModule) {
                case "text2vec-openai" -> VectorConfig.Kind.TEXT2VEC_OPENAI;
                case "text2vec-transformers" -> VectorConfig.Kind.TEXT2VEC_TRANSFORMERS;
                default -> VectorConfig.Kind.NONE;
            };

            List<String> mismatches = new ArrayList<>();

            if (existingKind != expectedKind) {
                mismatches.add("vectorizer module: existing='%s', configured='%s'".formatted(existingKind.jsonValue(), vectorizerModule));
            }

            // For text2vec-openai, also compare baseUrl and model
            if (existingKind == VectorConfig.Kind.TEXT2VEC_OPENAI && expectedKind == VectorConfig.Kind.TEXT2VEC_OPENAI) {
                Text2VecOpenAiVectorizer existingOpenAi = existingVector._as(VectorConfig.Kind.TEXT2VEC_OPENAI);
                if (!Objects.equals(existingOpenAi.baseUrl(), weaviateApiBaseUrl)) {
                    mismatches.add("api-base-url: existing='%s', configured='%s'".formatted(existingOpenAi.baseUrl(), weaviateApiBaseUrl));
                }
                if (!Objects.equals(existingOpenAi.model(), weaviateApiEmbeddingModel)) {
                    mismatches.add("api-embedding-model: existing='%s', configured='%s'".formatted(existingOpenAi.model(), weaviateApiEmbeddingModel));
                }
            }

            if (!mismatches.isEmpty()) {
                log.warn(
                        "Collection '{}' exists but its vectorizer configuration differs from the application config: {}. "
                                + "Delete the collection and restart Artemis to apply the new settings: curl -X DELETE \"http://{}:{}/v1/schema/{}\"",
                        collectionName, String.join("; ", mismatches), weaviateHost, weaviateHttpPort, collectionName);
            }
        }
        catch (Exception e) {
            log.debug("Could not verify configuration of existing collection '{}': {}", collectionName, e.getMessage());
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

    /**
     * Returns whether a text vectorizer is configured that can automatically
     * create embeddings from text. When this returns {@code false}, only keyword
     * (BM25) search should be used instead of hybrid search because hybrid search
     * requires a vectorizer to convert the query text into a vector.
     *
     * @return {@code true} if a text vectorizer is available, {@code false} otherwise
     */
    public boolean isVectorizerAvailable() {
        return WeaviateConfigurationProperties.VECTORIZER_TEXT2VEC_TRANSFORMERS.equals(vectorizerModule);
    }
}
