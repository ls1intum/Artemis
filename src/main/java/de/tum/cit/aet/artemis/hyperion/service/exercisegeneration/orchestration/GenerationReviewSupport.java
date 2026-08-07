package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome.Disposition;

/** Pure rendering and classification rules shared by the generation review loop. */
final class GenerationReviewSupport {

    private static final int MAX_EXECUTED_MUTANT_HISTORY = 12;

    record SemanticMutantRecheck(List<SemanticMutant> unresolvedMutants, List<String> failureReasons) {
    }

    record ExecutableProbeSummary(List<SemanticMutantOutcome> mutantOutcomes, List<ContractWitnessOutcome> witnessOutcomes, int adoptableWitnesses, int awaitingReferencePass,
            int awaitingReferenceAdjudication, int awaitingAdoptionAdjudication) {

        String render() {
            long survivors = mutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE).count();
            long killed = mutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.KILLED_BY_GRADED_SUITE).count();
            long referenceFailedMutants = mutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.REFERENCE_TEST_FAILED).count();
            long mutantInconclusive = mutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.INCONCLUSIVE).count();
            long validatedWitnesses = witnessOutcomes.stream().filter(outcome -> outcome.disposition() == ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED)
                    .count();
            long starterDidNotFail = witnessOutcomes.stream().filter(outcome -> outcome.disposition() == ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_NOT_FAILED)
                    .count();
            long referenceFailed = witnessOutcomes.stream().filter(outcome -> outcome.disposition() == ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED).count();
            long witnessInconclusive = witnessOutcomes.stream().filter(outcome -> outcome.disposition() == ContractWitnessOutcome.Disposition.INCONCLUSIVE).count();
            return "Executable semantic probes: " + mutantOutcomes.size() + " mutant probe(s): " + survivors + " survived, " + killed + " killed by existing tests, "
                    + referenceFailedMutants + " reference-fail, " + mutantInconclusive + " inconclusive; " + witnessOutcomes.size() + " contract-witness proposal(s): "
                    + validatedWitnesses + " reference-pass/starter-fail, " + starterDidNotFail + " reference-pass/starter-not-fail, " + referenceFailed + " reference-fail, "
                    + witnessInconclusive + " inconclusive, " + adoptableWitnesses + " eligible for adoption; " + awaitingReferencePass
                    + " adjudicated reference defect(s) still failing, " + awaitingReferenceAdjudication + " unresolved reference adjudication(s), " + awaitingAdoptionAdjudication
                    + " unresolved adoption adjudication(s).";
        }
    }

    private GenerationReviewSupport() {
    }

    static SpecFidelityReport.Finding referenceDefectStillFailing(ContractWitness witness) {
        return new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                "Reference solution still violates " + witness.ruleId() + " in executable witness " + witness.testName(),
                "A prior independent adjudication grounded this exact witness in the frozen specification. The environment executed it again and the reference test still "
                        + "failed. Repair the reference behavior without weakening the contract; convergence remains blocked until this exact witness passes.\n" + witness.code());
    }

    static SpecFidelityReport.Finding positiveWitnessAdjudicationUnavailable(ContractWitness witness) {
        return new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_WITNESS_ADJUDICATION_UNAVAILABLE,
                "Executable witness " + witness.testName() + " for rule " + witness.ruleId() + " was not source-approved",
                "The environment executed this optional proposal: the reference passed and the starter failed. Independent review could not establish that the exact assertion "
                        + "follows from the frozen specification and belongs to student-owned work, so it was not offered for adoption. An instructor may review it manually.\n"
                        + witness.code());
    }

    static SpecFidelityReport.Finding approvedContractWitnessAvailable(ContractWitness witness) {
        return new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE,
                "Rule " + witness.ruleId() + " has a source-approved executable witness " + witness.testName(),
                "Independent review already grounded this exact assertion in the frozen specification. The environment now executes it successfully against the reference and "
                        + "observes it fail against the starter at student work. Add it unless an existing graded assertion already distinguishes the same behavior. The author "
                        + "proposed it for this plausible wrong behavior, which the environment did not execute: " + witness.wrongBehavior() + "\nWitness:\n" + witness.code());
    }

    static SpecFidelityReport.Finding semanticMutantFinding(SemanticMutant mutant, boolean specificationApproved) {
        return new SpecFidelityReport.Finding(
                specificationApproved ? SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE : SpecFidelityReport.Kind.EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL,
                "Rule " + mutant.ruleId() + " has an environment-proven surviving semantic mutant",
                "The existing graded suite passed this complete replacement for " + mutant.solutionPath() + ", while the counterexample below executed and passed on the "
                        + "pristine solution and executed and failed on the mutant. Execution proves the suite does not distinguish these complete implementations; the frozen "
                        + "rule and independent review explain why that difference matters. "
                        + (specificationApproved
                                ? "Add or adapt a discriminating test for the counterexample; the environment will accept any executed test that kills the mutant, so preserve "
                                        + "stronger or more idiomatic coverage rather than copying a method mechanically. "
                                : "Do not autonomously strengthen grading while the specification still has an unresolved pre-freeze review finding; retain this executed evidence "
                                        + "for instructor adjudication. ")
                        + "Plausible misconception: " + mutant.counterexample().wrongBehavior() + "\n" + mutant.counterexample().code());
    }

    static List<SpecFidelityReport.Finding> withPriorSemanticMutants(List<SpecFidelityReport.Finding> findings, List<SemanticMutant> mutants) {
        return java.util.stream.Stream.concat(mutants.stream().map(mutant -> semanticMutantFinding(mutant, false)), findings.stream()).toList();
    }

    static List<SemanticMutant.Exclusion> rememberExecutedMutants(List<SemanticMutant.Exclusion> history, List<SemanticMutantOutcome> freshOutcomes, int limit) {
        return java.util.stream.Stream
                .concat(history.stream(), freshOutcomes.stream().filter(outcome -> outcome.disposition() != Disposition.INCONCLUSIVE).map(outcome -> outcome.mutant().exclusion()))
                .distinct().limit(limit).toList();
    }

    static List<SemanticMutant.Exclusion> rememberExecutedMutants(List<SemanticMutant.Exclusion> history, List<SemanticMutantOutcome> freshOutcomes) {
        return rememberExecutedMutants(history, freshOutcomes, MAX_EXECUTED_MUTANT_HISTORY);
    }

    static SemanticMutantRecheck semanticMutantRecheck(List<SemanticMutantOutcome> outcomes) {
        List<SemanticMutant> unresolved = outcomes.stream().filter(outcome -> outcome.disposition() != Disposition.KILLED_BY_GRADED_SUITE).map(SemanticMutantOutcome::mutant)
                .toList();
        List<String> reasons = outcomes.stream().filter(outcome -> outcome.disposition() != Disposition.KILLED_BY_GRADED_SUITE).map(outcome -> {
            SemanticMutant mutant = outcome.mutant();
            if (outcome.disposition() == Disposition.INCONCLUSIVE) {
                return "The environment could not conclusively recheck the previously proven semantic mutant for rule " + mutant.ruleId()
                        + "; no kill is inferred from a timeout, compilation failure, or missing executed-test report.";
            }
            return "The graded suite still passes the environment-proven semantic mutant for rule " + mutant.ruleId() + " (" + mutant.counterexample().wrongBehavior()
                    + "). Add a focused assertion that kills this plausible wrong implementation; keep the verified solution unchanged.";
        }).toList();
        return new SemanticMutantRecheck(unresolved, reasons);
    }

    static SpecFidelityReport.Finding semanticMutantRecheckUnavailable(SemanticMutant mutant) {
        return SpecFidelityReport.executableEvidenceUnavailable("The environment could not conclusively recheck the previously proven semantic mutant for rule " + mutant.ruleId()
                + " at " + mutant.solutionPath() + " (counterexample " + mutant.counterexample().testName() + ", misconception: " + mutant.counterexample().wrongBehavior()
                + "). It remains unresolved until an executed graded test kills it.").findings().getFirst();
    }

    /** A text-only or interrupted review cannot discharge executable mutant state; retain exact provenance without asserting a fresh survival result. */
    static SpecFidelityReport preserveSemanticMutantState(SpecFidelityReport report, List<SemanticMutant> pendingRepair, List<SemanticMutant> awaitingRecheck) {
        List<SemanticMutant> unresolved = java.util.stream.Stream.concat(pendingRepair.stream(), awaitingRecheck.stream()).distinct().toList();
        if (unresolved.isEmpty()) {
            return report;
        }
        List<SpecFidelityReport.Finding> findings = new java.util.ArrayList<>(report.findings());
        unresolved.stream().map(GenerationReviewSupport::semanticMutantRecheckUnavailable).filter(finding -> !findings.contains(finding)).forEach(findings::add);
        return new SpecFidelityReport(List.copyOf(findings));
    }

    /**
     * Attaches the objections raised against a concept the run proceeded with anyway, using the same report the quality review writes into, so they reach the instructor as
     * ordinary review notes on a saved {@code NEEDS_REVIEW} exercise rather than through a second channel of their own.
     */
    static SpecFidelityReport preserveConceptAdmissionState(SpecFidelityReport report, List<String> unresolvedConceptFindings) {
        if (unresolvedConceptFindings.isEmpty()) {
            return report;
        }
        List<SpecFidelityReport.Finding> findings = new java.util.ArrayList<>(report.findings());
        unresolvedConceptFindings.stream()
                .map(finding -> new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONCEPT_ADMISSION_FINDING, finding,
                        "The concept review admitted no candidate, so this exercise was generated from the one it rejected least. Deciding whether the objection matters is a "
                                + "design call only you can make: accept it, regenerate from a sharper brief, or discard the draft."))
                .filter(finding -> !findings.contains(finding)).forEach(findings::add);
        return new SpecFidelityReport(List.copyOf(findings));
    }

    static SpecFidelityReport preserveSpecificationReviewState(SpecFidelityReport report, List<String> unresolvedSpecificationFindings) {
        if (unresolvedSpecificationFindings.isEmpty()) {
            return report;
        }
        List<SpecFidelityReport.Finding> findings = new java.util.ArrayList<>(report.findings());
        unresolvedSpecificationFindings.stream()
                .map(finding -> new SpecFidelityReport.Finding(SpecFidelityReport.Kind.SPECIFICATION_REVIEW_FINDING, finding,
                        "The independent pre-freeze review did not approve this compiled specification. The exact finding is retained so the saved exercise remains NEEDS_REVIEW "
                                + "rather than silently treating the contract as approved."))
                .filter(finding -> !findings.contains(finding)).forEach(findings::add);
        return new SpecFidelityReport(List.copyOf(findings));
    }

    static SpecFidelityReport preservePendingSpecApprovalMutants(SpecFidelityReport report, List<SemanticMutant> mutants) {
        if (mutants.isEmpty()) {
            return report;
        }
        List<SpecFidelityReport.Finding> findings = new java.util.ArrayList<>(report.findings());
        var grouped = mutants.stream().map(mutant -> semanticMutantFinding(mutant, false))
                .collect(Collectors.groupingBy(finding -> finding.kind() + "\n" + finding.requirement(), java.util.LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
        grouped.forEach((key, variants) -> {
            int existingIndex = java.util.stream.IntStream.range(0, findings.size())
                    .filter(index -> key.equals(findings.get(index).kind() + "\n" + findings.get(index).requirement())).findFirst().orElse(-1);
            var details = java.util.stream.Stream.concat(existingIndex < 0 ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(findings.get(existingIndex).detail()),
                    variants.stream().map(SpecFidelityReport.Finding::detail)).distinct().toList();
            SpecFidelityReport.Finding representative = existingIndex < 0 ? variants.getFirst() : findings.get(existingIndex);
            SpecFidelityReport.Finding groupedFinding = new SpecFidelityReport.Finding(representative.kind(), representative.requirement(),
                    String.join("\n\n--- additional environment-proven variant ---\n", details));
            if (existingIndex < 0) {
                findings.add(groupedFinding);
            }
            else {
                findings.set(existingIndex, groupedFinding);
            }
        });
        return new SpecFidelityReport(List.copyOf(findings));
    }

    static void addReferenceUnavailability(List<SpecFidelityReport.Finding> findings, int omitted, int pendingPass, int pendingAdjudication) {
        if (omitted > 0) {
            findings.addAll(
                    SpecFidelityReport
                            .executableEvidenceUnavailable("Independent adjudication omitted " + omitted
                                    + " environment-confirmed reference test failure(s); convergence remains blocked until every witness receives an explicit verdict.")
                            .findings());
        }
        if (pendingPass > 0) {
            findings.addAll(
                    SpecFidelityReport
                            .executableEvidenceUnavailable("The environment could not re-execute " + pendingPass
                                    + " previously adjudicated reference-defect witness(es); convergence remains blocked until each exact witness executes and passes.")
                            .findings());
        }
        if (pendingAdjudication > 0) {
            findings.addAll(SpecFidelityReport
                    .executableEvidenceUnavailable("The environment could not re-execute " + pendingAdjudication
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
        boolean evidenceUnavailable = report.findings().stream().anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_EVIDENCE_UNAVAILABLE);
        boolean everyAdjudicatedWitnessRepresented = awaitingPass.stream().allMatch(witness -> report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking)
                .anyMatch(finding -> (finding.requirement() + "\n" + finding.detail()).contains(witness.testName())));
        if (evidenceUnavailable || (awaitingAdjudication.isEmpty() && everyAdjudicatedWitnessRepresented)) {
            return report;
        }
        List<SpecFidelityReport.Finding> findings = new java.util.ArrayList<>(report.findings());
        findings.addAll(SpecFidelityReport.executableEvidenceUnavailable("Executable reference evidence remains unresolved: " + awaitingPass.size()
                + " independently adjudicated witness(es) await an " + "environment pass and " + awaitingAdjudication.size()
                + " environment-confirmed failure(s) await an explicit independent verdict. A text-only review cannot discharge either state.").findings());
        return new SpecFidelityReport(List.copyOf(findings));
    }

    /** Retains optional positive proposals as advisory review context without turning an auxiliary reviewer outage into a blocking exercise defect. */
    static SpecFidelityReport preservePositiveWitnessState(SpecFidelityReport report, List<ContractWitness> awaitingAdjudication) {
        if (awaitingAdjudication.isEmpty()) {
            return report;
        }
        List<SpecFidelityReport.Finding> findings = new java.util.ArrayList<>(report.findings());
        awaitingAdjudication.stream().map(GenerationReviewSupport::positiveWitnessAdjudicationUnavailable).filter(finding -> !findings.contains(finding)).forEach(findings::add);
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
        if (ExerciseIntegrityGate.techniqueMandatesInSpecification(specification).isEmpty()
                || report.findings().stream().noneMatch(GenerationReviewSupport::demandsUngradeableTechnique)) {
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
