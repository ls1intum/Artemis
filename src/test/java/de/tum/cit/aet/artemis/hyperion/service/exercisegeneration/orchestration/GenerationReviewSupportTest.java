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
        assertThat(targets.getFirst()).isEqualTo(staticRisk);
        assertThat(targets.get(1).kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL);
        assertThat(targets.get(1).detail()).contains("uses fixed thresholds");
    }

    private static SemanticMutant mutant(String testName, String wrongBehavior) {
        return new SemanticMutant("R3", "src/Classifier.java", "class Classifier {}", "class Classifier { int " + testName + "; }",
                new ContractWitness("R3", testName, "@Test void " + testName + "() {}", wrongBehavior));
    }
}
