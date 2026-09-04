package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome.Disposition;

/** Reconciles text-only coverage and oracle hypotheses with stronger, exactly attributed execution evidence. */
final class SemanticEvidenceReconciler {

    private SemanticEvidenceReconciler() {
    }

    /**
     * Updates only the exact text-review hypothesis the mutant author targeted. One killed mutant refutes one concrete hypothesis, never an entire rule, so unassociated,
     * paraphrased, or inconclusive findings remain untouched. A conclusive survivor is replaced by executable weak-oracle evidence in the caller; a killed mutant drains the
     * exact hypothesis because the current suite already distinguishes it.
     */
    static List<SpecFidelityReport.Finding> reconcile(SpecFidelityReport report, List<SemanticMutantOutcome> outcomes) {
        Map<SpecFidelityReport.Finding, List<Disposition>> dispositionsByTarget = outcomes.stream().filter(outcome -> outcome.mutant().reviewTarget() != null).collect(
                Collectors.groupingBy(outcome -> outcome.mutant().reviewTarget(), Collectors.mapping(SemanticMutantOutcome::disposition, Collectors.toCollection(ArrayList::new))));
        return report.findings().stream().filter(finding -> {
            if (finding.kind() != SpecFidelityReport.Kind.WEAK_TEST_ORACLE && finding.kind() != SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT) {
                return true;
            }
            List<Disposition> dispositions = dispositionsByTarget.get(finding);
            return dispositions == null || dispositions.isEmpty() || dispositions.contains(Disposition.INCONCLUSIVE) || dispositions.contains(Disposition.REFERENCE_TEST_FAILED);
        }).toList();
    }
}
