package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Adversarial unit tests for the spec-fidelity / coverage critic. The critic talks to the model through the shared {@link ChatClient}; the tests wrap a mocked {@link ChatModel} in
 * a real {@code ChatClient} (mirroring the sibling Hyperion service tests) so the fluent plumbing is exercised end-to-end without a GPU. They pin the three defect classes the
 * differential oracle is blind to (uncovered brief requirement, fully-covered brief, grader-mechanics leak), and — crucially — the critic's two non-negotiable safety properties:
 * it
 * degrades gracefully on any model failure, and it produces structured, capped, defensively-parsed findings that can never explode.
 */
class SpecFidelityCriticServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ChatResponse jsonResponse(String body) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(body))));
    }

    private SpecFidelityCriticService criticReturning(ChatResponse response) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        return new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
    }

    private static final String UNICODE_BRIEF = "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark "
            + "sequence, CJK characters, and at least one emoji.";

    /**
     * A brief naming CJK + emoji, with tests covering only ASCII, makes the critic flag the CJK/emoji gap. The model is told (truthfully) those cases are uncovered; the critic
     * surfaces them as UNCOVERED_REQUIREMENT findings.
     */
    @Test
    void uncoveredCjkAndEmoji_areFlagged() {
        SpecFidelityCriticService critic = criticReturning(
                jsonResponse("{\"uncovered\":[{\"requirement\":\"CJK characters\",\"reason\":\"no CJK test\"},{\"requirement\":\"emoji\",\"reason\":\"no emoji test\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("test_ascii_only", "test_cafe_precomposed"));

        assertThat(report.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactlyInAnyOrder("CJK characters", "emoji");
    }

    /** A fully-covered brief (the model returns an empty uncovered list) produces no findings. */
    @Test
    void fullyCoveredBrief_flagsNothing() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse("{\"uncovered\":[],\"missingExamples\":[]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("test_cjk", "test_emoji", "test_combining", "test_cafe"));

        assertThat(report.hasFindings()).isFalse();
    }

    /** An error/edge behaviour described only in prose (no concrete fenced example) is reported as a MISSING_WORKED_EXAMPLE finding. */
    @Test
    void missingWorkedExample_isFlagged() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[{\"behaviour\":\"pop on empty throws\",\"reason\":\"only a prose 'would throw' sentence, no fenced trace\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Implement a stack; pop on an empty stack must throw.", List.of("test_pop_empty_throws"));

        assertThat(report.findings()).hasSize(1).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactly("pop on empty throws");
    }

    /** A requirement the produced statement imposes that the brief never asked for is reported as an INVENTED_REQUIREMENT (scope-drift) finding. */
    @Test
    void inventedRequirementNotInBrief_isFlagged() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[{\"requirement\":\"O(1) extra space\",\"reason\":\"the brief never constrains space complexity\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Rotate the matrix; your solution must use O(1) extra space.", List.of("test_rotate"));

        assertThat(report.findings()).hasSize(1).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.INVENTED_REQUIREMENT);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactly("O(1) extra space");
    }

    /**
     * A grader-mechanics phrase in the problem statement ("make the tests fail" / "NotImplementedError in the template") is flagged WITHOUT any model call — the leak check is a
     * deterministic regex pass, so it fires even when the model returns nothing.
     */
    @Test
    void graderMechanicsLeak_isFlaggedWithoutModel() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse("{\"uncovered\":[]}"));

        String leakyStatement = "Implement the sorter.\n\nAll functions should raise NotImplementedError in the template file to make the tests fail.";
        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, leakyStatement, List.of("test_sort"));

        assertThat(report.findings()).isNotEmpty();
        assertThat(report.findings()).anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.MECHANICS_LEAK);
    }

    /**
     * The mechanics-leak pass needs NO model at all: even with no ChatClient configured (null), the deterministic leak check still fires while the coverage pass is silently
     * skipped. Proves the model-free check is independent of the LLM pass.
     */
    @Test
    void mechanicsLeak_firesEvenWithNoModelConfigured() {
        SpecFidelityCriticService critic = new SpecFidelityCriticService(null, objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "todo!() in the template; the template must fail every test.", List.of("test_x"));

        assertThat(report.findings()).anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.MECHANICS_LEAK);
        // No model => no coverage findings, only the deterministic leak ones.
        assertThat(report.findings()).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.MECHANICS_LEAK);
    }

    /** A model error degrades gracefully: the coverage pass contributes nothing and the critic returns (here, an empty report) instead of throwing. */
    @Test
    void modelError_degradesGracefully() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("gpu timeout"));
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        assertThat(report.hasFindings()).isFalse();
    }

    /** Garbage (non-JSON) model output degrades gracefully to no coverage findings rather than throwing. */
    @Test
    void garbageOutput_degradesGracefully() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse("I think the tests look fine to me, no JSON here at all."));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        assertThat(report.hasFindings()).isFalse();
    }

    /** An empty model response (null/blank text) degrades gracefully. */
    @Test
    void emptyOutput_degradesGracefully() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(""));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        assertThat(report.hasFindings()).isFalse();
    }

    /** JSON embedded in prose / a code fence is still parsed (defensive payload extraction). */
    @Test
    void jsonWrappedInProse_isStillParsed() {
        SpecFidelityCriticService critic = criticReturning(
                jsonResponse("Sure, here is the result:\n```json\n{\"uncovered\":[{\"requirement\":\"empty input\",\"reason\":\"none\"}]}\n```\nHope that helps!"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Clean statement.", List.of("test_happy"));

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).requirement()).isEqualTo("empty input");
    }

    /** A short / placeholder brief skips the LLM pass entirely (no real spec to critique) — the model is never called. */
    @Test
    void trivialBrief_skipsModelCall() {
        ChatModel chatModel = mock(ChatModel.class);
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique("too short", "Clean statement.", List.of("test_x"));

        assertThat(report.hasFindings()).isFalse();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    /** A degenerate response with far too many entries is capped, so a critic finding list can never flood the retry prompt or review panel. */
    @Test
    void floodOfFindings_isCapped() {
        StringBuilder body = new StringBuilder("{\"uncovered\":[");
        for (int i = 0; i < 100; i++) {
            body.append(i == 0 ? "" : ",").append("{\"requirement\":\"req").append(i).append("\",\"reason\":\"r\"}");
        }
        body.append("]}");
        SpecFidelityCriticService critic = criticReturning(jsonResponse(body.toString()));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Clean statement.", List.of("test_x"));

        // Capped well below 100 so a degenerate model response cannot explode downstream.
        assertThat(report.findings().size()).isLessThanOrEqualTo(12);
    }

    /** Entries missing a requirement string are skipped rather than producing blank findings. */
    @Test
    void entriesMissingRequirement_areSkipped() {
        SpecFidelityCriticService critic = criticReturning(
                jsonResponse("{\"uncovered\":[{\"reason\":\"orphan\"},{\"requirement\":\"\"},{\"requirement\":\"CJK\",\"reason\":\"no test\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Clean statement.", List.of("test_x"));

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).requirement()).isEqualTo("CJK");
    }

    /** The retry-prompt rendering folds both finding kinds into actionable, advisory-framed instructions, and is empty for an empty report. */
    @Test
    void renderForRetryPrompt_foldsFindingsAndIsEmptyWhenNone() {
        // renderForRetryPrompt is model-free, so a critic without a ChatClient suffices.
        SpecFidelityCriticService critic = new SpecFidelityCriticService(null, objectMapper);
        assertThat(critic.renderForRetryPrompt(SpecFidelityReport.empty())).isEmpty();

        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "CJK", "no CJK test"),
                new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MECHANICS_LEAK, "make the tests fail", "leak")));
        String rendered = critic.renderForRetryPrompt(report);
        assertThat(rendered).contains("did NOT cause rejection").contains("No test covers this requirement").contains("CJK").contains("grader-mechanics phrasing")
                .contains("make the tests fail");
    }

    // --- detectMessagelessAssertions (deterministic, model-free) ---

    /** A critic instance whose model is never used (the detector is deterministic). */
    private SpecFidelityCriticService detector() {
        return new SpecFidelityCriticService(null, objectMapper);
    }

    @Test
    void messageless_flagsAWhollyBareJvmTestFile() {
        // The ego-death case: every assertion is a bare value/throws check, so a failing student sees only "expected 600 but was 500".
        String bare = "class FSSizeCalculatorTest {\n  @Test void calc() { assertEquals(600L, new FSSizeCalculator().calculateSize(root)); }\n"
                + "  @Test void nul() { assertThrows(IllegalArgumentException.class, () -> new FSSizeCalculator().calculateSize(null)); }\n}";
        var findings = detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/FSSizeCalculatorTest.java", bare));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).kind()).isEqualTo(SpecFidelityReport.Kind.MISSING_FAILURE_MESSAGE);
        assertThat(findings.get(0).requirement()).isEqualTo("test/FSSizeCalculatorTest.java");
    }

    @Test
    void messageless_doesNotFlagWhenAssertionsCarryAMessage() {
        // A descriptive failure message on an assertion (the gold-standard bar) -> not flagged.
        String messaged = "class T {\n  @Test void a() { assertEquals(600L, calc.size(root), \"size must sum every file regardless of depth\"); }\n}";
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/T.java", messaged))).isEmpty();
    }

    @Test
    void messageless_doesNotFlagWhenFailHasAMessage() {
        // The SortingExample idiom: if (!ok) fail("BubbleSort does not sort correctly"); counts as messaged.
        String failStyle = "class T {\n  @Test void a() { if (!ok) fail(\"BubbleSort does not sort correctly\"); }\n}";
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/T.java", failStyle))).isEmpty();
    }

    @Test
    void messageless_doesNotFlagAMixedFile_fileLevelThreshold() {
        // One messaged assertion makes the file not-wholly-bare -> not flagged (conservative file-level threshold avoids over-firing on a partially-good file).
        String mixed = "class T {\n  @Test void a() { assertEquals(1, x); }\n  @Test void b() { assertTrue(ok, \"b must hold after push\"); }\n}";
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/T.java", mixed))).isEmpty();
    }

    @Test
    void messageless_doesNotCountACommentedOutMessageAsCoverage() {
        // A commented-out messaged assertion must not make a wholly-bare file look messaged.
        String commented = "class T {\n  // assertEquals(1, x, \"old message\")\n  @Test void a() { assertEquals(1, x); }\n}";
        var findings = detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/T.java", commented));
        assertThat(findings).hasSize(1);
    }

    @Test
    void messageless_failsOpenForNonJvmLanguages() {
        // Go (t.Errorf format strings), TS (Jest auto-diff + it() names) and C++ (Catch2 expression expansion) self-describe -> out of scope -> never flagged.
        String goBare = "func TestReverse(t *testing.T){ if got != want { t.Errorf(\"x\") } }";
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.GO, Map.of("stringutils_test.go", goBare))).isEmpty();
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.TYPESCRIPT, Map.of("Stack.test.ts", "expect(s.pop()).toBe(1);"))).isEmpty();
    }

    @Test
    void messageless_ignoresFilesWithoutAssertions() {
        // A helper/fixture file with no assertions is not a graded test file -> nothing to flag.
        String helper = "class Helpers {\n  static FSNode tree() { return new FSNode(\"root\", List.of()); }\n}";
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/Helpers.java", helper))).isEmpty();
    }
}
