package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the verdict parsing of {@link DefaultDeimosLlmClient}.
 * <p>
 * A strict converter turned every cosmetic formatting difference in the model's answer into a failed participation,
 * which is the most likely reason a run analyses only part of its candidates. These cases pin down what is recovered
 * and, just as importantly, what is still rejected.
 */
class DefaultDeimosLlmClientTest {

    @Test
    void shouldParseBareJson() {
        var verdict = DefaultDeimosLlmClient.parseVerdict("{\"malicious\": true, \"rationale\": \"spawns a reverse shell\"}");

        assertThat(verdict).isPresent();
        assertThat(verdict.get().malicious()).isTrue();
        assertThat(verdict.get().rationale()).isEqualTo("spawns a reverse shell");
    }

    @Test
    void shouldParseFencedJson() {
        String content = """
                ```json
                {"malicious": false, "rationale": "ordinary solution"}
                ```
                """;

        var verdict = DefaultDeimosLlmClient.parseVerdict(content);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().malicious()).isFalse();
    }

    @Test
    void shouldParseJsonAfterReasoningPreamble() {
        String content = """
                Let me think about this step by step. The diff adds a file that opens a socket.
                That is suspicious, so my answer is:
                {"malicious": true, "rationale": "opens an outbound socket during the build"}
                """;

        var verdict = DefaultDeimosLlmClient.parseVerdict(content);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().malicious()).isTrue();
    }

    @Test
    void shouldNotBeConfusedByBracesInsideTheRationale() {
        String content = "{\"malicious\": true, \"rationale\": \"the student added `if (x) { exec(); }` to the build\"}";

        var verdict = DefaultDeimosLlmClient.parseVerdict(content);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().malicious()).isTrue();
        assertThat(verdict.get().rationale()).contains("exec(); }");
    }

    @Test
    void shouldNotBeConfusedByEscapedQuotesInsideTheRationale() {
        String content = "prefix {\"malicious\": true, \"rationale\": \"found \\\"payload\\\" marker {here}\"} suffix";

        var verdict = DefaultDeimosLlmClient.parseVerdict(content);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().rationale()).isEqualTo("found \"payload\" marker {here}");
    }

    @Test
    void shouldRejectResponseWithoutMaliciousField() {
        // Binding straight onto the boolean record component would silently yield false here, i.e. a security tool
        // would report "benign" for an answer it never understood.
        var verdict = DefaultDeimosLlmClient.parseVerdict("{\"rationale\": \"I could not decide\"}");

        assertThat(verdict).isEmpty();
    }

    @Test
    void shouldRejectResponseWithWronglyTypedMaliciousField() {
        assertThat(DefaultDeimosLlmClient.parseVerdict("{\"malicious\": \"true\", \"rationale\": \"x\"}")).isEmpty();
        assertThat(DefaultDeimosLlmClient.parseVerdict("{\"malicious\": 1, \"rationale\": \"x\"}")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "I cannot answer that.", "[]", "{\"unrelated\": true}", "{ broken json" })
    void shouldRejectUnusableResponses(String content) {
        assertThat(DefaultDeimosLlmClient.parseVerdict(content == null ? "" : content)).isEmpty();
    }

    @Test
    void shouldRejectResponseWithoutRationale() {
        // The rationale is the only thing an instructor can review. A verdict without one is not usable evidence, and
        // accepting it would let "{"malicious": false}" be counted as a clean participation.
        assertThat(DefaultDeimosLlmClient.parseVerdict("{\"malicious\": false}")).isEmpty();
        assertThat(DefaultDeimosLlmClient.parseVerdict("{\"malicious\": true, \"rationale\": \"   \"}")).isEmpty();
        assertThat(DefaultDeimosLlmClient.parseVerdict("{\"malicious\": true, \"rationale\": null}")).isEmpty();
        assertThat(DefaultDeimosLlmClient.parseVerdict("{\"malicious\": true, \"rationale\": 42}")).isEmpty();
    }

    @Test
    void shouldPreferTheFencedVerdictOverAnEarlierUnfencedGuess() {
        // A reasoning model may state a provisional answer and then correct itself. Taking the first object found
        // would return the guess, so a response with trailing content must fall through to the fenced candidate.
        String content = """
                My initial impression is {"malicious": false, "rationale": "looks ordinary"} but on closer reading the
                build file spawns a shell. Final answer:
                ```json
                {"malicious": true, "rationale": "build file spawns a shell during compilation"}
                ```
                """;

        var verdict = DefaultDeimosLlmClient.parseVerdict(content);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().malicious()).isTrue();
        assertThat(verdict.get().rationale()).isEqualTo("build file spawns a shell during compilation");
    }
}
