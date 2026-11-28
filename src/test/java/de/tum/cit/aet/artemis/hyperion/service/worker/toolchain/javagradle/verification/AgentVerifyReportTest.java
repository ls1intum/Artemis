package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentVerifyReportTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void starterFeedbackShowsFailingPreservationChecksWithoutExpectedStudentWorkFailures(boolean includeStatementGuidance) {
        var expected = new AgentVerifyReport.TestFailureEvidence("testClass[Extension]", "ClassNotFoundException: Extension");
        var preservation = new AgentVerifyReport.TestFailureEvidence("keepsSuppliedBalance", "expected: <10> but was: <0>");
        var report = new AgentVerifyReport(3, true, List.of(), List.of(), 3, true, false, List.of(expected, preservation), List.of("rejectsBrokenWork"),
                List.of(expected.testName(), preservation.testName(), "rejectsBrokenWork"), List.of(), List.of(), false, List.of("starter passes assessed work"), List.of(), "", "",
                List.of(preservation.testName()));

        String observation = includeStatementGuidance ? report.toObservation() : report.toTestsStageObservation();

        assertThat(observation).contains("Template WRONGLY PASSES", "rejectsBrokenWork", preservation.message()).doesNotContain(expected.message());
        assertThat(report.templateFailureEvidence()).containsExactly(expected, preservation);
        assertThat(report.wouldBeAccepted()).isFalse();
    }

    @Test
    void expectedMissingDeclarationsAreNotRepairTargetsWhenAnotherGradedTestPassesOnTheStarter() {
        var expected = new AgentVerifyReport.TestFailureEvidence("testClass[Extension]", "ClassNotFoundException: Extension");
        var report = new AgentVerifyReport(2, true, List.of(), List.of(), 2, true, false, List.of(expected), List.of("rejectsBrokenWork"),
                List.of(expected.testName(), "rejectsBrokenWork"), List.of(), List.of(), false, List.of("starter passes assessed work"));

        assertThat(report.toTestsStageObservation()).contains("Template WRONGLY PASSES", "rejectsBrokenWork", "MECHANICAL PRECHECK: FAIL").doesNotContain(expected.message(),
                "Template failure evidence");
        assertThat(report.templateFailureEvidence()).containsExactly(expected);
    }

    @Test
    void anInvalidPlanDoesNotTurnExpectedMissingDeclarationsOrPreservationPassesIntoRepairTargets() {
        var missingClass = new AgentVerifyReport.TestFailureEvidence("testClass[Token]", "ClassNotFoundException: Token");
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), List.of(), 2, true, false, List.of(missingClass), List.of("providedConstructor"),
                List.of("testClass[Token]", "providedConstructor"), List.of(), List.of(), false, List.of("Invalid test-plan.json: duplicate test name"), List.of(), "", "",
                List.of(), false);

        assertThat(report.toTestsStageObservation()).contains("Starter-policy assessment is unavailable", "Invalid test-plan.json", "compiled and ran 2 tests")
                .doesNotContain("Template WRONGLY PASSES", "Template does not satisfy", "ClassNotFoundException");
        assertThat(report.templateFailureEvidence()).containsExactly(missingClass);
        assertThat(report.wouldBeAccepted()).isFalse();
    }

    @Test
    void testsStageDefersTaskBindingGuidance() {
        AgentVerifyReport report = new AgentVerifyReport(1, true, List.of(), 1, true, true, List.of(), List.of("calculatesCost"), List.of(), List.of(), true, List.of());

        assertThat(report.toTestsStageObservation()).contains("test-plan.json", "later STATEMENT stage").doesNotContain("bind each [task]", "Each credited check must first");
    }

    @Test
    void aCreditedCheckThatPassesOnTheStarterGetsPairedGradingGuidance() {
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of("acceptsCorrectTests"), List.of("acceptsCorrectTests", "rejectsFaultyBehavior"),
                List.of(), List.of(), false, List.of("starter passes assessed work"));

        assertThat(report.toTestsStageObservation()).contains("acceptsCorrectTests", "Each credited check must first", "then require them to reject",
                "keep the supplied implementation correct");
    }

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
    void rendersFailureEvidenceInAStableOrderWhateverTheReportOrder() {
        var later = new AgentVerifyReport.TestFailureEvidence("zeroBalanceIsRejected", "expected: <true> but was: <false>");
        var earlier = new AgentVerifyReport.TestFailureEvidence("addsInterest", "expected: <105.0> but was: <100.0>");

        String rendered = AgentVerifyReport.renderFailureEvidence("Solution", List.of(later, earlier));

        assertThat(rendered).isEqualTo(AgentVerifyReport.renderFailureEvidence("Solution", List.of(earlier, later)))
                .isEqualTo("Solution failure evidence (sanitized, untrusted excerpts):\n- addsInterest: expected: <105.0> but was: <100.0>\n"
                        + "- zeroBalanceIsRejected: expected: <true> but was: <false>\n");
        assertThat(AgentVerifyReport.renderFailureEvidence("Solution", List.of())).isEmpty();
    }

    @Test
    void solutionFailureEvidencePrecedesStarterFeedbackWithoutDuplicatingItsBlockingReason() {
        var failure = new AgentVerifyReport.TestFailureEvidence("addsInterest", "expected: <105.0> but was: <100.0>");
        String reason = "The solution does not pass its own tests (1 failing of 1).\n" + AgentVerifyReport.renderFailureEvidence("Solution", List.of(failure));
        var expectedStarterFailure = new AgentVerifyReport.TestFailureEvidence("addsInterest", "expected starter failure");
        AgentVerifyReport report = new AgentVerifyReport(1, false, List.of("addsInterest"), List.of(failure), 1, true, true, List.of(expectedStarterFailure), List.of(),
                List.of("addsInterest"), List.of(), List.of(), false, List.of(reason));

        String observation = report.toObservation();

        assertThat(observation).containsOnlyOnce("addsInterest: expected: <105.0> but was: <100.0>").contains("Solution FAILS: [addsInterest]")
                .doesNotContain(expectedStarterFailure.message());
        assertThat(observation.indexOf(failure.message())).isLessThan(observation.indexOf("Template:"));
    }

    @Test
    void failedStarterPolicyStillReportsItsFailureEvidence() {
        var failure = new AgentVerifyReport.TestFailureEvidence("keepsSuppliedBalance", "expected: <10> but was: <0>");
        AgentVerifyReport report = new AgentVerifyReport(1, true, List.of(), List.of(), 1, true, false, List.of(failure), List.of(), List.of("keepsSuppliedBalance"), List.of(),
                List.of(), false, List.of("supplied behavior changed"), List.of(), "", "", List.of("keepsSuppliedBalance"));

        assertThat(report.toTestsStageObservation()).contains(failure.testName(), failure.message());
    }

    @Test
    void describesMechanicalSaveEligibilityWithoutClaimingQualityAcceptance() {
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), List.of(), 2, true, true, List.of(), List.of(), List.of("a", "b"), List.of(), List.of(), true,
                List.of());

        assertThat(report.toObservation()).contains("Template: all required gradable tests fail; supplied-behavior preservation checks pass.").contains(
                "MECHANICAL PRECHECK: PASS — authoritative post-loop verification determines save eligibility; quality review may request repairs or flag instructor review.")
                .doesNotContain("acceptance", "correctly fails all 2", "would be ACCEPTED");
    }

    @Test
    void aBuildThatRunsNoTestsIncludesItsBoundedCompilerDiagnostic() {
        AgentVerifyReport report = new AgentVerifyReport(0, false, List.of(), List.of(), 0, false, false, List.of(), List.of(), List.of(), List.of(), List.of(), false,
                List.of("solution ran no tests"), List.of(), "ElevatorDispatcher.java:42: error: call to this must be first statement",
                "Template.java:7: error: cannot find symbol");

        assertThat(report.toTestsStageObservation()).contains("Solution build diagnostic (bounded, sanitized, untrusted output)",
                "ElevatorDispatcher.java:42: error: call to this must be first statement", "Template build diagnostic (bounded, sanitized, untrusted output)",
                "Template.java:7: error: cannot find symbol");
    }

    @Test
    void exactRuntimeConstructorMismatchExplainsTheDeclaredSignatureReflectionPattern() {
        var failure = new AgentVerifyReport.TestFailureEvidence("dispatcherDelegates",
                "Could not instantiate ElevatorDispatcher because the class does not have a constructor with the arguments: [ ArrayList, RoundRobinStrategy ]");
        AgentVerifyReport report = new AgentVerifyReport(1, false, List.of("dispatcherDelegates"), List.of(failure), 1, true, true, List.of(), List.of(),
                List.of("dispatcherDelegates"), List.of(), List.of(), false, List.of("solution failed"));

        assertThat(report.toTestsStageObservation()).contains("Ares newInstance(className, arguments...) infers exact runtime argument classes",
                "getConstructor(getClazz(\"package.Owner\"), List.class, getClazz(\"package.Collaborator\"))", "do NOT add concrete overloads to production code");
    }

    @Test
    void unrelatedConstructorFailureDoesNotEmitAresSpecificGuidance() {
        var failure = new AgentVerifyReport.TestFailureEvidence("constructsDispatcher", "InvocationTargetException: constructor rejected the empty list");
        AgentVerifyReport report = new AgentVerifyReport(1, false, List.of("constructsDispatcher"), List.of(failure), 1, true, true, List.of(), List.of(),
                List.of("constructsDispatcher"), List.of(), List.of(), false, List.of("solution failed"));

        assertThat(report.toTestsStageObservation()).doesNotContain("REFLECTION HARNESS DIAGNOSTIC");
    }

    @ParameterizedTest
    @ValueSource(strings = { "The class 'Extension' was not found within the submission. Make sure to implement it properly.", "java.lang.ClassNotFoundException: Extension" })
    void referenceClassLookupFailureExplainsFullyQualifiedNames(String message) {
        var failure = new AgentVerifyReport.TestFailureEvidence("loadsExtension", message);
        AgentVerifyReport report = new AgentVerifyReport(1, false, List.of("loadsExtension"), List.of(failure), 1, true, true, List.of(), List.of(), List.of("loadsExtension"),
                List.of(), List.of(), false, List.of("reference failed"));

        assertThat(report.toTestsStageObservation()).contains("Class.forName", "fully qualified binary name", "Imports do not qualify string arguments")
                .contains("do not add student-created declarations to the template");
        assertThat(report.toObservation()).contains("Class.forName", "fully qualified binary name");
    }

    @Test
    void expectedMissingStarterClassDoesNotSuggestChangingTheVerifiedReference() {
        var failure = new AgentVerifyReport.TestFailureEvidence("loadsExtension", "The class 'Extension' was not found within the submission.");
        AgentVerifyReport report = new AgentVerifyReport(1, true, List.of(), List.of(), 1, true, true, List.of(failure), List.of(), List.of("loadsExtension"), List.of(), List.of(),
                true, List.of());

        assertThat(report.toTestsStageObservation()).doesNotContain("REFLECTION CLASS LOOKUP DIAGNOSTIC");
    }
}
