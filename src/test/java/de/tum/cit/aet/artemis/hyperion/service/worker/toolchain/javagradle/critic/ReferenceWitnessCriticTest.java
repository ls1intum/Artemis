package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport.Kind;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome.Disposition;

class ReferenceWitnessCriticTest {

    private static final String SPECIFICATION = "R1 accepts every integer.";

    private static final ContractWitness REFERENCE = new ContractWitness("R1", "reference", "@Test void reference() { assertTrue(false); }", "rejects a legal input");

    private static final ContractWitness GRADING = new ContractWitness("R1", "grading", "@Test void grading() { assertTrue(true); }", "rejects a legal input");

    private static final ContractWitness UNRESOLVED = new ContractWitness("R1", "unresolved", "@Test void unresolved() { assertTrue(false); }", "rejects another legal input");

    private static final String VALID_ROWS = """
            {"testName":"reference","verdict":"SUPPORTED_REFERENCE_DEFECT","sourceQuote":"R1 accepts every integer.","reason":"This legal input fails."},
            {"testName":"grading","verdict":"SUPPORTED_GRADING_WITNESS","sourceQuote":"R1 accepts every integer.","ownerType":"Selector","reason":"This legal input distinguishes the student-owned implementation."}
            """;

    @ParameterizedTest
    @MethodSource("unusableRows")
    void unusableVerdictLeavesOnlyItsOwnWitnessUnresolved(String unusableRows) {
        var review = review(VALID_ROWS + (unusableRows.isEmpty() ? "" : "," + unusableRows), List.of(outcome(REFERENCE, Disposition.REFERENCE_TEST_FAILED),
                outcome(GRADING, Disposition.REFERENCE_PASSED_STARTER_FAILED), outcome(UNRESOLVED, Disposition.REFERENCE_TEST_FAILED)));

        assertThat(review.supportedWitnesses()).containsExactly(REFERENCE);
        assertThat(review.adoptableWitnesses()).containsExactly(GRADING);
        assertThat(review.invalidWitnesses()).isEmpty();
        assertThat(review.unresolvedReferenceWitnesses()).containsExactly(UNRESOLVED);
        assertThat(review.unresolvedAdoptionWitnesses()).isEmpty();
        assertThat(review.findings()).extracting(finding -> finding.kind()).containsExactlyInAnyOrder(Kind.CONTRACT_CONTRADICTION, Kind.CONTRACT_WITNESS_AVAILABLE,
                Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    private static Stream<String> unusableRows() {
        return Stream.of("", "null", "{}", """
                {"testName":"unresolved","verdict":"UNRECOGNIZED","reason":"Unknown decision."}
                """, """
                {"testName":"unresolved","verdict":"UNRECOGNIZED","reason":"Unknown decision.","confidence":1}
                """, """
                {"testName":"unresolved","verdict":"INVALID_WITNESS","reason":""}
                """, """
                {"testName":"unresolved","verdict":"SUPPORTED_REFERENCE_DEFECT","sourceQuote":"An invented rule.","reason":"Not grounded."}
                """, """
                {"testName":"unresolved","verdict":"SUPPORTED_GRADING_WITNESS","sourceQuote":"R1 accepts every integer.","ownerType":"Selector","reason":"Wrong executed outcome."}
                """, """
                {"testName":"unresolved","verdict":"INVALID_WITNESS","reason":"First decision."},
                {"testName":"unresolved","verdict":"SUPPORTED_REFERENCE_DEFECT","sourceQuote":"R1 accepts every integer.","reason":"Conflicting duplicate."}
                """);
    }

    @Test
    void unknownAdditionalTestCannotInvalidateGroundedKnownOutcomes() {
        var review = review(VALID_ROWS + ",{\"testName\":\"invented\",\"verdict\":\"UNKNOWN\",\"reason\":\"Not an observed witness.\"}",
                List.of(outcome(REFERENCE, Disposition.REFERENCE_TEST_FAILED), outcome(GRADING, Disposition.REFERENCE_PASSED_STARTER_FAILED)));

        assertThat(review.supportedWitnesses()).containsExactly(REFERENCE);
        assertThat(review.adoptableWitnesses()).containsExactly(GRADING);
        assertThat(review.unresolvedReferenceWitnesses()).isEmpty();
        assertThat(review.unresolvedAdoptionWitnesses()).isEmpty();
    }

    @Test
    void additionalMetadataDoesNotReplaceRequiredGroundingOrDiscardValidDecisions() {
        var review = review(VALID_ROWS.replace("\"testName\":\"reference\"", "\"confidence\":1,\"testName\":\"reference\""),
                List.of(outcome(REFERENCE, Disposition.REFERENCE_TEST_FAILED), outcome(GRADING, Disposition.REFERENCE_PASSED_STARTER_FAILED)));

        assertThat(review.supportedWitnesses()).containsExactly(REFERENCE);
        assertThat(review.adoptableWitnesses()).containsExactly(GRADING);
        assertThat(review.unresolvedReferenceWitnesses()).isEmpty();
        assertThat(review.unresolvedAdoptionWitnesses()).isEmpty();
    }

    @Test
    void invalidOwnershipCannotClearOrBlockAnUnrelatedReferenceDefect() {
        var review = review(VALID_ROWS.replace("\"ownerType\":\"Selector\"", "\"ownerType\":\"GivenHelper\""),
                List.of(outcome(REFERENCE, Disposition.REFERENCE_TEST_FAILED), outcome(GRADING, Disposition.REFERENCE_PASSED_STARTER_FAILED)));

        assertThat(review.supportedWitnesses()).containsExactly(REFERENCE);
        assertThat(review.adoptableWitnesses()).isEmpty();
        assertThat(review.unresolvedAdoptionWitnesses()).containsExactly(GRADING);
        assertThat(review.findings()).extracting(finding -> finding.kind()).containsExactly(Kind.CONTRACT_CONTRADICTION);
    }

    private static ContractWitnessOutcome outcome(ContractWitness witness, Disposition disposition) {
        return new ContractWitnessOutcome(witness, disposition, "Observed execution result");
    }

    private static SpecFidelityCritic.ReferenceWitnessReview review(String rows, List<ContractWitnessOutcome> outcomes) {
        ReviewerClient reviewer = mock(ReviewerClient.class);
        when(reviewer.configured()).thenReturn(true);
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenReturn("{\"outcomes\":[" + rows + "]}");
        return new ReferenceWitnessCritic(reviewer, JsonMapper.builder().build()).adjudicate(SPECIFICATION, "class Selector {}",
                Map.of("Selector", "student-creates", "GivenHelper", "given"), outcomes, null, () -> false);
    }
}
