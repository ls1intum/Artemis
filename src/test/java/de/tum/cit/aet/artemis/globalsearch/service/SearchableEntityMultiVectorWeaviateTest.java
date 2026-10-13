package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.weaviate.WeaviateContainer;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityChunker;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;
import de.tum.cit.aet.artemis.shared.WeaviateTestContainerFactory;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Tests the index layout with one vector per chunk against a real Weaviate: the rebuild on a changed signature, writes
 * that reuse unchanged vectors and never store a row without all its vectors, hybrid search that scores a row by its
 * best chunk and returns that chunk as the passage, and the keyword fallback.
 * <p>
 * It needs no Spring context: it works on its own collections under a dedicated prefix, with a fake embedding model
 * whose vectors are derived from the text.
 */
@EnabledIf("isWeaviateAvailable")
class SearchableEntityMultiVectorWeaviateTest {

    private static final String PREFIX = "MultiVectorTest_";

    private static final WeaviateContainer weaviate = WeaviateTestContainerFactory.getContainer();

    private static final int DIMENSIONS = 64;

    private static final long COURSE_ID = 7L;

    private WeaviateClient client;

    private final SearchableEntityEmbeddingClient embeddingClient = mock(SearchableEntityEmbeddingClient.class);

    private final SearchableEntityResolver resolver = mock(SearchableEntityResolver.class);

    private final SearchableEntitySyncStateRepository syncStateRepository = mock(SearchableEntitySyncStateRepository.class);

    private final SearchableEntityReconcileStateRepository reconcileStateRepository = mock(SearchableEntityReconcileStateRepository.class);

    private WeaviateService weaviateService;

    private SearchableEntityWeaviateService searchService;

    static boolean isWeaviateAvailable() {
        return weaviate != null && weaviate.isRunning();
    }

    @BeforeEach
    void setUp() throws Exception {
        client = WeaviateClient.connectToLocal(config -> config.host(weaviate.getHost()).port(weaviate.getMappedPort(8080)).grpcPort(weaviate.getMappedPort(50051)));
        when(embeddingClient.isEnabled()).thenReturn(true);
        when(embeddingClient.maxInputsPerRequest()).thenReturn(32);
        when(embeddingClient.embedDocuments(any()))
                .thenAnswer(invocation -> invocation.<List<String>>getArgument(0).stream().map(SearchableEntityMultiVectorWeaviateTest::vector).toList());
        useModel("model-a");
        rebuildService("model-a").ensureIndexMatchesConfiguration();
    }

    @AfterEach
    void dropCollections() throws Exception {
        for (String name : List.of(SearchableEntitySchema.COLLECTION_NAME, SearchableEntityIndexRebuildService.VERSION_COLLECTION_BASE_NAME,
                SearchableEntityIndexRebuildService.LEGACY_EXERCISES_COLLECTION)) {
            if (client.collections.exists(PREFIX + name)) {
                client.collections.delete(PREFIX + name);
            }
        }
        client.close();
    }

    private static WeaviateConfigurationProperties properties(String model) {
        return new WeaviateConfigurationProperties(true, weaviate.getHost(), weaviate.getMappedPort(8080), weaviate.getMappedPort(50051), "http", PREFIX, "text2vec-openai", model,
                "http://localhost:1", "unused", null, null);
    }

    private void useModel(String model) {
        weaviateService = new WeaviateService(Optional.of(client), properties(model), new MockEnvironment());
        var vectorWriter = new SearchableEntityVectorWriter(weaviateService, embeddingClient);
        searchService = new SearchableEntityWeaviateService(weaviateService, mock(WeaviateOutboxRepository.class), resolver, JsonObjectMapper.get(),
                new SearchableEntityContentHasher(JsonObjectMapper.get()), mock(ApplicationEventPublisher.class), vectorWriter, embeddingClient);
    }

    private SearchableEntityIndexRebuildService rebuildService(String model) {
        return new SearchableEntityIndexRebuildService(Optional.of(client), properties(model), new MockEnvironment(), weaviateService, syncStateRepository,
                reconcileStateRepository, mock(WeaviateOutboxRepository.class));
    }

    /**
     * The fake embedding model: a unit vector derived from the SHA-256 of the text, so equal texts get equal vectors and
     * different texts nearly orthogonal ones.
     */
    private static float[] vector(String text) {
        byte[] digest;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            digest = new byte[DIMENSIONS];
            byte[] seed = text.getBytes(StandardCharsets.UTF_8);
            for (int block = 0; block < DIMENSIONS / 32; block++) {
                sha.update(seed);
                sha.update((byte) block);
                System.arraycopy(sha.digest(), 0, digest, block * 32, 32);
            }
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        float[] vector = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = digest[i];
            norm += vector[i] * vector[i];
        }
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] /= (float) Math.sqrt(norm);
        }
        return vector;
    }

    /**
     * The properties the resolver produces for an exercise, including the chunk metadata.
     */
    private static Map<String, Object> exercise(long id, String title, String description) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, id);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, COURSE_ID);
        properties.put(SearchableEntitySchema.Properties.TITLE, title);
        properties.put(SearchableEntitySchema.Properties.DESCRIPTION, description);
        properties.put(SearchableEntitySchema.Properties.CHUNK_HASHES, SearchableEntityChunker.chunk(properties).stream().map(SearchableEntityChunker::hash).toList());
        properties.put(SearchableEntitySchema.Properties.CHUNKER_VERSION, SearchableEntityChunker.CHUNKER_VERSION);
        return properties;
    }

    private static String longText(String topic, int sentences) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < sentences; i++) {
            text.append("Paragraph ").append(i).append(" about ").append(topic).append(" covers step ").append(i).append(" of the solution in some detail. ");
        }
        return text.toString();
    }

    private long nextOutboxId = 1;

    private void index(Map<String, Object> properties) {
        long id = ((Number) properties.get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue();
        when(resolver.resolve(SearchableEntitySchema.TypeValues.EXERCISE, id)).thenReturn(Optional.of(properties));
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(SearchableEntitySchema.TypeValues.EXERCISE, id, WeaviateOutboxOrigin.LIVE);
        entry.setId(nextOutboxId++);
        searchService.applyOutboxEntry(entry);
    }

    private Optional<StoredRow> stored(long id) throws Exception {
        String uuid = WeaviateUuidUtil.deterministicUuid(SearchableEntitySchema.TypeValues.EXERCISE, id);
        return weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).query.fetchObjectById(uuid, query -> query.includeVector())
                .map(object -> new StoredRow(object.properties(), object.vectors().getDefaultMulti()));
    }

    private record StoredRow(Map<String, Object> properties, float[][] vectors) {
    }

    @Test
    void theIndexIsCreatedWithTheSignatureAndRebuiltOnlyWhenTheModelChanges() throws Exception {
        assertThat(client.collections.exists(PREFIX + SearchableEntitySchema.COLLECTION_NAME)).isTrue();
        assertThat(rebuildService("model-a").ensureIndexMatchesConfiguration()).as("same configuration").isFalse();
        index(exercise(1L, "Sorting", "Bubble sort and merge sort."));
        clearInvocations(syncStateRepository, reconcileStateRepository);

        useModel("model-b");
        assertThat(rebuildService("model-b").ensureIndexMatchesConfiguration()).as("new model").isTrue();

        assertThat(stored(1L)).as("vectors of the old model are gone").isEmpty();
        verify(syncStateRepository).deleteAllForRebuild();
        verify(reconcileStateRepository).deleteAllForRebuild();
        ArgumentCaptor<SearchableEntityReconcileState> refill = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(refill.capture());
        assertThat(refill.getValue().getPass()).isEqualTo(ReconcilePass.REFILL);
        assertThat(refill.getValue().getPositionEntityType()).isEqualTo(SearchableEntitySchema.TypeValues.POST);
        assertThat(rebuildService("model-b").ensureIndexMatchesConfiguration()).isFalse();
    }

    @Test
    void aRebuildInterruptedBeforeItsSignatureIsWrittenRunsAgain() throws Exception {
        useModel("model-b");
        doThrow(new IllegalStateException("database unavailable")).doNothing().when(syncStateRepository).deleteAllForRebuild();

        assertThatThrownBy(() -> rebuildService("model-b").ensureIndexMatchesConfiguration()).isInstanceOf(IllegalStateException.class);

        assertThat(rebuildService("model-b").ensureIndexMatchesConfiguration()).as("the signature still names the old model").isTrue();
        assertThat(rebuildService("model-b").ensureIndexMatchesConfiguration()).isFalse();
    }

    @Test
    void switchingFromKeywordOnlyToEmbeddingsRebuildsTheIndex() throws Exception {
        var keywordOnly = new WeaviateConfigurationProperties(true, weaviate.getHost(), weaviate.getMappedPort(8080), weaviate.getMappedPort(50051), "http", PREFIX, "none", null,
                null, null, null, null);
        dropCollections();
        client = WeaviateClient.connectToLocal(config -> config.host(weaviate.getHost()).port(weaviate.getMappedPort(8080)).grpcPort(weaviate.getMappedPort(50051)));
        var keywordService = new WeaviateService(Optional.of(client), keywordOnly, new MockEnvironment());
        assertThat(new SearchableEntityIndexRebuildService(Optional.of(client), keywordOnly, new MockEnvironment(), keywordService, syncStateRepository, reconcileStateRepository,
                mock(WeaviateOutboxRepository.class)).ensureIndexMatchesConfiguration()).isTrue();

        useModel("model-a");
        assertThat(rebuildService("model-a").ensureIndexMatchesConfiguration()).isTrue();
        index(exercise(1L, "Sorting", "Bubble sort and merge sort."));
        assertThat(stored(1L).orElseThrow().vectors()).isNotEmpty();
    }

    @Test
    void aNewWeaviateKeepsTheCollectionCreatedAtStartupInsteadOfDeletingIt() throws Exception {
        // No Artemis index yet: the collection the startup created is already current, and deleting it would only leave a
        // window in which searches and writes fail.
        dropCollections();
        client = WeaviateClient.connectToLocal(config -> config.host(weaviate.getHost()).port(weaviate.getMappedPort(8080)).grpcPort(weaviate.getMappedPort(50051)));
        useModel("model-a");
        weaviateService.ensureAllCollectionsExist();
        index(exercise(1L, "Sorting", "Bubble sort and merge sort."));
        clearInvocations(syncStateRepository);

        assertThat(rebuildService("model-a").ensureIndexMatchesConfiguration()).isTrue();

        assertThat(stored(1L)).as("the collection was not deleted").isPresent();
        verify(syncStateRepository).deleteAllForRebuild();
        assertThat(rebuildService("model-a").ensureIndexMatchesConfiguration()).isFalse();
    }

    @Test
    void anOldSingleVectorCollectionWithoutAVersionCollectionIsRebuilt() throws Exception {
        // An earlier version created the search collection at startup and stopped before it wrote its version collection.
        dropCollections();
        client = WeaviateClient.connectToLocal(config -> config.host(weaviate.getHost()).port(weaviate.getMappedPort(8080)).grpcPort(weaviate.getMappedPort(50051)));
        client.collections.create(PREFIX + SearchableEntitySchema.COLLECTION_NAME,
                config -> config.vectorConfig(io.weaviate.client6.v1.api.collections.VectorConfig.selfProvided()));
        useModel("model-a");

        assertThat(rebuildService("model-a").ensureIndexMatchesConfiguration()).isTrue();

        var vectors = client.collections.getConfig(PREFIX + SearchableEntitySchema.COLLECTION_NAME).orElseThrow().vectors().values();
        assertThat(vectors).as("the collection now has one vector per chunk")
                .anyMatch(vector -> vector.vectorIndex() instanceof io.weaviate.client6.v1.api.collections.vectorindex.Hnsw hnsw && hnsw.multiVector() != null
                        && hnsw.multiVector().enabled());
        index(exercise(1L, "Sorting", "Bubble sort and merge sort."));
        assertThat(stored(1L).orElseThrow().vectors()).isNotEmpty();
    }

    @Test
    void anOldCollectionWithItsOwnVectorizerIsRebuiltWhenSwitchingToKeywordSearch() throws Exception {
        // Earlier versions let Weaviate call the embedding endpoint itself. Keeping such a collection in keyword mode would
        // keep that vectorizer, which then fails on every write without the API key Artemis used to forward.
        var keywordOnly = new WeaviateConfigurationProperties(true, weaviate.getHost(), weaviate.getMappedPort(8080), weaviate.getMappedPort(50051), "http", PREFIX, "none", null,
                null, null, null, null);
        dropCollections();
        client = WeaviateClient.connectToLocal(config -> config.host(weaviate.getHost()).port(weaviate.getMappedPort(8080)).grpcPort(weaviate.getMappedPort(50051)));
        client.collections.create(PREFIX + SearchableEntitySchema.COLLECTION_NAME,
                config -> config.vectorConfig(io.weaviate.client6.v1.api.collections.VectorConfig.text2vecOpenAi()));
        var keywordService = new WeaviateService(Optional.of(client), keywordOnly, new MockEnvironment());

        assertThat(new SearchableEntityIndexRebuildService(Optional.of(client), keywordOnly, new MockEnvironment(), keywordService, syncStateRepository, reconcileStateRepository,
                mock(WeaviateOutboxRepository.class)).ensureIndexMatchesConfiguration()).isTrue();

        var vectors = client.collections.getConfig(PREFIX + SearchableEntitySchema.COLLECTION_NAME).orElseThrow().vectors().values();
        assertThat(vectors).as("the collection was recreated with vectors provided by Artemis")
                .allMatch(vector -> vector instanceof io.weaviate.client6.v1.api.collections.vectorizers.SelfProvidedVectorizer);
    }

    @Test
    void aRebuildAlsoReplacesTheIndexOfAnInstallationFromBeforeSignatures() throws Exception {
        // The first layout: an Exercises collection and a version object without a signature.
        dropCollections();
        client = WeaviateClient.connectToLocal(config -> config.host(weaviate.getHost()).port(weaviate.getMappedPort(8080)).grpcPort(weaviate.getMappedPort(50051)));
        client.collections.create(PREFIX + SearchableEntityIndexRebuildService.LEGACY_EXERCISES_COLLECTION);
        client.collections.create(PREFIX + SearchableEntityIndexRebuildService.VERSION_COLLECTION_BASE_NAME,
                config -> config.properties(io.weaviate.client6.v1.api.collections.Property.integer(SearchableEntityIndexRebuildService.SCHEMA_VERSION_PROPERTY)));
        useModel("model-a");

        assertThat(rebuildService("model-a").ensureIndexMatchesConfiguration()).isTrue();

        assertThat(client.collections.exists(PREFIX + SearchableEntityIndexRebuildService.LEGACY_EXERCISES_COLLECTION)).isFalse();
        assertThat(client.collections.exists(PREFIX + SearchableEntitySchema.COLLECTION_NAME)).isTrue();
        assertThat(rebuildService("model-a").ensureIndexMatchesConfiguration()).isFalse();
    }

    @Test
    void aWriteStoresOneVectorPerChunkInOrderAndReusesTheVectorsOfUnchangedChunks() throws Exception {
        Map<String, Object> first = exercise(1L, "Graphs", longText("graph search", 120));
        List<String> chunks = SearchableEntityChunker.chunk(first);
        assertThat(chunks).hasSizeGreaterThan(2);

        index(first);

        StoredRow row = stored(1L).orElseThrow();
        assertThat(row.vectors()).hasDimensions(chunks.size(), DIMENSIONS);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(row.vectors()[i]).containsExactly(vector(chunks.get(i)));
        }

        // Only the release date changes: no chunk changes, so no embedding request is sent.
        reset(embeddingClient);
        when(embeddingClient.isEnabled()).thenReturn(true);
        when(embeddingClient.maxInputsPerRequest()).thenReturn(32);
        Map<String, Object> sameText = new HashMap<>(first);
        sameText.put(SearchableEntitySchema.Properties.RELEASE_DATE, "2026-10-01T10:00:00Z");
        index(sameText);
        verify(embeddingClient, never()).embedDocuments(any());

        // A changed ending changes only the last chunks; the unchanged ones keep their vectors.
        when(embeddingClient.embedDocuments(any()))
                .thenAnswer(invocation -> invocation.<List<String>>getArgument(0).stream().map(SearchableEntityMultiVectorWeaviateTest::vector).toList());
        Map<String, Object> changed = exercise(1L, "Graphs", longText("graph search", 120) + "A new final remark about cycles.");
        List<String> changedChunks = SearchableEntityChunker.chunk(changed);
        index(changed);

        ArgumentCaptor<List<String>> embedded = ArgumentCaptor.captor();
        verify(embeddingClient).embedDocuments(embedded.capture());
        assertThat(embedded.getValue()).doesNotContain(chunks.getFirst()).allSatisfy(chunk -> assertThat(chunks).doesNotContain(chunk));
        assertThat(stored(1L).orElseThrow().vectors()).hasDimensions(changedChunks.size(), DIMENSIONS);
    }

    @Test
    void storedVectorsOfAnotherChunkerVersionAreNotReused() throws Exception {
        Map<String, Object> exercise = exercise(1L, "Sorting", longText("sorting arrays", 60));
        index(exercise);
        // Mark the stored row as written by an older chunker version.
        String uuid = WeaviateUuidUtil.deterministicUuid(SearchableEntitySchema.TypeValues.EXERCISE, 1L);
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data.update(uuid,
                update -> update.properties(Map.of(SearchableEntitySchema.Properties.CHUNKER_VERSION, SearchableEntityChunker.CHUNKER_VERSION - 1)));
        clearInvocations(embeddingClient);

        index(exercise);

        ArgumentCaptor<List<String>> embedded = ArgumentCaptor.captor();
        verify(embeddingClient, org.mockito.Mockito.atLeastOnce()).embedDocuments(embedded.capture());
        assertThat(embedded.getAllValues().stream().mapToInt(List::size).sum()).isEqualTo(SearchableEntityChunker.chunk(exercise).size());
    }

    @Test
    void writeRefusesARowWithAChunkThatHasNoVector() {
        var vectorWriter = new SearchableEntityVectorWriter(weaviateService, embeddingClient);
        var row = vectorWriter.prepare(SearchableEntitySchema.TypeValues.EXERCISE, 3L, exercise(3L, "Graphs", "Breadth first search."));

        assertThatThrownBy(() -> vectorWriter.write(row, Map.of())).isInstanceOf(EmbeddingFailedException.class);
        assertThat(client.collections.use(PREFIX + SearchableEntitySchema.COLLECTION_NAME).data.exists(row.uuid())).isFalse();
    }

    @Test
    void aRowIsNeverWrittenWithoutAllItsVectors() throws Exception {
        index(exercise(1L, "Sorting", "Bubble sort and merge sort."));
        doThrow(new EmbeddingFailedException("endpoint down")).when(embeddingClient).embedDocuments(any());

        assertThatThrownBy(() -> index(exercise(1L, "Sorting", "Quick sort and heap sort."))).isInstanceOf(EmbeddingFailedException.class);

        StoredRow row = stored(1L).orElseThrow();
        assertThat(row.properties().get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo("Bubble sort and merge sort.");
        assertThat(row.vectors()).isNotEmpty();
    }

    @Test
    void hybridSearchRanksARowByItsBestChunkAndReturnsThatChunkAsThePassage() {
        Map<String, Object> graphs = exercise(1L, "Graphs", longText("graph search", 120));
        Map<String, Object> sorting = exercise(2L, "Sorting", longText("sorting arrays", 40));
        index(graphs);
        index(sorting);
        List<String> graphChunks = SearchableEntityChunker.chunk(graphs);
        String deepChunk = graphChunks.get(graphChunks.size() - 1);
        // The words of the query match the other exercise, but its meaning is exactly the last chunk of the long one: only a
        // search that weighs the vector ranks the long exercise first (a keyword-only search ranks the other one first).
        String query = "sorting arrays";
        when(embeddingClient.embedQuery(query)).thenReturn(vector(deepChunk));

        var candidates = searchService.searchEntityCandidatesForAnswer(query, null, 5);

        assertThat(candidates).isNotEmpty();
        assertThat(((Number) candidates.getFirst().properties().get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue()).isEqualTo(1L);
        assertThat(candidates.getFirst().passage()).isEqualTo(SearchableEntityChunker.body(deepChunk));
    }

    @Test
    void searchFallsBackToKeywordsWhenTheQueryCannotBeEmbedded() {
        index(exercise(1L, "Dijkstra shortest paths", "Compute shortest paths with a priority queue."));
        index(exercise(2L, "Sorting", "Bubble sort and merge sort."));
        when(embeddingClient.embedQuery(any())).thenThrow(new EmbeddingFailedException("timeout"));

        var results = searchService.searchSearchableEntities("Dijkstra", null, 5);

        assertThat(results).isNotEmpty();
        assertThat(((Number) results.getFirst().get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue()).isEqualTo(1L);
    }
}
