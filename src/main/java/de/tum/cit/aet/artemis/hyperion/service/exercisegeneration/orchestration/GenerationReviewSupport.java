package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;

/** Pure rendering and classification rules shared by the generation review loop. */
final class GenerationReviewSupport {

    private GenerationReviewSupport() {
    }

    static SpecFidelityReport.Finding referenceDefectStillFailing(ContractWitness witness) {
        return new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                "Reference solution still violates " + witness.ruleId() + " in executable witness " + witness.testName(),
                "A prior independent adjudication grounded this exact witness in the frozen specification. The environment executed it again and the reference test still "
                        + "failed. Repair the reference behavior without weakening the contract; convergence remains blocked until this exact witness passes.\n" + witness.code());
    }

    static void addReferenceUnavailability(List<SpecFidelityReport.Finding> findings, int omitted, int pendingPass, int pendingAdjudication) {
        if (omitted > 0) {
            findings.addAll(
                    SpecFidelityReport
                            .qualityReviewUnavailable("Independent adjudication omitted " + omitted
                                    + " environment-confirmed reference test failure(s); convergence remains blocked until every witness receives an explicit verdict.")
                            .findings());
        }
        if (pendingPass > 0) {
            findings.addAll(
                    SpecFidelityReport
                            .qualityReviewUnavailable("The environment could not re-execute " + pendingPass
                                    + " previously adjudicated reference-defect witness(es); convergence remains blocked until each exact witness executes and passes.")
                            .findings());
        }
        if (pendingAdjudication > 0) {
            findings.addAll(SpecFidelityReport
                    .qualityReviewUnavailable("The environment could not re-execute " + pendingAdjudication
                            + " reference witness(es) awaiting independent adjudication; convergence remains blocked until each executes and receives an explicit verdict.")
                    .findings());
        }
    }

    /**
     * Keeps executable reference evidence represented in every review used for promotion or convergence. A clean text-only retry must not erase a witness merely because that
     * retry did not execute or adjudicate it.
     */
    static SpecFidelityReport preserveReferenceWitnessState(SpecFidelityReport report, List<ContractWitness> awaitingPass, List<ContractWitness> awaitingAdjudication) {
        if (awaitingPass.isEmpty() && awaitingAdjudication.isEmpty()) {
            return report;
        }
        boolean reviewUnavailable = report.findings().stream().anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
        boolean everyAdjudicatedWitnessRepresented = awaitingPass.stream().allMatch(witness -> report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking)
                .anyMatch(finding -> (finding.requirement() + "\n" + finding.detail()).contains(witness.testName())));
        if (reviewUnavailable || (awaitingAdjudication.isEmpty() && everyAdjudicatedWitnessRepresented)) {
            return report;
        }
        List<SpecFidelityReport.Finding> findings = new java.util.ArrayList<>(report.findings());
        findings.addAll(SpecFidelityReport.qualityReviewUnavailable("Executable reference evidence remains unresolved: " + awaitingPass.size()
                + " independently adjudicated witness(es) await an " + "environment pass and " + awaitingAdjudication.size()
                + " environment-confirmed failure(s) await an explicit independent verdict. A text-only review cannot discharge either state.").findings());
        return new SpecFidelityReport(List.copyOf(findings));
    }

    static String renderArtifactSources(Map<String, String> files) {
        return files.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> "// " + entry.getKey() + "\n" + entry.getValue()).collect(Collectors.joining("\n\n"));
    }

    /**
     * Keeps a real but untestable implementation-technique finding visible to instructors without scheduling an impossible oracle repair. Provenance is required in the frozen
     * contract, so an unsupported critic inference is never reclassified.
     */
    static SpecFidelityReport reclassifyUngradeableTechniqueFindings(SpecFidelityReport report, @Nullable String specification) {
        if (ExerciseIntegrityGate.techniqueMandatesInRules(specification).isEmpty() || report.findings().stream().noneMatch(GenerationReviewSupport::demandsUngradeableTechnique)) {
            return report;
        }
        List<SpecFidelityReport.Finding> reclassified = report.findings().stream()
                .map(finding -> demandsUngradeableTechnique(finding)
                        ? new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE, finding.requirement(),
                                "No assertion through the public API can observe this, so it cannot be repaired by strengthening the tests: " + finding.detail())
                        : finding)
                .toList();
        return new SpecFidelityReport(reclassified);
    }

    private static boolean demandsUngradeableTechnique(SpecFidelityReport.Finding finding) {
        return (finding.kind() == SpecFidelityReport.Kind.WEAK_TEST_ORACLE || finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE
                || finding.kind() == SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT)
                && ExerciseIntegrityGate.describesTechniqueRatherThanBehaviour(finding.requirement() + " " + finding.detail());
    }

    static String specContractSection(@Nullable String specification) {
        if (specification == null || specification.isBlank()) {
            return "";
        }
        return "\n\nTHE SPECIFICATION (frozen at the spec gate — the read-only behavioural contract; repair downstream artifacts against it):\n" + specification.strip();
    }

    static String effectiveSpecReviewContext(@Nullable String approvedSpecification, @Nullable String liveSpecification) {
        String approved = approvedSpecification == null ? "" : approvedSpecification.strip();
        return approved.isEmpty() ? liveSpecification == null ? "" : liveSpecification.strip() : approved;
    }
}
