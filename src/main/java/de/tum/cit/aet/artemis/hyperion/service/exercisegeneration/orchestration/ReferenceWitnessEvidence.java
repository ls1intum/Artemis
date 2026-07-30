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

/** Pure collection operations for carrying executable contract-witness evidence between review rounds. */
final class ReferenceWitnessEvidence {

    private ReferenceWitnessEvidence() {
    }

    static List<ContractWitness> candidates(List<ContractWitness> awaitingPass, List<ContractWitness> awaitingReferenceAdjudication,
            List<ContractWitness> awaitingAdoptionAdjudication, List<ContractWitness> pendingAdoption, List<ContractWitness> authored) {
        Map<String, ContractWitness> byName = new LinkedHashMap<>();
        Stream.of(awaitingPass, awaitingReferenceAdjudication, awaitingAdoptionAdjudication, pendingAdoption, authored).flatMap(List::stream)
                .forEach(witness -> byName.putIfAbsent(witness.testName(), witness));
        return List.copyOf(byName.values());
    }

    /**
     * Selects only outcomes that still need a source-authority decision. Previously supported reference defects await an exact pass, while a previously approved adoption is
     * already source-grounded.
     */
    static List<ContractWitnessOutcome> adjudicationCandidates(List<SemanticMutantOutcome> freshMutantOutcomes, List<ContractWitnessOutcome> witnessOutcomes,
            Set<String> awaitingPassNames, Set<String> approvedAdoptionNames, Set<String> excludedPositiveRules) {
        Map<String, ContractWitnessOutcome> byName = new LinkedHashMap<>();
        freshMutantOutcomes.stream().filter(outcome -> outcome.disposition() == SemanticMutantOutcome.Disposition.REFERENCE_TEST_FAILED)
                .map(outcome -> new ContractWitnessOutcome(outcome.mutant().counterexample(), ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, outcome.diagnostic()))
                .forEach(outcome -> byName.putIfAbsent(outcome.witness().testName(), outcome));
        witnessOutcomes.stream().filter(outcome -> {
            if (outcome.disposition() == ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED) {
                return !awaitingPassNames.contains(outcome.witness().testName());
            }
            return outcome.disposition() == ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED && !awaitingPassNames.contains(outcome.witness().testName())
                    && !approvedAdoptionNames.contains(outcome.witness().testName()) && !excludedPositiveRules.contains(outcome.witness().ruleId());
        }).forEach(outcome -> byName.putIfAbsent(outcome.witness().testName(), outcome));
        return List.copyOf(byName.values());
    }

    static List<ContractWitness> approvedForAdoption(List<ContractWitnessOutcome> outcomes, Set<String> approvedNames) {
        return outcomes.stream().filter(outcome -> outcome.disposition() == ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED).map(ContractWitnessOutcome::witness)
                .filter(witness -> approvedNames.contains(witness.testName())).toList();
    }

    static State reconcile(List<ContractWitnessOutcome> adjudicationCandidates, List<ContractWitnessOutcome> witnessOutcomes, Set<String> awaitingPassNames,
            Set<String> awaitingReferenceAdjudicationNames, Set<String> awaitingAdoptionAdjudicationNames, ReferenceWitnessReview review) {
        Set<String> adjudicatedNames = Stream.of(review.supportedWitnesses(), review.adoptableWitnesses(), review.invalidWitnesses(), review.unresolvedReferenceWitnesses(),
                review.unresolvedAdoptionWitnesses()).flatMap(List::stream).map(ContractWitness::testName).collect(java.util.stream.Collectors.toSet());
        List<ContractWitnessOutcome> omitted = adjudicationCandidates.stream().filter(outcome -> !adjudicatedNames.contains(outcome.witness().testName())).toList();
        List<ContractWitness> omittedReference = witnessesWith(ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, omitted, null);
        List<ContractWitness> omittedAdoption = witnessesWith(ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED, omitted, null);

        List<ContractWitness> stillFailing = witnessesWith(ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, witnessOutcomes, awaitingPassNames);
        List<ContractWitness> pendingPassInconclusive = witnessesWith(ContractWitnessOutcome.Disposition.INCONCLUSIVE, witnessOutcomes, awaitingPassNames);
        List<ContractWitness> referenceAdjudicationInconclusive = witnessesWith(ContractWitnessOutcome.Disposition.INCONCLUSIVE, witnessOutcomes,
                awaitingReferenceAdjudicationNames);
        List<ContractWitness> adoptionAdjudicationInconclusive = witnessesWith(ContractWitnessOutcome.Disposition.INCONCLUSIVE, witnessOutcomes, awaitingAdoptionAdjudicationNames);

        List<ContractWitness> awaitingPass = Stream.concat(Stream.concat(stillFailing.stream(), pendingPassInconclusive.stream()), review.supportedWitnesses().stream()).distinct()
                .toList();
        List<ContractWitness> awaitingReferenceAdjudication = Stream.of(referenceAdjudicationInconclusive, review.unresolvedReferenceWitnesses(), omittedReference)
                .flatMap(List::stream).distinct().toList();
        List<ContractWitness> awaitingAdoptionAdjudication = Stream.of(adoptionAdjudicationInconclusive, review.unresolvedAdoptionWitnesses(), omittedAdoption)
                .flatMap(List::stream).distinct().toList();
        return new State(awaitingPass, awaitingReferenceAdjudication, awaitingAdoptionAdjudication, omittedReference, stillFailing, pendingPassInconclusive,
                referenceAdjudicationInconclusive);
    }

    private static List<ContractWitness> witnessesWith(ContractWitnessOutcome.Disposition disposition, List<ContractWitnessOutcome> outcomes, Set<String> names) {
        return outcomes.stream().filter(outcome -> outcome.disposition() == disposition).map(ContractWitnessOutcome::witness)
                .filter(witness -> names == null || names.contains(witness.testName())).toList();
    }

    record State(List<ContractWitness> awaitingPass, List<ContractWitness> awaitingReferenceAdjudication, List<ContractWitness> awaitingAdoptionAdjudication,
            List<ContractWitness> omittedReferenceAdjudication, List<ContractWitness> stillFailing, List<ContractWitness> pendingPassInconclusive,
            List<ContractWitness> referenceAdjudicationInconclusive) {

        State {
            awaitingPass = List.copyOf(awaitingPass);
            awaitingReferenceAdjudication = List.copyOf(awaitingReferenceAdjudication);
            awaitingAdoptionAdjudication = List.copyOf(awaitingAdoptionAdjudication);
            omittedReferenceAdjudication = List.copyOf(omittedReferenceAdjudication);
            stillFailing = List.copyOf(stillFailing);
            pendingPassInconclusive = List.copyOf(pendingPassInconclusive);
            referenceAdjudicationInconclusive = List.copyOf(referenceAdjudicationInconclusive);
        }
    }
}
