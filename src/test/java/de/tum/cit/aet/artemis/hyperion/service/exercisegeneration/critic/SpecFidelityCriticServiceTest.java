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
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport.Kind;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class SpecFidelityCriticServiceTest {

    private static final String GITHUB_SENTINEL = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String UNICODE_BRIEF = "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark "
            + "sequence, CJK characters, and at least one emoji.";

    /**
     * A complete artifact set whose solution declares a {@code public} member. That is what makes the contract reviewer's {@code apiChecks} array mandatory, so an empty one is a
     * skipped discoverability check rather than a clean verdict.
     */
    private static final Map<RepositoryType, Map<String, String>> PUBLIC_API_ARTIFACTS = Map.of(RepositoryType.SOLUTION,
            Map.of("src/Graphemes.java", "class Graphemes { public int count(String value) { return value.length(); } }"), RepositoryType.TEMPLATE,
            Map.of("src/Graphemes.java", "class Graphemes { int count(String value) { return 0; } }"), RepositoryType.TESTS,
            Map.of("test/GraphemesTest.java", "class GraphemesTest { void cjk() { assertEquals(2, count(\"漢字\")); } }"));

    /** A complete artifact set whose rendered evidence alone exceeds the reviewer's artifact budget, so the review stops at the evidence cap and not at an earlier guard. */
    private static final Map<RepositoryType, Map<String, String>> OVERSIZED_ARTIFACTS = Map.of(RepositoryType.SOLUTION, Map.of("src/Large.java", "x".repeat(100_000)),
            RepositoryType.TEMPLATE, Map.of("src/Large.java", "class Large {}"), RepositoryType.TESTS, Map.of("test/LargeTest.java", "class LargeTest {}"));

    private static final Map<RepositoryType, Map<String, String>> COMPLETE_ARTIFACTS = Map.of(RepositoryType.SOLUTION,
            Map.of("src/Graphemes.java", "class Graphemes { int count(String value) { return value.length(); } }"), RepositoryType.TEMPLATE,
            Map.of("src/Graphemes.java", "class Graphemes { int count(String value) { return 0; } }"), RepositoryType.TESTS,
            Map.of("test/GraphemesTest.java", "class GraphemesTest { void cjk() { assertEquals(2, count(\"漢字\")); } }"));

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
        if (body.startsWith("{") && body.contains("\"learningFit\"") && !body.contains("\"boundaryChecks\"")) {
            body = "{\"boundaryChecks\":[]," + body.substring(1);
        }
        return exactRawResponse(body);
    }

    private static ChatResponse exactRawResponse(String body) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(body))));
    }

    /**
     * The smallest artifact set that passes the completeness check, so a test that is not about artifact evidence does not have to spell one out. The tests repository carries the
     * reviewed test names because several assertions read them back out of the rendered reviewer prompt.
     */
    private static Map<RepositoryType, Map<String, String>> minimalArtifacts(List<String> testNames) {
        return Map.of(RepositoryType.SOLUTION, Map.of("src/ReviewFixture.java", "class ReviewFixture {}"), RepositoryType.TEMPLATE,
                Map.of("src/ReviewFixture.java", "class ReviewFixture {}"), RepositoryType.TESTS,
                Map.of("test-names.txt", testNames.isEmpty() ? "(no test names)" : String.join("\n", testNames)));
    }

    private static SpecFidelityReport critique(SpecFidelityCriticService critic, String brief, String problemStatement, List<String> testNames) {
        return critique(critic, brief, problemStatement, testNames, null);
    }

    private static SpecFidelityReport critique(SpecFidelityCriticService critic, String brief, String problemStatement, List<String> testNames,
            @Nullable Consumer<ChatResponse> usageSink) {
        return critique(critic, brief, problemStatement, testNames, minimalArtifacts(testNames), usageSink);
    }

    private static SpecFidelityReport critique(SpecFidelityCriticService critic, @Nullable String brief, @Nullable String problemStatement, List<String> testNames,
            Map<RepositoryType, Map<String, String>> artifacts, @Nullable Consumer<ChatResponse> usageSink) {
        return critic.critique(brief, problemStatement, testNames, artifacts, usageSink, () -> false, null, null, null, null);
    }

    private static SpecFidelityReport critiqueAdaptation(SpecFidelityCriticService critic, String brief, String problemStatement, List<String> testNames, String adaptationChanges,
            @Nullable Consumer<ChatResponse> usageSink) {
        return critic.critiqueAdaptation(brief, problemStatement, testNames, adaptationChanges, minimalArtifacts(testNames), usageSink, () -> false, null);
    }

    private SpecFidelityCriticService criticWithModel(ChatClient chatClient, String configuredModel) {
        return criticWithCooldown(chatClient, configuredModel, Duration.ZERO, ProviderFailureCooldown.disabled());
    }

    private SpecFidelityCriticService criticWithCooldown(ChatClient chatClient, String configuredModel, Duration providerHardFailureCooldown,
            ProviderFailureCooldown providerFailureCooldown) {
        return new SpecFidelityCriticService(chatClient, objectMapper, new HyperionPromptTemplateService(), configuredModel, providerHardFailureCooldown, providerFailureCooldown,
                128_000, (ChatOptions) null);
    }

    private SpecFidelityCriticService criticReturning(ChatResponse response) {
        return criticScripted(response).critic();
    }

    /** A critic wired to a scripted model, plus the model itself for the tests that must script consecutive responses or capture the prompts that were sent. */
    private record ScriptedCritic(SpecFidelityCriticService critic, ChatModel model) {
    }

    private ScriptedCritic criticScripted(ChatResponse first, ChatResponse... rest) {
        List<ChatResponse> responses = new ArrayList<>();
        responses.add(first);
        responses.addAll(List.of(rest));
        return criticScripted(responses);
    }

    /** An empty response list leaves {@code call} unstubbed, for the cases that must fail closed before spending a provider call. */
    private ScriptedCritic criticScripted(List<ChatResponse> responses) {
        ChatModel chatModel = mock(ChatModel.class);
        if (!responses.isEmpty()) {
            when(chatModel.call(any(Prompt.class))).thenReturn(responses.getFirst(), responses.subList(1, responses.size()).toArray(ChatResponse[]::new));
        }
        // Spring AI 2.0 merges per-request options with the model defaults, so ChatClient reads getOptions() during the request.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        return new ScriptedCritic(new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper), chatModel);
    }

    @Test
    void conceptReviewSelectsAnExactGeneratorAuthoredCandidateWithoutWritingTheDesign() {
        Map<Integer, String> candidates = conceptCandidates("Strategies multiply a score by different constants.",
                "Strategies reconcile overlapping radio fragments using different conflict policies.", "Strategies return different fixed labels.");
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"selectedCandidate":2,
                         "selectionReason":"Candidate 2 is the simplest complete fit.",
                         "evaluations":[
                          {"candidate":1,"candidateEvidenceIds":["C1.2"],"briefCoverage":"The surface requirements are present.",
                           "objectiveCounterfactual":"Only constants distinguish the interchangeable behavior.","difficultyFit":"Only scalar transcription remains after plumbing.",
                           "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.",
                           "domainGrounding":"The score theme does not cause the formula.","feasibility":"The concept is feasible but too shallow.",
                           "objectiveEssential":false,"briefCovered":true,"learningFitSufficient":false,"learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"prematureContractClosure":false,"difficultySufficient":false,"domainGrounded":false,"feasibleAndProportionate":true},
                          {"candidate":2,"candidateEvidenceIds":["C2.2"],
                           "briefCoverage":"The candidate teaches Strategy in Java with an unusual theme.","objectiveCounterfactual":"Interchangeable policies own meaningfully different conflict behavior.",
                           "difficultyFit":"Ordered overlap reconciliation requires multi-step collection processing.",
                           "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.","domainGrounding":"Conflicting fragments are inherent to damaged transmissions.",
                           "feasibility":"The bounded reconstruction behavior is deterministic and proportionate.",
                           "objectiveEssential":true,"briefCovered":true,"learningFitSufficient":true,"learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"prematureContractClosure":false,"difficultySufficient":true,"domainGrounded":true,"feasibleAndProportionate":true},
                          {"candidate":3,"candidateEvidenceIds":["C3.2"],"briefCoverage":"The surface requirements are present.",
                           "objectiveCounterfactual":"Fixed labels leave no meaningful strategy-owned behavior.","difficultyFit":"Routine return statements are below the requested level.",
                           "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.",
                           "domainGrounding":"The labels have no stated domain constraint.","feasibility":"The concept is feasible but too shallow.",
                           "objectiveEssential":false,"briefCovered":true,"learningFitSufficient":false,"learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"prematureContractClosure":false,"difficultySufficient":false,"domainGrounded":false,"feasibleAndProportionate":true}
                         ]}
                        """));
        SpecFidelityCriticService.ConceptSelectionReview review = critic.reviewConceptCandidates("Create an intermediate unusual Strategy exercise.", candidates, null,
                () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isTrue();
        assertThat(review.selectedCandidate()).isEqualTo(2);
        assertThat(review.findings()).isEmpty();
        assertThat(review.auditSummary()).contains("Selected candidate: 2", "Candidate 1", "Only scalar transcription remains after plumbing.", "Candidate 2",
                "The bounded reconstruction behavior is deterministic and proportionate.", "Candidate 3");
    }

    @Test
    void conceptReviewRejectsScalarReskinsAndRequestsPropertiesNotAReplacementDesign() {
        Map<Integer, String> candidates = conceptCandidates("Strategies multiply potion volume by different constants.",
                "Strategies multiply artifact size by different constants.", "Strategies multiply robot distance by different constants.");
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"selectedCandidate":null,
                         "selectionReason":"Every candidate is a shallow scalar reskin.",
                         "evaluations":[
                          {"candidate":1,"candidateEvidenceIds":["C1.2"],"briefCoverage":"The surface brief is covered.",
                           "objectiveCounterfactual":"All variants share one scalar formula.","difficultyFit":"Formula transcription is not intermediate.",
                                   "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.",
                           "domainGrounding":"The nouns can be replaced without changing behavior.","feasibility":"The scope is feasible but shallow.",
                           "objectiveEssential":false,"briefCovered":true,"learningFitSufficient":false,"learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"prematureContractClosure":false,"difficultySufficient":false,"domainGrounded":false,"feasibleAndProportionate":true},
                          {"candidate":2,"candidateEvidenceIds":["C2.2"],"briefCoverage":"The surface brief is covered.",
                           "objectiveCounterfactual":"All variants share one scalar formula.","difficultyFit":"Formula transcription is not intermediate.",
                                   "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.",
                           "domainGrounding":"The nouns can be replaced without changing behavior.","feasibility":"The scope is feasible but shallow.",
                           "objectiveEssential":false,"briefCovered":true,"learningFitSufficient":false,"learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"prematureContractClosure":false,"difficultySufficient":false,"domainGrounded":false,"feasibleAndProportionate":true},
                          {"candidate":3,"candidateEvidenceIds":["C3.2"],"briefCoverage":"The surface brief is covered.",
                           "objectiveCounterfactual":"All variants share one scalar formula.","difficultyFit":"Formula transcription is not intermediate.",
                                   "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.",
                           "domainGrounding":"The nouns can be replaced without changing behavior.","feasibility":"The scope is feasible but shallow.",
                           "objectiveEssential":false,"briefCovered":true,"learningFitSufficient":false,"learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"prematureContractClosure":false,"difficultySufficient":false,"domainGrounded":false,"feasibleAndProportionate":true}
                         ]}
                        """));
        SpecFidelityCriticService.ConceptSelectionReview review = critic.reviewConceptCandidates("Create an intermediate unusual Strategy exercise.", candidates, null,
                () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.selectedCandidate()).isNull();
        assertThat(review.feedback()).contains("scalar formula", "not intermediate", "nouns can be replaced").doesNotContain("radio", "logistics", "compression", "use a");
        assertThat(review.decisionSummary()).contains("learner-owned learning fit", "requested difficulty", "domain grounding").doesNotContain("potion", "artifact", "robot");
    }

    @Test
    void conceptReviewCannotSelectPrescribedLeafFormulasWithSuppliedCollaboration() {
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"selectedCandidate":null,"selectionReason":"No candidate leaves a sufficient learner-owned objective path.","evaluations":[
                 {"candidate":1,"candidateEvidenceIds":["C1.2"],"briefCoverage":"The explicit brief is covered.",
                  "objectiveCounterfactual":"Removing Strategy leaves the same two prescribed calculations in one supplied client.",
                  "difficultyFit":"Only prescribed scalar calculations remain after contract closure.",
                  "smallestStudentImplementation":"Students transcribe two calculation methods.","reasoningAfterRoutineWork":"No objective-level reasoning remains.",
                  "domainGrounding":"The names are themed but the calculations have no stated domain cause.","feasibility":"The task is bounded but too shallow.",
                  "objectiveEssential":false,"briefCovered":true,"learningFitSufficient":false,"learnerOwnsObjectiveMechanism":false,
                  "objectiveObservable":false,"prematureContractClosure":true,"difficultySufficient":false,"domainGrounded":false,"feasibleAndProportionate":true},
                 {"candidate":2,"candidateEvidenceIds":["C2.2"],"briefCoverage":"The explicit brief is covered.",
                  "objectiveCounterfactual":"The unrelated graph algorithm dominates the pattern.","difficultyFit":"The graph work is disproportionate.",
                  "smallestStudentImplementation":"Students implement graph search.","reasoningAfterRoutineWork":"Graph search remains but is unrelated.",
                  "domainGrounding":"The theme fits route planning.","feasibility":"The task is too large.",
                  "objectiveEssential":false,"briefCovered":true,"learningFitSufficient":false,"learnerOwnsObjectiveMechanism":false,
                  "objectiveObservable":false,"prematureContractClosure":false,"difficultySufficient":false,"domainGrounded":true,"feasibleAndProportionate":false},
                 {"candidate":3,"candidateEvidenceIds":["C3.2"],"briefCoverage":"The requested unusual theme is absent.",
                  "objectiveCounterfactual":"A direct conditional is equivalent.","difficultyFit":"Only a trivial conditional remains.",
                  "smallestStudentImplementation":"Students implement one conditional.","reasoningAfterRoutineWork":"No intermediate reasoning remains.",
                  "domainGrounding":"The theme is a common teaching example.","feasibility":"The task is bounded but too shallow.",
                  "objectiveEssential":false,"briefCovered":false,"learningFitSufficient":false,"learnerOwnsObjectiveMechanism":false,
                  "objectiveObservable":false,"prematureContractClosure":false,"difficultySufficient":false,"domainGrounded":false,"feasibleAndProportionate":true}
                ]}
                """));
        SpecFidelityCriticService critic = scripted.critic();

        SpecFidelityCriticService.ConceptSelectionReview review = critic.reviewConceptCandidates("Create an intermediate Strategy exercise.",
                conceptCandidates("Supplied context; learners transcribe two formulas.", "Students implement Dijkstra.", "Use payment strategies."), null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.findings().getFirst()).contains("learner ownership", "assessment path", "prematurely fixes contract details");
    }

    @Test
    void conceptReviewCorrectionCallNamesTheServerValidationFailureItMustFix() {
        // The correction call is what turns one unparseable response into a usable verdict, so it must carry the server's own diagnosis rather than silently re-ask. The concept
        // prompt's audited clauses are pinned against the rendered template in CriticPromptContractTest; the sentinel here only proves this pass renders that template.
        ScriptedCritic scripted = criticScripted(rawResponse("not json"));

        scripted.critic().reviewConceptCandidates("Create an intermediate Strategy exercise.",
                conceptCandidates("Candidate one behavior.", "Candidate two behavior.", "Candidate three behavior."), null, () -> false);

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getInstructions().get(1).getText()).contains("SERVER VALIDATION FAILURE TO CORRECT",
                "not valid JSON in the required object shape");
        assertThat(prompts.getValue().getInstructions().getFirst().getText()).contains("Evaluate EACH candidate independently");
    }

    private static Map<Integer, String> conceptCandidates(String first, String second, String third) {
        return Map.of(1, """
                ## Candidate 1
                """ + first, 2, """
                ## Candidate 2
                """ + second, 3, """
                ## Candidate 3
                """ + third);
    }

    @Test
    void specificationReview_acceptsGroundedLearningFitAndEmptyDefectVerdict() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],
                         "specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"The state-preserving reroute remains after routine strategy delegation and makes the logistics domain affect the behavior.","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate exercise about strategies in an unusual logistics theme.", """
                # Exercise
                ## Rules
                - R1: rerouting transfers the undelivered cargo without losing already delivered parcels.\
                """, null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isTrue();
        assertThat(review.findings()).isEmpty();
    }

    @Test
    void specificationReviewRejectsRelocatingARequiredOperationBoundary() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],
                         "specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"Students implement the public operation and its boundary behavior.",
                         "remainingStudentReasoning":"Students must preserve the operation boundary while implementing the behavior.","domainGrounding":"The boundary is explicitly required by the domain brief.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],
                         "boundaryChecks":[{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"publicSetup":"Attempt to construct an empty queue and then invoke call().","observedOperation":"call()","reachable":false,"timingPreserved":false,"reason":"The specification moves the required call-time outcome to construction."}],
                         "exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Calling run with an empty queue is rejected.",
                "R1: the constructor rejects an empty queue.", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.findings()).singleElement().asString().contains("Boundary reachability conflict", "call()", "moves the required call-time outcome");
    }

    @Test
    void specificationReviewCorrectsAMissingBoundaryInventory() {
        String verdict = """
                {"learningFit":{"briefEvidenceIds":["B1"],
                 "specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"Students implement the requested public operation.",
                 "remainingStudentReasoning":"Students implement the operation and its observable behavior.","domainGrounding":"The brief directly motivates the operation.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                """;
        ScriptedCritic scripted = criticScripted(exactRawResponse(verdict), rawResponse(verdict));

        SpecFidelityCriticService.SpecificationReview review = scripted.critic().reviewSpecification("Process one value.", "R1: process returns that value.", null, () -> false);

        assertThat(review.complete()).isTrue();
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions().get(1).getText()).contains("One or more mandatory finding arrays were missing");
    }

    @Test
    void specificationReview_rejectsDefectFreeVerdictWithMiscitedEvidenceId() {
        // A positive verdict must point at evidence it actually reviewed. Otherwise fluent generic prose can approve a specification without examining its objective.
        ScriptedCritic scripted = criticScripted(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],
                         "specEvidenceIds":["E1"],"objectiveEvidenceIds":["E999"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"The state-preserving reroute remains after routine strategy delegation and makes the logistics domain affect the behavior.","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate exercise about strategies in an unusual logistics theme.", """
                # Exercise
                ## Rules
                - R1: rerouting transfers the undelivered cargo without losing already delivered parcels.\
                """, null, () -> false);
        assertThat(review.complete()).isFalse();
        assertThat(review.accepted()).isFalse();
        assertThat(review.auditSummary()).contains("objectiveEvidenceIds", "known, substantive E evidence");
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void specificationReviewRejectsAbandoningTheReviewedConceptWithoutReopeningSelection() {
        String concept = "Restore ordered messages from overlapping radio fragments using conflict policies.";
        String specification = "R1: each policy returns a fixed score.";
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"The specification claims a policy collaboration.","domainGrounding":"The brief leaves the exact domain open.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C1"],
                          "specEvidenceIds":["E1"],"disposition":"SPEC_REPAIR",
                          "reason":"The specification replaced fragment reconciliation with unrelated fixed scores."},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Strategy exercise.", concept, specification, null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.conceptualReworkRequired()).isFalse();
        assertThat(review.feedback()).contains("Concept continuity", "fragment reconciliation", "do not reopen theme selection");
    }

    @Test
    void specificationReviewRepairsAShallowSpecificationWithoutDiscardingAViableConcept() {
        String concept = "Students reconcile overlapping fragments through interchangeable conflict policies.";
        String specification = "R1: each policy returns a prescribed fixed score.";
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"Only fixed-score transcription remains after routine Strategy wiring.",
                         "domainGrounding":"The specification does not connect scores to fragment conflicts.","learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"difficultySufficient":false,"domainGrounded":true,"sufficient":false,"direction":"TOO_SHALLOW"},
                         "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C1"],
                          "specEvidenceIds":["E1"],"disposition":"SPEC_REPAIR",
                          "reason":"The specification reduces the viable selected policy interaction to fixed scores."},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Strategy exercise.", concept, specification, null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.conceptualReworkRequired()).isFalse();
        assertThat(review.feedback()).contains("Only fixed-score transcription remains");
    }

    @Test
    void specificationReviewReopensSelectionOnlyWhenTheReviewedConceptItselfIsNotViable() {
        String concept = "Students return a different fixed label from each interchangeable policy.";
        String specification = "R1: each policy returns its prescribed fixed label.";
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"Only fixed-label transcription remains after routine Strategy wiring.",
                         "domainGrounding":"The labels are not caused by a domain interaction.","learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"difficultySufficient":false,"domainGrounded":true,"sufficient":false,"direction":"TOO_SHALLOW"},
                         "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C1"],
                          "specEvidenceIds":["E1"],"disposition":"CONCEPT_RESELECTION",
                          "reason":"The central interaction is routine transcription and cannot meet the requested intermediate level without replacement."},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Strategy exercise.", concept, specification, null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.conceptualReworkRequired()).isTrue();
        assertThat(review.feedback()).contains("Concept viability", "cannot meet the requested intermediate level");
    }

    @Test
    void specificationReviewBoundsMoreThanFourGroundedFindingsWithoutDiscardingTheReview() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.","remainingStudentReasoning":"The counter work is meaningful.","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[
                           {"briefEvidenceIds":["B1"],"reason":"one"},
                           {"briefEvidenceIds":["B1"],"reason":"two"}],
                         "conflicts":[],"internalConflicts":[],"exampleChecks":[],
                         "ambiguities":[],"unsupportedConstraints":[
                           {"specEvidenceIds":["E1"],"reason":"three"},
                           {"specEvidenceIds":["E1"],"reason":"four"},
                           {"specEvidenceIds":["E1"],"reason":"five"}]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create a counter.", "# Counter", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.findings()).hasSize(4);
    }

    @Test
    void specificationReviewCorrectionCanSupplyAMissingConceptAlignmentWithoutDiscardingPreservedJudgments() {
        ScriptedCritic scripted = criticScripted(
                rawResponse(
                        """
                                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                                 "remainingStudentReasoning":"Students reconcile fragments through interchangeable policies.",
                                 "domainGrounding":"Radio fragments naturally require conflict policies.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                                """),
                rawResponse(
                        """
                                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                                 "remainingStudentReasoning":"Students reconcile fragments through interchangeable policies.",
                                 "domainGrounding":"Radio fragments naturally require conflict policies.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                                 "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C1"],
                                  "specEvidenceIds":["E1"],"disposition":"ALIGNED",
                                  "reason":"The specification preserves the viable fragment-reconciliation interaction through policies."},
                                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Strategy exercise.",
                "Restore ordered messages from overlapping radio fragments using conflict policies.", "The exercise reconciles overlapping fragments.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isTrue();
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat((prompts.getAllValues().get(1).getInstructions().get(1)).getText()).contains("SERVER VALIDATION FAILURE TO CORRECT", "conceptAlignment validation failed",
                "mandatory conceptAlignment object is missing");
    }

    @Test
    void specificationReviewCorrectionExplainsThatAConceptHeadingIsNotEvidence() {
        ScriptedCritic scripted = criticScripted(
                rawResponse(
                        """
                                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"Students implement the comparison.",
                                 "remainingStudentReasoning":"Students place the exact boundary comparison.","domainGrounding":"The domain makes the boundary meaningful.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                                 "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C1"],"specEvidenceIds":["E1"],"disposition":"ALIGNED","reason":"The boundary behavior is preserved."},
                                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                                """),
                rawResponse(
                        """
                                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"Students implement the comparison.",
                                 "remainingStudentReasoning":"Students place the exact boundary comparison.","domainGrounding":"The domain makes the boundary meaningful.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                                 "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C2"],"specEvidenceIds":["E1"],"disposition":"ALIGNED","reason":"The boundary behavior is preserved."},
                                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                                """));

        SpecFidelityCriticService.SpecificationReview review = scripted.critic().reviewSpecification("Create a boundary exercise.", """
                ## Candidate 1
                Students implement the exact boundary comparison.
                """, "Students implement the exact boundary comparison.", null, () -> false);

        assertThat(review.complete()).isTrue();
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions().get(1).getText()).contains("candidate heading alone is not evidence");
    }

    @Test
    void specificationReviewAcceptsAllKnownEvidenceIdsInsteadOfTreatingCitationCountAsCorrectness() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1","E2","E3","E4","E5","E6"],"objectiveEvidenceIds":["E1","E2","E3","E4","E5","E6"],"studentOwnershipEvidenceIds":["E1","E2","E3","E4","E5","E6"],"assessmentEvidenceIds":["E1","E2","E3","E4","E5","E6"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"Only prescribed scalar adjustments remain after routine Strategy mechanics.",
                         "domainGrounding":"The themed constants do not create distinct domain policies.","learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"difficultySufficient":false,"domainGrounded":true,"sufficient":false,"direction":"TOO_SHALLOW"},
                         "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C1","C2","C3","C4"],
                          "specEvidenceIds":["E1","E2","E3","E4"],"disposition":"SPEC_REPAIR",
                          "reason":"The viable concept was collapsed to interchangeable constants."},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Strategy exercise.", """
                Candidate situation.
                Candidate constraint.
                Candidate behavior.
                Candidate interaction.\
                """, """
                Rule one.
                Rule two.
                Rule three.
                Design one.
                Design two.
                Testing seam.\
                """, null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("Learning fit", "Concept continuity");
    }

    @Test
    void specificationReviewPromptNumbersBriefAndSpecificationEvidenceSeparately() {
        // Every specification finding cites a [B*] or [E*] ID, so the two sources must reach the reviewer as separately numbered evidence rather than as one blended document.
        // The prompt's audited clauses are pinned against the rendered template in CriticPromptContractTest; the sentinel here only proves this pass renders that template.
        ScriptedCritic scripted = criticScripted(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"the cited ownership preserves the requested design work","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        scripted.critic().reviewSpecification("Students create the strategy interface.", "The design table marks it student-creates.", null, () -> false);
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model()).call(prompt.capture());
        assertThat((prompt.getValue().getInstructions().get(1)).getText()).contains("INSTRUCTOR BRIEF EVIDENCE", "[B1] Students create the strategy interface.",
                "CANDIDATE SPECIFICATION EVIDENCE", "[E1] The design table marks it student-creates.", "FINAL REPRESENTATION-DOMAIN CHECK", "NaN", "MIN_VALUE..MAX_VALUE");
        assertThat((prompt.getValue().getInstructions().getFirst()).getText()).contains("Design ownership table");
    }

    @Test
    void specificationReReviewCarriesPriorFindingsAndRequiresFreshAdjudication() {
        ScriptedCritic scripted = criticScripted(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"Students implement the boundary comparison.",
                         "remainingStudentReasoning":"Students reason about equality.","domainGrounding":"The domain requires a boundary.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "priorFindingChecks":[{"findingId":"F1","disposition":"RESOLVED","specEvidenceIds":["E1"],"reason":"The current rule now assigns equality to exactly one region."}],
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview previous = new SpecFidelityCriticService.SpecificationReview(true, false, false,
                List.of("Internal conflict — lower == upper satisfies both R2 and R4."), "prior audit", "SUFFICIENT");

        SpecFidelityCriticService.SpecificationReview review = scripted.critic().reviewSpecification("Classify numeric readings.", null,
                "R2 and R4 now claim to be mutually exclusive.", previous, null, () -> false);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model()).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions().get(1).getText()).contains("PREVIOUS SPECIFICATION REVIEW HYPOTHESES THAT CAUSED THIS REVISION",
                "[F1] Internal conflict — lower == upper satisfies both R2 and R4", "Adjudicate every F ID", "merely asserts the conflict is resolved is not evidence");
        assertThat(review.accepted()).isTrue();
        assertThat(review.auditSummary()).contains("Prior finding adjudications", "F1 RESOLVED", "current rule now assigns equality to exactly one region");
    }

    @Test
    void specificationReReviewCannotSilentlyForgetAPriorFinding() {
        String cleanVerdictWithoutAdjudication = """
                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"Students implement the boundary comparison.",
                 "remainingStudentReasoning":"Students reason about equality.","domainGrounding":"The domain requires a boundary.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                """;
        String correctedVerdict = """
                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"Students implement the boundary comparison.",
                 "remainingStudentReasoning":"Students reason about equality.","domainGrounding":"The domain requires a boundary.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                 "priorFindingChecks":[{"findingId":"F1","disposition":"STILL_PRESENT","specEvidenceIds":["E1"],"reason":"Equality still satisfies both rules."}],
                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                """;
        ScriptedCritic scripted = criticScripted(rawResponse(cleanVerdictWithoutAdjudication), rawResponse(correctedVerdict));
        String priorFinding = "Internal conflict — lower == upper satisfies both R2 and R4.";
        SpecFidelityCriticService.SpecificationReview previous = new SpecFidelityCriticService.SpecificationReview(true, List.of(priorFinding));

        SpecFidelityCriticService.SpecificationReview review = scripted.critic().reviewSpecification("Classify numeric readings.", null, "R2 and R4 still both include equality.",
                previous, null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.findings()).singleElement().asString()
                .contains("Persistent specification defect [F1]", "R2 and R4 still both include equality", "Equality still satisfies both rules")
                .doesNotContain("lower == upper satisfies both R2 and R4");
        assertThat(review.auditSummary()).contains("F1 STILL_PRESENT", "lower == upper satisfies both R2 and R4", "R2 and R4 still both include equality");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions().get(1).getText()).contains("priorFindingChecks validation failed", "mandatory when F findings were supplied");
    }

    @Test
    void specificationReview_returnsGroundedFindingsAsActionableFeedback() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E2"],"objectiveEvidenceIds":["E2"],"studentOwnershipEvidenceIds":["E2"],"assessmentEvidenceIds":["E2"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"the candidate does not preserve the requested interface work","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"difficultySufficient":false,"domainGrounded":true,"sufficient":false,"direction":"TOO_SHALLOW"},
                         "omissions":[{"briefEvidenceIds":["B1"],"reason":"the interface is supplied"}],
                         "conflicts":[],"internalConflicts":[],"exampleChecks":[],
                         "ambiguities":[],"unsupportedConstraints":[{"specEvidenceIds":["E2"],"reason":"the brief does not request a message"}]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Have students create the strategy interface.", """
                # Exercise
                The context must throw an exact message.\
                """, null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("students create the strategy interface", "throw an exact message", "choose the content yourself",
                "remove or relax only the cited unsupported obligation").doesNotContain("mark it student-owned", "remove the exact message");
    }

    @Test
    void specificationReview_rejectsAnIncorrectWorkedExampleBeforeTheContractFreezes() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E5"],"objectiveEvidenceIds":["E5"],"studentOwnershipEvidenceIds":["E5"],"assessmentEvidenceIds":["E5"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"the arithmetic rule is the requested learning work","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"ambiguities":[],"unsupportedConstraints":[],
                         "exampleChecks":[
                           {"exampleEvidenceId":"E5","replayedOutcome":"4","consistent":false,"reason":"the arithmetic evaluates to four"},
                           {"exampleEvidenceId":"E6","replayedOutcome":"6","consistent":true,"reason":"the arithmetic evaluates to six"}]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an arithmetic exercise.", """
                # Arithmetic
                ## Worked Examples
                | Rules | Input | Expected |
                |---|---|---|
                | R1 | 2 + 2 | 5 |
                | R1 | 3 + 3 | 6 |
                """, null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.coherentRewriteRequired()).isFalse();
        assertThat(review.feedback()).contains("2 + 2", "replay gives \"4\"", "evaluates to four", "correct the erroneous outcome").doesNotContain("replace 5 with 4");
    }

    @Test
    void specificationReview_rejectsMutuallyIncompatibleRulesBeforeTheContractFreezes() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"the switch policy is the relevant collaboration","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[],
                         "internalConflicts":[{"firstSpecEvidenceIds":["E1"],
                         "secondSpecEvidenceIds":["E1"],"reason":"both cannot hold for the same switch"}]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Teach the strategy pattern.",
                "Switching strategy preserves accumulated energy. Switching strategy resets accumulated energy.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("preserves accumulated energy", "resets accumulated energy", "choose one coherent interpretation grounded in the brief")
                .doesNotContain("choose and state one switch policy");
    }

    @Test
    void rejectedSpecificationReviewReturnsToTheAuthorWithoutAnAsymmetricAppeal() {
        ChatResponse proposed = rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The context invokes the selected strategy.",
                         "remainingStudentReasoning":"Students implement the allocation policy.","domainGrounding":"The potion domain motivates constrained mixing.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[],
                         "internalConflicts":[{"firstSpecEvidenceIds":["E1"],"secondSpecEvidenceIds":["E3"],
                          "reason":"R2 conflicts with R5 even though R5 is not cited."}]}
                        """);
        ScriptedCritic scripted = criticScripted(proposed);
        SpecFidelityCriticService critic = scripted.critic();

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Strategy exercise.", """
                | R2 | Total potency must equal the requested potency. |
                | R5 | Any allocation satisfying R1-R4 is acceptable. |
                | R6 | Throw when no allocation satisfies the constraints. |
                """, null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        verify(scripted.model()).call(any(Prompt.class));
    }

    @Test
    void rejectedSpecificationReviewPreservesAGroundedBlockingFindingForTheAuthor() {
        ChatResponse groundedFinding = rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The context invokes the selected strategy.",
                         "remainingStudentReasoning":"Students implement the requested policy.","domainGrounding":"No qualitative theme was requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"reason":"The brief requires preserving state but the specification resets it."}],
                         "internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """);
        ScriptedCritic scripted = criticScripted(groundedFinding);
        SpecFidelityCriticService critic = scripted.critic();

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Preserve accumulated state when strategies change.",
                "Switching strategies resets accumulated state.", null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("preserving state", "resets");
        verify(scripted.model()).call(any(Prompt.class));
    }

    @Test
    void malformedInitialSpecificationReviewStillUsesOneCorrectionCall() {
        ChatResponse proposed = rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The context invokes the selected strategy.",
                         "remainingStudentReasoning":"Students implement the requested policy.","domainGrounding":"No qualitative theme was requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[{"briefEvidenceIds":["B1"],"reason":"The requested behavior is absent."}],"conflicts":[],"internalConflicts":[],"exampleChecks":[],
                         "ambiguities":[],"unsupportedConstraints":[]}
                        """);
        ScriptedCritic scripted = criticScripted(rawResponse("not json"), proposed);
        SpecFidelityCriticService critic = scripted.critic();

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create a stateful strategy exercise.", "A context invokes a strategy.", null,
                () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void specificationReviewDerivesLearningFitFromOwnershipAndObservabilitySubchecks() {
        String contradictory = """
                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E4"],"objectiveEvidenceIds":["E4","E8"],
                 "studentOwnershipEvidenceIds":["E4"],"assessmentEvidenceIds":["E8"],
                 "objectiveMechanism":"Students implement leaf policies while the supplied context performs the collaboration.",
                 "remainingStudentReasoning":"Students transcribe two fixed calculations.","domainGrounding":"The themed calculations use domain vocabulary.",
                 "learnerOwnsObjectiveMechanism":true,"objectiveObservable":false,"difficultySufficient":false,"domainGrounded":true,
                 "sufficient":true,"direction":"SUFFICIENT"},
                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                """;
        String corrected = contradictory.replace("\"sufficient\":true,\"direction\":\"SUFFICIENT\"", "\"sufficient\":false,\"direction\":\"TOO_SHALLOW\"");
        ScriptedCritic scripted = criticScripted(rawResponse(contradictory), rawResponse(corrected));
        SpecFidelityCriticService critic = scripted.critic();

        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Strategy exercise.", """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `FireStrategy` | prescribed calculation | student-creates |
                ## Testing Strategy
                | ID | Owner type | Observable responsibility | Weight | Hidden after-due-date |
                |---|---|---|---|---|
                | S1 | FireStrategy | calculation only | 3 | no |
                """, null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.learningFitDirection()).isEqualTo("TOO_SHALLOW");
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void specificationReview_rejectsUndefinedProgressBeforeTheContractFreezes() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"students implement the repeated selection policy","domainGrounding":"The robot theme motivates choosing affordable moves.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"unsupportedConstraints":[],
                         "ambiguities":[{"specEvidenceIds":["E1"],"reason":"zero-cost moves permit another iteration without consuming energy, so progress and termination are undefined"}]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Teach the strategy pattern.",
                "R1: repeatedly choose any affordable move until no energy remains.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("Ambiguous contract", "progress and termination are undefined", "finite, and testable behavior");
    }

    @Test
    void specificationReviewResolvesMarkdownSourceLinesByEvidenceId() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"the quoted rule is the strategy behaviour","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[],
                         "internalConflicts":[{"firstSpecEvidenceIds":["E1"],
                         "secondSpecEvidenceIds":["E1"],"reason":"the arithmetic conflicts"}]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Teach the strategy pattern.",
                "**Healing potency** is `two` per herb. **Healing potency** is four for six herbs.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("the arithmetic conflicts", "choose one coherent interpretation grounded in the brief").doesNotContain("correct the worked example");
    }

    @Test
    void specificationReviewResolvesHtmlWhitespaceSourceLinesByEvidenceId() {
        String specification = """
                R2: A concrete strategy adjusts the drink as follows:
                &nbsp;&nbsp;• **SpicyFlavorStrategy** adds **3** to `flavorScore`.
                """;
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],
                         "specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"Only the prescribed increment remains after routine wiring.",
                         "domainGrounding":"The cited rule does not explain why spiciness changes this score.","learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"difficultySufficient":false,"domainGrounded":true,"sufficient":false,"direction":"TOO_SHALLOW"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Strategy exercise.", specification, null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("Only the prescribed increment remains");
    }

    @Test
    void specificationReview_distinguishesMalformedFromUngroundedVerdicts() {
        // A malformed verdict and a fluent but ungrounded verdict are both incomplete. Neither may freeze the generation contract.
        SpecFidelityCriticService malformedCritic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.","remainingStudentReasoning":"the counter work is meaningful","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        assertThat(malformedCritic.reviewSpecification("Create a counter.", "# Counter", null, () -> false).complete()).isFalse();

        SpecFidelityCriticService ungroundedCritic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E99"],"objectiveEvidenceIds":["E99"],"studentOwnershipEvidenceIds":["E99"],"assessmentEvidenceIds":["E99"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.","remainingStudentReasoning":"the counter work is meaningful","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview ungrounded = ungroundedCritic.reviewSpecification("Create a Java counter.", "# Counter", null, () -> false);
        assertThat(ungrounded.complete()).isFalse();
        assertThat(ungrounded.accepted()).isFalse();

        SpecFidelityCriticService groundedFindingCritic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.","remainingStudentReasoning":"the counter work is meaningful","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[{"briefEvidenceIds":["B1"],"reason":"the learning objective is missing"}],
                         "conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview groundedFinding = groundedFindingCritic.reviewSpecification("Create a Java counter.", "# Counter", null, () -> false);
        assertThat(groundedFinding.complete()).isTrue();
        assertThat(groundedFinding.accepted()).isFalse();
    }

    @Test
    void specificationReview_rejectsPositiveLearningFitWithoutItsMandatoryDirection() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"Students resolve the requested state transition.",
                         "domainGrounding":"No qualitative theme was requested.","sufficient":true},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        assertThat(critic.reviewSpecification("Create a state transition exercise.", "R1: resolve the state transition.", null, () -> false).complete()).isFalse();
    }

    @Test
    void specificationReview_requiresTheRequestedObjectiveMechanismProse() {
        // Evidence-ID citation is advisory, but the learningFit's own prose is mandatory: this fixture omits objectiveMechanism entirely,
        // so the verdict is discarded as incomplete.
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],
                         "remainingStudentReasoning":"Students implement three policy algorithms.","domainGrounding":"The policies follow the domain.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Teach the Strategy pattern.", "R1: each policy implements a calculation.", null,
                () -> false);
        assertThat(review.complete()).isFalse();
        assertThat(review.auditSummary()).contains("learningFit validation failed", "objectiveMechanism is mandatory");
    }

    @Test
    void specificationReviewDoesNotAcceptAGenericReasonWithoutBothRequiredAnalyses() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "reason":"The theme and strategy types satisfy the brief.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        assertThat(critic.reviewSpecification("Create an intermediate strategy exercise.", "R1: compute a themed value.", null, () -> false).complete()).isFalse();
    }

    @Test
    void specificationReview_rejectsPositiveLearningVerdictWithoutGroundedEvidenceIds() {
        // Booleans and plausible prose are self-reports. The reviewer must cite evidence from this prompt before its positive verdict can freeze the SPEC.
        ScriptedCritic scripted = criticScripted(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E99"],"objectiveEvidenceIds":["E99"],"studentOwnershipEvidenceIds":["E99"],"assessmentEvidenceIds":["E99"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"the invented rule supplies depth","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate exercise.", "R1: meaningful domain interaction", null,
                () -> false);
        assertThat(review.complete()).isFalse();
        assertThat(review.accepted()).isFalse();
        assertThat(review.auditSummary()).contains("known, substantive E evidence");
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void specificationReviewToleratesAnUnknownEvidenceIdWithoutRetrying() {
        // An unknown evidence ID (E99) inside an otherwise coherent finding is advisory: the pointer resolves to an empty quote, the finding
        // still renders, and the verdict is complete after a single call with no correction retry.
        ScriptedCritic scripted = criticScripted(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"Students must reason about a limiting ingredient after routine delegation is removed.",
                         "domainGrounding":"A potion is constrained by its weakest ingredient, which motivates the rule.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                         "omissions":[],"conflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[],
                         "internalConflicts":[{"firstSpecEvidenceIds":["E1"],"secondSpecEvidenceIds":["E99"],
                         "reason":"The claims conflict."}]}
                        """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate strategy exercise with a potion theme.",
                "R1: weak ingredients limit potency. R2: every ingredient contributes equally.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.feedback()).contains("R1: weak ingredients limit potency.", "The claims conflict.");
        verify(scripted.model(), times(1)).call(any(Prompt.class));
    }

    @Test
    void specificationReviewUsesTheCompleteFreshRetryVerdict() {
        // The first verdict is genuinely incomplete (its learningFit omits the mandatory direction), so the reviewer makes its one
        // correction call. The fresh, complete ALIGNED retry is used verbatim rather than the discarded first verdict.
        ScriptedCritic scripted = criticScripted(
                rawResponse(
                        """
                                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                                 "remainingStudentReasoning":"The specification claims a policy collaboration.","domainGrounding":"No qualitative theme was requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true},
                                 "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C1"],"specEvidenceIds":["E1"],
                                  "disposition":"CONCEPT_RESELECTION",
                                  "reason":"The selected interaction cannot meet the requested level without replacement."},
                                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                                """),
                rawResponse(
                        """
                                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                                 "remainingStudentReasoning":"The specification claims a policy collaboration.","domainGrounding":"No qualitative theme was requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                                 "conceptAlignment":{"briefEvidenceIds":["B1"],"conceptEvidenceIds":["C1"],"specEvidenceIds":["E1"],
                                  "disposition":"ALIGNED",
                                  "reason":"The specification now preserves the viable selected interaction."},
                                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate strategy exercise.",
                "Restore ordered messages from overlapping fragments.", "R1: each policy returns a fixed score.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isTrue();
        assertThat(review.conceptualReworkRequired()).isFalse();
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void specificationReviewDoesNotCarryAnUngroundedFirstFindingIntoTheFreshRetry() {
        // The first verdict is genuinely incomplete (its learningFit omits the mandatory direction), so it is discarded before its finding
        // is ever rendered. The single correction call returns a clean, complete verdict, and none of the first response's finding leaks in.
        ScriptedCritic scripted = criticScripted(
                rawResponse(
                        """
                                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                                 "remainingStudentReasoning":"Students choose a meaningful strategy interaction.","domainGrounding":"No qualitative theme was requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true},
                                 "omissions":[],"conflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[],
                                 "internalConflicts":[{"firstSpecEvidenceIds":["E1"],"secondSpecEvidenceIds":["E99"],
                                 "reason":"The decisions conflict."}]}
                                """),
                rawResponse(
                        """
                                {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                                 "remainingStudentReasoning":"Students choose a meaningful strategy interaction.","domainGrounding":"No qualitative theme was requested.","learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate strategy exercise.",
                "R1: meaningful strategy decision. R2: contradicting strategy decision.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isTrue();
        assertThat(review.feedback()).doesNotContain("The decisions conflict");
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void specificationReview_turnsInsufficientLearningEvidenceIntoOneFocusedRepair() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"after subtracting formula transcription and routine delegation, no intermediate domain decision remains","domainGrounding":"The cited behavior is plausibly motivated by the requested domain, or no qualitative theme is requested.","learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"difficultySufficient":false,"domainGrounded":true,"sufficient":false,"direction":"TOO_SHALLOW"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Create an intermediate Java exercise.",
                "Fireball computes basePower * 2 + 10. SpellCaster delegates to the current strategy.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.coherentRewriteRequired()).isTrue();
        assertThat(review.findings()).singleElement().asString()
                .contains("Learning fit", "no intermediate domain decision remains", "restore or deepen", "all affected rules, examples, ownership, and testing seams together",
                        "Deepen the requested concept's interaction", "incidental mathematics or collection work cannot rescue learning fit", "Do not manufacture difficulty")
                .doesNotContain("simplifying", "give the core behavior to supplied scaffolding");
    }

    @Test
    void specificationReviewPreservesCentralReasoningWhenSupportingWorkIsTooComplex() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                """
                        {"learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],"studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],"objectiveMechanism":"The cited student work exercises the requested objective through an observable collaboration.",
                         "remainingStudentReasoning":"The repeated supporting engine dominates the requested abstraction.",
                         "domainGrounding":"The domain interaction itself remains coherent.","learnerOwnsObjectiveMechanism":false,"objectiveObservable":false,"difficultySufficient":false,"domainGrounded":true,"sufficient":false,"direction":"TOO_COMPLEX"},
                         "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[]}
                        """));
        SpecFidelityCriticService.SpecificationReview review = critic.reviewSpecification("Teach an intermediate abstraction.",
                "Every interchangeable implementation repeats the same substantial supporting algorithm.", null, () -> false);
        assertThat(review.complete()).isTrue();
        assertThat(review.feedback()).contains("preserve", "central learner-owned reasoning", "factoring genuinely shared work once", "Do not give the core behavior")
                .doesNotContain("restore or deepen", "CONCEPT_RESELECTION");
    }

    @Test
    void supportedSecretInGeneratedCandidatePreventsEveryCriticProviderCall() {
        ScriptedCritic scripted = criticScripted(List.of());
        SpecFidelityCriticService critic = scripted.critic();
        Map<RepositoryType, Map<String, String>> artifacts = Map.of(RepositoryType.SOLUTION, Map.of("src/Fixture.java", "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij"),
                RepositoryType.TEMPLATE, Map.of("src/Fixture.java", "class Fixture {}"), RepositoryType.TESTS, Map.of("test/FixtureTest.java", "class FixtureTest {}"));
        assertThatExceptionOfType(HyperionSecretMaterialPolicy.SecretMaterialException.class)
                .isThrownBy(() -> critique(critic, "brief", "# Problem", List.of("fixture"), artifacts, null)).withMessageContaining("GITHUB_TOKEN")
                .withMessageNotContaining("ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij");
        verify(scripted.model(), never()).call(any(Prompt.class));
    }

    @Test
    void blockingContractReviewMapsAcceptanceBlockersAndIncludesExecutableEvidence() {
        ScriptedCritic scripted = criticScripted(jsonResponse("""
                {"exampleChecks": [{"claim": "the rover ends at (2,3) E", "computedOutcome": "the rover ends at (2,2) N", "consistent": false,
                    "reason": "replaying the command sequence gives a different state"}],
                 "apiChecks": [{"symbol": "Rover(int,int,Collection<int[]>)", "discoverable": false, "reason": "tests require it while the statement leaves the API open"}],
                 "templateChecks": [{"ownerType":"FixtureType","test": "turnsLeft", "targetReached": false, "reason": "the constructor throws before the turn assertion",
                     "evidenceQuote":"class Graphemes"}],
                 "mutantChecks": [{"mutant": "reject CJK characters", "killed": false, "sourceQuote": "CJK characters", "reason": "no assertion exercises CJK input"}],
                 "uncovered": [{"requirement": "CJK characters", "sourceQuote": "CJK characters", "reason": "no assertion exercises CJK input"}],
                 "contradictions": [], "hiddenRequirements": [], "weakOracle": [], "templateGaps": [],
                 "missingExamples": [], "invented": [], "unrequestedChanges": [], "missingRequestedChanges": []}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"reject CJK characters","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                """
                        # Rover
                        Worked example: the rover ends at (2,3) E\
                        """, List.of("turnsLeft"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(Kind.CONTRACT_CONTRADICTION, Kind.HIDDEN_GRADED_REQUIREMENT,
                Kind.TEMPLATE_QUALITY_GAP);
        assertThat(report.hasBlockingFindings()).isTrue();
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues())
                .allSatisfy((value) -> assertThat(value.getContents()).contains("SOLUTION: src/Graphemes.java", "TEMPLATE: src/Graphemes.java", "TESTS: test/GraphemesTest.java")
                        .contains("assertEquals(2, count(\"漢字\"))").contains("Do not treat test names or comments as proof"));
    }

    @Test
    void templateQualityGapReviewSurfacesMissingTeachingScaffoldWithQuotedEvidence() {
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[],
                 "apiChecks":[],
                 "templateChecks":[{"ownerType":"FixtureType","test":"count","targetReached":false,
                     "reason":"the template's count(String value) method has no doc comment",
                     "evidenceQuote":"int count(String value) { return 0; }"}],
                 "contradictions":[],
                 "hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return 0","killed":true,"reason":"the cjk assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(Kind.TEMPLATE_QUALITY_GAP);
        assertThat(report.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> {
            assertThat(finding.requirement()).isEqualTo("count");
            assertThat(finding.detail()).contains("count(String value)", "has no doc comment");
        });
        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void contractReviewCannotDemandAStubForAStudentCreatedType() {
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[],"apiChecks":[{"symbol":"FireStrategy","discoverable":true,"reason":"the statement describes it"}],
                 "templateChecks":[{"ownerType":"FireStrategy","test":"missing FireStrategy stub","targetReached":false,
                     "reason":"the template has no FireStrategy class or TODO","evidenceQuote":"class Graphemes"}],
                 "contradictions":[],"hiddenRequirements":[],"missingExamples":[],"invented":[],"unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return 0","killed":true,"reason":"the assertion kills it"}],"uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();

        SpecFidelityReport report = critic.critique("Create a Strategy exercise.", "Create FireStrategy.", List.of("fire"), COMPLETE_ARTIFACTS, null, () -> false, null, """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `FireStrategy` | concrete policy | student-creates |
                """, null, null);

        assertThat(report.findings()).isEmpty();
    }

    @Test
    void contractReviewTemplateGapAgainstATypeTheDesignNeverNamesFailsClosed() {
        // Once the approved Design fixes who owns which type, a scaffold complaint about some other type means the reviewer was not reading that contract. Surfacing it would
        // send the repair loop after a type the design does not have, so the whole verdict is discarded instead.
        ScriptedCritic scripted = criticScripted(jsonResponse(
                "{\"templateChecks\":[{\"ownerType\":\"WaterStrategy\",\"test\":\"missing WaterStrategy stub\",\"targetReached\":false,\"reason\":\"the template has no WaterStrategy class or TODO\"}]}"),
                rawResponse(COMPLETE_ORACLE_VERDICT));

        SpecFidelityReport report = scripted.critic().critique("Create a Strategy exercise.", "Create FireStrategy.", List.of("fire"), COMPLETE_ARTIFACTS, null, () -> false, null,
                DESIGN_WITH_STUDENT_CREATED_FIRE_STRATEGY, null, null);

        assertThat(report.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> {
            assertThat(finding.kind()).isEqualTo(Kind.QUALITY_REVIEW_UNAVAILABLE);
            assertThat(finding.detail()).contains("contract reviewer");
        });
    }

    @Test
    void contractReviewTemplateGapAgainstSharedScaffoldIsReportedWithoutADesignRow() {
        // Build files, fixtures, and other shared scaffold are real and repairable but deliberately absent from the Design ownership table, so the reserved owner name must pass
        // the guard that discards a complaint about a type the design never names.
        ScriptedCritic scripted = criticScripted(jsonResponse(
                "{\"templateChecks\":[{\"ownerType\":\"shared scaffold\",\"test\":\"buildsBeforeAnyTask\",\"targetReached\":false,\"reason\":\"the shared fixture fails to compile before any student-owned code runs\",\"evidenceQuote\":\"class Graphemes\"}]}"),
                rawResponse(COMPLETE_ORACLE_VERDICT));

        SpecFidelityReport report = scripted.critic().critique("Create a Strategy exercise.", "Create FireStrategy.", List.of("fire"), COMPLETE_ARTIFACTS, null, () -> false, null,
                DESIGN_WITH_STUDENT_CREATED_FIRE_STRATEGY, null, null);

        assertThat(report.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> {
            assertThat(finding.kind()).isEqualTo(Kind.TEMPLATE_QUALITY_GAP);
            assertThat(finding.requirement()).isEqualTo("buildsBeforeAnyTask");
        });
    }

    @Test
    void contractReviewAbstainsOnTemplateGapsWithoutArtifactEvidence() {
        ScriptedCritic scripted = criticScripted(jsonResponse(
                "{\"templateChecks\":[{\"ownerType\":\"FixtureType\",\"test\":\"imagined prerequisite\",\"targetReached\":false,\"reason\":\"another task blocks it\",\"evidenceQuote\":\"a line that is not in any artifact\"}]}"),
                rawResponse(COMPLETE_ORACLE_VERDICT));

        SpecFidelityReport report = critique(scripted.critic(), "Create an exercise.", "Implement the fixture.", List.of("fixture"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).isEmpty();
    }

    @Test
    void eachFullArtifactReviewPassRendersItsOwnSpecializedSystemPrompt() {
        // The two passes are specialized only as long as each receives its own system prompt in its own call. Their audited clauses, and the cross-pass vocabulary neither may
        // name, are pinned against the rendered templates in CriticPromptContractTest; the two sentinels here prove the routing that no resource assertion can.
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[],
                 "apiChecks":[],
                 "templateChecks":[{"ownerType":"FixtureType","test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],
                 "hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":false,"sourceQuote":"user-perceived characters","reason":"no assertion uses a surrogate pair"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityReport report = critique(scripted.critic(), UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactly(Kind.WEAK_TEST_ORACLE);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat((prompts.getAllValues().get(0).getInstructions().getFirst()).getText()).contains("house teaching scaffold");
        assertThat((prompts.getAllValues().get(1).getInstructions().getFirst()).getText()).contains("at most six highest-risk representative mutants");
    }

    /** An approved Design whose ownership table names exactly one type, so a scaffold complaint about any other owner is unattributable. */
    private static final String DESIGN_WITH_STUDENT_CREATED_FIRE_STRATEGY = """
            ## Design
            | Type | Role | Template status |
            |---|---|---|
            | `FireStrategy` | concrete policy | student-creates |
            """;

    /** A contract verdict answering every mandatory array, so a row can make exactly one array malformed. */
    private static final String COMPLETE_CONTRACT_VERDICT = """
            {"exampleChecks":[],"apiChecks":[],
             "templateChecks":[{"ownerType":"FixtureType","test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
             "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
             "unrequestedChanges":[],"missingRequestedChanges":[]}
            """;

    /** An oracle verdict answering every mandatory array, so a row can make exactly one array malformed. */
    private static final String COMPLETE_ORACLE_VERDICT = """
            {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":true,"reason":"the assertion kills it"}],
             "uncovered":[],"weakOracle":[]}
            """;

    /**
     * One incomplete review input and the fail-closed verdict it must produce.
     * <p>
     * Each row kills a different conjunct of {@code CriticVerdictParser}'s malformed-verdict predicates — sixteen for the contract pass, seven for the oracle pass — or a
     * different pre-flight guard in {@code SpecFidelityCriticService#reviewArtifacts}. They share one table because they make one claim: an incomplete review is never reported
     * as a clean one. They stay separate rows because no row implies another.
     *
     * @param responses the scripted reviewer responses; {@code null} means no AI provider is configured and an empty list means the provider must never be called
     */
    private record FailClosedCase(String label, @Nullable List<ChatResponse> responses, boolean cancelled, Map<RepositoryType, Map<String, String>> artifacts, String brief,
            String statement, @Nullable String adaptationChanges, Kind expectedKind, String expectedDetailFragment, int expectedFindingCount, int expectedModelCalls) {

        @Override
        public String toString() {
            return label;
        }
    }

    private static FailClosedCase generation(String label, @Nullable List<ChatResponse> responses, String detailFragment, int findingCount, int modelCalls) {
        return generation(label, responses, COMPLETE_ARTIFACTS, UNICODE_BRIEF, detailFragment, findingCount, modelCalls);
    }

    private static FailClosedCase generation(String label, @Nullable List<ChatResponse> responses, Map<RepositoryType, Map<String, String>> artifacts, String brief,
            String detailFragment, int findingCount, int modelCalls) {
        return new FailClosedCase(label, responses, false, artifacts, brief, "Count graphemes.", null, Kind.QUALITY_REVIEW_UNAVAILABLE, detailFragment, findingCount, modelCalls);
    }

    private static FailClosedCase adaptation(String label, @Nullable List<ChatResponse> responses, String detailFragment, int findingCount, int modelCalls) {
        return new FailClosedCase(label, responses, false, minimalArtifacts(List.of("test_x")), "Change remove(0).", "# Inventory", "- old\n+ new",
                Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE, detailFragment, findingCount, modelCalls);
    }

    private static Stream<FailClosedCase> failClosedCases() {
        return Stream.of(
                // --- Contract-pass verdict integrity: one mandatory array malformed at a time, with a complete oracle verdict alongside.
                generation("contract verdict omits contradictions",
                        List.of(rawResponse(COMPLETE_CONTRACT_VERDICT.replace("\"contradictions\":[],", "")), rawResponse(COMPLETE_ORACLE_VERDICT)), "contract reviewer", 1, 2),
                generation("contract invented entry omits its source quote",
                        List.of(jsonResponse("{\"invented\":[{\"requirement\":\"O(1) extra space\",\"reason\":\"the brief never constrains space complexity\"}]}")),
                        "contract reviewer", 1, 2),
                generation("contract invented entry is empty", List.of(jsonResponse("{\"invented\":[{}]}")), "contract reviewer", 1, 2),
                generation("contract contradiction entry is empty", List.of(jsonResponse("{\"contradictions\":[{}]}")), "contract reviewer", 1, 2),
                generation("contract contradiction entry omits its reason", List.of(jsonResponse("{\"contradictions\":[{\"requirement\":\"conflict\"}]}")), "contract reviewer", 1,
                        2),
                // A solution declaring a public member makes apiChecks mandatory, so an empty array is a skipped check rather than a clean one.
                generation("contract api checks are empty although the solution declares a public member",
                        List.of(rawResponse(COMPLETE_CONTRACT_VERDICT), rawResponse(COMPLETE_ORACLE_VERDICT)), PUBLIC_API_ARTIFACTS, UNICODE_BRIEF, "contract reviewer", 1, 2),

                // --- Oracle-pass verdict integrity, with a complete contract verdict alongside.
                generation("oracle verdict omits weakOracle",
                        List.of(rawResponse(COMPLETE_CONTRACT_VERDICT), rawResponse(COMPLETE_ORACLE_VERDICT.replace("\"weakOracle\":[]", ""))), "test-oracle reviewer", 1, 3),
                generation("oracle verdict omits mutantChecks", List.of(rawResponse(COMPLETE_CONTRACT_VERDICT), rawResponse("{\"uncovered\":[],\"weakOracle\":[]}")),
                        "test-oracle reviewer", 1, 3),

                // --- Responses neither pass can parse: both passes report separately, so neither is silently clean.
                generation("response is brace-delimited but unparseable", List.of(rawResponse("{ this is not valid json }")), "contract reviewer", 2, 3),
                generation("response is prose with no JSON at all", List.of(rawResponse("I think the tests look fine to me, no JSON here at all.")), "contract reviewer", 2, 3),
                generation("response body is empty", List.of(rawResponse("")), "contract reviewer", 2, 3),
                generation("response answers only the oracle's uncovered array", List.of(rawResponse("{\"uncovered\":[]}")), "test-oracle reviewer", 2, 3),

                // --- Pre-flight guards: the review must fail closed before spending a provider call.
                generation("no AI provider is configured", null, "No AI reviewer is configured.", 1, 0),
                new FailClosedCase("cancelled before the first reviewer call", List.of(), true, COMPLETE_ARTIFACTS, UNICODE_BRIEF, "Count graphemes.", null,
                        Kind.QUALITY_REVIEW_UNAVAILABLE, "cancelled before both review passes completed", 1, 0),
                // The artifact set is deliberately complete, so this row reaches the evidence cap instead of stopping at the completeness guard ahead of it.
                generation("artifact evidence exceeds its bounded size", List.of(), OVERSIZED_ARTIFACTS, "Create an exercise.", "exceeded the bounded review input", 1, 0),
                generation("artifact set is missing a repository", List.of(),
                        Map.of(RepositoryType.SOLUTION, Map.of("src/Exercise.java", "class Exercise {}"), RepositoryType.TESTS, Map.of("test/ExerciseTest.java", "class T {}")),
                        "Create an exercise.", "snapshot was missing", 1, 0),
                generation("complete review prompt exceeds its bounded size", List.of(), COMPLETE_ARTIFACTS, "x".repeat(120_000), "exceeded its bounded size", 1, 0),

                // --- The same guarantees on the adaptation path, where the finding must name adaptation scope rather than exercise quality.
                adaptation("adaptation verdict is not JSON", List.of(rawResponse("not json")), "contract reviewer", 2, 3),
                adaptation("adaptation verdict omits the scope arrays", List.of(rawResponse("{\"uncovered\":[]}")), "contract reviewer", 2, 3),
                adaptation("adaptation unrequested change entry omits its change",
                        List.of(jsonResponse("{\"unrequestedChanges\":[{\"reason\":\"missing change\"}],\"missingRequestedChanges\":[]}")), "contract reviewer", 1, 2),
                adaptation("no AI provider is configured for the adaptation review", null, "No AI reviewer is configured.", 1, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failClosedCases")
    void incompleteReviewFailsClosedWithoutReportingACleanVerdict(FailClosedCase testCase) {
        ScriptedCritic scripted = testCase.responses() == null ? null : criticScripted(testCase.responses());
        SpecFidelityCriticService critic = scripted == null ? new SpecFidelityCriticService(null, objectMapper) : scripted.critic();
        List<String> testNames = List.of("cjk");

        SpecFidelityReport report = testCase.adaptationChanges() == null
                ? critic.critique(testCase.brief(), testCase.statement(), testNames, testCase.artifacts(), null, testCase::cancelled, null, null, null, null)
                : critic.critiqueAdaptation(testCase.brief(), testCase.statement(), testNames, testCase.adaptationChanges(), testCase.artifacts(), null, testCase::cancelled, null);

        assertThat(report.findings()).as("%s must fail closed", testCase).hasSize(testCase.expectedFindingCount())
                .allSatisfy(finding -> assertThat(finding.kind()).isEqualTo(testCase.expectedKind()))
                .anySatisfy(finding -> assertThat(finding.detail()).contains(testCase.expectedDetailFragment()));
        assertThat(report.hasBlockingFindings()).as("%s must block persistence", testCase).isTrue();
        if (scripted != null) {
            verify(scripted.model(), times(testCase.expectedModelCalls())).call(any(Prompt.class));
        }
    }

    @Test
    void blockingContractFindingIsMergedWithIndependentOracleFinding() {
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[{"claim":"member 101 has two checkouts","computedOutcome":"member 101 has one checkout","consistent":false,
                    "reason":"the early return is ignored"}],
                 "apiChecks":[],"templateChecks":[{"ownerType":"FixtureType","test":"example","targetReached":true,"reason":"the assertion reaches the target"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"ignore CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Worked example: member 101 has two checkouts", List.of("example"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactlyInAnyOrder(Kind.CONTRACT_CONTRADICTION, Kind.WEAK_TEST_ORACLE);
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void unavailableContractPassDoesNotSuppressOracleFinding() {
        ScriptedCritic scripted = criticScripted(rawResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":false,"sourceQuote":"user-perceived characters","reason":"no assertion uses a surrogate pair"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactlyInAnyOrder(Kind.QUALITY_REVIEW_UNAVAILABLE, Kind.WEAK_TEST_ORACLE);
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void uncoveredCjkAndEmoji_areFlagged() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[{\"requirement\":\"CJK characters\",\"sourceQuote\":\"CJK characters\",\"reason\":\"no CJK test\"},{\"requirement\":\"emoji\",\"sourceQuote\":\"emoji\",\"reason\":\"no emoji test\"}]}"));
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("test_ascii_only", "test_cafe_precomposed"));
        assertThat(report.findings()).hasSize(2).allMatch((SpecFidelityReport.Finding finding) -> finding.kind() == Kind.UNCOVERED_REQUIREMENT);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactlyInAnyOrder("CJK characters", "emoji");
    }

    @Test
    void oracleReviewCorrectsUngroundedClaimsFromEveryFindingArray() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
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
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> {
            assertThat(finding.kind()).isEqualTo(Kind.UNCOVERED_REQUIREMENT);
            assertThat(finding.requirement()).isEqualTo("CJK characters");
        });
        verify(scripted.model(), times(3)).call(any(Prompt.class));
    }

    @Test
    void producedStatementCannotAuthorizeAnOracleFinding() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"treat equality as not overdue","killed":false,"sourceQuote":"equal to or after dueAt",
                    "reason":"no equality assertion distinguishes the mutant"}],
                 "uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"ignore penalty records","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic, "Create a Java exercise about calculating member penalties from checkout records; choose coherent API and business rules.",
                "A checkout is overdue when returnedAt is equal to or after dueAt. Add one penalty point for every overdue checkout.", List.of("countsDueDateEquality"),
                COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).isEmpty();
        verify(scripted.model(), times(3)).call(any(Prompt.class));
    }

    @Test
    void oracleReviewPreservesGroundedRepairFeedbackWhenAnotherClaimIsUngrounded() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[
                    {"mutant":"reject CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"},
                    {"mutant":"require sorted output","killed":false,"sourceQuote":"results must be sorted","reason":"only the generated statement says this"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes and return sorted diagnostics.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactlyInAnyOrder(Kind.WEAK_TEST_ORACLE, Kind.QUALITY_REVIEW_UNAVAILABLE);
        assertThat(report.findings()).anySatisfy((SpecFidelityReport.Finding finding) -> {
            assertThat(finding.kind()).isEqualTo(Kind.WEAK_TEST_ORACLE);
            assertThat(finding.requirement()).isEqualTo("reject CJK input");
        });
    }

    @Test
    void oracleReviewRetriesAnUngroundedVerdictAndUsesTheCompleteCorrection() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[
                    {"mutant":"reject CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"},
                    {"mutant":"require sorted output","killed":false,"sourceQuote":"results must be sorted","reason":"only the generated statement says this"}],
                 "uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"ignore emoji input","killed":false,"sourceQuote":"P1",
                    "reason":"no assertion uses emoji input"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes and return sorted diagnostics.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactly("ignore emoji input");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(3)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getContents()).contains("PRIMARY SOURCE EVIDENCE IDS FOR ORACLE ONLY",
                "[P1] Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.");
        assertThat(prompts.getAllValues().getLast().getContents()).contains("previous verdict was incomplete, malformed, or cited an unknown PRIMARY SOURCE EVIDENCE ID",
                "INSTRUCTOR BRIEF", "PRODUCED PROBLEM STATEMENT");
    }

    @Test
    void oracleReviewUsesApprovedDesignOwnershipAndDropsGivenSupportFindings() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"dispatcher accepts an empty list","killed":false,"sourceQuote":"empty list",
                    "ownerType":"Dispatcher","reason":"no graded assertion rejects it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        String specification = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Dispatcher` | provided orchestration | given |
                | `Strategy` | selected behavior | student-creates |
                ## Rules
                | ID | Rule |
                |---|---|
                | R1 | The dispatcher rejects an empty list. |
                """;

        SpecFidelityReport report = scripted.critic().critique("Create a strategy exercise with an empty list rule.", "Implement both strategies.", List.of("selects"),
                COMPLETE_ARTIFACTS, null, () -> false, null, specification, null, null);

        assertThat(report.findings()).isEmpty();
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void oracleReviewRetriesMissingOwnershipAndAcceptsCorrectedStudentOwner() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"strategy ignores CJK characters","killed":false,"sourceQuote":"CJK characters",
                    "reason":"the test uses ASCII only"}],
                 "uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"strategy ignores CJK characters","killed":false,"sourceQuote":"CJK characters",
                    "ownerType":"Graphemes","reason":"the test uses ASCII only"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        String specification = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Graphemes` | counts graphemes | stubbed |
                ## Rules
                | ID | Rule |
                |---|---|
                | R1 | Count CJK characters as user-perceived characters. |
                """;

        SpecFidelityReport report = scripted.critic().critique("Support CJK characters.", "Count user-perceived characters.", List.of("countsCjk"), COMPLETE_ARTIFACTS, null,
                () -> false, null, specification, null, null);

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.WEAK_TEST_ORACLE);
            assertThat(finding.requirement()).isEqualTo("strategy ignores CJK characters");
        });
        verify(scripted.model(), times(3)).call(any(Prompt.class));
    }

    @Test
    void oracleCorrectionMustStillShowThatItReviewedAnExecutableTest() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"reject zero fuel","killed":false,
                    "sourceQuote":"R6 | fuel-consuming strategies reject zero fuel","reason":"no assertion covers it"}],
                 "uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[],"uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic, "Create a spacecraft navigation exercise.", "Use strategy objects.", List.of("navigates"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(Kind.QUALITY_REVIEW_UNAVAILABLE));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(3)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getContents()).contains("mutantChecks must still contain at least one applicable passing or failing check");
    }

    @Test
    void oracleReviewRetriesAnEmptyMutantAuditAndUsesTheCompleteCorrection() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[],"uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"always return zero","killed":true,"reason":"navigates asserts the computed distance"}],
                 "uncovered":[],"weakOracle":[]}
                """));

        SpecFidelityReport report = critique(scripted.critic(), "Create a spacecraft navigation exercise.", "Use strategy objects.", List.of("navigates"), COMPLETE_ARTIFACTS,
                null);

        assertThat(report.findings()).isEmpty();
        verify(scripted.model(), times(3)).call(any(Prompt.class));
    }

    @Test
    void criticLlmCall_reportsTokenUsageToTheSink() {
        ChatResponse response = jsonResponse("{\"uncovered\":[]}");
        SpecFidelityCriticService critic = criticReturning(response);
        List<ChatResponse> tracked = new ArrayList<>();
        critique(critic, UNICODE_BRIEF, "A clean problem statement.", List.of("test_x"), tracked::add);
        assertThat(tracked).containsExactly(response, response);
    }

    @Test
    void criticUsesOpenAiChatOptions_soOpenAiModelsAcceptTheRequestOptions() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{\"uncovered\":[]}"));
        when(chatModel.getOptions()).thenReturn(
                OpenAiChatOptions.builder().model("configured-model").reasoningEffort("medium").serviceTier("priority").customHeaders(Map.of("X-Test", "value")).build());
        SpecFidelityCriticService critic = criticWithModel(ChatClient.create(chatModel), "configured-model");
        critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "A clean problem statement.", List.of("test_x"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy((value) -> assertThat(value.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, (options) -> {
            assertThat(options.getMaxCompletionTokens()).isEqualTo(32768);
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
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper, new HyperionPromptTemplateService(), "configured-model",
                Duration.ZERO, ProviderFailureCooldown.disabled(), 128000, chatModel.getOptions());
        critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "A clean problem statement.", List.of("test_x"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy((value) -> assertThat(value.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, (options) -> {
            assertThat(options.getMaxTokens()).isEqualTo(1234);
            assertThat(options.getMaxCompletionTokens()).isNull();
        }));
    }

    @Test
    void critic_clampsOutputToTheConfiguredContextWindow() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{\"uncovered\":[]}"));
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper, new HyperionPromptTemplateService(), "configured-model",
                Duration.ZERO, ProviderFailureCooldown.disabled(), 16000, chatModel.getOptions());
        critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "A clean problem statement.", List.of("test_x"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy((value) -> assertThat(value.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, (options) -> {
            assertThat(options.getMaxCompletionTokens()).isGreaterThanOrEqualTo(4096).isLessThan(32768);
            assertThat(options.getMaxTokens()).isNull();
        }));
    }

    @Test
    void criticPinsTheConfiguredProviderModelOnBothReviewPasses() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(jsonResponse("{}"));
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        SpecFidelityCriticService critic = criticWithModel(ChatClient.create(chatModel), "provider/reviewer-model");
        critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "A clean problem statement.", List.of("test_x"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy((value) -> assertThat(value.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
                (options) -> assertThat(options.getModel()).isEqualTo("provider/reviewer-model")));
    }

    @Test
    void missingWorkedExample_isFlagged() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[{\"behaviour\":\"rollback after a failed checkout\",\"reason\":\"the interaction between undoing the charge and preserving queue position is difficult to apply without a trace\"}]}"));
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "If checkout fails after charging the member, undo the charge and preserve the member's original queue position.", List.of("failedCheckoutRollsBackAtomically"));
        assertThat(report.findings()).hasSize(1).allMatch((SpecFidelityReport.Finding finding) -> finding.kind() == Kind.MISSING_WORKED_EXAMPLE);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactly("rollback after a failed checkout");
    }

    @Test
    void contractAdvisoryDoesNotSkipOracleReview() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{\"missingExamples\":[{\"behaviour\":\"combining marks\",\"reason\":\"a trace would clarify the rule\"}]}"),
                rawResponse("""
                        {"mutantChecks":[{"mutant":"reject CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"}],
                         "uncovered":[],"weakOracle":[]}
                        """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).containsExactlyInAnyOrder(Kind.MISSING_WORKED_EXAMPLE, Kind.WEAK_TEST_ORACLE);
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void inventedRequirementNotInBrief_isFlagged() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[{\"requirement\":\"O(1) extra space\",\"sourceQuote\":\"O(1) extra space\",\"reason\":\"the brief never constrains space complexity\"}]}"));
        SpecFidelityReport report = critic.critique(
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Rotate the matrix; your solution must use O(1) extra space.", List.of("test_rotate"), COMPLETE_ARTIFACTS, null, () -> false, null, """
                        # Approved specification
                        Count grapheme clusters.\
                        """, null, null);
        assertThat(report.findings()).hasSize(1).allMatch((SpecFidelityReport.Finding finding) -> finding.kind() == Kind.INVENTED_REQUIREMENT);
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::requirement).containsExactly("O(1) extra space");
        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void inventedRequirementFoundOnlyInFrozenSpecification_isNotAnUnrepairableBlocker() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[{\"requirement\":\"O(1) extra space\",\"sourceQuote\":\"O(1) extra space\",\"reason\":\"the brief never constrains space complexity\"}]}"));
        SpecFidelityReport report = critic.critique(
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Rotate the matrix.", List.of("test_rotate"), COMPLETE_ARTIFACTS, null, () -> false, null, """
                        # Approved specification
                        Use O(1) extra space.\
                        """, null, null);
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void incorrectExampleFoundOnlyInFrozenSpecification_isNotAnUnrepairableBlocker() {
        SpecFidelityCriticService critic = criticReturning(
                jsonResponse("{\"exampleChecks\":[{\"claim\":\"2 + 2 = 5\",\"computedOutcome\":\"2 + 2 = 4\",\"consistent\":false,\"reason\":\"the arithmetic is wrong\"}]}"));
        SpecFidelityReport report = critic.critique("Create an arithmetic exercise.", "Calculate each result.", List.of("calculates"), COMPLETE_ARTIFACTS, null, () -> false, null,
                """
                        # Approved specification
                        Example: 2 + 2 = 5\
                        """, null, null);
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void incorrectExampleRepeatedInStudentFacingStatement_remainsBlocking() {
        SpecFidelityCriticService critic = criticReturning(
                jsonResponse("{\"exampleChecks\":[{\"claim\":\"2 + 2 = 5\",\"computedOutcome\":\"2 + 2 = 4\",\"consistent\":false,\"reason\":\"the arithmetic is wrong\"}]}"));
        SpecFidelityReport report = critic.critique("Create an arithmetic exercise.", "Example: 2 + 2 = 5", List.of("calculates"), COMPLETE_ARTIFACTS, null, () -> false, null, """
                # Approved specification
                Example: 2 + 2 = 5\
                """, null, null);
        assertThat(report.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> assertThat(finding.kind()).isEqualTo(Kind.CONTRACT_CONTRADICTION));
    }

    @Test
    void contractReviewAcceptsScalarComputedOutcomes() {
        SpecFidelityCriticService critic = criticReturning(
                jsonResponse("{\"exampleChecks\":[{\"claim\":\"ready = false\",\"computedOutcome\":true,\"consistent\":false,\"reason\":\"the operation sets ready\"}]}"));

        SpecFidelityReport report = critic.critique("Create a state exercise.", "Example: ready = false", List.of("setsReady"), COMPLETE_ARTIFACTS, null, () -> false, null, null,
                null, null);

        assertThat(report.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> {
            assertThat(finding.kind()).isEqualTo(Kind.CONTRACT_CONTRADICTION);
            assertThat(finding.detail()).contains("computes to \"true\"");
        });
    }

    @Test
    void oraclePassRejectsArtifactOnlySourceQuotes() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"accept null inventory names","killed":false,"sourceQuote":"reject null inventory names",
                    "reason":"the generated tests reject null"}],"uncovered":[],"weakOracle":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"sort descending","killed":true,"reason":"the ascending assertion kills it"}],"uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        Map<RepositoryType, Map<String, String>> artifacts = Map.of(RepositoryType.SOLUTION,
                Map.of("src/Inventory.java", "class Inventory { String policy = \"reject null inventory names\"; }"), RepositoryType.TEMPLATE,
                Map.of("src/Inventory.java", "class Inventory {}"), RepositoryType.TESTS, Map.of("test/InventoryTest.java", "class InventoryTest {}"));
        SpecFidelityReport report = critique(critic, "Sort integer values.", "Sort integer values in ascending order.", List.of("sortsValues"), artifacts, null);
        assertThat(report.findings()).isEmpty();
        verify(scripted.model(), times(3)).call(any(Prompt.class));
    }

    @Test
    void freeFormTemplateGapCannotOverrideSuccessfulTaskReachabilityChecks() {
        ScriptedCritic scripted = criticScripted(jsonResponse(
                "{\"templateChecks\":[{\"test\":\"summarizesEvents\",\"targetReached\":true,\"reason\":\"the intended method placeholder is reached\"}],\"templateGaps\":[{\"requirement\":\"Implement summarize\",\"reason\":\"the method is a TODO throwing UnsupportedOperationException\"}]}"),
                jsonResponse("{}"));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic, "Summarize checkout events.", "Implement summarize.", List.of("summarizesEvents"));
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void adaptationDiff_exposesUnrequestedDeletionAsBlockingFinding() {
        ScriptedCritic scripted = criticScripted(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[{\"change\":\"solution/src/Inventory.java removed displayName(String)\",\"reason\":\"the feedback explicitly preserves it\"}],\"missingRequestedChanges\":[]}"));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critiqueAdaptation(critic, "Change only remove(); preserve displayName(String).", "# Inventory", List.of("removeRejectsZero"), """
                --- solution/src/Inventory.java
                - String displayName(String itemId)
                """, null);
        assertThat(report.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> {
            assertThat(finding.kind()).isEqualTo(Kind.UNREQUESTED_ADAPTATION_CHANGE);
            assertThat(finding.requirement()).contains("displayName");
        });
        assertThat(report.hasBlockingFindings()).isTrue();
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues()).allSatisfy((value) -> assertThat(value.getContents()).contains("ADAPTATION CHANGES").contains("- String displayName(String itemId)"));
    }

    @Test
    void mechanicsLeakIsMergedWithSemanticReviewFindings() {
        ScriptedCritic scripted = criticScripted(jsonResponse(
                "{\"hiddenRequirements\":[{\"requirement\":\"count_graphemes(s)\",\"sourceQuote\":\"count_graphemes(s)\",\"reason\":\"the tests require this exact signature but the statement never restates it\"}]}"),
                rawResponse("""
                        {"mutantChecks":[{"mutant":"ignore CJK input","killed":false,"sourceQuote":"CJK characters","reason":"no assertion uses CJK input"}],
                         "uncovered":[],"weakOracle":[]}
                        """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "todo!() in the template; the template must fail every test.", List.of("test_x"));
        assertThat(report.findings()).extracting(SpecFidelityReport.Finding::kind).contains(Kind.MECHANICS_LEAK, Kind.HIDDEN_GRADED_REQUIREMENT, Kind.WEAK_TEST_ORACLE);
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void ungroundedContradictionFinding_isExcludedFromRepairPrompt() {
        ScriptedCritic scripted = criticScripted(jsonResponse(
                "{\"contradictions\":[{\"requirement\":\"the answer is always 42\",\"sourceQuote\":\"the answer is always 42\",\"evidenceArtifact\":\"TEMPLATE: src/Graphemes.java\",\"evidenceQuote\":\"return 0;\",\"reason\":\"the solution and template disagree\"}]}"),
                rawResponse("""
                        {"mutantChecks":[{"mutant":"ignore CJK input","killed":true,"reason":"the assertion kills it"}],
                         "uncovered":[],"weakOracle":[]}
                        """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).noneMatch((SpecFidelityReport.Finding finding) -> finding.kind() == Kind.CONTRACT_CONTRADICTION);
        assertThat(report.findings()).isEmpty();
        assertThat(critic.renderForRetryPrompt(report)).isEmpty();
    }

    @Test
    void producedArtifactCannotAuthorizeItsOwnContractContradiction() {
        ScriptedCritic scripted = criticScripted(jsonResponse(
                "{\"contradictions\":[{\"requirement\":\"Graphemes must be absent\",\"sourceQuote\":\"class Graphemes\",\"evidenceArtifact\":\"TEMPLATE: src/Graphemes.java\",\"evidenceQuote\":\"return 0;\",\"reason\":\"the template supplies this type\"}]}"),
                rawResponse("""
                        {"mutantChecks":[{"mutant":"return UTF-16 length","killed":true,"reason":"the assertion kills it"}],
                         "uncovered":[],"weakOracle":[]}
                        """));

        SpecFidelityReport report = critique(scripted.critic(),
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).isEmpty();
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void modelError_degradesGracefully() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("gpu timeout"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = new SpecFidelityCriticService(ChatClient.create(chatModel), objectMapper);
        ProviderUsageSink usageSink = mock(ProviderUsageSink.class);
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "A clean problem statement.", List.of("test_x"), usageSink);
        assertThat(report.findings()).hasSize(2).allMatch((SpecFidelityReport.Finding finding) -> finding.kind() == Kind.QUALITY_REVIEW_UNAVAILABLE);
        verify(chatModel, times(3)).call(any(Prompt.class));
        verify(usageSink, never()).markUncertain();
        verify(usageSink, never()).accept(any());
    }

    @Test
    void cancellationAfterContractResponse_skipsRemainingReviewerCalls() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"));
        SpecFidelityCriticService critic = scripted.critic();
        AtomicBoolean cancelled = new AtomicBoolean();
        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, response -> cancelled.set(true), cancelled::get, null,
                null, null, null);
        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(Kind.QUALITY_REVIEW_UNAVAILABLE));
        verify(scripted.model()).call(any(Prompt.class));
    }

    @Test
    void cancellationAfterOracleResponse_skipsCorrectionCall() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"), rawResponse("""
                {"mutantChecks":[{"mutant":"ignore CJK input","killed":false,"sourceQuote":"requirement absent from brief",
                    "reason":"the assertion does not cover it"}],"uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger responses = new AtomicInteger();
        SpecFidelityReport report = critic.critique(UNICODE_BRIEF, "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS,
                response -> cancelled.set(responses.incrementAndGet() == 2), cancelled::get, null, null, null, null);
        assertThat(report.findings()).singleElement().satisfies(finding -> assertThat(finding.kind()).isEqualTo(Kind.QUALITY_REVIEW_UNAVAILABLE));
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void hardProviderFailureStopsTheSecondReviewPassAndLaterJobsAtSharedAdmission() {
        ChatModel failingModel = mock(ChatModel.class);
        when(failingModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 401 unauthorized"));
        when(failingModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ProviderFailureCooldown cooldown = inMemoryCooldown();
        SpecFidelityCriticService failingCritic = criticWithCooldown(ChatClient.create(failingModel), "configured-model", Duration.ofMinutes(5L), cooldown);
        ProviderUsageSink firstUsageSink = mock(ProviderUsageSink.class);
        SpecFidelityReport firstReport = critique(failingCritic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "A clean problem statement.", List.of("test_x"), firstUsageSink);
        assertThat(firstReport.findings()).hasSize(2).allMatch((SpecFidelityReport.Finding finding) -> finding.kind() == Kind.QUALITY_REVIEW_UNAVAILABLE);
        verify(failingModel).call(any(Prompt.class));
        verify(firstUsageSink, never()).markUncertain();
        ChatModel nextModel = mock(ChatModel.class);
        when(nextModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService nextCritic = criticWithCooldown(ChatClient.create(nextModel), "configured-model", Duration.ofMinutes(5L), cooldown);
        ProviderUsageSink nextUsageSink = mock(ProviderUsageSink.class);
        SpecFidelityReport nextReport = critique(nextCritic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Another candidate.", List.of("test_x"), nextUsageSink);
        assertThat(nextReport.findings()).hasSize(2).allMatch((SpecFidelityReport.Finding finding) -> finding.kind() == Kind.QUALITY_REVIEW_UNAVAILABLE);
        verify(nextModel, never()).call(any(Prompt.class));
        verifyNoInteractions(nextUsageSink);
    }

    @Test
    void transientRateLimitDoesNotBlockTheSecondReviewPassOrLaterJob() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 429 rate_limit_exceeded: too many requests")).thenReturn(jsonResponse("{}"));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        SpecFidelityCriticService critic = criticWithCooldown(ChatClient.create(chatModel), "configured-model", Duration.ofMinutes(5L), inMemoryCooldown());
        SpecFidelityReport firstReport = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "A clean problem statement.", List.of("test_x"));
        SpecFidelityReport nextReport = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Another candidate.", List.of("test_x"));
        assertThat(firstReport.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> assertThat(finding.kind()).isEqualTo(Kind.QUALITY_REVIEW_UNAVAILABLE));
        assertThat(nextReport.findings()).isEmpty();
        verify(chatModel, times(4)).call(any(Prompt.class));
    }

    @Test
    void jsonWrappedInHarmonyTokens_isStillParsed() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                """
                        <|start|>assistant<|channel|>final<|message|>
                        ```json
                        {"exampleChecks":[],"apiChecks":[],"templateChecks":[{"ownerType":"FixtureType","test":"happy path","targetReached":true,"reason":"the target is reached"}],"mutantChecks":[{"mutant":"CJK input is rejected","killed":true,"reason":"the assertion kills it"}],"uncovered":[{"requirement":"CJK characters","sourceQuote":"CJK characters","reason":"none"}],"contradictions":[],"hiddenRequirements":[],"weakOracle":[],"templateGaps":[],"missingExamples":[],"invented":[],"unrequestedChanges":[],"missingRequestedChanges":[]}
                        ```
                        <|end|>\
                        """));
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Clean statement.", List.of("test_happy"));
        assertThat(report.findings()).hasSize(1);
        assertThat((report.findings().get(0)).requirement()).isEqualTo("CJK characters");
    }

    @Test
    void trivialBrief_stillReviewsTheProducedArtifacts() {
        ScriptedCritic scripted = criticScripted(jsonResponse("{}"));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic, "too short", "Clean statement.", List.of("test_x"));
        assertThat(report.findings()).isEmpty();
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void adaptationWithNoChanges_blocksWithoutTrustingTheReviewer() {
        ScriptedCritic scripted = criticScripted(List.of());
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critiqueAdaptation(critic, "Change remove(0).", "# Inventory", List.of("test_x"), "", null);
        assertThat(report.findings()).singleElement()
                .satisfies((SpecFidelityReport.Finding finding) -> assertThat(finding.kind()).isEqualTo(Kind.REQUESTED_ADAPTATION_CHANGE_MISSING));
        assertThat(report.hasBlockingFindings()).isTrue();
        verify(scripted.model(), never()).call(any(Prompt.class));
    }

    @Test
    void adaptationResponseMapsMissingRequestedChangeToBlockingFinding() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[],\"missingRequestedChanges\":[{\"requirement\":\"reject zero quantities\",\"reason\":\"no validation was added\"}]}"));
        SpecFidelityReport report = critiqueAdaptation(critic, "Reject zero quantities.", "# Inventory", List.of("test_x"), "(no changes)", null);
        assertThat(report.findings()).singleElement().satisfies((SpecFidelityReport.Finding finding) -> {
            assertThat(finding.kind()).isEqualTo(Kind.REQUESTED_ADAPTATION_CHANGE_MISSING);
            assertThat(finding.requirement()).isEqualTo("reject zero quantities");
        });
        assertThat(report.hasBlockingFindings()).isTrue();
    }

    @Test
    void generationIgnoresAdaptationOnlyFields() {
        SpecFidelityCriticService critic = criticReturning(jsonResponse(
                "{\"uncovered\":[],\"missingExamples\":[],\"invented\":[],\"unrequestedChanges\":[{\"change\":\"solution added reset()\",\"reason\":\"not requested\"}],\"missingRequestedChanges\":[{\"requirement\":\"change remove()\",\"reason\":\"missing\"}]}"));
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "# Inventory", List.of("test_x"));
        assertThat(report.hasFindings()).isFalse();
    }

    @Test
    void floodOfFindings_isCapped() {
        StringBuilder body = new StringBuilder("{\"uncovered\":[");

        for (int i = 0; i < 100; ++i) {
            body.append(i == 0 ? "" : ",").append("{\"requirement\":\"req").append(i).append("\",\"reason\":\"r\"}");
        }

        body.append("]}");
        SpecFidelityCriticService critic = criticReturning(jsonResponse(body.toString()));
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Clean statement.", List.of("test_x"));
        assertThat(report.findings().size()).isLessThanOrEqualTo(12);
    }

    @Test
    void advisoryFindingFlood_cannotDisplaceBlockingAdaptationScopeDrift() {
        StringBuilder body = new StringBuilder("{\"uncovered\":[");

        for (int i = 0; i < 20; ++i) {
            body.append(i == 0 ? "" : ",").append("{\"requirement\":\"req").append(i).append("\",\"reason\":\"r\"}");
        }

        body.append("],\"unrequestedChanges\":[{\"change\":\"solution removed displayName\",\"reason\":\"explicitly preserved\"}],\"missingRequestedChanges\":[]}");
        SpecFidelityCriticService critic = criticReturning(jsonResponse(body.toString()));
        SpecFidelityReport report = critiqueAdaptation(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Clean statement.", List.of("test_x"), "- displayName", null);
        assertThat(report.hasBlockingFindings()).isTrue();
        assertThat(report.findings()).hasSizeLessThanOrEqualTo(12);
    }

    @Test
    void contractFindingFlood_cannotDisplaceTheOracleVerdict() {
        StringBuilder contradictions = new StringBuilder();

        for (int i = 0; i < 12; ++i) {
            contradictions.append(i == 0 ? "" : ",").append("{\"requirement\":\"contract ").append(i).append(
                    "\",\"sourceQuote\":\"user-perceived characters\",\"evidenceArtifact\":\"TEMPLATE: src/Graphemes.java\",\"evidenceQuote\":\"return 0;\",\"reason\":\"statement and test disagree; align both\"}");
        }

        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"ownerType":"FixtureType","test":"cjk","targetReached":true,"reason":"the assertion reaches the target"}],
                 "contradictions":[%s],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """.formatted(contradictions)), rawResponse(
                """
                        {"mutantChecks":[{"mutant":"return UTF-16 length","killed":false,"sourceQuote":"user-perceived characters","reason":"test/GraphemesTest.java has no surrogate-pair assertion; add one"}],
                         "uncovered":[],"weakOracle":[]}
                        """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport report = critique(critic,
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null);
        assertThat(report.findings()).hasSizeLessThanOrEqualTo(12).extracting(SpecFidelityReport.Finding::kind).contains(Kind.WEAK_TEST_ORACLE);
        verify(scripted.model(), times(2)).call(any(Prompt.class));
    }

    @Test
    void continuityReview_threadsPreviousFindingsAndReVerificationInstructionIntoThePrompt() {
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"ownerType":"FixtureType","test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":true,"reason":"the assertion now kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport previousReport = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(Kind.WEAK_TEST_ORACLE, "return the UTF-16 length", "no assertion uses a surrogate pair")));
        critic.critique(
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null, () -> false, previousReport, null, null, null);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues()).allSatisfy((Prompt prompt) -> assertThat(prompt.getContents()).contains("PREVIOUS REVIEW", "return the UTF-16 length",
                "no assertion uses a surrogate pair", "adjudicate each item", "omit it if resolved", "repeat it with fresh current evidence if still open",
                "complete review of the current candidate", "including a defect overlooked previously"));
    }

    @Test
    void continuityReview_showsTheRepairDeltaBeforeAdjudicatingPriorFindings() {
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],"templateChecks":[{"ownerType":"FixtureType","test":"cjk","targetReached":true,"reason":"current assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],"unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"revert after first call","killed":true,"reason":"the added second assertion kills it"}],"uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport previous = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(Kind.WEAK_TEST_ORACLE, "revert after first call", "only one call was asserted")));
        critic.critique(
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null, () -> false, previous, "contract", """
                        --- tests/ExampleTest.java
                        + assertEquals(expected, callAgain());\
                        """, null);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues()).allSatisfy((Prompt prompt) -> assertThat(prompt.getContents()).contains("REPAIR DELTA", "+ assertEquals(expected, callAgain())",
                "PREVIOUS REVIEW HYPOTHESES", "explicitly decide whether the added/changed assertion now kills that same mutant"));
    }

    @Test
    void continuityReview_resolvedPriorFindingIsNotCarriedForwardWhenTheCurrentPassOmitsIt() {
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"ownerType":"FixtureType","test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return the UTF-16 length","killed":true,"reason":"the assertion now kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        SpecFidelityReport previousReport = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(Kind.WEAK_TEST_ORACLE, "return the UTF-16 length", "no assertion uses a surrogate pair")));
        SpecFidelityReport report = critic.critique(
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null, () -> false, previousReport, null, null, null);
        assertThat(report.hasFindings()).as("the mutant is now killed, so the previously reported finding is resolved and must not reappear", new Object[0]).isFalse();
    }

    @Test
    void continuityReview_firstAttemptOmitsThePreviousReviewSection() {
        ScriptedCritic scripted = criticScripted(rawResponse("""
                {"exampleChecks":[],"apiChecks":[],
                 "templateChecks":[{"ownerType":"FixtureType","test":"cjk","targetReached":true,"reason":"the assertion reaches count"}],
                 "contradictions":[],"hiddenRequirements":[],"templateGaps":[],"missingExamples":[],"invented":[],
                 "unrequestedChanges":[],"missingRequestedChanges":[]}
                """), rawResponse("""
                {"mutantChecks":[{"mutant":"return 0","killed":true,"reason":"the assertion kills it"}],
                 "uncovered":[],"weakOracle":[]}
                """));
        SpecFidelityCriticService critic = scripted.critic();
        critic.critique(
                "Implement count_graphemes(s) counting user-perceived characters. It MUST be tested on accented Latin (café), a combining-mark sequence, CJK characters, and at least one emoji.",
                "Count graphemes.", List.of("cjk"), COMPLETE_ARTIFACTS, null, () -> false, SpecFidelityReport.empty(), null, null, null);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(scripted.model(), times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues()).allSatisfy((Prompt prompt) -> assertThat(prompt.getContents()).doesNotContain("PREVIOUS REVIEW"));
    }

    @Test
    void renderForRetryPrompt_foldsFindingsAndIsEmptyWhenNone() {
        SpecFidelityCriticService critic = new SpecFidelityCriticService(null, objectMapper);
        assertThat(critic.renderForRetryPrompt(SpecFidelityReport.empty())).isEmpty();
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(Kind.UNCOVERED_REQUIREMENT, "CJK", "no CJK test"),
                new SpecFidelityReport.Finding(Kind.UNREQUESTED_ADAPTATION_CHANGE, "solution/Queue.java added clear()", "The feedback did not request it."),
                new SpecFidelityReport.Finding(Kind.MECHANICS_LEAK, "make the tests fail", "leak"),
                new SpecFidelityReport.Finding(Kind.MISSING_WORKED_EXAMPLE, "rollback", "clarify state restoration")));
        String rendered = critic.renderForRetryPrompt(report);
        assertThat(rendered).contains("must fix before saving", "Optional quality improvements", "Unrequested adaptation change", "solution/Queue.java added clear()")
                .contains("No test covers this student-owned requirement", "CJK", "no CJK test", "grader-mechanics phrasing", "make the tests fail", "leak",
                        "clarify state restoration", "Confirm its Design owner is stubbed or student-creates")
                .doesNotContain("Add a test that asserts it");
    }

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
        String bare = """
                class FSSizeCalculatorTest {
                  @Test void calc() { assertEquals(600L, new FSSizeCalculator().calculateSize(root)); }
                  @Test void nul() { assertThrows(IllegalArgumentException.class, () -> new FSSizeCalculator().calculateSize(null)); }
                }\
                """;
        List<SpecFidelityReport.Finding> findings = detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/FSSizeCalculatorTest.java", bare));
        assertThat(findings).hasSize(1);
        assertThat((findings.get(0)).kind()).isEqualTo(Kind.MISSING_FAILURE_MESSAGE);
        assertThat((findings.get(0)).requirement()).isEqualTo("test/FSSizeCalculatorTest.java");
    }

    @Test
    void messageless_doesNotFlagWhenAssertionsCarryAMessage() {
        String messaged = """
                class T {
                  @Test void a() { assertEquals(600L, calc.size(root), "size must sum every file regardless of depth"); }
                }\
                """;
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/T.java", messaged))).isEmpty();
    }

    @Test
    void messageless_doesNotFlagWhenFailHasAMessage() {
        String failStyle = """
                class T {
                  @Test void a() { if (!ok) fail("BubbleSort does not sort correctly"); }
                }\
                """;
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/T.java", failStyle))).isEmpty();
    }

    @Test
    void messageless_doesNotFlagAMixedFile_fileLevelThreshold() {
        String mixed = """
                class T {
                  @Test void a() { assertEquals(1, x); }
                  @Test void b() { assertTrue(ok, "b must hold after push"); }
                }\
                """;
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/T.java", mixed))).isEmpty();
    }

    @Test
    void messageless_doesNotCountACommentedOutMessageAsCoverage() {
        String commented = """
                class T {
                  // assertEquals(1, x, "old message")
                  @Test void a() { assertEquals(1, x); }
                }\
                """;
        List<SpecFidelityReport.Finding> findings = detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/T.java", commented));
        assertThat(findings).hasSize(1);
    }

    @Test
    void messageless_failsOpenForNonJvmLanguages() {
        String goBare = "func TestReverse(t *testing.T){ if got != want { t.Errorf(\"x\") } }";
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.GO, Map.of("stringutils_test.go", goBare))).isEmpty();
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.TYPESCRIPT, Map.of("Stack.test.ts", "expect(s.pop()).toBe(1);"))).isEmpty();
    }

    @Test
    void messageless_ignoresFilesWithoutAssertions() {
        String helper = """
                class Helpers {
                  static FSNode tree() { return new FSNode("root", List.of()); }
                }\
                """;
        assertThat(detector().detectMessagelessAssertions(ProgrammingLanguage.JAVA, Map.of("test/Helpers.java", helper))).isEmpty();
    }

    // --- Unenforceable technique rules ---

    private SpecFidelityCriticService detectorOnly() {
        return new SpecFidelityCriticService(null, objectMapper);
    }

    @Test
    void techniqueRules_flagARecursionMandateNoAssertionCanObserve() {
        // A specification whose brief asked for recursion: rewriting both methods iteratively still passes every graded test, so the mandate is unenforceable.
        String spec = "## Rules\n| R1 | `factorial(int n)` returns n!. The implementation **must be recursive** (direct or indirect self-call). |\n";

        assertThat(detectorOnly().detectUnenforceableTechniqueRules(spec)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE);
            assertThat(finding.isBlocking()).as("nothing downstream can repair it, so scheduling it would burn repair rounds on impossible work").isFalse();
            assertThat(finding.requirement()).containsIgnoringCase("must be recursive");
        });
    }

    @Test
    void techniqueRules_flagAStreamPipelineMandate() {
        // A specification whose brief asked for the Streams API: a plain for-loop scores full marks against the graded suite.
        String spec = "## Rules\nR3: The implementation must use a Stream pipeline with a filter lambda and a mapToDouble step.\n";

        assertThat(detectorOnly().detectUnenforceableTechniqueRules(spec)).singleElement()
                .satisfies(finding -> assertThat(finding.requirement()).containsIgnoringCase("must use a Stream"));
    }

    @Test
    void techniqueRules_doNotFireOnOrdinaryRulesThatMerelyMentionStreamsOrLoops() {
        // "stream" and "loop" are ordinary domain nouns — an input stream, a self-loop in a graph, a retry loop — and an iteratively refined algorithm is not a mandate to
        // write a loop. A false positive downgrades a real blocking finding to advisory, so the pattern has to earn every match.
        String spec = """
                ## Rules
                | R1 | The parser must use the provided input stream and close it. |
                | R2 | Self-loops are not allowed in the dependency graph. |
                | R3 | Retry loops in the client are not allowed; fail fast instead. |
                | R4 | Nested loops in the generated report are not allowed. |
                | R5 | Recursion is not allowed in the grammar of the input language. |
                | R6 | The result must be iteratively refined until it converges. |
                """;

        assertThat(detectorOnly().detectUnenforceableTechniqueRules(spec)).isEmpty();
    }

    @Test
    void techniqueRules_catchTheMandateShapesSpecificationsActuallyUse() {
        // Realistic phrasings, including markdown emphasis and words between the verb and the construct.
        for (String rule : List.of("The implementation **must be recursive** (direct or indirect self-call).",
                "Students must implement each method using **pure recursion**; explicit iterative constructs such as `for`, `while` are not allowed.",
                "The total must use a Java **Stream** pipeline (filter, map, reduce).",
                "Each method must be implemented *recursively*; the code may not contain any loop construct.",
                "The implementation must use lambda expressions for the predicate and mapper.", "The method must be recursive and must not use any looping construct.")) {
            assertThat(detectorOnly().detectUnenforceableTechniqueRules("## Rules\n" + rule)).as("should flag: %s", rule).isNotEmpty();
        }
    }

    @Test
    void techniqueRules_staySilentOnRulesThatAreObservable() {
        // Delegation IS observable through a recording fake, and ordering and validation through ordinary assertions, so none may be flagged or the finding becomes noise
        // attached to every exercise.
        String spec = """
                ## Rules
                R1: `aggregate` must delegate to the injected PricingPolicy and return its result unchanged.
                R2: Each group must be ordered by amount descending; ties keep encounter order.
                R3: A line with fewer than three fields must be treated as invalid and ignored.
                R4: The method must return an empty map for an empty input list.
                """;

        assertThat(detectorOnly().detectUnenforceableTechniqueRules(spec)).isEmpty();
    }

    @Test
    void techniqueRules_reportEachDistinctMandateOnce() {
        String spec = "## Rules\nR1: must be recursive\nR2: must be recursive\nR3: must not use loops\n";

        assertThat(detectorOnly().detectUnenforceableTechniqueRules(spec)).hasSize(2);
    }

    @Test
    void techniqueRules_stopAtTheAdvisoryBudgetWhenASpecificationIsFullOfThem() {
        // The findings are advisory and unrepairable, so a specification that mandates a technique in every rule must not push every other advisory finding out of the report.
        String spec = """
                ## Rules
                R1: The implementation must be recursive.
                R2: The implementation must use a Stream pipeline.
                R3: The implementation must use lambda expressions.
                R4: The implementation must not use loops.
                R5: The implementation must not use recursion.
                R6: The implementation must use a for loop.
                """;

        assertThat(detectorOnly().detectUnenforceableTechniqueRules(spec)).hasSize(4);
    }

    // --- Grader-mechanics leak detection ---

    /**
     * Each phrasing the deterministic, model-free leak scan must catch. Every pattern is independently deletable from
     * {@code SpecFidelityCriticService#MECHANICS_LEAK_PATTERNS} without any other test noticing, so each one is pinned by its own row.
     */
    private static Stream<String> mechanicsLeakPhrasings() {
        return Stream.of("Raise NotImplementedError from the stub.", "The starter contains todo!() where your code goes.", "These make the tests fail until you implement it.",
                "Note that the template must fail every test before you start.", "Use the exact test name reported below.",
                "The mismatch is reported by the test runner as a failure.", "The report is generated by the test suite after each push.",
                "The stub lives in the template file you were given.");
    }

    @ParameterizedTest
    @MethodSource("mechanicsLeakPhrasings")
    void mechanicsLeak_isFlaggedInTheStudentFacingStatement(String statement) {
        SpecFidelityReport report = critique(detector(), UNICODE_BRIEF, statement, List.of("test_x"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).as("%s describes how the exercise is rigged for grading, not the task", statement)
                .anySatisfy(finding -> assertThat(finding.kind()).isEqualTo(Kind.MECHANICS_LEAK));
    }

    /** Near misses that share vocabulary with a leak but describe the task, so flagging them would attach noise to ordinary statements. */
    private static Stream<String> mechanicsLeakNearMisses() {
        return Stream.of("Your tests must fail fast on invalid input.", "Return the template string that the caller supplied.",
                "The runner reports each shipment by name in the generated manifest.");
    }

    @ParameterizedTest
    @MethodSource("mechanicsLeakNearMisses")
    void mechanicsLeak_staysSilentOnTaskProseThatMerelySharesItsVocabulary(String statement) {
        SpecFidelityReport report = critique(detector(), UNICODE_BRIEF, statement, List.of("test_x"), COMPLETE_ARTIFACTS, null);

        assertThat(report.findings()).as("%s describes the task, not the grading rig", statement).noneSatisfy(finding -> assertThat(finding.kind()).isEqualTo(Kind.MECHANICS_LEAK));
    }

    @Test
    void techniqueRules_emptyWithoutASpecification() {
        assertThat(detectorOnly().detectUnenforceableTechniqueRules(null)).isEmpty();
        assertThat(detectorOnly().detectUnenforceableTechniqueRules("   ")).isEmpty();
    }

    // --- Contract witnesses ---

    private static final String SPEC_WITH_RULES = "## Rules\n| ID | Rule |\n| R1 | A negative salary makes the record invalid. |\n";

    private List<ContractWitness> witnessesFrom(String body) {
        return criticReturning(rawResponse(body)).authorContractWitnesses(SPEC_WITH_RULES, "class RosterParserTest { }", "class RosterParser { }", null, () -> false);
    }

    @Test
    void contractWitnessContextMakesStudentCreatedTypesExplicitlyReflectionOnly() {
        assertThat(ContractWitnessAuthor.renderTemplateOwnership(Map.of("Elevator", "given", "ElevatorDispatcher", "student-creates")))
                .contains("Elevator: given — present in the starter").contains("ElevatorDispatcher: student-creates — ABSENT from the starter")
                .contains("reflection/dynamic-proxy");
    }

    @Test
    void contractWitnessOwnershipUsesTheSameCanonicalStatusAsTheSpecGate() {
        assertThat(SpecFidelityCriticService.designTemplateStatuses("""
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `ElevatorDispatcher` | dispatches | **student‑creates** |
                """)).containsEntry("ElevatorDispatcher", "student-creates");
    }

    @Test
    void authorContractWitnesses_parsesTheRuleNameAndMethod() {
        List<ContractWitness> witnesses = witnessesFrom("""
                {"witnesses":[{"rule":"R1","testName":"testWitnessNegativeSalary",
                 "code":"@Test\\nvoid testWitnessNegativeSalary() { assertEquals(0, parse(\\"a|b|-5\\"), \\"negative is invalid\\"); }",
                 "wrongBehavior":"accepts negative salary records"}]}
                """);

        assertThat(witnesses).singleElement().satisfies(witness -> {
            assertThat(witness.ruleId()).isEqualTo("R1");
            assertThat(witness.testName()).isEqualTo("testWitnessNegativeSalary");
            assertThat(witness.code()).contains("void testWitnessNegativeSalary()");
            assertThat(witness.wrongBehavior()).isEqualTo("accepts negative salary records");
        });
    }

    @Test
    void authorContractWitnesses_dropsAWitnessWithoutAConcreteWrongBehavior() {
        assertThat(witnessesFrom("""
                {"witnesses":[{"rule":"R1","testName":"testWitnessNegativeSalary",
                 "code":"@Test\\nvoid testWitnessNegativeSalary() { assertEquals(0, parse(\\"a|b|-5\\"), \\"negative is invalid\\"); }"}]}
                """)).isEmpty();
    }

    @Test
    void authorContractWitnesses_dropsAWitnessWhoseNameIsNotTheMethodItDeclares() {
        // The name is how a build result is attributed back to a witness, so one that does not appear in its own body would count as validated whatever the build reported.
        assertThat(witnessesFrom("""
                {"witnesses":[{"rule":"R1","testName":"testClaimedName","code":"@Test\\nvoid testActualDifferentName() { assertTrue(true, \\"x\\"); }",
                 "wrongBehavior":"does the wrong thing"}]}
                """)).isEmpty();
    }

    @Test
    void authorContractWitnesses_dropsAWitnessWhoseNameOnlyAppearsInACommentOrString() {
        // A substring check would accept this: the build reports `actual`, nothing is attributed to `testClaimedName`, and it is validated on no evidence.
        assertThat(witnessesFrom("""
                {"witnesses":[{"rule":"R1","testName":"testClaimedName",
                 "code":"@Test\\nvoid actual() { assertEquals(1, 1, \\"see testClaimedName\\"); } // testClaimedName",
                 "wrongBehavior":"does the wrong thing"}]}
                """)).isEmpty();
    }

    @Test
    void authorContractWitnesses_dropsAWitnessThatAssertsNothing() {
        // A witness with no assertion passes against every implementation, correct or not, so it pins no rule.
        assertThat(witnessesFrom("""
                {"witnesses":[{"rule":"R1","testName":"testWitnessEmpty","code":"@Test\\nvoid testWitnessEmpty() { new RosterParser().formatRoster(\\"a\\"); }",
                 "wrongBehavior":"accepts an invalid record"}]}
                """)).isEmpty();
    }

    @Test
    void authorContractWitnesses_dropsAWitnessForARuleTheSpecificationNeverStates() {
        // Inventing a requirement is the one thing this pass must never do: the witness would become grading material for a rule no student was told about.
        assertThat(witnessesFrom("""
                {"witnesses":[{"rule":"R999","testName":"testWitnessInvented","code":"@Test\\nvoid testWitnessInvented() { assertEquals(1, 1, \\"invented\\"); }",
                 "wrongBehavior":"violates an invented rule"}]}
                """)).isEmpty();
    }

    @Test
    void authorContractWitnesses_dropsDuplicateAndIncompleteEntries() {
        List<ContractWitness> witnesses = witnessesFrom("""
                {"witnesses":[
                 {"rule":"R1","testName":"testWitnessA","code":"@Test\\nvoid testWitnessA() { assertEquals(1, 1, \\"a\\"); }","wrongBehavior":"wrong A"},
                 {"rule":"R1","testName":"testWitnessA","code":"@Test\\nvoid testWitnessA() { assertEquals(1, 1, \\"a\\"); }","wrongBehavior":"wrong A"},
                 {"rule":"R1","testName":"","code":"@Test\\nvoid testWitnessB() { assertEquals(1, 1, \\"b\\"); }","wrongBehavior":"wrong B"},
                 {"rule":null,"testName":"testWitnessC","code":"@Test\\nvoid testWitnessC() { assertEquals(1, 1, \\"c\\"); }","wrongBehavior":"wrong C"}]}
                """);

        assertThat(witnesses).extracting(ContractWitness::testName).containsExactly("testWitnessA");
    }

    @Test
    void authorContractWitnesses_capsTheBudgetBecauseEachWitnessCostsAValidatingBuild() {
        String entries = java.util.stream.IntStream.range(0, 6).mapToObj(index -> "{\"rule\":\"R1\",\"testName\":\"testWitness" + index + "\",\"code\":\"@Test\\nvoid testWitness"
                + index + "() { assertEquals(1, 1, \\\"w\\\"); }\",\"wrongBehavior\":\"wrong " + index + "\"}").collect(java.util.stream.Collectors.joining(","));

        assertThat(witnessesFrom("{\"witnesses\":[" + entries + "]}")).hasSize(3);
    }

    @Test
    void authorContractWitnesses_authorsNothingWhenTheResponseIsNotUsableJson() {
        // A critic that fails must never cost the exercise anything: the caller keeps the suite it already has.
        assertThat(witnessesFrom("I could not find any uncovered rules.")).isEmpty();
        assertThat(witnessesFrom("{\"witnesses\": null}")).isEmpty();
    }

    @Test
    void authorContractWitnesses_authorsNothingWhenCancelled() {
        SpecFidelityCriticService critic = criticReturning(rawResponse(
                "{\"witnesses\":[{\"rule\":\"R1\",\"testName\":\"testW\",\"code\":\"@Test void testW() { assertEquals(1, 1, \\\"w\\\"); }\",\"wrongBehavior\":\"wrong\"}]}"));

        assertThat(critic.authorContractWitnesses(SPEC_WITH_RULES, "class T { }", "class S { }", null, () -> true)).isEmpty();
    }

}
