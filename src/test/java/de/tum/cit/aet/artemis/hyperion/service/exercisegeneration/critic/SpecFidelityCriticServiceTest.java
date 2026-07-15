package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class SpecFidelityCriticServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ChatResponse jsonResponse(String body) {
        if (body.startsWith("{") && body.endsWith("}")) {
            Map<String, String> auditDefaults = Map.of("exampleChecks",
                    "[{\"claim\":\"fixture outcome\",\"computedOutcome\":\"fixture outcome\",\"consistent\":true,\"reason\":\"the outcomes agree\"}]", "apiChecks",
                    "[{\"symbol\":\"fixture API\",\"discoverable\":true,\"reason\":\"the API is stated\"}]", "templateChecks",
                    "[{\"test\":\"fixture\",\"targetReached\":true,\"reason\":\"the target is reached\"}]", "mutantChecks",
                    "[{\"mutant\":\"fixture mutant\",\"killed\":true,\"reason\":\"an assertion kills it\"}]");
            for (Map.Entry<String, String> field : auditDefaults.entrySet()) {
                if (!body.contains("\"" + field.getKey() + "\"")) {
                    String remainder = body.substring(1);
                    body = "{\"" + field.getKey() + "\":" + field.getValue() + (remainder.equals("}") ? "" : ",") + remainder;
                }
            }
            for (String field : List.of("uncovered", "contradictions", "hiddenRequirements", "weakOracle", "templateGaps", "missingExamples", "invented", "unrequestedChanges",
                    "missingRequestedChanges")) {
                if (!body.contains("\"" + field + "\"")) {
                    String remainder = body.substring(1);
                    body = "{\"" + field + "\":[]" + (remainder.equals("}") ? "" : ",") + remainder;
                }
            }
        }
        return rawResponse(body);
    }

    private static ChatResponse rawResponse(String body) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(body))));
    }

    private SpecFidelityCriticService criticReturning(ChatResponse response) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        // Spring AI 2.0 merges the per-request .options(...) with the model's default options, so the ChatClient calls getOptions() during the request.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        return new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
    }

    private static final String UNICODE_BRIEF = "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark "
            + "sequence, CJK characters, and at least one emoji.";

    private static final Map<RepositoryType, Map<String, String>> COMPLETE_ARTIFACTS = Map.of(RepositoryType.SOLUTION,
            Map.of("src/Graphemes.java", "class Graphemes { int count(String value) { return value.length(); } }"), RepositoryType.TEMPLATE,
            Map.of("src/Graphemes.java", "class Graphemes { int count(String value) { return 0; } }"), RepositoryType.TESTS,
            Map.of("test/GraphemesTest.java", "class GraphemesTest { void cjk() { assertEquals(2, count(\"\u6f22\u5b57\")); } }"));

    @Test
    void fullArtifactReviewMapsAcceptanceBlockersAndIncludesExecutableEvidence() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("""
                {"exampleChecks": [{"claim": "the rover ends at (2,3) E", "computedOutcome": "the rover ends at (2,2) N", "consistent": false,
                    "reason": "replaying the command sequence gives a different state"}],
                 "apiChecks": [{"symbol": "Rover(int,int,Collection<int[]>)", "discoverable": false, "reason": "tests require it while the statement leaves the API open"}],
                 "templateChecks": [{"test": "turnsLeft", "targetReached": false, "reason": "the constructor throws before the turn assertion"}],
                 "mutantChecks": [{"mutant": "west and south movement are reversed", "killed": false, "reason": "no assertion exercises those transitions"}],
                 "uncovered": [{"requirement": "left turns", "reason": "no assertion executes L"}],
                 "contradictions": [], "hiddenRequirements": [], "weakOracle": [], "templateGaps": [],
                 "missingExamples": [], "invented": [], "unrequestedChanges": [], "missingRequestedChanges": []}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "# Rover", List.of("turnsLeft"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                SpecFidelityReport.Kind.HIDDEN_GRADED_REQUIREMENT, SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, SpecFidelityReport.Kind.WEAK_TEST_ORACLE,
                SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT);
        assertThat(report.hasBlockingFindings()).isTrue();
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues())
                .allSatisfy(value -> assertThat(value.getContents()).contains("SOLUTION: src/Graphemes.java", "TEMPLATE: src/Graphemes.java", "TESTS: test/GraphemesTest.java")
                        .contains("assertEquals(2, count(\"\u6f22\u5b57\"))").contains("Do not treat test names or comments as proof"));
    }

    @Test
    void focusedReviewPassesAcceptOnlyTheirOwnEvidenceShape() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],
                 "apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[{"requirement":"empty input","reason":"the statement and starter disagree"}],
                 "hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":false,"reason":"no assertion uses a surrogate pair"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(0).getInstructions().getFirst().getText())
                .contains("Return every failed check", "one representative passing check", "mandatory and unambiguous", "unrelated prerequisite fails before the target call",
                        "Do not invent requirements from solution-only behavior", "claims alternatives", "one operation or the whole call")
                .contains("At most 3 exampleChecks, 8 apiChecks, 6 templateChecks, and 4 items in every other array").doesNotContain("mutantChecks", "weakOracle", "uncovered");
        assertThat(prompts.getAllValues().get(1).getInstructions().getFirst().getText()).contains("at most six highest-risk representative mutants")
                .contains("At most 6 mutantChecks, 4 uncovered items, and 4 weakOracle items").doesNotContain("For every explicit rule and public operation")
                .doesNotContain("exampleChecks", "apiChecks", "templateChecks", "contradictions");
    }

    @Test
    void contractPassMissingRequiredEvidenceFailsClosed() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
            assertThat(finding.detail()).contains("contract reviewer");
        });
    }

    @Test
    void oraclePassMissingRequiredEvidenceFailsClosed() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":true,"reason":"the emoji assertion kills it"}],
                 "uncovered":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
            assertThat(finding.detail()).contains("test-oracle reviewer");
        });
    }

    @Test
    void unavailableOraclePassDoesNotDiscardContractFindings() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[{"claim":"member 101 has two checkouts","computedOutcome":"member 101 has one checkout","consistent":false,
                    "reason":"the early return is ignored"}],
                 "apiChecks":[],"templateChecks":[{"test":"example","targetReached":true,"reason":"the assertion reaches the target"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"count early returns","killed":true,"reason":"the assertion kills it"}],"uncovered":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Worked example", List.of("example"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    @Test
    void unavailableContractPassDoesNotDiscardOracleFindings() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":false,"reason":"no assertion uses a surrogate pair"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE,
                SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
    }

    @Test
    void unavailableReviewPassesRetainIndependentDiagnostics() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("{}"), rawResponse("{}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::detail).anyMatch(detail -> detail.contains("contract reviewer"))
                .anyMatch(detail -> detail.contains("test-oracle reviewer"));
    }

    @Test
    void unavailableOrMalformedQualityReviewFailsClosed() {
        SpecFidelityReport unavailable = new SpecFidelityCriticService(null, objectMapper).critique(UNICODE_BRIEF, "# Graphemes", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        SpecFidelityReport malformed = criticReturning(rawResponse("{\"uncovered\":[]}")).critique(UNICODE_BRIEF, "# Graphemes", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        SpecFidelityReport malformedBlockingFinding = criticReturning(jsonResponse("{\"contradictions\":[{}]}")).critique(UNICODE_BRIEF, "# Graphemes", List.of("cjk"),
                COMPLETE_ARTIFACTS, null);
        SpecFidelityReport unsupportedBlockingFinding = criticReturning(jsonResponse("{\"contradictions\":[{\"requirement\":\"conflict\"}]}")).critique(UNICODE_BRIEF,
                "# Graphemes", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(unavailable.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        assertThat(malformed.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
        assertThat(malformedBlockingFinding.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        assertThat(unsupportedBlockingFinding.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        assertThat(unavailable.hasBlockingFindings()).isTrue();
        assertThat(malformed.hasBlockingFindings()).isTrue();
        assertThat(malformedBlockingFinding.hasBlockingFindings()).isTrue();
        assertThat(unsupportedBlockingFinding.hasBlockingFindings()).isTrue();
    }

    @Test
    void artifactSetBeyondBoundedReviewInputFailsClosedWithoutCallingTheModel() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        Map<RepositoryType, Map<String, String>> oversizedArtifacts = Map.of(RepositoryType.SOLUTION, Map.of("src/Large.java", "x".repeat(100_000)));

        SpecFidelityReport report = critic.critique("Create an exercise.", "# Large", List.of(), oversizedArtifacts, null);

        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void incompleteArtifactSetFailsClosedWithoutCallingTheModel() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        Map<RepositoryType, Map<String, String>> missingTemplate = Map.of(RepositoryType.SOLUTION, Map.of("src/Exercise.java", "class Exercise {}"), RepositoryType.TESTS,
                Map.of("test/ExerciseTest.java", "class ExerciseTest {}"));

        SpecFidelityReport report = critic.critique("Create an exercise.", "# Exercise", List.of("testExercise"), missingTemplate, null);

        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void completeReviewPromptBeyondBoundedInputFailsClosedWithoutCallingTheModel() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique("x".repeat(120_000), "# Large", List.of(), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        verify(chatModel, never()).call(any(Prompt.class));
    }

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

        assertThat(report.findings()).isEmpty();
    }

    /** The critic's own LLM call must report its token usage to the run's sink; without it the critic's spend goes entirely unrecorded. */
    @Test
    void criticLlmCall_reportsTokenUsageToTheSink() {
        ChatResponse response = jsonResponse("{\"uncovered\":[]}");
        SpecFidelityCriticService critic = criticReturning(response);
        List<ChatResponse> tracked = new ArrayList<>();

        critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"), tracked::add);

        assertThat(tracked).containsExactly(response, response);
    }

    /** The live OpenAI-compatible model rejects generic DefaultChatOptions, so the critic must send provider-specific options. */
    @Test
    void criticUsesOpenAiChatOptions_soOpenAiModelsAcceptTheRequestOptions() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{\"uncovered\":[]}"));
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy(value -> assertThat(value.getOptions()).isInstanceOf(OpenAiChatOptions.class));
    }

    @Test
    void criticPinsTheConfiguredProviderModelOnBothReviewPasses() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"));
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper, "provider/reviewer-model");

        critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy(value -> assertThat(value.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
                options -> assertThat(options.getModel()).isEqualTo("provider/reviewer-model")));
    }

    /** An important, non-obvious behaviour that remains ambiguous without an example is reported as a MISSING_WORKED_EXAMPLE finding. */
    @Test
    void missingWorkedExample_isFlagged() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[{\"behaviour\":\"rollback after a failed checkout\",\"reason\":\"the interaction between undoing the charge and preserving queue position is difficult to apply without a trace\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF,
                "If checkout fails after charging the member, undo the charge and preserve the member's original queue position.", List.of("failedCheckoutRollsBackAtomically"));

        assertThat(report.findings()).hasSize(1).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactly("rollback after a failed checkout");
    }

    @Test
    void criticPrompt_acceptsConcreteExamplesInTablesOrProseAndDoesNotDemandOnePerEdgeCase() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{\"uncovered\":[],\"missingExamples\":[]}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        String reviewInstructions = prompt.getAllValues().stream().map(value -> value.getInstructions().getFirst().getText()).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(reviewInstructions).contains("replay every worked-example outcome", "unrequested and missing requested changes", "executable setup", "contract-breaking mutants")
                .contains("Do not invent requirements from solution-only behavior");
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

    @Test
    void oraclePassCannotInventSolutionOnlyRequirements() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"),
                jsonResponse("{\"invented\":[{\"requirement\":\"reject null\",\"reason\":\"the solution throws for null\"}]}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique("Sort integer values.", "Sort integer values.", List.of("sortsValues"));

        assertThat(report.findings()).isEmpty();
    }

    @Test
    void adaptationDiff_exposesUnrequestedDeletionAsBlockingFinding() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[{\"change\":\"solution/src/Inventory.java removed displayName(String)\",\"reason\":\"the feedback explicitly preserves it\"}],\"missingRequestedChanges\":[]}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critiqueAdaptation("Change only remove(); preserve displayName(String).", "# Inventory", List.of("removeRejectsZero"),
                "--- solution/src/Inventory.java\n- String displayName(String itemId)\n", null);

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE);
            assertThat(finding.requirement()).contains("displayName");
        });
        assertThat(report.hasBlockingFindings()).isTrue();
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy(value -> assertThat(value.getContents()).contains("ADAPTATION CHANGES").contains("- String displayName(String itemId)"));
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
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).contains(SpecFidelityReport.Kind.MECHANICS_LEAK,
                SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    /** A model error does not escape, but it blocks automatic acceptance because semantic evidence is missing. */
    @Test
    void modelError_degradesGracefully() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("gpu timeout"));
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        assertThat(report.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    /** Garbage (non-JSON) model output blocks automatic acceptance rather than failing open. */
    @Test
    void garbageOutput_degradesGracefully() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse("I think the tests look fine to me, no JSON here at all."));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        assertThat(report.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    /** An empty model response (null/blank text) degrades gracefully. */
    @Test
    void emptyOutput_degradesGracefully() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(""));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        assertThat(report.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    /** Harmony framing outside the JSON is harmless because payload extraction selects the fenced object. */
    @Test
    void jsonWrappedInHarmonyTokens_isStillParsed() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "<|start|>assistant<|channel|>final<|message|>\n```json\n{\"exampleChecks\":[],\"apiChecks\":[],\"templateChecks\":[{\"test\":\"happy path\",\"targetReached\":true,\"reason\":\"the target is reached\"}],\"mutantChecks\":[{\"mutant\":\"empty input is rejected\",\"killed\":true,\"reason\":\"the assertion kills it\"}],\"uncovered\":[{\"requirement\":\"empty input\",\"reason\":\"none\"}],\"contradictions\":[],\"hiddenRequirements\":[],\"weakOracle\":[],\"templateGaps\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[],\"missingRequestedChanges\":[]}\n```\n<|end|>"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Clean statement.", List.of("test_happy"));

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).requirement()).isEqualTo("empty input");
    }

    /** A short brief still receives semantic review because the generated statement and artifacts define a substantial student contract. */
    @Test
    void trivialBrief_stillReviewsTheProducedArtifacts() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique("too short", "Clean statement.", List.of("test_x"));

        assertThat(report.findings()).isEmpty();
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void adaptationWithNoChanges_blocksWithoutTrustingTheReviewer() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critiqueAdaptation("Change remove(0).", "# Inventory", List.of("test_x"), "", null);

        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING));
        assertThat(report.hasBlockingFindings()).isTrue();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void unavailableAdaptationScopeReview_blocksLivePersistence() {
        SpecFidelityCriticService critic = new SpecFidelityCriticService(null, objectMapper);

        SpecFidelityReport report = critic.critiqueAdaptation("Change remove(0).", "# Inventory", List.of("test_x"), "- old\n+ new", null);

        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE));
        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void adaptationDiff_exposesUnrequestedAdditionAsBlockingFinding() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[{\"change\":\"solution/src/Inventory.java added reset()\",\"reason\":\"the feedback only requests changing remove()\"}],\"missingRequestedChanges\":[]}"));

        SpecFidelityReport report = critic.critiqueAdaptation("Change only remove().", "# Inventory", List.of("removeRejectsZero"),
                "--- solution/src/Inventory.java\n+ void reset()\n", null);

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE);
            assertThat(finding.requirement()).contains("added reset()");
        });
        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void adaptationResponseMapsMissingRequestedChangeToBlockingFinding() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[],\"missingRequestedChanges\":[{\"requirement\":\"reject zero quantities\",\"reason\":\"no validation was added\"}]}"));

        SpecFidelityReport report = critic.critiqueAdaptation("Reject zero quantities.", "# Inventory", List.of("test_x"), "(no changes)", null);

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING);
            assertThat(finding.requirement()).isEqualTo("reject zero quantities");
        });
        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void malformedAdaptationScopeResponse_blocksLivePersistence() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse("not json"));

        SpecFidelityReport report = critic.critiqueAdaptation("Change remove(0).", "# Inventory", List.of("test_x"), "- old\n+ new", null);

        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void adaptationResponseMissingScopeVerdict_blocksLivePersistence() {
        SpecFidelityCriticService critic = criticReturning(rawResponse("{\"uncovered\":[]}"));

        SpecFidelityReport report = critic.critiqueAdaptation("Change remove(0).", "# Inventory", List.of("test_x"), "- old\n+ new", null);

        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void malformedAdaptationScopeEntry_blocksLivePersistence() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse("{\"unrequestedChanges\":[{\"reason\":\"missing change\"}],\"missingRequestedChanges\":[]}"));

        SpecFidelityReport report = critic.critiqueAdaptation("Change remove(0).", "# Inventory", List.of("test_x"), "- old\n+ new", null);

        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE));
    }

    @Test
    void generationIgnoresAdaptationOnlyFields() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[{\"change\":\"solution added reset()\",\"reason\":\"not requested\"}],\"missingRequestedChanges\":[{\"requirement\":\"change remove()\",\"reason\":\"missing\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "# Inventory", List.of("test_x"));

        assertThat(report.hasFindings()).isFalse();
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

    @Test
    void advisoryFindingFlood_cannotDisplaceBlockingAdaptationScopeDrift() {
        StringBuilder body = new StringBuilder("{\"uncovered\":[");
        for (int i = 0; i < 20; i++) {
            body.append(i == 0 ? "" : ",").append("{\"requirement\":\"req").append(i).append("\",\"reason\":\"r\"}");
        }
        body.append("],\"unrequestedChanges\":[{\"change\":\"solution removed displayName\",\"reason\":\"explicitly preserved\"}],\"missingRequestedChanges\":[]}");
        SpecFidelityCriticService critic = criticReturning(jsonResponse(body.toString()));

        SpecFidelityReport report = critic.critiqueAdaptation(UNICODE_BRIEF, "Clean statement.", List.of("test_x"), "- displayName", null);

        assertThat(report.hasBlockingFindings()).isTrue();
        assertThat(report.findings()).hasSizeLessThanOrEqualTo(12);
    }

    /** Retry rendering distinguishes advisory generation findings from blocking adaptation-scope findings and is empty for an empty report. */
    @Test
    void renderForRetryPrompt_foldsFindingsAndIsEmptyWhenNone() {
        // renderForRetryPrompt is model-free, so a critic without a ChatClient suffices.
        SpecFidelityCriticService critic = new SpecFidelityCriticService(null, objectMapper);
        assertThat(critic.renderForRetryPrompt(SpecFidelityReport.empty())).isEmpty();

        SpecFidelityReport report = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "CJK", "no CJK test"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE, "solution/Queue.java added clear()",
                                "The feedback did not request it."),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MECHANICS_LEAK, "make the tests fail", "leak"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, "rollback", "clarify state restoration")));
        String rendered = critic.renderForRetryPrompt(report);
        assertThat(rendered).contains("must fix before saving", "Optional quality improvements", "Unrequested adaptation change", "solution/Queue.java added clear()")
                .contains("No test covers this requirement", "CJK", "grader-mechanics phrasing", "make the tests fail");
    }

    // --- detectMessagelessAssertions (deterministic, model-free) ---

    /** A critic instance whose model is never used (the detector is deterministic). */
    private SpecFidelityCriticService detector() {
        return new SpecFidelityCriticService(null, objectMapper);
    }

    @Test
    void messageless_flagsAWhollyBareJvmTestFile() {
        // Every assertion is a bare value/throws check, so a failing student sees only "expected 600 but was 500".
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
