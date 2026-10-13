package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import io.weaviate.client6.v1.api.WeaviateApiException;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.SortBy;
import io.weaviate.client6.v1.api.collections.vectorindex.Hnsw;
import io.weaviate.client6.v1.api.collections.vectorizers.SelfProvidedVectorizer;

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

    /** Channel rows read per request while looking for rows written before they carried a visibility date. */
    private static final int CHANNEL_BACKFILL_PAGE_SIZE = 1000;

    private final WeaviateClient client;

    private final WeaviateConfigurationProperties properties;

    private final WeaviateService weaviateService;

    private final SearchableEntitySyncStateRepository syncStateRepository;

    private final SearchableEntityReconcileStateRepository reconcileStateRepository;

    private final WeaviateOutboxRepository outboxRepository;

    public SearchableEntityIndexRebuildService(Optional<WeaviateClient> clientOptional, WeaviateConfigurationProperties properties, Environment environment,
            WeaviateService weaviateService, SearchableEntitySyncStateRepository syncStateRepository, SearchableEntityReconcileStateRepository reconcileStateRepository,
            WeaviateOutboxRepository outboxRepository) {
        boolean isOpenApiDocsGeneration = Boolean.parseBoolean(environment.getProperty("artemis.openapi-docs-generation", "false"));
        if (clientOptional.isEmpty() && !isOpenApiDocsGeneration) {
            throw new IllegalStateException("WeaviateClient bean is required when Weaviate is enabled and not in OpenAPI docs generation mode");
        }
        this.client = clientOptional.orElse(null);
        this.properties = properties;
        this.weaviateService = weaviateService;
        this.syncStateRepository = syncStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
        this.outboxRepository = outboxRepository;
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
            queueChannelRowsWithoutVisibilityDate();
            return false;
        }
        log.warn("The global search index was built for '{}' but the configuration needs '{}'. Rebuilding it from the database; search results stay incomplete until the "
                + "reconcile passes have re-indexed every entity.", stored, desired);
        String collectionName = properties.collectionPrefix() + SearchableEntitySchema.COLLECTION_NAME;
        if (!newWeaviate || !hasCurrentVectorLayout(collectionName)) {
            deleteIfExists(collectionName);
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

    /**
     * Queues a rewrite of every channel row written before channel rows carried the date from which students may see the
     * channel. The search shows students only channel rows with that date, so such a row stays hidden from them until it is
     * rewritten; once all are, this finds nothing. A failure only delays the repair to the next startup: the rows stay
     * hidden from students meanwhile, and index writes must not wait for it.
     */
    private void queueChannelRowsWithoutVisibilityDate() {
        try {
            int queued = queueChannelRowsWithoutVisibilityDate(CHANNEL_BACKFILL_PAGE_SIZE);
            if (queued > 0) {
                log.info("Queued {} channel rows without the date from which students may see them for a rewrite", queued);
            }
        }
        catch (RuntimeException e) {
            log.warn("Could not look for channel rows without a visibility date; they stay hidden from students until the next startup retries: {}", e.getMessage(), e);
        }
    }

    /**
     * Reads the channel rows without a visibility date page by page, ordered by channel id and continuing after the last
     * one, so the walk is not bound by Weaviate's limit on offsets.
     *
     * @param pageSize rows read per request
     * @return the number of rewrites queued
     */
    int queueChannelRowsWithoutVisibilityDate(int pageSize) {
        var collection = client.collections.use(properties.collectionPrefix() + SearchableEntitySchema.COLLECTION_NAME);
        Filter withoutDate = Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.CHANNEL),
                Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).isNull());
        List<WeaviateOutboxEntry> rewrites = new ArrayList<>();
        long lastChannelId = Long.MIN_VALUE;
        while (true) {
            Filter page = Filter.and(withoutDate, Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).gt(lastChannelId));
            var rows = collection.query.fetchObjects(query -> query.filters(page).sort(SortBy.property(SearchableEntitySchema.Properties.ENTITY_ID).asc()).limit(pageSize)
                    .returnProperties(SearchableEntitySchema.Properties.ENTITY_ID)).objects();
            for (var row : rows) {
                if (row.properties().get(SearchableEntitySchema.Properties.ENTITY_ID) instanceof Number channelId) {
                    rewrites.add(WeaviateOutboxEntry.forUpsert(SearchableEntitySchema.TypeValues.CHANNEL, channelId.longValue(), WeaviateOutboxOrigin.RECONCILE_DRIFT));
                    lastChannelId = Math.max(lastChannelId, channelId.longValue());
                }
            }
            if (rows.size() < pageSize) {
                break;
            }
        }
        if (!rewrites.isEmpty()) {
            outboxRepository.saveAll(rewrites);
        }
        return rewrites.size();
    }

    private Optional<String> readStoredSignature() throws IOException {
        var versionCollection = client.collections.use(properties.collectionPrefix() + VERSION_COLLECTION_BASE_NAME);
        var stored = versionCollection.query.fetchObjectById(VERSION_OBJECT_UUID);
        return stored.map(object -> object.properties().get(INDEX_SIGNATURE_PROPERTY)).map(Object::toString);
    }

    /**
     * Whether the collection is built for the vector layout the configuration needs: vectors provided by Artemis, one per
     * chunk when Artemis embeds and no multi-vector index otherwise. A collection without a version collection next to it was usually created by
     * this startup, but one left behind by an earlier version that stopped before it wrote its version collection still
     * has the old single-vector layout, and keeping it would make every write fail.
     *
     * @param collectionName the collection to check
     * @return true if the collection does not exist or already has the needed layout
     */
    private boolean hasCurrentVectorLayout(String collectionName) throws IOException {
        var config = client.collections.getConfig(collectionName);
        if (config.isEmpty()) {
            return true;
        }
        var vectors = config.get().vectors().values();
        // Artemis provides every vector itself; a collection with a vectorizer of its own (such as text2vec-openai) comes
        // from an earlier layout and would keep calling that vectorizer on every write.
        boolean selfProvided = vectors.stream().allMatch(vector -> vector instanceof SelfProvidedVectorizer);
        boolean multiVector = vectors.stream().anyMatch(vector -> vector.vectorIndex() instanceof Hnsw hnsw && hnsw.multiVector() != null && hnsw.multiVector().enabled());
        return selfProvided && multiVector == SupportedVectorizer.TEXT2VEC_OPENAI.configValue().equals(properties.vectorizerModule());
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
     * Every Artemis version with global search created this collection, so its absence usually means the Weaviate holds
     * no Artemis index yet and the {@code SearchableEntities} collection was just created at startup. The rebuild still
     * checks that collection's vector layout before keeping it (see {@link #hasCurrentVectorLayout}).
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
