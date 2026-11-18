package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityChunker;
import io.weaviate.client6.v1.api.collections.Vectors;

/**
 * Finds the passage of a search hit that matched the query: the chunk whose vector is closest to the query vector. The
 * assistant shows this passage to its reranker instead of the beginning of the description, which for long texts often
 * does not contain what was found.
 * <p>
 * Chunk texts are not stored; the chunk is re-derived from the stored row and accepted only if its hash equals the
 * stored hash at the same position, so a row written by another chunker version never yields a wrong passage.
 */
final class SearchableEntityPassages {

    private SearchableEntityPassages() {
    }

    /**
     * Returns the matched passage of a hit.
     *
     * @param properties  the hit's stored properties
     * @param vectors     the hit's vectors, as returned by the search
     * @param queryVector the query vector
     * @return the body of the closest chunk, or null when it cannot be determined reliably or the chunk has no body
     */
    @Nullable
    static String matchedPassage(Map<String, Object> properties, @Nullable Vectors vectors, float[] queryVector) {
        float[][] chunkVectors = vectors != null ? vectors.getDefaultMulti() : null;
        if (chunkVectors == null || chunkVectors.length == 0 || !(properties.get(SearchableEntitySchema.Properties.CHUNK_HASHES) instanceof List<?> hashes)
                || hashes.size() != chunkVectors.length) {
            return null;
        }
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < chunkVectors.length; i++) {
            double score = cosine(chunkVectors[i], queryVector);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        List<String> chunks = SearchableEntityChunker.chunk(properties);
        if (best < 0 || best >= chunks.size() || !SearchableEntityChunker.hash(chunks.get(best)).equals(String.valueOf(hashes.get(best)))) {
            return null;
        }
        String passage = SearchableEntityChunker.body(chunks.get(best));
        return passage.isEmpty() ? null : passage;
    }

    private static double cosine(float[] first, float[] second) {
        if (first.length != second.length) {
            return Double.NEGATIVE_INFINITY;
        }
        double dot = 0;
        double firstNorm = 0;
        double secondNorm = 0;
        for (int i = 0; i < first.length; i++) {
            dot += (double) first[i] * second[i];
            firstNorm += (double) first[i] * first[i];
            secondNorm += (double) second[i] * second[i];
        }
        return firstNorm == 0 || secondNorm == 0 ? Double.NEGATIVE_INFINITY : dot / Math.sqrt(firstNorm * secondNorm);
    }
}
