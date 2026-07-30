package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService.ReferenceWitnessReview;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome;

/** Pure collection operations for carrying executable reference-witness evidence between review rounds. */
final class ReferenceWitnessEvidence {

    private ReferenceWitnessEvidence() {
    }

    static List<ContractWitness> candidates(List<ContractWitness> awaitingPass, List<ContractWitness> awaitingAdjudication, List<ContractWitness> pendingAdoption,
            List<ContractWitness> authored) {
        Map<String, ContractWitness> byName = new LinkedHashMap<>();
        Stream.of(awaitingPass, awaitingAdjudication, pendingAdoption, authored).flatMap(List::stream).forEach(witness -> byName.putIfAbsent(witness.testName(), witness));
        return List.copyOf(byName.values());
    }

    static List<ContractWitnessOutcome> freshFailures(List<SemanticMutantOutcome> freshMutantOutcomes, List<ContractWitnessOutcome> witnessOutcomes,
            Set<String> pendingWitnessNames) {
        Map<String, ContractWitnessOutcome> byName = new LinkedHashMap<>();
        freshMutantOutcomes.stream().filter(outcome -> outcome.disposition() == SemanticMutantOutcome.Disposition.REFERENCE_TEST_FAILED)
                .map(outcome -> new ContractWitnessOutcome(outcome.mutant().counterexample(), ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, outcome.diagnostic()))
                .forEach(outcome -> byName.putIfAbsent(outcome.witness().testName(), outcome));
        witnessOutcomes.stream().filter(outcome -> outcome.disposition() == ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED)
                .filter(outcome -> !pendingWitnessNames.contains(outcome.witness().testName())).forEach(outcome -> byName.putIfAbsent(outcome.witness().testName(), outcome));
        return List.copyOf(byName.values());
    }

    static State reconcile(List<ContractWitnessOutcome> freshFailures, List<ContractWitnessOutcome> witnessOutcomes, Set<String> pendingWitnessNames,
            Set<String> pendingAdjudicationNames, ReferenceWitnessReview review) {
        Set<String> adjudicatedNames = Stream.of(review.supportedWitnesses(), review.invalidWitnesses(), review.unresolvedWitnesses()).flatMap(List::stream)
                .map(ContractWitness::testName).collect(java.util.stream.Collectors.toSet());
        List<ContractWitness> omitted = freshFailures.stream().map(ContractWitnessOutcome::witness).filter(witness -> !adjudicatedNames.contains(witness.testName())).toList();
        List<ContractWitness> stillFailing = witnessesWith(ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, witnessOutcomes, pendingWitnessNames);
        List<ContractWitness> pendingInconclusive = witnessesWith(ContractWitnessOutcome.Disposition.INCONCLUSIVE, witnessOutcomes, pendingWitnessNames);
        List<ContractWitness> adjudicationInconclusive = witnessesWith(ContractWitnessOutcome.Disposition.INCONCLUSIVE, witnessOutcomes, pendingAdjudicationNames);
        List<ContractWitness> awaitingPass = Stream.concat(Stream.concat(stillFailing.stream(), pendingInconclusive.stream()), review.supportedWitnesses().stream()).distinct()
                .toList();
        List<ContractWitness> awaitingAdjudication = Stream.concat(Stream.concat(adjudicationInconclusive.stream(), review.unresolvedWitnesses().stream()), omitted.stream())
                .distinct().toList();
        return new State(awaitingPass, awaitingAdjudication, omitted, stillFailing, pendingInconclusive, adjudicationInconclusive);
    }

    private static List<ContractWitness> witnessesWith(ContractWitnessOutcome.Disposition disposition, List<ContractWitnessOutcome> outcomes, Set<String> names) {
        return outcomes.stream().filter(outcome -> outcome.disposition() == disposition).map(ContractWitnessOutcome::witness).filter(witness -> names.contains(witness.testName()))
                .toList();
    }

    record State(List<ContractWitness> awaitingPass, List<ContractWitness> awaitingAdjudication, List<ContractWitness> omittedFromAdjudication, List<ContractWitness> stillFailing,
            List<ContractWitness> pendingInconclusive, List<ContractWitness> adjudicationInconclusive) {

        State {
            awaitingPass = List.copyOf(awaitingPass);
            awaitingAdjudication = List.copyOf(awaitingAdjudication);
            omittedFromAdjudication = List.copyOf(omittedFromAdjudication);
            stillFailing = List.copyOf(stillFailing);
            pendingInconclusive = List.copyOf(pendingInconclusive);
            adjudicationInconclusive = List.copyOf(adjudicationInconclusive);
        }
    }
}
