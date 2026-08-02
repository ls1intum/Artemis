package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService.ConceptSelectionReview;

class ConceptAdmissionCriticTest {

    private static final ConceptSelectionReview SELECTED = new ConceptSelectionReview(true, 1, List.of(), "Candidate 1 is the strongest fit.", "Broad review accepted it.");

    private final ReviewerClient reviewer = mock(ReviewerClient.class);

    private final ConceptAdmissionCritic critic = new ConceptAdmissionCritic(reviewer, new ObjectMapper());

    @Test
    void rejectsUnsupportedTechniqueAndBehaviorallyRedundantBoundary() {
        when(reviewer.call(anyString(), anyString(), any(), anyInt())).thenReturn(
                """
                        {"auditedCandidateEvidenceIds":["C1.1","C1.2","C1.3"],
                         "smallestEquivalentImplementation":"Compare the temperature with zero, twenty, and thirty and return the corresponding category.",
                         "observablePartitionAudit":"The regions below and above minus ten both return Freezing without changing state, so they merge.",
                         "unsupportedChoices":[{"candidateEvidenceIds":["C1.1","C1.2"],"detail":"The exact numeric thresholds and labels are not supplied by the open instructor brief."}],
                         "unobservableRequirements":[{"candidateEvidenceIds":["C1.3"],"detail":"A switch or ternary implementation can return the same public results as the required if-else form."}],
                         "redundantDistinctions":[{"candidateEvidenceIds":["C1.2"],"detail":"The claimed minus-ten boundary has the same returned value and state on both sides."}],
                         "admissible":false,"summary":"The selected candidate closes unsupported details and counts an unobservable, redundant branch as reasoning."}
                        """);

        ConceptSelectionReview result = critic.admit("Create a branching exercise with clear boundaries.", 1, """
                ## Candidate 1
                Boundaries are -10, 0, 20, and 30; both regions below 0 return Freezing.
                Students must use only if-else statements.
                """, SELECTED, null, () -> false);

        assertThat(result.complete()).isTrue();
        assertThat(result.accepted()).isFalse();
        assertThat(result.findings()).singleElement().asString().contains("thresholds", "switch or ternary", "minus-ten boundary");
        assertThat(result.auditSummary()).contains("Selected-concept admission", "Decision: rejected");
    }

    @Test
    void admitsGroundedObservableConcept() {
        when(reviewer.call(anyString(), anyString(), any(), anyInt())).thenReturn("""
                {"auditedCandidateEvidenceIds":["C1.2"],
                 "smallestEquivalentImplementation":"Students implement two policies that resolve the same overlap with observably different results.",
                 "observablePartitionAudit":"The policies share one public operation and choose different retained fragments for the same overlapping input.",
                 "unsupportedChoices":[],"unobservableRequirements":[],"redundantDistinctions":[],
                 "admissible":true,"summary":"Every concept-level distinction changes the public result and exact specification details remain open."}
                """);

        ConceptSelectionReview result = critic.admit("Create an unusual Strategy exercise.", 1,
                "## Candidate 1\nStudents implement interchangeable overlap-resolution policies with different observable choices.", SELECTED, null, () -> false);

        assertThat(result.accepted()).isTrue();
        assertThat(result.selectedCandidate()).isEqualTo(1);
        assertThat(result.auditSummary()).contains("Decision: admitted");
    }

    @Test
    void doesNotTurnDescriptiveStudentReasoningIntoANormativeImplementationConstraint() {
        when(reviewer.call(anyString(), anyString(), any(), anyInt())).thenReturn("""
                {"auditedCandidateEvidenceIds":["C1.2"],
                 "smallestEquivalentImplementation":"Classify the value into the three observable boundary regions.",
                 "observablePartitionAudit":"Each boundary region returns its corresponding category without changing state.",
                 "unsupportedChoices":[],
                 "unobservableRequirements":[{"candidateEvidenceIds":["C1.2"],"detail":"The reasoning describes an ordered comparison even though equivalent orders exist."}],
                 "redundantDistinctions":[],"admissible":false,"summary":"The illustrative reasoning was mistaken for a requirement."}
                """);

        ConceptSelectionReview result = critic.admit("Teach boundary-aware conditionals.", 1,
                "## Candidate 1\nStudent-owned reasoning: The solution conducts two comparisons and maps the value to one of three regions.", SELECTED, null, () -> false);

        assertThat(result.accepted()).isTrue();
        assertThat(result.auditSummary()).contains("Server normalization", "non-normative Student-owned reasoning");
    }

    @Test
    void keepsANormativeStudentReasoningConstraintRejectable() {
        when(reviewer.call(anyString(), anyString(), any(), anyInt())).thenReturn("""
                {"auditedCandidateEvidenceIds":["C1.2"],
                 "smallestEquivalentImplementation":"Classify the value into the three observable boundary regions.",
                 "observablePartitionAudit":"Each boundary region returns its corresponding category without changing state.",
                 "unsupportedChoices":[],
                 "unobservableRequirements":[{"candidateEvidenceIds":["C1.2"],"detail":"The candidate requires one exact comparison order even though equivalent orders exist."}],
                 "redundantDistinctions":[],"admissible":false,"summary":"The candidate imposes an exact implementation order."}
                """);

        ConceptSelectionReview result = critic.admit("Teach boundary-aware conditionals.", 1,
                "## Candidate 1\nStudent-owned reasoning: Students must evaluate the lower boundary first and never reverse the comparisons.", SELECTED, null, () -> false);

        assertThat(result.accepted()).isFalse();
        assertThat(result.findings()).singleElement().asString().contains("exact comparison order");
    }
}
