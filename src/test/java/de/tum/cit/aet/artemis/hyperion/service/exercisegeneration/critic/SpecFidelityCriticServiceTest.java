package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class SpecFidelityCriticServiceTest {

    private static final String GITHUB_SENTINEL = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

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

    @Test
    void specificationReview_acceptsGroundedLearningFitAndEmptyDefectVerdict() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefQuote":"Create an intermediate exercise about strategies in an unusual logistics theme.",
                         "specQuotes":["R1: rerouting transfers the undelivered cargo without losing already delivered parcels."],
                         "remainingStudentReasoning":"The state-preserving reroute remains after routine strategy delegation and makes the logistics domain affect the behavior.","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                        """));

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate exercise about strategies in an unusual logistics theme.",
                "# Exercise\n## Rules\n- R1: rerouting transfers the undelivered cargo without losing already delivered parcels.", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isTrue();
        assertThat(review.findings()).isEmpty();
    }

    @Test
    void specificationReviewRejectsMoreThanFourCombinedFindings() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefQuote":"counter","specQuotes":["Counter"],"remainingStudentReasoning":"The counter work is meaningful.","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[
                           {"briefQuote":"counter","reason":"one","repair":"repair one"},
                           {"briefQuote":"counter","reason":"two","repair":"repair two"}],
                         "conflicts":[],"internalConflicts":[],"incorrectExamples":[],
                         "unsupportedConstraints":[
                           {"specQuote":"Counter","reason":"three","repair":"repair three"},
                           {"specQuote":"Counter","reason":"four","repair":"repair four"},
                           {"specQuote":"Counter","reason":"five","repair":"repair five"}]}
                        """));

        assertThat(critic.reviewSpecification("Create a counter.", "# Counter", null, () -> false).complete()).isFalse();
    }

    @Test
    void specificationReview_explicitlyChecksOwnershipTableAgainstLaterTemplateProse() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse(
                """
                        {"learningFit":{"briefQuote":"strategy interface","specQuotes":["student-creates"],
                         "remainingStudentReasoning":"the cited ownership preserves the requested design work","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                        """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        critic.reviewSpecification("Students create the strategy interface.", "The design table marks it student-creates.", null, () -> false);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions().getFirst().getText()).contains("Design ownership table").contains("template supplies a type marked `student-creates`")
                .contains("correct table does not cancel contradictory prose").contains("does not assign ownership of the strategy interface")
                .contains("Non-student-visible harness notes are not observable constraints")
                .contains("Package, source-root, and class-visibility choices required by the seeded build")
                .contains("explicitly requested difficulty", "one-operation formula transcription", "declared archetype", "Testing Strategy gives supporting calculations")
                .contains("defect detection, not design optimization", "try to", "whole specification", "common teaching examples for the requested programming concept")
                .contains("domain is familiar in popular culture", "theme identity from theme integration", "arbitrary formula", "constants under themed names",
                        "itself wiring the collaboration")
                .contains("Empty defect arrays are not sufficient evidence", "learningFit", "subtractive", "erasing the domain nouns")
                .contains("remainingStudentReasoning", "domainGrounding", "Do not invent a plausible post-hoc domain rationale", "do not invent validation")
                .contains("at most four blocking findings TOTAL", "Diagnose properties only", "never supply replacement names, domains, formulas, or APIs")
                .doesNotContain("compression, payment, sorting, and navigation");
    }

    @Test
    void specificationReview_returnsGroundedFindingsAsActionableFeedback() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefQuote":"strategy interface","specQuotes":["Exercise"],
                         "remainingStudentReasoning":"the candidate does not preserve the requested interface work","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":false,"repair":"preserve meaningful interface design"},
                         "omissions":[{"briefQuote":"students create the strategy interface","reason":"the interface is supplied","repair":"mark it student-owned"}],
                         "conflicts":[],"internalConflicts":[],"incorrectExamples":[],
                         "unsupportedConstraints":[{"specQuote":"throw an exact message","reason":"the brief does not request a message","repair":"remove the exact message"}]}
                        """));

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Have students create the strategy interface.",
                "# Exercise\nThe context must throw an exact message.", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("students create the strategy interface", "throw an exact message", "choose the content yourself",
                "remove or relax only the cited unsupported obligation").doesNotContain("mark it student-owned", "remove the exact message");
    }

    @Test
    void specificationReview_rejectsAnIncorrectWorkedExampleBeforeTheContractFreezes() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefQuote":"arithmetic exercise","specQuotes":["2 + 2 = 5"],
                         "remainingStudentReasoning":"the arithmetic rule is the requested learning work","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"unsupportedConstraints":[],
                         "incorrectExamples":[{"specQuote":"2 + 2 = 5","reason":"the arithmetic evaluates to four","repair":"replace 5 with 4"}]}
                        """));

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an arithmetic exercise.", "# Examples\n2 + 2 = 5", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("2 + 2 = 5", "evaluates to four", "independently recompute the example").doesNotContain("replace 5 with 4");
    }

    @Test
    void specificationReview_rejectsMutuallyIncompatibleRulesBeforeTheContractFreezes() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefQuote":"strategy pattern","specQuotes":["Switching strategy preserves accumulated energy."],
                         "remainingStudentReasoning":"the switch policy is the relevant collaboration","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[],"conflicts":[],"incorrectExamples":[],"unsupportedConstraints":[],
                         "internalConflicts":[{"firstSpecQuote":"Switching strategy preserves accumulated energy.",
                         "secondSpecQuote":"Switching strategy resets accumulated energy.","reason":"both cannot hold for the same switch",
                         "repair":"choose and state one switch policy"}]}
                        """));

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Teach the strategy pattern.",
                "Switching strategy preserves accumulated energy. Switching strategy resets accumulated energy.", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("preserves accumulated energy", "resets accumulated energy", "choose one coherent interpretation grounded in the brief")
                .doesNotContain("choose and state one switch policy");
    }

    @Test
    void specificationReviewTreatsMarkdownEmphasisAsPresentationWhenGroundingAQuote() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefQuote":"strategy pattern","specQuotes":["`Healing potency is two per herb.`"],
                         "remainingStudentReasoning":"the quoted rule is the strategy behaviour","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[],"conflicts":[],"incorrectExamples":[],"unsupportedConstraints":[],
                         "internalConflicts":[{"firstSpecQuote":"`Healing potency is two per herb.`",
                         "secondSpecQuote":"Healing potency is four for six herbs.","reason":"the arithmetic conflicts",
                         "repair":"correct the worked example"}]}
                        """));

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Teach the strategy pattern.",
                "**Healing potency** is `two` per herb. **Healing potency** is four for six herbs.", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("the arithmetic conflicts", "choose one coherent interpretation grounded in the brief").doesNotContain("correct the worked example");
    }

    @Test
    void specificationReview_distinguishesMalformedUngroundedAndShortGroundedVerdicts() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse malformed = rawResponse("not json");
        ChatResponse ungrounded = rawResponse(
                """
                        {"learningFit":{"briefQuote":"counter","specQuotes":["Counter"],"remainingStudentReasoning":"the counter work is meaningful","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[{"briefQuote":"a requirement that is not in the brief","reason":"missing","repair":"add it"}],
                         "conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                        """);
        ChatResponse grounded = rawResponse(
                """
                        {"learningFit":{"briefQuote":"Java counter","specQuotes":["Counter"],"remainingStudentReasoning":"the counter work is meaningful","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[{"briefQuote":"Java","reason":"the learning objective is missing","repair":"change it"}],
                         "conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                        """);
        when(chatModel.call(any(Prompt.class))).thenReturn(malformed, malformed, ungrounded, ungrounded, grounded);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        assertThat(critic.reviewSpecification("Create a counter.", "# Counter", null, () -> false).complete()).isFalse();
        assertThat(critic.reviewSpecification("Create a counter.", "# Counter", null, () -> false).complete()).isFalse();
        SpecFidelityCriticService.SpecificationReview shortGrounded = critic.reviewSpecification("Create a Java counter.", "# Counter", null, () -> false);
        assertThat(shortGrounded.complete()).isTrue();
        assertThat(shortGrounded.accepted()).isFalse();
    }

    @Test
    void specificationReviewDoesNotAcceptAGenericReasonWithoutBothRequiredAnalyses() {
        SpecFidelityCriticService critic = criticReturning(rawResponse("""
                {"learningFit":{"briefQuote":"intermediate strategy exercise","specQuotes":["R1: compute a themed value."],
                 "reason":"The theme and strategy types satisfy the brief.","sufficient":true,"repair":null},
                 "omissions":[],"conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                """));

        assertThat(critic.reviewSpecification("Create an intermediate strategy exercise.", "R1: compute a themed value.", null, () -> false).complete()).isFalse();
    }

    @Test
    void specificationReview_requiresGroundedPositiveLearningEvidence() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"omissions":[],"conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                """), rawResponse(
                """
                        {"learningFit":{"briefQuote":"intermediate","specQuotes":["an invented rule"],
                         "remainingStudentReasoning":"the invented rule supplies depth","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":true,"repair":null},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                        """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        assertThat(critic.reviewSpecification("Create an intermediate exercise.", "R1: meaningful domain interaction", null, () -> false).complete()).isFalse();
        assertThat(critic.reviewSpecification("Create an intermediate exercise.", "R1: meaningful domain interaction", null, () -> false).complete()).isFalse();
    }

    @Test
    void specificationReviewRetriesOneMalformedGroundingVerdictWithoutRelaxingQuoteValidation() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"learningFit":{"briefQuote":"Create an intermediate strategy exercise with a potion theme.","specQuotes":["R1: weak ingredients limit potency."],
                 "remainingStudentReasoning":"Students must reason about a limiting ingredient after routine delegation is removed.",
                 "domainGrounding":"A potion is constrained by its weakest ingredient, which motivates the rule.","sufficient":true,"repair":null},
                 "omissions":[],"conflicts":[],"incorrectExamples":[],"unsupportedConstraints":[],
                 "internalConflicts":[{"firstSpecQuote":"R1 | weak ingredients limit potency","secondSpecQuote":"R2: every ingredient contributes equally.",
                 "reason":"The claims conflict.","repair":"Choose one potency rule."}]}
                """), rawResponse("""
                {"learningFit":{"briefQuote":"Create an intermediate strategy exercise with a potion theme.","specQuotes":["R1: weak ingredients limit potency."],
                 "remainingStudentReasoning":"Students must reason about a limiting ingredient after routine delegation is removed.",
                 "domainGrounding":"A potion is constrained by its weakest ingredient, which motivates the rule.","sufficient":true,"repair":null},
                 "omissions":[],"conflicts":[],"incorrectExamples":[],"unsupportedConstraints":[],
                 "internalConflicts":[{"firstSpecQuote":"R1: weak ingredients limit potency.","secondSpecQuote":"R2: every ingredient contributes equally.",
                 "reason":"The claims can coexist after all.","repair":"Ignore the conflict."}]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate strategy exercise with a potion theme.",
                "R1: weak ingredients limit potency. R2: every ingredient contributes equally.", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("R1: weak ingredients limit potency.", "R2: every ingredient contributes equally.", "The claims conflict.")
                .doesNotContain("The claims can coexist", "Ignore the conflict");
        ArgumentCaptor<Prompt> correction = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(correction.capture());
        assertThat(correction.getAllValues().get(1).getInstructions().get(1).getText()).contains("PREVIOUS VERDICT", "untrusted data", "R1 | weak ingredients limit potency");
    }

    @Test
    void specificationReviewCorrectionCannotDropABlockingFinding() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"learningFit":{"briefQuote":"Create an intermediate strategy exercise.","specQuotes":["R1: meaningful strategy decision."],
                 "remainingStudentReasoning":"Students choose a meaningful strategy interaction.","domainGrounding":"No qualitative theme was requested.","sufficient":true},
                 "omissions":[],"conflicts":[],"incorrectExamples":[],"unsupportedConstraints":[],
                 "internalConflicts":[{"firstSpecQuote":"R1 | meaningful strategy decision","secondSpecQuote":"R2: contradicting strategy decision.",
                 "reason":"The decisions conflict.","repair":"Choose one decision."}]}
                """), rawResponse("""
                {"learningFit":{"briefQuote":"Create an intermediate strategy exercise.","specQuotes":["R1: meaningful strategy decision."],
                 "remainingStudentReasoning":"Students choose a meaningful strategy interaction.","domainGrounding":"No qualitative theme was requested.","sufficient":true},
                 "omissions":[],"conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate strategy exercise.",
                "R1: meaningful strategy decision. R2: contradicting strategy decision.", null, () -> false);

        assertThat(review.complete()).isFalse();
        assertThat(review.accepted()).isFalse();
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void specificationReview_turnsInsufficientLearningEvidenceIntoOneFocusedRepair() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefQuote":"intermediate Java exercise","specQuotes":["Fireball computes basePower * 2 + 10.",
                         "SpellCaster delegates to the current strategy."],
                         "remainingStudentReasoning":"after subtracting formula transcription and routine delegation, no intermediate domain decision remains","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","sufficient":false,"repair":"preserve spellcasting but deepen one domain-grounded strategy interaction"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"incorrectExamples":[],"unsupportedConstraints":[]}
                        """));

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Java exercise.",
                "Fireball computes basePower * 2 + 10. SpellCaster delegates to the current strategy.", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.findings()).singleElement().asString().contains("Learning fit", "no intermediate domain decision remains", "preserve unaffected theme",
                "If the residual reasoning is too shallow", "if it is excessive or off-objective, simplify or refocus it", "if only the domain grounding is weak",
                "Choose the behavior yourself");
    }

    private static final String UNICODE_BRIEF = "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark "
            + "sequence, CJK characters, and at least one emoji.";

    private static final Map<RepositoryType, Map<String, String>> COMPLETE_ARTIFACTS = Map.of(RepositoryType.SOLUTION,
            Map.of("src/Graphemes.java", "class Graphemes { int count(String value) { return value.length(); } }"), RepositoryType.TEMPLATE,
            Map.of("src/Graphemes.java", "class Graphemes { int count(String value) { return 0; } }"), RepositoryType.TESTS,
            Map.of("test/GraphemesTest.java", "class GraphemesTest { void cjk() { assertEquals(2, count(\"\u6f22\u5b57\")); } }"));

    @Test
    void supportedSecretInGeneratedCandidatePreventsEveryCriticProviderCall() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        Map<RepositoryType, Map<String, String>> artifacts = Map.of(RepositoryType.SOLUTION, Map.of("src/Fixture.java", GITHUB_SENTINEL), RepositoryType.TEMPLATE,
                Map.of("src/Fixture.java", "class Fixture {}"), RepositoryType.TESTS, Map.of("test/FixtureTest.java", "class FixtureTest {}"));

        assertThatExceptionOfType(de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy.SecretMaterialException.class)
                .isThrownBy(() -> critic.critique("brief", "# Problem", List.of("fixture"), artifacts, null)).withMessageContaining("GITHUB_TOKEN")
                .withMessageNotContaining(GITHUB_SENTINEL);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void blockingContractReviewMapsAcceptanceBlockersAndIncludesExecutableEvidence() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("""
                {"exampleChecks": [{"claim": "the rover ends at (2,3) E", "computedOutcome": "the rover ends at (2,2) N", "consistent": false,
                    "reason": "replaying the command sequence gives a different state"}],
                 "apiChecks": [{"symbol": "Rover(int,int,Collection<int[]>)", "discoverable": false, "reason": "tests require it while the statement leaves the API open"}],
                 "templateChecks": [{"test": "turnsLeft", "targetReached": false, "reason": "the constructor throws before the turn assertion"}],
                 "mutantChecks": [{"mutant": "reject CJK characters", "killed": false, "sourceQuote": "CJK characters", "reason": "no assertion exercises CJK input"}],
                 "uncovered": [{"requirement": "CJK characters", "sourceQuote": "CJK characters", "reason": "no assertion exercises CJK input"}],
                 "contradictions": [], "hiddenRequirements": [], "weakOracle": [], "templateGaps": [],
                 "missingExamples": [], "invented": [], "unrequestedChanges": [], "missingRequestedChanges": []}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"reject CJK characters","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "# Rover\nWorked example: the rover ends at (2,3) E", List.of("turnsLeft"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                SpecFidelityReport.Kind.HIDDEN_GRADED_REQUIREMENT, SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP);
        assertThat(report.hasBlockingFindings()).isTrue();
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues())
                .allSatisfy(value -> assertThat(value.getContents()).contains("SOLUTION: src/Graphemes.java", "TEMPLATE: src/Graphemes.java", "TESTS: test/GraphemesTest.java")
                        .contains("assertEquals(2, count(\"\u6f22\u5b57\"))").contains("Do not treat test names or comments as proof"));
    }

    /** The teaching-scaffold axis (missing contract doc, missing TODO anchor, or a non-student solution/template diff) folds into the same TEMPLATE_QUALITY_GAP kind. */
    @Test
    void templateQualityGapReviewSurfacesMissingTeachingScaffoldWithQuotedEvidence() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],
                 "apiChecks":[],
                 "templateChecks":[{"test":"count","targetReached":false,
                     "reason":"the stub's doc comment does not restate its contract: the solution's Javadoc reads 'Returns the number of user-perceived characters in value.' but \
                the template's count(String value) method has no doc comment at all"}],
                 "contradictions":[],
                 "hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return 0","killed":true,"reason":"the cjk assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP);
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.requirement()).isEqualTo("count");
            assertThat(finding.detail()).contains("Returns the number of user-perceived characters in value.", "has no doc comment at all");
        });
        assertThat(report.hasBlockingFindings()).isTrue();
    }

    /** The reviewer prompt wires the scaffold-quality instructions into the same templateChecks axis, without a schema change. */
    @Test
    void contractReviewPromptInstructsScaffoldQualityChecks() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return 0","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(0).getInstructions().getFirst().getText()).contains("house teaching scaffold", "restate its student-visible contract",
                "imperative TODO", "stubbed owner", "solution/template diff", "compact API surface", "PlantUML diagram", "the template is the API reference at the point of use")
                .doesNotContain("breadcrumb for a type the student must still create");
    }

    @Test
    void focusedReviewPassesAcceptOnlyTheirOwnEvidenceShape() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],
                 "apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],
                 "hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":false,"sourceQuote":"user-perceived characters","reason":"no assertion uses a surrogate pair"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(0).getInstructions().getFirst().getText())
                .contains("Return every failed check", "one representative passing check", "mandatory and unambiguous", "Do not infer task reachability",
                        "Do not invent requirements from solution-only behavior", "claims alternatives", "one operation or the whole call")
                .doesNotContain("Trace each task through the starter", "first failure is the intended placeholder")
                .contains("At most 3 exampleChecks, 8 apiChecks, 6 templateChecks, and 4 items in every other array").doesNotContain("mutantChecks", "weakOracle", "uncovered");
        assertThat(prompts.getAllValues().get(1).getInstructions().getFirst().getText()).contains("at most six highest-risk representative mutants")
                .contains("only the few highest-leverage blockers that have distinct repairs").doesNotContain("For every explicit rule and public operation")
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
    void malformedInventedRequirementFailsClosed() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse("{\"invented\":[{}]}"));

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
    void blockingContractFindingIsMergedWithIndependentOracleFinding() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[{"claim":"member 101 has two checkouts","computedOutcome":"member 101 has one checkout","consistent":false,
                    "reason":"the early return is ignored"}],
                 "apiChecks":[],"templateChecks":[{"test":"example","targetReached":true,"reason":"the assertion reaches the target"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"ignore CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Worked example: member 101 has two checkouts", List.of("example"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactlyInAnyOrder(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void unavailableContractPassDoesNotSuppressOracleFinding() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":false,"sourceQuote":"user-perceived characters","reason":"no assertion uses a surrogate pair"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactlyInAnyOrder(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE,
                SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
        verify(chatModel, times(2)).call(any(Prompt.class));
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
    void cancelledBeforeTheFirstReviewerCall_skipsEveryProviderCallAndReturnsNoFindings() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "# Rover", List.of("turnsLeft"), COMPLETE_ARTIFACTS, null, () -> true);

        assertThat(report.findings()).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
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
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[{\"requirement\":\"CJK characters\",\"sourceQuote\":\"CJK characters\",\"reason\":\"no CJK test\"},{\"requirement\":\"emoji\",\"sourceQuote\":\"emoji\",\"reason\":\"no emoji test\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("test_ascii_only", "test_cafe_precomposed"));

        assertThat(report.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactlyInAnyOrder("CJK characters", "emoji");
    }

    @Test
    void oracleReviewCorrectsUngroundedClaimsFromEveryFindingArray() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"return UTF-16 length","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[
                    {"requirement":"CJK characters","sourceQuote":"CJK characters","reason":"no CJK assertion"},
                    {"requirement":"result must be non-negative","sourceQuote":"result must be non-negative","reason":"the solution happens to enforce it"}],
                 "weakOracle":[{"requirement":"results must be sorted","sourceQuote":"results must be sorted","reason":"sorting is not asserted"}]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return UTF-16 length","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[{"requirement":"CJK characters","sourceQuote":"CJK characters","reason":"no CJK assertion"}],
                 "weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT);
            assertThat(finding.requirement()).isEqualTo("CJK characters");
        });
        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    @Test
    void producedStatementCannotAuthorizeAnOracleFinding() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"treat equality as not overdue","killed":false,"sourceQuote":"equal to or after dueAt",
                    "reason":"no equality assertion distinguishes the mutant"}],
                 "uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"ignore penalty records","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique("Create a Java exercise about calculating member penalties from checkout records; choose coherent API and business rules.",
                "A checkout is overdue when returnedAt is equal to or after dueAt. Add one penalty point for every overdue checkout.", List.of("countsDueDateEquality"),
                COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).isEmpty();
        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    @Test
    void oracleReviewPreservesGroundedRepairFeedbackWhenAnotherClaimIsUngrounded() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[
                    {"mutant":"reject CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"},
                    {"mutant":"require sorted output","killed":false,"sourceQuote":"results must be sorted","reason":"only the generated statement says this"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes and return sorted diagnostics.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactlyInAnyOrder(SpecFidelityReport.Kind.WEAK_TEST_ORACLE,
                SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
        assertThat(report.findings()).anySatisfy(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
            assertThat(finding.requirement()).isEqualTo("reject CJK input");
        });
    }

    @Test
    void oracleReviewRetriesAnUngroundedVerdictAndUsesTheCompleteCorrection() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[
                    {"mutant":"reject CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"},
                    {"mutant":"require sorted output","killed":false,"sourceQuote":"results must be sorted","reason":"only the generated statement says this"}],
                 "uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"ignore emoji input","killed":false,"sourceQuote":"at least one emoji",
                    "reason":"no assertion uses emoji input"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes and return sorted diagnostics.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactlyInAnyOrder("reject CJK input", "ignore emoji input");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(3)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getContents()).contains("previous verdict cited at least one sourceQuote", "PRIMARY SOURCE REQUIREMENTS",
                "PRODUCED PROBLEM STATEMENT");
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
        when(chatModel.getOptions()).thenReturn(
                OpenAiChatOptions.builder().model("configured-model").reasoningEffort("medium").serviceTier("priority").customHeaders(Map.of("X-Test", "value")).build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper, "configured-model");

        critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy(value -> assertThat(value.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxCompletionTokens()).isEqualTo(32_768);
            assertThat(options.getMaxTokens()).isNull();
            assertThat(options.getReasoningEffort()).isEqualTo("medium");
            assertThat(options.getServiceTier()).isEqualTo("priority");
            assertThat(options.getCustomHeaders()).containsEntry("X-Test", "value");
        }));
    }

    @Test
    void critic_preservesTheLegacyConfiguredTokenParameter() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{\"uncovered\":[]}"));
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().maxTokens(1_234).build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper, "configured-model", Duration.ZERO,
                ProviderFailureCooldown.disabled(), 128_000, chatModel.getOptions());

        critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy(value -> assertThat(value.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxTokens()).isEqualTo(1_234);
            assertThat(options.getMaxCompletionTokens()).isNull();
        }));
    }

    @Test
    void critic_clampsOutputToTheConfiguredContextWindow() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{\"uncovered\":[]}"));
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper, "configured-model", Duration.ZERO,
                ProviderFailureCooldown.disabled(), 16_000, chatModel.getOptions());

        critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy(value -> assertThat(value.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxCompletionTokens()).isGreaterThanOrEqualTo(4_096).isLessThan(32_768);
            assertThat(options.getMaxTokens()).isNull();
        }));
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
    void contractAdvisoryDoesNotSkipOracleReview() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(jsonResponse("{\"missingExamples\":[{\"behaviour\":\"combining marks\",\"reason\":\"a trace would clarify the rule\"}]}"), rawResponse("""
                        {"mutantChecks":[{"mutant":"reject CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"}],
                         "uncovered":[],"weakOracle":[]}
                        """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactlyInAnyOrder(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE,
                SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
        verify(chatModel, times(2)).call(any(Prompt.class));
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
                .contains("Do not invent requirements from solution-only behavior", "Distinguish observable guarantees from pedagogical objectives",
                        "INSTRUCTOR BRIEF is the sole scope authority", "input permitted by the declared contract", "mathematically redundant transformations",
                        "states that the declared types make impossible", "The produced statement is evidence to compare against the primary source, not authority",
                        "If the primary source requirements do not require every behavior needed to distinguish the proposed wrong implementation")
                .doesNotContain("derived contract", "source requirements and produced statement do not require");
    }

    /** A requirement the produced statement imposes that the brief never asked for is reported as an INVENTED_REQUIREMENT (scope-drift) finding. */
    @Test
    void inventedRequirementNotInBrief_isFlagged() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[{\"requirement\":\"O(1) extra space\",\"sourceQuote\":\"O(1) extra space\",\"reason\":\"the brief never constrains space complexity\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Rotate the matrix; your solution must use O(1) extra space.", List.of("test_rotate"), COMPLETE_ARTIFACTS, null,
                () -> false, null, "# Approved specification\nUse O(1) extra space.");

        assertThat(report.findings()).hasSize(1).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.INVENTED_REQUIREMENT);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactly("O(1) extra space");
        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void inventedRequirementFoundOnlyInFrozenSpecification_isNotAnUnrepairableBlocker() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[{\"requirement\":\"O(1) extra space\",\"sourceQuote\":\"O(1) extra space\",\"reason\":\"the brief never constrains space complexity\"}]}"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Rotate the matrix.", List.of("test_rotate"), COMPLETE_ARTIFACTS, null, () -> false, null,
                "# Approved specification\nUse O(1) extra space.");

        assertThat(report.findings()).isEmpty();
    }

    @Test
    void incorrectExampleFoundOnlyInFrozenSpecification_isNotAnUnrepairableBlocker() {
        SpecFidelityCriticService critic = criticReturning(
                jsonResponse("{\"exampleChecks\":[{\"claim\":\"2 + 2 = 5\",\"computedOutcome\":\"2 + 2 = 4\",\"consistent\":false,\"reason\":\"the arithmetic is wrong\"}]}"));

        SpecFidelityReport report = critic.critique("Create an arithmetic exercise.", "Calculate each result.", List.of("calculates"), COMPLETE_ARTIFACTS, null, () -> false, null,
                "# Approved specification\nExample: 2 + 2 = 5");

        assertThat(report.findings()).isEmpty();
    }

    @Test
    void incorrectExampleRepeatedInStudentFacingStatement_remainsBlocking() {
        SpecFidelityCriticService critic = criticReturning(
                jsonResponse("{\"exampleChecks\":[{\"claim\":\"2 + 2 = 5\",\"computedOutcome\":\"2 + 2 = 4\",\"consistent\":false,\"reason\":\"the arithmetic is wrong\"}]}"));

        SpecFidelityReport report = critic.critique("Create an arithmetic exercise.", "Example: 2 + 2 = 5", List.of("calculates"), COMPLETE_ARTIFACTS, null, () -> false, null,
                "# Approved specification\nExample: 2 + 2 = 5");

        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION));
    }

    @Test
    void oraclePassRejectsArtifactOnlySourceQuotes() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"accept null inventory names","killed":false,"sourceQuote":"reject null inventory names",
                    "reason":"the generated tests reject null"}],"uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"sort descending","killed":true,"reason":"the ascending assertion kills it"}],"uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        Map<RepositoryType, Map<String, String>> artifacts = Map.of(RepositoryType.SOLUTION,
                Map.of("src/Inventory.java", "class Inventory { String policy = \"reject null inventory names\"; }"), RepositoryType.TEMPLATE,
                Map.of("src/Inventory.java", "class Inventory {}"), RepositoryType.TESTS, Map.of("test/InventoryTest.java", "class InventoryTest {}"));

        SpecFidelityReport report = critic.critique("Sort integer values.", "Sort integer values in ascending order.", List.of("sortsValues"), artifacts, null);

        assertThat(report.findings()).isEmpty();
        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    @Test
    void freeFormTemplateGapCannotOverrideSuccessfulTaskReachabilityChecks() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                jsonResponse("{\"templateChecks\":[{\"test\":\"summarizesEvents\",\"targetReached\":true,\"reason\":\"the intended method placeholder is reached\"}],"
                        + "\"templateGaps\":[{\"requirement\":\"Implement summarize\",\"reason\":\"the method is a TODO throwing UnsupportedOperationException\"}]}"),
                jsonResponse("{}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique("Summarize checkout events.", "Implement summarize.", List.of("summarizesEvents"));

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

    @Test
    void mechanicsLeakIsMergedWithSemanticReviewFindings() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse(
                "{\"hiddenRequirements\":[{\"requirement\":\"count_graphemes(s)\",\"sourceQuote\":\"count_graphemes(s)\",\"reason\":\"the tests require this exact signature but the statement never restates it\"}]}"),
                rawResponse("""
                        {"mutantChecks":[{"mutant":"ignore CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"}],
                         "uncovered":[],"weakOracle":[]}
                        """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "todo!() in the template; the template must fail every test.", List.of("test_x"));

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).contains(SpecFidelityReport.Kind.MECHANICS_LEAK,
                SpecFidelityReport.Kind.HIDDEN_GRADED_REQUIREMENT, SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    /**
     * A contradiction whose {@code sourceQuote} does not appear verbatim in the brief or the produced statement is the critic's abstain outcome: dropped rather than surfaced, so
     * it can never reach the retry prompt as a hallucinated repair instruction. Every finding category that drives repair (contradictions, hidden requirements, invented
     * requirements) is held to this same uniform grounding requirement.
     */
    @Test
    void ungroundedContradictionFinding_isExcludedFromRepairPrompt() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(jsonResponse("{\"contradictions\":[{\"requirement\":\"the answer is always 42\",\"sourceQuote\":\"the answer is always 42\","
                        + "\"reason\":\"the solution and template disagree\"}]}"), rawResponse("""
                                {"mutantChecks":[{"mutant":"ignore CJK input","killed":true,"reason":"the assertion kills it"}],
                                 "uncovered":[],"weakOracle":[]}
                                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);
        assertThat(report.findings()).isEmpty();
        assertThat(critic.renderForRetryPrompt(report)).isEmpty();
    }

    /** A model error does not escape, but it blocks automatic acceptance because semantic evidence is missing. */
    @Test
    void modelError_degradesGracefully() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("gpu timeout"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        ProviderUsageSink usageSink = mock(ProviderUsageSink.class);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"), usageSink);

        assertThat(report.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
        verify(chatModel, times(2)).call(any(Prompt.class));
        // The critic is advisory: its provider failures must never reach the uncertainty path, which would stop the whole generation job.
        verify(usageSink, never()).markUncertain();
        verify(usageSink, never()).accept(any());
    }

    @Test
    void cancellationAfterContractResponse_skipsRemainingReviewerCalls() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        AtomicBoolean cancelled = new AtomicBoolean();

        critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, response -> cancelled.set(true), cancelled::get);

        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void cancellationAfterOracleResponse_skipsCorrectionCall() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"ignore CJK input","killed":false,"sourceQuote":"requirement absent from brief",
                    "reason":"the assertion does not cover it"}],"uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger responses = new AtomicInteger();

        critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, response -> cancelled.set(responses.incrementAndGet() == 2), cancelled::get);

        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void hardProviderFailureStopsTheSecondReviewPassAndLaterJobsAtSharedAdmission() {
        ChatModel failingModel = mock(ChatModel.class);
        when(failingModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 401 unauthorized"));
        when(failingModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ProviderFailureCooldown cooldown = inMemoryCooldown();
        SpecFidelityCriticService failingCritic = new SpecFidelityCriticService(ChatClient.create(failingModel), objectMapper, "configured-model", Duration.ofMinutes(5), cooldown);
        ProviderUsageSink firstUsageSink = mock(ProviderUsageSink.class);

        SpecFidelityReport firstReport = failingCritic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"), firstUsageSink);

        assertThat(firstReport.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
        verify(failingModel, times(1)).call(any(Prompt.class));
        verify(firstUsageSink, never()).markUncertain();

        ChatModel nextModel = mock(ChatModel.class);
        when(nextModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService nextCritic = new SpecFidelityCriticService(ChatClient.create(nextModel), objectMapper, "configured-model", Duration.ofMinutes(5), cooldown);
        ProviderUsageSink nextUsageSink = mock(ProviderUsageSink.class);

        SpecFidelityReport nextReport = nextCritic.critique(UNICODE_BRIEF, "Another candidate.", List.of("test_x"), nextUsageSink);

        assertThat(nextReport.findings()).hasSize(2).allMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
        verify(nextModel, never()).call(any(Prompt.class));
        verifyNoInteractions(nextUsageSink);
    }

    @Test
    void transientRateLimitDoesNotBlockTheSecondReviewPassOrLaterJob() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 429 rate_limit_exceeded: too many requests")).thenReturn(jsonResponse("{}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper, "configured-model", Duration.ofMinutes(5), inMemoryCooldown());

        SpecFidelityReport firstReport = critic.critique(UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"));
        SpecFidelityReport nextReport = critic.critique(UNICODE_BRIEF, "Another candidate.", List.of("test_x"));

        assertThat(firstReport.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        assertThat(nextReport.findings()).isEmpty();
        verify(chatModel, times(4)).call(any(Prompt.class));
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
                "<|start|>assistant<|channel|>final<|message|>\n```json\n{\"exampleChecks\":[],\"apiChecks\":[],\"templateChecks\":[{\"test\":\"happy path\",\"targetReached\":true,\"reason\":\"the target is reached\"}],\"mutantChecks\":[{\"mutant\":\"CJK input is rejected\",\"killed\":true,\"reason\":\"the assertion kills it\"}],\"uncovered\":[{\"requirement\":\"CJK characters\",\"sourceQuote\":\"CJK characters\",\"reason\":\"none\"}],\"contradictions\":[],\"hiddenRequirements\":[],\"weakOracle\":[],\"templateGaps\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[],\"missingRequestedChanges\":[]}\n```\n<|end|>"));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Clean statement.", List.of("test_happy"));

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).requirement()).isEqualTo("CJK characters");
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

    @Test
    void contractFindingFlood_cannotDisplaceTheOracleVerdict() {
        StringBuilder contradictions = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            contradictions.append(i == 0 ? "" : ",").append("{\"requirement\":\"contract ").append(i)
                    .append("\",\"sourceQuote\":\"user-perceived characters\",\"reason\":\"statement and test disagree; align both\"}");
        }
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches the target"}],
                 "contradictions":[%s],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """.formatted(contradictions)), rawResponse(
                """
                        {"mutantChecks":[{"mutant":"return UTF-16 length","killed":false,"sourceQuote":"user-perceived characters","reason":"test/GraphemesTest.java has no surrogate-pair assertion; add one"}],
                         "uncovered":[],"weakOracle":[]}
                        """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).hasSizeLessThanOrEqualTo(12).extracting(SpecFidelityReport.Finding::kind).contains(SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    /**
     * A statement that fences a template stub's full signature and javadoc verbatim, instead of a compact API surface, is a house-teaching-scaffold gap and folds into the same
     * TEMPLATE_QUALITY_GAP kind as the other scaffold checks (no schema change).
     */
    @Test
    void templateQualityGapReviewSurfacesStatementDuplicationOfTemplateStubWithQuotedEvidence() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],
                 "apiChecks":[],
                 "templateChecks":[{"test":"count","targetReached":false,
                     "reason":"the statement fences the entire template stub verbatim instead of a compact API surface: 'public int count(String value) { return 0; }'"}],
                 "contradictions":[],
                 "hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return 0","killed":true,"reason":"the cjk assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP);
        assertThat(report.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.detail()).contains("public int count(String value) { return 0; }", "compact API surface"));
    }

    /** The immediately preceding attempt's report is threaded into the next reviewer prompt as a PREVIOUS REVIEW section with a re-verification instruction. */
    @Test
    void continuityReview_threadsPreviousFindingsAndReVerificationInstructionIntoThePrompt() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":true,"reason":"the assertion now kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        SpecFidelityReport previousReport = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "return the UTF-16 length", "no assertion uses a surrogate pair")));

        critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null, () -> false, previousReport);

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> assertThat(prompt.getContents()).contains("PREVIOUS REVIEW", "return the UTF-16 length",
                "no assertion uses a surrogate pair", "adjudicate each item", "omit it if resolved", "repeat it with fresh current evidence if still open",
                "complete review of the current candidate", "including a defect overlooked previously"));
    }

    @Test
    void continuityReview_showsTheRepairDeltaBeforeAdjudicatingPriorFindings() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],"templateChecks":[{"test":"cjk","targetReached":true,"reason":"current assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],"unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"revert after first call","killed":true,"reason":"the added second assertion kills it"}],"uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        SpecFidelityReport previous = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "revert after first call", "only one call was asserted")));

        critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null, () -> false, previous, "contract",
                "--- tests/ExampleTest.java\n+ assertEquals(expected, callAgain());");

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> assertThat(prompt.getContents()).contains("REPAIR DELTA", "+ assertEquals(expected, callAgain())",
                "PREVIOUS REVIEW HYPOTHESES", "explicitly decide whether the added/changed assertion now kills that same mutant"));
    }

    /** A prior finding the current pass no longer reports (the model considers it resolved) does not linger in the new report. */
    @Test
    void continuityReview_resolvedPriorFindingIsNotCarriedForwardWhenTheCurrentPassOmitsIt() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":true,"reason":"the assertion now kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        SpecFidelityReport previousReport = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "return the UTF-16 length", "no assertion uses a surrogate pair")));

        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null, () -> false, previousReport);

        assertThat(report.hasFindings()).as("the mutant is now killed, so the previously reported finding is resolved and must not reappear").isFalse();
    }

    /** A first attempt has no history, so no PREVIOUS REVIEW section is rendered and the reviewer prompt is unchanged. */
    @Test
    void continuityReview_firstAttemptOmitsThePreviousReviewSection() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return 0","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);

        critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null, () -> false, SpecFidelityReport.empty());

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> assertThat(prompt.getContents()).doesNotContain("PREVIOUS REVIEW"));
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
                .contains("No test covers this requirement", "CJK", "no CJK test", "grader-mechanics phrasing", "make the tests fail", "leak", "clarify state restoration",
                        "First confirm that it comes from the source requirements")
                .doesNotContain("Add a test that asserts it");
    }

    // --- detectMessagelessAssertions (deterministic, model-free) ---

    /** A critic instance whose model is never used (the detector is deterministic). */
    private SpecFidelityCriticService detector() {
        return new SpecFidelityCriticService(null, objectMapper);
    }

    private static ProviderFailureCooldown inMemoryCooldown() {
        Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
        return new ProviderFailureCooldown() {

            @Override
            public Instant cooldownUntil(String key) {
                return cooldowns.get(key);
            }

            @Override
            public void startCooldown(String key, Instant until) {
                cooldowns.put(key, until);
            }
        };
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
