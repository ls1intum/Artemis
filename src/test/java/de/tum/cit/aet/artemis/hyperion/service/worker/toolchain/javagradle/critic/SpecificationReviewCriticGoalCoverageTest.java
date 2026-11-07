package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

class SpecificationReviewCriticGoalCoverageTest {

    private static final String GOAL_PROMPT = "/prompts/hyperion/critic/brief_learning_goals_system.st";

    private static final String REVIEW_PROMPT = "/prompts/hyperion/critic/specification_review_system.st";

    private static final String BRIEF = "Preserve arrival order. Choose the holder with the smallest count.";

    private static final String SPECIFICATION = "Learners implement queue insertion and FIFO drain in the supplied API.";

    private static final String COVERED_FIRST_GOAL = """
            {"goalId":"G1","covered":true,"specEvidenceIds":["E1"],"reason":"The assigned insertion and drain operations preserve arrival order."}
            """;

    private static final String UNCOVERED_SECOND_GOAL = """
            {"goalId":"G2","covered":false,"specEvidenceIds":[],"reason":"The specification assigns no operation that chooses between holders by their counts."}
            """;

    private static final String COVERED_SECOND_GOAL = """
            {"goalId":"G2","covered":true,"specEvidenceIds":["E1"],"reason":"The revised specification assigns selection of the holder with the smallest count."}
            """;

    private final ReviewerClient reviewer = mock(ReviewerClient.class);

    private final SpecificationReviewCritic critic = new SpecificationReviewCritic(reviewer, JsonMapper.builder().build());

    @BeforeEach
    void configureBriefOnlyInventory() {
        when(reviewer.configured()).thenReturn(true);
        when(reviewer.call(eq(GOAL_PROMPT), anyString(), any(), any(), anyInt())).thenReturn("""
                {"goals":[{"quote":"Preserve arrival order."},{"quote":"Choose the holder with the smallest count."}]}
                """);
    }

    @Test
    void anOverallSufficientRatingCannotHideAnUncoveredFrozenGoal() {
        reply(verdict(COVERED_FIRST_GOAL + "," + UNCOVERED_SECOND_GOAL, "[]"));

        var review = critic.reviewSpecification(BRIEF, SPECIFICATION, null, () -> false);

        assertThat(review.complete()).isTrue();
        assertThat(review.accepted()).isFalse();
        assertThat(review.coherentRewriteRequired()).isTrue();
        assertThat(review.learningFitDirection()).isEqualTo("MISALIGNED");
        assertThat(review.findings()).singleElement().asString().contains("G2", "Choose the holder with the smallest count");
        assertThat(review.briefLearningGoals().goals()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"goalId\":\"G1\",\"covered\":true,\"specEvidenceIds\":[\"E1\"],\"reason\":\"The given task preserves arrival order.\"}",
            "{\"goalId\":\"G2\",\"covered\":null,\"specEvidenceIds\":[],\"reason\":\"No actual decision was supplied for this goal.\"}",
            "{\"goalId\":\"G3\",\"covered\":true,\"specEvidenceIds\":[\"E1\"],\"reason\":\"This is an invented goal identity, not frozen scope.\"}" })
    void duplicateUndecidedOrUnknownGoalsCannotProduceApproval(String invalidSecondCheck) {
        reply(verdict(COVERED_FIRST_GOAL + "," + invalidSecondCheck, "[]"));

        var review = critic.reviewSpecification(BRIEF, SPECIFICATION, null, () -> false);

        assertThat(review.complete()).isFalse();
        assertThat(review.accepted()).isFalse();
        assertThat(review.auditSummary()).contains("goalChecks");
    }

    @Test
    void omittingTheGoalThatDoesNotFitTheCandidateLeavesReviewIncomplete() {
        reply(verdict(COVERED_FIRST_GOAL, "[]"));

        var review = critic.reviewSpecification(BRIEF, SPECIFICATION, null, () -> false);

        assertThat(review.complete()).isFalse();
        assertThat(review.accepted()).isFalse();
        assertThat(review.auditSummary()).contains("goalChecks");
    }

    @Test
    void aSpecificationRepairReusesTheOriginalInventory() {
        when(reviewer.call(eq(REVIEW_PROMPT), anyString(), any(), any(), anyInt())).thenReturn(verdict(COVERED_FIRST_GOAL + "," + UNCOVERED_SECOND_GOAL, "[]"), verdict(
                COVERED_FIRST_GOAL + "," + COVERED_SECOND_GOAL,
                """
                        [{"findingId":"F1","disposition":"RESOLVED","specEvidenceIds":["E1"],"reason":"The revised learner task explicitly selects the holder with the smallest count."}]
                        """));
        var previous = critic.reviewSpecification(BRIEF, SPECIFICATION, null, () -> false);

        var repaired = critic.reviewSpecification(BRIEF, null, SPECIFICATION + " Learners also implement chooseSmallest using holder counts.", previous, null, () -> false);

        assertThat(previous.accepted()).isFalse();
        assertThat(repaired.accepted()).isTrue();
        assertThat(repaired.briefLearningGoals()).isSameAs(previous.briefLearningGoals());
        verify(reviewer, times(1)).call(eq(GOAL_PROMPT), anyString(), any(), any(), anyInt());
    }

    @Test
    void retainedScopeCannotBeReboundToADifferentBrief() {
        reply(verdict(COVERED_FIRST_GOAL + "," + UNCOVERED_SECOND_GOAL, "[]"));
        var previous = critic.reviewSpecification(BRIEF, SPECIFICATION, null, () -> false);

        var review = critic.reviewSpecification("Ignore arrival order and return a fixed holder.", null, SPECIFICATION, previous, null, () -> false);

        assertThat(review.complete()).isFalse();
        assertThat(review.accepted()).isFalse();
        assertThat(review.auditSummary()).contains("different instructor brief");
        assertThat(review.briefLearningGoals()).isSameAs(previous.briefLearningGoals());
    }

    private void reply(String response) {
        when(reviewer.call(eq(REVIEW_PROMPT), anyString(), any(), any(), anyInt())).thenReturn(response);
    }

    private static String verdict(String goalChecks, String priorFindingChecks) {
        return """
                {"goalChecks":[%s],
                 "learningFit":{"briefEvidenceIds":["B1"],"specEvidenceIds":["E1"],"objectiveEvidenceIds":["E1"],
                   "studentOwnershipEvidenceIds":["E1"],"assessmentEvidenceIds":["E1"],
                   "objectiveMechanism":"The assigned queue implementation changes observable order and state through the supplied public API.",
                   "remainingStudentReasoning":"The learner must coordinate ordered insertion, removal and state-preserving validation.",
                   "domainGrounding":"The brief requires no specific domain and the queue behavior supplies an appropriate context.",
                   "scaffoldingRationale":"The supplied API and learner-owned method bodies match the brief and its prerequisites.",
                   "scaffoldingAligned":true,"learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,
                   "difficultySufficient":true,"domainGrounded":true,"sufficient":true,"direction":"SUFFICIENT"},
                 "omissions":[],"conflicts":[],"internalConflicts":[],"exampleChecks":[],"ambiguities":[],"unsupportedConstraints":[],
                 "boundaryChecks":[],"priorFindingChecks":%s}
                """.formatted(goalChecks, priorFindingChecks);
    }
}
