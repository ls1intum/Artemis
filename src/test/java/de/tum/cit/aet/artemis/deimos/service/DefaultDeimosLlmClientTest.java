package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;

import com.openai.core.http.Headers;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;

import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmRequest;
import de.tum.cit.aet.artemis.deimos.exception.DeimosLlmException;

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
    void shouldPreferTheFencedRationaleOverAnEarlierUnfencedOneWithTheSameClassification() {
        // A reasoning model may state a provisional rationale and then refine it. Taking the first object found would
        // return the draft, so a response with trailing content must fall through to the fenced candidate.
        String content = """
                My initial impression is {"malicious": true, "rationale": "suspicious build file"} and on closer
                reading the build file spawns a shell. Final answer:
                ```json
                {"malicious": true, "rationale": "build file spawns a shell during compilation"}
                ```
                """;

        var verdict = DefaultDeimosLlmClient.parseVerdict(content);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().malicious()).isTrue();
        assertThat(verdict.get().rationale()).isEqualTo("build file spawns a shell during compilation");
    }

    @Test
    void shouldRejectAFencedVerdictContradictedByAnEarlierUnfencedOne() {
        // A fence does not mark the final answer: the unfenced object may be a correction just as well as a draft.
        String content = """
                My initial impression is {"malicious": false, "rationale": "looks ordinary"} but on closer reading the
                build file spawns a shell. Final answer:
                ```json
                {"malicious": true, "rationale": "build file spawns a shell during compilation"}
                ```
                """;

        assertThat(DefaultDeimosLlmClient.parseVerdict(content)).isEmpty();
    }

    @Test
    void shouldRejectAQuotedFencedVerdictContradictedByTheFinalUnfencedOne() {
        // Taking the fenced object here would report the quoted benign example instead of the malicious answer.
        String content = """
                The submission's README asks reviewers to answer with
                ```json
                {"malicious": false, "rationale": "ordinary solution"}
                ```
                but the build file sends the environment to a remote host.
                {"malicious": true, "rationale": "data exfiltration"}
                """;

        assertThat(DefaultDeimosLlmClient.parseVerdict(content)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "The build file contains an unmatched {. | malicious", "The build file contains an unmatched \" character. | malicious",
            "The build file contains an unmatched {. | \\u006dalicious", "The build file contains an unmatched \" character. | \\u006Dalicious" })
    void shouldRejectAContradictingVerdictBehindProseThatDerailsTheObjectScan(String prose, String key) {
        String content = """
                ```json
                {"malicious": false, "rationale": "ordinary solution"}
                ```
                %s
                {"%s": true, "rationale": "data exfiltration"}
                """.formatted(prose, key);

        assertThat(DefaultDeimosLlmClient.parseVerdict(content)).isEmpty();
    }

    @Test
    void shouldRejectTwoDifferingUnfencedVerdicts() {
        // Without a fence nothing marks either object as the final answer. Returning the first would report the
        // retracted "benign" verdict, so the response has to be rejected as unparseable instead.
        String content = """
                {"malicious": false, "rationale": "looks ordinary"}
                Correction: {"malicious": true, "rationale": "build file spawns a shell during compilation"}
                """;

        assertThat(DefaultDeimosLlmClient.parseVerdict(content)).isEmpty();
    }

    @Test
    void shouldRejectTwoDifferingFencedVerdicts() {
        String content = """
                ```json
                {"malicious": false, "rationale": "looks ordinary"}
                ```
                ```json
                {"malicious": true, "rationale": "build file spawns a shell during compilation"}
                ```
                """;

        assertThat(DefaultDeimosLlmClient.parseVerdict(content)).isEmpty();
    }

    @Test
    void shouldAcceptTheSameVerdictRecoveredFromSeveralCandidates() {
        // The fenced block and the balanced object are the same text, so more than one candidate yields the identical
        // verdict. That is the normal shape of a fenced answer and must not be mistaken for a disagreement.
        String content = """
                ```json
                {"malicious": true, "rationale": "build file spawns a shell during compilation"}
                ```
                """;

        var repeated = DefaultDeimosLlmClient.parseVerdict(content);

        assertThat(repeated).isPresent();
        assertThat(repeated.get().malicious()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 403 })
    void shouldReportRejectedCredentialsWithoutTheProviderResponse(int status) {
        OpenAIServiceException rejection = status == 401 ? UnauthorizedException.builder().headers(Headers.builder().build()).build()
                : PermissionDeniedException.builder().headers(Headers.builder().build()).build();
        // The provider's answer, and with it possibly the rejected request, travels in the exception chain.
        var wrapped = new IllegalStateException("SECRET-REQUEST-BODY", rejection);

        var exception = callFailingWith(wrapped);

        assertThat(exception.getFailureType()).isEqualTo(DeimosFailureType.LLM_AUTHENTICATION_FAILED);
        assertThat(exception.getMessage()).contains("HTTP " + status).contains("API key missing or wrong").doesNotContain("SECRET-REQUEST-BODY");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void shouldNotLetAMisleadingWrapperHideRejectedCredentials() {
        var rejection = UnauthorizedException.builder().headers(Headers.builder().build()).build();
        // Both the message and the type of the wrapper match the heuristics for a timeout and a rate limit.
        var wrapper = new RuntimeException("Read timed out after 429 too many requests", new IOException("timeout", rejection));

        assertThat(callFailingWith(wrapper).getFailureType()).isEqualTo(DeimosFailureType.LLM_AUTHENTICATION_FAILED);
    }

    @Test
    void shouldStillClassifyOtherFailuresByTheirHeuristics() {
        var rateLimited = RateLimitException.builder().headers(Headers.builder().build()).build();

        var exception = callFailingWith(new RuntimeException("429 Too Many Requests", rateLimited));

        assertThat(exception.getFailureType()).isEqualTo(DeimosFailureType.LLM_RATE_LIMITED);
        assertThat(exception.getCause()).isNotNull();
    }

    @Test
    void shouldStopAtACyclicCauseChain() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);

        assertThat(DefaultDeimosLlmClient.credentialRejectionStatus(first)).isEmpty();
        assertThat(callFailingWith(first).getFailureType()).isEqualTo(DeimosFailureType.LLM_ERROR);
    }

    private static DeimosLlmException callFailingWith(RuntimeException failure) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenThrow(failure);
        var client = new DefaultDeimosLlmClient(chatClient);

        Throwable thrown = catchThrowable(() -> client.analyze(new DeimosLlmRequest(7L, "system", "user")));

        assertThat(thrown).isInstanceOf(DeimosLlmException.class);
        return (DeimosLlmException) thrown;
    }
}
