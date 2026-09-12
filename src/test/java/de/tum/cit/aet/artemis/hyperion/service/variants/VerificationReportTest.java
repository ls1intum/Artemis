package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.variants.VerificationReport.VerificationFinding;
import de.tum.cit.aet.artemis.hyperion.service.variants.VerificationReport.VerificationGate;

/**
 * Unit tests for the stuck-repair-loop detection helpers: two build-failure messages that differ only in the
 * volatile parts of a build log (line numbers, timestamps, container/job IDs, test counts) must produce the SAME
 * signature, while genuinely different failures must not.
 */
class VerificationReportTest {

    @Test
    void shouldProduceTheSameSignatureWhenOnlyDigitsDiffer() {
        VerificationFinding first = new VerificationFinding(VerificationGate.TEMPLATE_BUILD,
                "package org.junit.jupiter.api does not exist\nBuild job 1391784886958220 took 14.89sec");
        VerificationFinding second = new VerificationFinding(VerificationGate.TEMPLATE_BUILD,
                "package org.junit.jupiter.api does not exist\nBuild job 1351784883423869 took 12.03sec");

        assertThat(first.stableSignature()).isEqualTo(second.stableSignature());
    }

    @Test
    void shouldProduceDifferentSignaturesForDifferentFailures() {
        VerificationFinding first = new VerificationFinding(VerificationGate.TEMPLATE_BUILD, "package org.junit.jupiter.api does not exist");
        VerificationFinding second = new VerificationFinding(VerificationGate.TEMPLATE_BUILD, "incompatible types: MergeSort cannot be converted to SortStrategy");

        assertThat(first.stableSignature()).isNotEqualTo(second.stableSignature());
    }

    @Test
    void shouldProduceDifferentSignaturesForTheSameMessageOnDifferentGates() {
        VerificationFinding solutionFinding = new VerificationFinding(VerificationGate.SOLUTION_BUILD, "some shared tail text");
        VerificationFinding templateFinding = new VerificationFinding(VerificationGate.TEMPLATE_BUILD, "some shared tail text");

        assertThat(solutionFinding.stableSignature()).isNotEqualTo(templateFinding.stableSignature());
    }

    @Test
    void shouldOnlyCompareTheTailOfAVeryLongMessage() {
        // Exactly SIGNATURE_TAIL_LENGTH (400) characters and not whitespace-terminated, so it IS the tail exactly
        // (a trailing space would get trimmed by strip() before truncation, shifting the window by one char).
        String sharedTail = "failure-".repeat(50);
        VerificationFinding first = new VerificationFinding(VerificationGate.TEMPLATE_BUILD, "A".repeat(1000) + sharedTail);
        VerificationFinding second = new VerificationFinding(VerificationGate.TEMPLATE_BUILD, "totally different and much shorter prefix ".repeat(20) + sharedTail);

        assertThat(first.stableSignature()).isEqualTo(second.stableSignature());
    }

    @Test
    void findingSignaturesShouldReturnOneEntryPerDistinctSignature() {
        VerificationReport report = new VerificationReport(false, List.of(new VerificationFinding(VerificationGate.SOLUTION_BUILD, "error at line 12"),
                new VerificationFinding(VerificationGate.SOLUTION_BUILD, "error at line 99"), new VerificationFinding(VerificationGate.CONSISTENCY, "unrelated semantic issue")));

        Set<String> signatures = report.findingSignatures();

        assertThat(signatures).hasSize(2);
    }

    @Test
    void findingSignaturesShouldBeEmptyWhenReportPassed() {
        VerificationReport report = new VerificationReport(true, List.of());

        assertThat(report.findingSignatures()).isEmpty();
    }
}
