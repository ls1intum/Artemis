package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic.ReferenceWitnessReview;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

/** A discriminating counterexample is not a contract requirement until its complete interaction has independent source authority. */
final class SemanticMutantAuthority {

    private SemanticMutantAuthority() {
    }

    static List<ContractWitnessOutcome> candidates(List<SemanticMutantOutcome> outcomes, List<SemanticMutantOutcome> alreadyReviewed) {
        return outcomes.stream().filter(outcome -> outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE && !alreadyReviewed.contains(outcome)).map(
                outcome -> new ContractWitnessOutcome(outcome.mutant().counterexample(), ContractWitnessOutcome.Disposition.REFERENCE_PASSED_MUTANT_FAILED, outcome.diagnostic()))
                .toList();
    }

    static Review reconcile(List<SemanticMutantOutcome> outcomes, List<ContractWitnessOutcome> candidates, ReferenceWitnessReview authority) {
        Set<ContractWitness> proposed = candidates.stream().map(ContractWitnessOutcome::witness).collect(Collectors.toSet());
        Set<ContractWitness> approved = Set.copyOf(authority.adoptableWitnesses());
        Set<ContractWitness> invalid = Set.copyOf(authority.invalidWitnesses());
        List<ContractWitness> unavailable = proposed.stream().filter(witness -> !approved.contains(witness) && !invalid.contains(witness)).toList();
        List<SemanticMutantOutcome> classified = outcomes.stream().map(outcome -> {
            ContractWitness witness = outcome.mutant().counterexample();
            if (!proposed.contains(witness) || approved.contains(witness)) {
                return outcome;
            }
            String reason = invalid.contains(witness) ? "The independent contract review rejected this witness."
                    : "Independent contract authority is unavailable for this witness.";
            return new SemanticMutantOutcome(outcome.mutant(), Disposition.INCONCLUSIVE,
                    reason + " The executed outcome was " + outcome.disposition() + "; discrimination alone does not justify a grader repair. " + outcome.diagnostic());
        }).toList();
        return new Review(classified, unavailable);
    }

    static List<SpecFidelityReport.Finding> unavailableFindings(int count) {
        return count == 0 ? List.of()
                : SpecFidelityReport.executableEvidenceUnavailable(count
                        + " executed mutant counterexample(s) lack independent contract authority. Their execution is retained, but they cannot require new grader assertions.")
                        .findings();
    }

    record Review(List<SemanticMutantOutcome> outcomes, List<ContractWitness> unresolvedWitnesses) {
    }
}
