package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityChunker;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;
import io.weaviate.client6.v1.api.WeaviateApiException;
import io.weaviate.client6.v1.api.collections.Vectors;
import io.weaviate.client6.v1.api.collections.WeaviateObject;

/**
 * Writes one entity row of the {@code SearchableEntities} collection together with all its vectors: one per chunk of
 * the row's embedding inputs (see {@link SearchableEntityChunker}).
 * <p>
 * A write happens in two steps so the dispatcher can embed the chunks of several rows in one request: {@link #prepare}
 * re-derives the chunks and reuses every stored vector whose chunk text and chunker version are unchanged; the missing
 * chunks are embedded by the caller; {@link #write} then stores the row. A row is never written without all its vectors
 * (Weaviate drops the vectors of a row replaced without them), and a write that only changes metadata needs no embedding
 * request at all. With {@code vectorizer-module: none} rows are written without vectors.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityVectorWriter {

    private final WeaviateService weaviateService;

    private final SearchableEntityEmbeddingClient embeddingClient;

    public SearchableEntityVectorWriter(WeaviateService weaviateService, SearchableEntityEmbeddingClient embeddingClient) {
        this.weaviateService = weaviateService;
        this.embeddingClient = embeddingClient;
    }

    /**
     * A row ready to be written once its missing chunk vectors are known.
     *
     * @param uuid       the deterministic row id
     * @param properties the properties to write
     * @param chunks     the chunk texts, in vector order (empty when the index stores no vectors)
     * @param vectors    the reused vectors, {@code null} where a chunk still needs an embedding
     */
    record PreparedRow(String uuid, Map<String, Object> properties, List<String> chunks, float[][] vectors) {

        /**
         * @return the chunk texts that still need an embedding, without duplicates, in order
         */
        List<String> missingChunks() {
            LinkedHashSet<String> missing = new LinkedHashSet<>();
            for (int i = 0; i < chunks.size(); i++) {
                if (vectors[i] == null) {
                    missing.add(chunks.get(i));
                }
            }
            return new ArrayList<>(missing);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PreparedRow(String otherUuid, Map<String, Object> otherProperties, List<String> otherChunks, float[][] otherVectors)
                    && Objects.equals(uuid, otherUuid) && Objects.equals(properties, otherProperties) && Objects.equals(chunks, otherChunks)
                    && Arrays.deepEquals(vectors, otherVectors);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, properties, chunks, Arrays.deepHashCode(vectors));
        }

        /**
         * Leaves out the property map and the vectors, which are large, and reports how many chunks already have one.
         */
        @Override
        public String toString() {
            long reused = vectors == null ? 0 : Arrays.stream(vectors).filter(Objects::nonNull).count();
            return "PreparedRow[uuid=" + uuid + ", chunks=" + (chunks == null ? 0 : chunks.size()) + ", reusedVectors=" + reused + "]";
        }
    }

    /**
     * Derives the row's chunks and reuses the stored vectors of unchanged chunks.
     *
     * @param type       the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @param properties the property map to write, including the chunk metadata added by the resolver
     * @return the prepared row
     */
    PreparedRow prepare(String type, long entityId, Map<String, Object> properties) {
        String uuid = WeaviateUuidUtil.deterministicUuid(type, entityId);
        if (!embeddingClient.isEnabled()) {
            return new PreparedRow(uuid, properties, List.of(), new float[0][]);
        }
        List<String> chunks = SearchableEntityChunker.chunk(properties);
        float[][] vectors = new float[chunks.size()][];
        Map<String, float[]> reusable = storedVectorsByChunkHash(uuid);
        for (int i = 0; i < chunks.size(); i++) {
            vectors[i] = reusable.get(SearchableEntityChunker.hash(chunks.get(i)));
        }
        return new PreparedRow(uuid, properties, chunks, vectors);
    }

    /**
     * Embeds the missing chunks of a row right away, without any budget, and writes it. Used for writes caused by users,
     * which go before all background work.
     *
     * @param row the prepared row
     */
    void embedAndWrite(PreparedRow row) {
        List<String> missing = row.missingChunks();
        Map<String, float[]> embedded = new HashMap<>();
        for (int from = 0; from < missing.size(); from += embeddingClient.maxInputsPerRequest()) {
            List<String> batch = missing.subList(from, Math.min(missing.size(), from + embeddingClient.maxInputsPerRequest()));
            List<float[]> vectors = embeddingClient.embedDocuments(batch);
            for (int i = 0; i < batch.size(); i++) {
                embedded.put(batch.get(i), vectors.get(i));
            }
        }
        write(row, embedded);
    }

    /**
     * Writes the row with all its vectors.
     *
     * @param row      the prepared row
     * @param embedded the vectors of the row's missing chunks, by chunk text
     * @throws EmbeddingFailedException if a chunk of the row still has no vector
     */
    void write(PreparedRow row, Map<String, float[]> embedded) {
        if (row.chunks().isEmpty()) {
            upsert(row.uuid(), row.properties(), null);
            return;
        }
        float[][] vectors = new float[row.chunks().size()][];
        for (int i = 0; i < vectors.length; i++) {
            vectors[i] = row.vectors()[i] != null ? row.vectors()[i] : embedded.get(row.chunks().get(i));
            if (vectors[i] == null) {
                throw new EmbeddingFailedException("Chunk " + i + " of row " + row.uuid() + " has no vector; the row is not written");
            }
        }
        upsert(row.uuid(), row.properties(), vectors);
    }

    /**
     * Reads the stored row and maps the hash of each of its chunks to its vector, if the row was written by the current
     * chunker version. A row without a stored vector for a chunk contributes nothing for it, so that chunk is embedded.
     */
    private Map<String, float[]> storedVectorsByChunkHash(String uuid) {
        Optional<WeaviateObject<Map<String, Object>>> stored;
        try {
            stored = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).query.fetchObjectById(uuid,
                    query -> query.includeVector().returnProperties(SearchableEntitySchema.Properties.CHUNK_HASHES, SearchableEntitySchema.Properties.CHUNKER_VERSION));
        }
        catch (RuntimeException e) {
            throw new WeaviateException("Failed to read the stored vectors of row " + uuid + ": " + e.getMessage(), e);
        }
        if (stored.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> storedProperties = stored.get().properties();
        Object version = storedProperties.get(SearchableEntitySchema.Properties.CHUNKER_VERSION);
        Object hashes = storedProperties.get(SearchableEntitySchema.Properties.CHUNK_HASHES);
        Vectors storedVectors = stored.get().vectors();
        float[][] vectors = storedVectors != null ? storedVectors.getDefaultMulti() : null;
        if (!(version instanceof Number number) || number.intValue() != SearchableEntityChunker.CHUNKER_VERSION || !(hashes instanceof List<?> hashList) || vectors == null) {
            return Map.of();
        }
        Map<String, float[]> byHash = new HashMap<>();
        for (int i = 0; i < Math.min(hashList.size(), vectors.length); i++) {
            if (hashList.get(i) != null && vectors[i] != null && vectors[i].length > 0) {
                byHash.put(hashList.get(i).toString(), vectors[i]);
            }
        }
        return byHash;
    }

    /**
     * Inserts or replaces the row under its deterministic id. The deterministic id makes re-applying an outbox row
     * idempotent. The {@code exists()} + {@code insert()} sequence can race with a concurrent insert, which then fails with
     * "already exists" and falls back to {@code replace()}.
     */
    private void upsert(String uuid, Map<String, Object> properties, float[][] vectors) {
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            if (collection.data.exists(uuid)) {
                collection.data.replace(uuid, replace -> vectors == null ? replace.properties(properties) : replace.properties(properties).vectors(Vectors.of(vectors)));
                return;
            }
            try {
                collection.data.insert(properties, insert -> vectors == null ? insert.uuid(uuid) : insert.uuid(uuid).vectors(Vectors.of(vectors)));
            }
            catch (WeaviateApiException e) {
                if (e.getMessage() == null || !e.getMessage().contains("already exists")) {
                    throw e;
                }
                collection.data.replace(uuid, replace -> vectors == null ? replace.properties(properties) : replace.properties(properties).vectors(Vectors.of(vectors)));
            }
        }
        catch (IOException e) {
            throw new WeaviateException("Failed to upsert row " + uuid + " in Weaviate: " + e.getMessage(), e);
        }
    }
}
