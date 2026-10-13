package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.SupportedVectorizer;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import io.weaviate.client6.v1.api.WeaviateApiException;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;

/**
 * Keeps the {@code SearchableEntities} collection matching the configured embedding mode and model.
 * <p>
 * The collection's vectors only make sense for the mode and model that produced them, and its vector configuration
 * cannot be changed in place. The index signature (layout version, mode, model) of the current collection is therefore
 * stored next to it, in the {@code SchemaVersion} collection. When it does not match the configuration, for example
 * after upgrading from the layout with one vector over the whole row or after a model change, the collection is
 * rebuilt:
 * <ol>
 * <li>the old collections are deleted and the new one is created (on a Weaviate without an earlier Artemis index, the
 * collection created at startup is kept);</li>
 * <li>the sync ledger and the reconcile positions are cleared, so the missing pass re-indexes every managed entity
 * from the database;</li>
 * <li>the {@link ReconcilePass#REFILL} pass is started for posts and answer posts, which no other pass manages;</li>
 * <li>the signature is written last, so a crash before it simply repeats the rebuild.</li>
 * </ol>
 * Search keeps working throughout on a growing index. Pending outbox entries are kept and apply to the new collection.
 * Runs on the scheduling node, called by the {@link WeaviateOutboxDispatcher} under its drain lock before its first write.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityIndexRebuildService {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityIndexRebuildService.class);

    /**
     * Version of the collection layout: one row per entity with one vector per chunk. Part of the signature, so a future
     * layout change triggers a rebuild too.
     */
    static final int INDEX_LAYOUT_VERSION = 2;

    static final String VERSION_COLLECTION_BASE_NAME = "SchemaVersion";

    static final String SCHEMA_VERSION_PROPERTY = "schema_version";

    static final String INDEX_SIGNATURE_PROPERTY = "index_signature";

    /**
     * The collection of the first global search layout, removed by a rebuild if it still exists.
     */
    static final String LEGACY_EXERCISES_COLLECTION = "Exercises";

    private static final String VERSION_OBJECT_UUID = "00000000-0000-0000-0000-000000000001";

    private final WeaviateClient client;

    private final WeaviateConfigurationProperties properties;

    private final WeaviateService weaviateService;

    private final SearchableEntitySyncStateRepository syncStateRepository;

    private final SearchableEntityReconcileStateRepository reconcileStateRepository;

    public SearchableEntityIndexRebuildService(Optional<WeaviateClient> clientOptional, WeaviateConfigurationProperties properties, Environment environment,
            WeaviateService weaviateService, SearchableEntitySyncStateRepository syncStateRepository, SearchableEntityReconcileStateRepository reconcileStateRepository) {
        boolean isOpenApiDocsGeneration = Boolean.parseBoolean(environment.getProperty("artemis.openapi-docs-generation", "false"));
        if (clientOptional.isEmpty() && !isOpenApiDocsGeneration) {
            throw new IllegalStateException("WeaviateClient bean is required when Weaviate is enabled and not in OpenAPI docs generation mode");
        }
        this.client = clientOptional.orElse(null);
        this.properties = properties;
        this.weaviateService = weaviateService;
        this.syncStateRepository = syncStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
    }

    /**
     * Rebuilds the collection if its signature does not match the configuration, otherwise only makes sure every
     * collection exists.
     *
     * @return whether a rebuild was started
     * @throws IOException if Weaviate cannot be reached; the caller retries
     */
    public boolean ensureIndexMatchesConfiguration() throws IOException {
        boolean newWeaviate = ensureVersionCollectionExists();
        String desired = desiredSignature();
        String stored = readStoredSignature().orElse(null);
        if (desired.equals(stored)) {
            weaviateService.ensureAllCollectionsExist();
            return false;
        }
        log.warn("The global search index was built for '{}' but the configuration needs '{}'. Rebuilding it from the database; search results stay incomplete until the "
                + "reconcile passes have re-indexed every entity.", stored, desired);
        if (!newWeaviate) {
            deleteIfExists(properties.collectionPrefix() + SearchableEntitySchema.COLLECTION_NAME);
        }
        deleteIfExists(properties.collectionPrefix() + LEGACY_EXERCISES_COLLECTION);
        weaviateService.ensureAllCollectionsExist();
        syncStateRepository.deleteAllForRebuild();
        reconcileStateRepository.deleteAllForRebuild();
        SearchableEntityReconcileState refill = new SearchableEntityReconcileState(ReconcilePass.REFILL);
        refill.setPositionEntityType(SearchableEntitySchema.TypeValues.POST);
        refill.setPositionEntityId(0L);
        reconcileStateRepository.save(refill);
        storeSignature(desired);
        log.info("Rebuilt the empty global search collection for '{}'", desired);
        return true;
    }

    /**
     * The signature the configuration requires: layout version, embedding mode and, when Artemis embeds, the model.
     *
     * @return the signature
     */
    String desiredSignature() {
        boolean embeds = SupportedVectorizer.TEXT2VEC_OPENAI.configValue().equals(properties.vectorizerModule());
        String signature = "layout=" + INDEX_LAYOUT_VERSION + ";mode=" + properties.vectorizerModule();
        return embeds ? signature + ";model=" + properties.openAiEmbeddingModel() : signature;
    }

    private Optional<String> readStoredSignature() throws IOException {
        var versionCollection = client.collections.use(properties.collectionPrefix() + VERSION_COLLECTION_BASE_NAME);
        var stored = versionCollection.query.fetchObjectById(VERSION_OBJECT_UUID);
        return stored.map(object -> object.properties().get(INDEX_SIGNATURE_PROPERTY)).map(Object::toString);
    }

    private void deleteIfExists(String collectionName) throws IOException {
        if (client.collections.exists(collectionName)) {
            client.collections.delete(collectionName);
            log.info("Deleted collection '{}' for the rebuild", collectionName);
        }
    }

    /**
     * Creates the version collection, or adds the signature property to one created before signatures existed.
     * <p>
     * Every Artemis version with global search created this collection, so its absence means the Weaviate holds no
     * Artemis index yet: the {@code SearchableEntities} collection was just created at startup with the current
     * configuration, and the rebuild does not need to delete it.
     *
     * @return whether the collection was created now, that is, whether this Weaviate holds no earlier Artemis index
     */
    private boolean ensureVersionCollectionExists() throws IOException {
        String name = properties.collectionPrefix() + VERSION_COLLECTION_BASE_NAME;
        if (client.collections.exists(name)) {
            var config = client.collections.getConfig(name);
            if (config.isPresent() && config.get().properties().stream().noneMatch(property -> INDEX_SIGNATURE_PROPERTY.equals(property.propertyName()))) {
                client.collections.use(name).config.addProperty(Property.text(INDEX_SIGNATURE_PROPERTY));
            }
            return false;
        }
        try {
            client.collections.create(name, config -> {
                config.vectorConfig(VectorConfig.selfProvided());
                config.properties(Property.integer(SCHEMA_VERSION_PROPERTY), Property.text(INDEX_SIGNATURE_PROPERTY));
                return config;
            });
            log.info("Created schema version collection '{}'", name);
            return true;
        }
        catch (WeaviateApiException exception) {
            if (!isAlreadyExists(exception)) {
                throw exception;
            }
            log.debug("Collection '{}' was created concurrently, ignoring", name);
            return false;
        }
    }

    private void storeSignature(String signature) throws IOException {
        var versionCollection = client.collections.use(properties.collectionPrefix() + VERSION_COLLECTION_BASE_NAME);
        Map<String, Object> versionProperties = Map.of(SCHEMA_VERSION_PROPERTY, INDEX_LAYOUT_VERSION, INDEX_SIGNATURE_PROPERTY, signature);
        if (versionCollection.data.exists(VERSION_OBJECT_UUID)) {
            versionCollection.data.replace(VERSION_OBJECT_UUID, replace -> replace.properties(versionProperties));
            return;
        }
        try {
            versionCollection.data.insert(versionProperties, insert -> insert.uuid(VERSION_OBJECT_UUID));
        }
        catch (WeaviateApiException exception) {
            if (!isAlreadyExists(exception)) {
                throw exception;
            }
            versionCollection.data.replace(VERSION_OBJECT_UUID, replace -> replace.properties(versionProperties));
        }
    }

    private static boolean isAlreadyExists(WeaviateApiException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("already exists") || message.contains("CLASS_ALREADY_EXISTS") || message.contains("TYPE_ADD_CLASS"));
    }
}
