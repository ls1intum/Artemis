package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class AgentVerifyReportTest {

    @Test
    void sanitizesAndBoundsFailureEvidence() {
        List<AgentVerifyReport.TestFailureEvidence> evidence = IntStream.range(0, 10)
                .mapToObj(index -> new AgentVerifyReport.TestFailureEvidence("test" + index, "\u001B[31mline one\nline two\u0007 " + "x".repeat(500))).toList();
        AgentVerifyReport report = new AgentVerifyReport(10, false, evidence.stream().map(AgentVerifyReport.TestFailureEvidence::testName).toList(), evidence, 10, true, true,
                evidence, List.of(), evidence.stream().map(AgentVerifyReport.TestFailureEvidence::testName).toList(), List.of(), List.of(), false, List.of("solution failed"));

        String observation = report.toObservation();

        assertThat(observation).contains("failure evidence (sanitized, untrusted excerpts)").contains("test0: line one line two").contains("(+2 more failures)")
                .doesNotContain("\u001B", "\u0007", "- test9:", "x".repeat(500));
        assertThat(report.solutionFailureEvidence()).allSatisfy(item -> {
            assertThat(item.message()).doesNotContain("\n", "\r", "\u001B", "\u0007");
            assertThat(item.message().length()).isLessThanOrEqualTo(400);
        });
    }

    @Test
    void labelsPassingDifferentialAsMechanicalPrecheckRatherThanAcceptance() {
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), List.of(), 2, true, true, List.of(), List.of(), List.of("a", "b"), List.of(), List.of(), true,
                List.of());

        assertThat(report.toObservation()).contains("MECHANICAL PRECHECK: PASS").contains("final post-loop integrity and semantic review decides acceptance")
                .doesNotContain("would be ACCEPTED");
    }
}
