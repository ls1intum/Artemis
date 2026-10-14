package de.tum.cit.aet.artemis.globalsearch.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    void longTextWithoutSpacesIsSplitWithoutLosingItsEnd() {
        // Chinese prose has no spaces, so a paragraph is one piece far longer than a chunk; it must be split, not cut.
        String ending = "最后的独特要求是用颜色翻转实现删除。";
        String paragraph = "平衡二叉树的旋转与着色规则需要仔细推导".repeat(150) + ending;
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);
        properties.put(SearchableEntitySchema.Properties.TITLE, "红黑树");
        properties.put(SearchableEntitySchema.Properties.DESCRIPTION, paragraph);

        List<String> chunks = SearchableEntityChunker.chunk(properties);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(SearchableEntityChunker.body(chunks.getLast())).endsWith(ending);
        assertThat(chunks.stream().map(SearchableEntityChunker::body).mapToInt(String::length).sum()).as("all text is kept").isGreaterThanOrEqualTo(paragraph.length());
    }

    @Test
    void veryLongTextWithoutSpacesIsChunkedInLinearTime() {
        // About 1.4 MB without a single space, mixed with characters outside the BMP: the cursor must neither repeat work
        // on the whole remainder nor cut a surrogate pair.
        String paragraph = "平衡二叉树🌳的旋转与着色规则".repeat(30_000) + "结尾。";
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);
        properties.put(SearchableEntitySchema.Properties.TITLE, "Tree");
        properties.put(SearchableEntitySchema.Properties.DESCRIPTION, paragraph);

        long start = System.nanoTime();
        List<String> chunks = SearchableEntityChunker.chunk(properties);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(20));
        assertThat(SearchableEntityChunker.body(chunks.getLast())).endsWith("结尾。");
        // A cut through a surrogate pair would leave a lone surrogate, which codePoints() reports as its own value.
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.codePoints().noneMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE)).isTrue());
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
