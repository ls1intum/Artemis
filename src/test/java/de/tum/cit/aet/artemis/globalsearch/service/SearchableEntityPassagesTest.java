package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityChunker;
import io.weaviate.client6.v1.api.collections.Vectors;

class SearchableEntityPassagesTest {

    /**
     * An exercise long enough for several chunks, with the stored chunk hashes the resolver adds.
     */
    private static Map<String, Object> longExercise() {
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            description.append("Sentence number ").append(i).append(" explains part ").append(i).append(" of the task in detail. ");
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);
        properties.put(SearchableEntitySchema.Properties.TITLE, "Long exercise");
        properties.put(SearchableEntitySchema.Properties.DESCRIPTION, description.toString());
        properties.put(SearchableEntitySchema.Properties.CHUNK_HASHES, SearchableEntityChunker.chunk(properties).stream().map(SearchableEntityChunker::hash).toList());
        return properties;
    }

    /**
     * One axis-aligned unit vector per chunk, so the query vector picks a chunk exactly.
     */
    private static float[][] oneHotVectors(int count) {
        float[][] vectors = new float[count][count];
        for (int i = 0; i < count; i++) {
            vectors[i][i] = 1f;
        }
        return vectors;
    }

    @Test
    void returnsTheBodyOfTheChunkClosestToTheQuery() {
        Map<String, Object> properties = longExercise();
        List<String> chunks = SearchableEntityChunker.chunk(properties);
        assertThat(chunks).hasSizeGreaterThan(2);
        float[][] vectors = oneHotVectors(chunks.size());

        String passage = SearchableEntityPassages.matchedPassage(properties, Vectors.of(vectors), vectors[2]);

        assertThat(passage).isEqualTo(SearchableEntityChunker.body(chunks.get(2))).isNotBlank();
    }

    @Test
    void returnsNothingWhenTheStoredHashDoesNotMatchTheRederivedChunk() {
        // The row was written from different content or by another chunker version.
        Map<String, Object> properties = longExercise();
        int count = SearchableEntityChunker.chunk(properties).size();
        List<String> hashes = new java.util.ArrayList<>((List<String>) properties.get(SearchableEntitySchema.Properties.CHUNK_HASHES));
        hashes.set(1, "0".repeat(64));
        properties.put(SearchableEntitySchema.Properties.CHUNK_HASHES, hashes);
        float[][] vectors = oneHotVectors(count);

        assertThat(SearchableEntityPassages.matchedPassage(properties, Vectors.of(vectors), vectors[1])).isNull();
        assertThat(SearchableEntityPassages.matchedPassage(properties, Vectors.of(vectors), vectors[0])).isNotNull();
    }

    @Test
    void returnsNothingWithoutVectorsOrWithAMismatchedNumberOfVectors() {
        Map<String, Object> properties = longExercise();
        int count = SearchableEntityChunker.chunk(properties).size();

        assertThat(SearchableEntityPassages.matchedPassage(properties, null, new float[] { 1f })).isNull();
        assertThat(SearchableEntityPassages.matchedPassage(properties, Vectors.of(oneHotVectors(count - 1)), new float[count - 1])).isNull();
        properties.remove(SearchableEntitySchema.Properties.CHUNK_HASHES);
        assertThat(SearchableEntityPassages.matchedPassage(properties, Vectors.of(oneHotVectors(count)), oneHotVectors(count)[0])).isNull();
    }
}
