package de.tum.cit.aet.artemis.globalsearch.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * The chunker must produce exactly the chunks of the reference implementation the chunk size was evaluated with. The
 * fixtures were generated from that reference over synthetic texts covering markup, multi-chunk prose, CJK, emoji,
 * non-breaking and thin spaces, digit runs, a single word longer than a chunk, Windows line ends and special-token text.
 */
class SearchableEntityChunkerTest {

    private record ParityCase(String name, Map<String, Object> properties, List<String> expectedChunks) {
    }

    private static List<ParityCase> loadCases() throws IOException {
        try (InputStream input = SearchableEntityChunkerTest.class.getResourceAsStream("/globalsearch/chunker-parity-fixtures.json")) {
            assertThat(input).as("parity fixtures on the test classpath").isNotNull();
            return JsonMapper.builder().build().readValue(new String(input.readAllBytes(), StandardCharsets.UTF_8), new TypeReference<List<ParityCase>>() {
            });
        }
    }

    static Stream<Arguments> parityCases() throws IOException {
        return loadCases().stream().map(parityCase -> Arguments.of(parityCase.name(), parityCase));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parityCases")
    void producesTheReferenceChunks(String name, ParityCase parityCase) {
        assertThat(SearchableEntityChunker.chunk(parityCase.properties())).containsExactlyElementsOf(parityCase.expectedChunks());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parityCases")
    void everyChunkStaysWithinTheBounds(String name, ParityCase parityCase) {
        for (String chunk : SearchableEntityChunker.chunk(parityCase.properties())) {
            assertThat(SearchableEntityChunker.estimateTokens(chunk)).isLessThanOrEqualTo(SearchableEntityChunker.TOKEN_LIMIT);
            assertThat(chunk.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(SearchableEntityChunker.HARD_BYTES);
        }
    }

    @Test
    void shortDescriptionIsOneChunkWithTheWholeText() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);
        properties.put(SearchableEntitySchema.Properties.TITLE, "Sorting");
        properties.put(SearchableEntitySchema.Properties.EXERCISE_TYPE, "programming");
        properties.put(SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE, "C_SHARP");
        properties.put(SearchableEntitySchema.Properties.SHORT_NAME, "S1");
        properties.put(SearchableEntitySchema.Properties.DESCRIPTION, "Implement **merge sort**.");

        assertThat(SearchableEntityChunker.chunk(properties)).containsExactly("Programming exercise (C Sharp): Sorting [S1]\nImplement merge sort.");
    }

    @Test
    void entityWithoutDescriptionIsItsHeader() {
        Map<String, Object> properties = Map.of(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.Properties.TITLE,
                "random");

        List<String> chunks = SearchableEntityChunker.chunk(properties);

        assertThat(chunks).containsExactly("Channel: random");
        assertThat(SearchableEntityChunker.body(chunks.getFirst())).isEmpty();
    }

    @Test
    void bodyIsTheTextAfterTheHeaderLine() {
        assertThat(SearchableEntityChunker.body("Lecture: Graphs\nBreadth first search.\nSecond line.")).isEqualTo("Breadth first search.\nSecond line.");
    }
}
