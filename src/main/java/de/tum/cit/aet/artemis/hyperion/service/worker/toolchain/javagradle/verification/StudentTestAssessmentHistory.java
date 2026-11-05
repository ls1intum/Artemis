package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

/** Keeps an executed learner-test counterexample binding when a later candidate removes its probe surface. Owned by one generation's verifier. */
final class StudentTestAssessmentHistory {

    private record Probe(String sessionId, String path, SemanticMutant.Evidence kind) {

        static Probe of(String sessionId, SemanticMutant mutant) {
            return new Probe(sessionId, mutant.solutionPath(), mutant.evidence());
        }
    }

    private final Map<Probe, SemanticMutant> required = new LinkedHashMap<>();

    List<SemanticMutantOutcome> complete(String sessionId, List<SemanticMutantOutcome> current) {
        current.stream().filter(outcome -> outcome.mutant().testsLearnerAssertions() && outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE)
                .forEach(outcome -> required.putIfAbsent(Probe.of(sessionId, outcome.mutant()), outcome.mutant()));
        Set<Probe> observed = current.stream().map(outcome -> Probe.of(sessionId, outcome.mutant())).collect(Collectors.toSet());
        List<SemanticMutantOutcome> results = new ArrayList<>(current);
        required.forEach((probe, mutant) -> {
            if (probe.sessionId().equals(sessionId) && !observed.contains(probe)) {
                results.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE, "A previously confirmed learner-test counterexample can no longer be rechecked at "
                        + probe.path()
                        + ". Removing, renaming, or supplying the learner's assertions does not establish that the grading defect is fixed. Preserve the assigned learner work and produce conclusive grading evidence."));
            }
        });
        return List.copyOf(results);
    }
}
