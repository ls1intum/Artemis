package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;

class GenerationReviewSupportTest {

    @Test
    void pendingSpecificationEvidenceIsGroupedByRuleRatherThanMutationText() {
        SemanticMutant first = mutant("hardCodedThresholds", "uses fixed thresholds");
        SemanticMutant second = mutant("roundedThresholds", "rounds fractional thresholds");

        SpecFidelityReport report = GenerationReviewSupport.preservePendingSpecApprovalMutants(SpecFidelityReport.empty(), List.of(first, second));

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL);
            assertThat(finding.requirement()).contains("Rule R3");
            assertThat(finding.detail()).contains("uses fixed thresholds", "rounds fractional thresholds", "additional environment-proven variant");
        });
    }

    @Test
    void priorExecutedMutantsAreIncludedInTheNextAuthoringContext() {
        SpecFidelityReport.Finding staticRisk = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "boundary", "check the boundary");
        SemanticMutant executed = mutant("hardCodedThresholds", "uses fixed thresholds");

        List<SpecFidelityReport.Finding> targets = GenerationReviewSupport.withPriorSemanticMutants(List.of(staticRisk), List.of(executed));

        assertThat(targets).hasSize(2);
        assertThat(targets.getFirst().kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL);
        assertThat(targets.getFirst().detail()).contains("uses fixed thresholds");
        assertThat(targets.get(1)).isEqualTo(staticRisk);
    }

    @Test
    void priorExecutedMutantsCannotBePushedPastTheAuthorTargetCap() {
        List<SpecFidelityReport.Finding> staticRisks = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(index -> new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "risk " + index, "static hypothesis")).toList();

        List<SpecFidelityReport.Finding> targets = GenerationReviewSupport.withPriorSemanticMutants(staticRisks, List.of(mutant("hardCodedThresholds", "uses fixed thresholds")));

        assertThat(targets.getFirst().kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL);
        assertThat(targets.subList(0, 4)).extracting(SpecFidelityReport.Finding::requirement).contains("Rule R3 has an environment-proven surviving semantic mutant");
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
