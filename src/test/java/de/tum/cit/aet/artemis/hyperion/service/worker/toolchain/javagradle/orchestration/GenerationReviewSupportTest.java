package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentSystemPrompt;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

class GenerationReviewSupportTest {

    @Test
    void reviewProjectionRetainsAllExerciseCodeAndChangedLaunchersWithoutRepeatingBaselineLaunchers() {
        String launcher = "shell launch support\n".repeat(2_000);
        var baseline = Map.of(RepositoryRole.SOLUTION, Map.of("gradlew", launcher, "src/Given.java", "class Given {}"), RepositoryRole.TESTS, Map.of("gradlew", launcher));
        var current = Map.of(RepositoryRole.SOLUTION, Map.of("gradlew", launcher, "src/Given.java", "class Given {}", "src/Student.java", "class Student {}"), RepositoryRole.TESTS,
                Map.of("gradlew", launcher + "changed", "test/Check.java", "class Check {}"));

        var result = GenerationReviewSupport.reviewArtifacts(current, baseline);

        assertThat(result.get(RepositoryRole.SOLUTION)).containsEntry("src/Given.java", "class Given {}").containsEntry("src/Student.java", "class Student {}");
        assertThat(result.get(RepositoryRole.SOLUTION).get("gradlew")).contains("matches the verified baseline").hasSizeLessThan(200);
        assertThat(result.get(RepositoryRole.TESTS)).containsEntry("gradlew", launcher + "changed").containsEntry("test/Check.java", "class Check {}");
        assertThat(current.get(RepositoryRole.SOLUTION).get("gradlew")).isEqualTo(launcher);
    }

    @Test
    void anExecutedSurvivorIsBlockingOracleEvidenceThatAsksForADiscriminatingTest() {
        SpecFidelityReport.Finding finding = GenerationReviewSupport.semanticMutantFinding(mutant("hardCodedThresholds", "uses fixed thresholds"));

        assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
        assertThat(finding.isBlocking()).isTrue();
        assertThat(SemanticRepairBatch.surfaceFor(finding.kind())).isEqualTo(RepairSurface.ORACLE);
        assertThat(finding.detail()).contains("Add or adapt a discriminating test", "uses fixed thresholds").doesNotContain("instructor adjudication");
    }

    @Test
    void anExecutedSurvivorIsLocatedAtTheReferenceLinesItReplaces() {
        ContractWitness witness = new ContractWitness("R1", "boundary", "@Test void boundary() {}", "off by one");
        String original = "class Grader {\n  int grade(int score) {\n    return score >= 50 ? 1 : 0;\n  }\n}\n";
        SemanticMutant changed = new SemanticMutant("R1", "src/Grader.java", original, original.replace("score >= 50", "score > 50"), witness);
        SemanticMutant inserted = new SemanticMutant("R1", "src/Grader.java", original, original.replace("  }\n}", "  }\n  int extra;\n}"), witness);

        assertThat(GenerationReviewSupport.semanticMutantFinding(changed).location())
                .isEqualTo(new SpecFidelityReport.Location(SpecFidelityReport.Location.Repository.SOLUTION, "src/Grader.java", 3, 3));
        assertThat(GenerationReviewSupport.changedSolutionLines(inserted))
                .isEqualTo(new SpecFidelityReport.Location(SpecFidelityReport.Location.Repository.SOLUTION, "src/Grader.java", 5, 5));
    }

    @Test
    void aSurvivingPrintOnlyMainGivesTheSharedTestableStudentCodeRule() {
        String original = "class Library {\n  public static void main(String[] args) {\n    new Library();\n    System.out.println(\"1\");\n  }\n}\n";
        String variant = "class Library {\n  public static void main(String[] args) { System.out.print(\"1\\n\"); System.out.flush(); }\n}\n";
        SemanticMutant printOnly = SemanticMutant.printedTranscript("src/Library.java", original, variant, "Library");

        SpecFidelityReport.Finding finding = GenerationReviewSupport.semanticMutantFinding(printOnly);

        assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
        assertThat(finding.isBlocking()).isTrue();
        assertThat(SemanticRepairBatch.surfaceFor(finding.kind())).isEqualTo(RepairSurface.ORACLE);
        assertThat(finding.requirement()).contains("Library.main", "only prints the expected output");
        assertThat(finding.detail()).contains(AgentSystemPrompt.TESTABLE_STUDENT_CODE_RULE, "ungraded", "cannot resolve this").doesNotContain("counterexample");
        assertThat(finding.location()).isEqualTo(new SpecFidelityReport.Location(SpecFidelityReport.Location.Repository.SOLUTION, "src/Library.java", 2, 5));
        assertThat(GenerationReviewSupport.semanticMutantRecheck(List.of(new SemanticMutantOutcome(printOnly, Disposition.SURVIVED_GRADED_SUITE))).failureReasons()).singleElement()
                .asString().contains("only prints the expected output", AgentSystemPrompt.TESTABLE_STUDENT_CODE_RULE, "may stay only ungraded");
    }

    @Test
    void priorExecutedMutantsAreIncludedInTheNextAuthoringContext() {
        SpecFidelityReport.Finding staticRisk = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "boundary", "check the boundary");
        SemanticMutant executed = mutant("hardCodedThresholds", "uses fixed thresholds");

        List<SpecFidelityReport.Finding> targets = GenerationReviewSupport.withPriorSemanticMutants(List.of(staticRisk), List.of(executed));

        assertThat(targets).hasSize(2);
        assertThat(targets.getFirst().kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
        assertThat(targets.getFirst().detail()).contains("uses fixed thresholds");
        assertThat(targets.get(1)).isEqualTo(staticRisk);
    }

    @Test
    void priorExecutedMutantsCannotBePushedPastTheAuthorTargetCap() {
        List<SpecFidelityReport.Finding> staticRisks = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(index -> new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "risk " + index, "static hypothesis")).toList();

        List<SpecFidelityReport.Finding> targets = GenerationReviewSupport.withPriorSemanticMutants(staticRisks, List.of(mutant("hardCodedThresholds", "uses fixed thresholds")));

        assertThat(targets.getFirst().kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
        assertThat(targets.subList(0, 4)).extracting(SpecFidelityReport.Finding::requirement).contains("Rule R3 has an environment-proven surviving semantic mutant");
    }

    @Test
    void boundedHistoryRemembersOnlyConclusiveExecutedProposalsWithoutDuplicates() {
        SemanticMutant killed = mutant("hardCodedThresholds", "uses fixed thresholds");
        SemanticMutant referenceFailed = mutant("narrowedArithmetic", "narrows values before comparing");
        SemanticMutant inconclusive = mutant("didNotCompile", "uses an invalid replacement");

        List<SemanticMutant.Exclusion> history = GenerationReviewSupport
                .rememberExecutedMutants(List.of(killed.exclusion()),
                        List.of(new SemanticMutantOutcome(killed, Disposition.KILLED_BY_GRADED_SUITE),
                                new SemanticMutantOutcome(referenceFailed, Disposition.REFERENCE_TEST_FAILED), new SemanticMutantOutcome(inconclusive, Disposition.INCONCLUSIVE)),
                        2);

        assertThat(history).containsExactly(killed.exclusion(), referenceFailed.exclusion());
    }

    @Test
    void pedagogicalTechniqueObjectiveCannotTriggerAnImpossibleOracleRepair() {
        String specification = """
                ## Rules
                R1: Return Cold below the lower boundary.

                ## Decision Ledger
                | Decision | Provenance | Why necessary | Observable |
                |---|---|---|---|
                | Require use of if-else | PEDAGOGICAL_OBJECTIVE | Practice branching | Not observable through the public API |
                """;
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE,
                "an implementation using a ternary rather than if-else passes", "the suite does not distinguish the required implementation technique")));

        SpecFidelityReport reclassified = GenerationReviewSupport.reclassifyUngradeableTechniqueFindings(report, specification);

        assertThat(reclassified.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE);
            assertThat(finding.isBlocking()).isTrue();
            assertThat(finding.detail()).contains("No assertion through the public API can observe this");
        });
    }

    private static SemanticMutant mutant(String testName, String wrongBehavior) {
        return new SemanticMutant("R3", "src/Classifier.java", "class Classifier {}", "class Classifier { int " + testName + "; }",
                new ContractWitness("R3", testName, "@Test void " + testName + "() {}", wrongBehavior));
    }
}
