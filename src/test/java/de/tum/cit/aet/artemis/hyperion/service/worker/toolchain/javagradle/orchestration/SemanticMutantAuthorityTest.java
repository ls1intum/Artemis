package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport.Kind;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic.ReferenceWitnessReview;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

class SemanticMutantAuthorityTest {

    private static final ContractWitness WITNESS = new ContractWitness("R1", "resultHistory",
            "@Test void resultHistory() { var first = source.results(); first.add(0); assertEquals(1, source.results().size()); }", "caches a mutable result");

    private static final SemanticMutant MUTANT = new SemanticMutant("R1", "Source.java", "original", "mutated", WITNESS, null);

    private static final SemanticMutantOutcome SURVIVOR = new SemanticMutantOutcome(MUTANT, Disposition.SURVIVED_GRADED_SUITE, "Reference passed; mutant failed the witness.");

    @Test
    void rejectedCounterexampleCannotBecomeAGraderRepairOrCoverageRegression() {
        var review = SemanticMutantAuthority.reconcile(List.of(SURVIVOR), SemanticMutantAuthority.candidates(List.of(SURVIVOR), List.of()),
                new ReferenceWitnessReview(List.of(), List.of(), List.of(), List.of(WITNESS), List.of(), List.of()));
        MutantEvidence evidence = new MutantEvidence();
        review.outcomes().forEach(evidence::record);

        assertThat(evidence.surviving()).isEmpty();
        assertThat(evidence.unresolvedSurvivors()).isEmpty();
        assertThat(review.unresolvedWitnesses()).isEmpty();
        assertThat(review.outcomes().getFirst().diagnostic()).contains("rejected", "SURVIVED_GRADED_SUITE", SURVIVOR.diagnostic());
    }

    @Test
    void missingAuthorityRemainsUnavailableWithoutTurningDiscriminationIntoARequirement() {
        var review = SemanticMutantAuthority.reconcile(List.of(SURVIVOR), SemanticMutantAuthority.candidates(List.of(SURVIVOR), List.of()), ReferenceWitnessReview.empty());

        assertThat(review.outcomes().getFirst().disposition()).isEqualTo(Disposition.INCONCLUSIVE);
        assertThat(review.unresolvedWitnesses()).containsExactly(WITNESS);
        assertThat(SemanticMutantAuthority.unavailableFindings(1)).extracting(finding -> finding.kind()).containsExactly(Kind.EXECUTABLE_EVIDENCE_UNAVAILABLE);
        assertThat(review.outcomes().getFirst().diagnostic()).contains(SURVIVOR.diagnostic());
    }

    @Test
    void approvedSourceAuthorityPreservesExecutedSurvivorAndDoesNotRejudgeUnchangedEvidence() {
        var candidates = SemanticMutantAuthority.candidates(List.of(SURVIVOR), List.of());
        var review = SemanticMutantAuthority.reconcile(List.of(SURVIVOR), candidates,
                new ReferenceWitnessReview(List.of(), List.of(), List.of(WITNESS), List.of(), List.of(), List.of()));

        assertThat(candidates.getFirst().disposition()).isEqualTo(ContractWitnessOutcome.Disposition.REFERENCE_PASSED_MUTANT_FAILED);
        assertThat(review.outcomes()).containsExactly(SURVIVOR);
        assertThat(review.unresolvedWitnesses()).isEmpty();
        assertThat(SemanticMutantAuthority.candidates(review.outcomes(), review.outcomes())).isEmpty();
    }
}
