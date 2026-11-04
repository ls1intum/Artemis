package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentSystemPrompt;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

/** Pure rendering and classification rules shared by the generation review loop. */
final class GenerationReviewSupport {

    /** Combines review evidence without allowing incomplete adaptation scope or ungradable techniques to disappear. */
    static SpecFidelityReport completeReview(SpecFidelityReport report, boolean adaptationTruncated, List<SpecFidelityReport.Finding> messageless,
            List<SpecFidelityReport.Finding> techniqueRules, @Nullable String specification) {
        List<SpecFidelityReport.Finding> combined = new ArrayList<>(report.findings());
        if (adaptationTruncated) {
            combined.addAll(SpecFidelityReport.adaptationScopeUnavailable("The bounded change summary was truncated, so not every changed line could be reviewed.").findings());
        }
        combined.addAll(messageless);
        SpecFidelityReport classified = reclassifyUngradeableTechniqueFindings(new SpecFidelityReport(combined), specification);
        combined = new ArrayList<>(classified.findings());
        combined.addAll(techniqueRules);
        return new SpecFidelityReport(combined);
    }

    static String reviewSummary(SpecFidelityReport report) {
        long blockingCount = report.findings().stream().filter(SpecFidelityReport.Finding::isBlocking).count();
        long advisoryCount = report.findings().size() - blockingCount;
        String counts = blockingCount > 0 && advisoryCount > 0 ? blockingCount + " blocking and " + advisoryCount + " advisory"
                : blockingCount > 0 ? blockingCount + " blocking" : advisoryCount + " advisory";
        String gaps = report.findings().size() == 1 ? " exercise-quality gap" : " exercise-quality gaps";
        return "The review found " + counts + gaps + (blockingCount > 0 ? " that require instructor attention." : ".");
    }

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

    static SpecFidelityReport.Finding semanticMutantFinding(SemanticMutant mutant) {
        if (mutant.printsTranscriptOnly()) {
            return new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE,
                    "The graded tests accept a " + mutant.ruleId() + " that only prints the expected output",
                    "The environment ran the reference " + mutant.ruleId() + " without arguments or input, then replaced only the body of that method in " + mutant.solutionPath()
                            + " with statements printing the captured output, leaving every other member unchanged. The graded suite still passed, so "
                            + "a graded test compares only the printed transcript, and a student passes it by hard-coding that output without using the program's objects. "
                            + AgentSystemPrompt.TESTABLE_STUDENT_CODE_RULE + " A pure transcript check may stay only ungraded: as a zero-weight PRESERVATION entry in "
                            + "test-plan.json when it also passes on the template, otherwise remove it. Testing other members through the public API cannot resolve this, "
                            + "because they are unchanged in the print-only version; the finding clears once no graded test compares the transcript alone.",
                    changedSolutionLines(mutant));
        }
        return new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE,
                "Rule " + mutant.ruleId() + " has an environment-proven surviving semantic mutant",
                "The existing graded suite passed this complete replacement for " + mutant.solutionPath() + ", while the counterexample below executed and passed on the "
                        + "pristine solution and executed and failed on the mutant. Execution proves the suite does not distinguish these complete implementations; the frozen "
                        + "rule and independent review explain why that difference matters. Add or adapt a discriminating test for the counterexample; the environment will "
                        + "accept any executed test that kills the mutant, so preserve stronger or more idiomatic coverage rather than copying a method mechanically. "
                        + "Plausible misconception: " + mutant.counterexample().wrongBehavior() + "\n" + mutant.counterexample().code(),
                changedSolutionLines(mutant));
    }

    /** The reference lines the mutant replaces: execution proved the graded suite does not distinguish this code from the mutant's version of it. */
    static SpecFidelityReport.Location changedSolutionLines(SemanticMutant mutant) {
        List<String> original = mutant.originalSolutionSource().lines().toList();
        List<String> replacement = mutant.mutantSource().lines().toList();
        int prefix = 0;
        while (prefix < original.size() && prefix < replacement.size() && original.get(prefix).equals(replacement.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < original.size() - prefix && suffix < replacement.size() - prefix
                && original.get(original.size() - 1 - suffix).equals(replacement.get(replacement.size() - 1 - suffix))) {
            suffix++;
        }
        // A pure insertion replaces no original line; anchor it at the original line after the insertion point, clamped to the file.
        int lastLine = Math.max(1, original.size());
        int start = Math.min(prefix + 1, lastLine);
        int end = Math.max(start, Math.min(original.size() - suffix, lastLine));
        return new SpecFidelityReport.Location(SpecFidelityReport.Location.Repository.SOLUTION, mutant.solutionPath(), start, end);
    }

    static List<SpecFidelityReport.Finding> withPriorSemanticMutants(List<SpecFidelityReport.Finding> findings, List<SemanticMutant> mutants) {
        return java.util.stream.Stream.concat(mutants.stream().map(GenerationReviewSupport::semanticMutantFinding), findings.stream()).toList();
    }

    /** The report as the repair-progress comparison must see it: prior survivors without a fresh verdict still count as executed blockers. */
    static SpecFidelityReport withUndrainedSurvivors(SpecFidelityReport report, List<SemanticMutant> survivorsWithoutVerdict) {
        if (survivorsWithoutVerdict.isEmpty()) {
            return report;
        }
        return new SpecFidelityReport(
                java.util.stream.Stream.concat(report.findings().stream(), survivorsWithoutVerdict.stream().map(GenerationReviewSupport::semanticMutantFinding)).toList());
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
            if (mutant.printsTranscriptOnly()) {
                return "The graded suite still passes a " + mutant.ruleId() + " that only prints the expected output. " + AgentSystemPrompt.TESTABLE_STUDENT_CODE_RULE
                        + " A pure transcript check may stay only ungraded (zero-weight PRESERVATION when it passes on the template) or must go.";
            }
            return "The graded suite still passes the environment-proven semantic mutant for rule " + mutant.ruleId() + " (" + mutant.counterexample().wrongBehavior()
                    + "). Add a focused assertion that kills this plausible wrong implementation; keep the verified solution unchanged.";
        }).toList();
        return new SemanticMutantRecheck(unresolved, reasons);
    }

    static SpecFidelityReport.Finding semanticMutantRecheckUnavailable(SemanticMutant mutant) {
        if (mutant.printsTranscriptOnly()) {
            return SpecFidelityReport
                    .executableEvidenceUnavailable("The environment could not conclusively recheck whether the graded suite still accepts a " + mutant.ruleId() + " in "
                            + mutant.solutionPath() + " that only prints the expected output. It remains unresolved until an executed graded test fails on it.")
                    .findings().getFirst();
        }
        return SpecFidelityReport.executableEvidenceUnavailable("The environment could not conclusively recheck the previously proven semantic mutant for rule " + mutant.ruleId()
                + " at " + mutant.solutionPath() + " (counterexample " + mutant.counterexample().testName() + ", misconception: " + mutant.counterexample().wrongBehavior()
                + "). It remains unresolved until an executed graded test kills it.").findings().getFirst();
    }

    /** An earlier repair's kill that this candidate's verification did not conclusively recheck, so a later change may have let the mutant survive again. */
    static SpecFidelityReport.Finding regressionRecheckUnavailable(SemanticMutant mutant) {
        return SpecFidelityReport
                .executableEvidenceUnavailable("The semantic mutant for rule " + mutant.ruleId() + " at " + mutant.solutionPath() + " (counterexample "
                        + mutant.counterexample().testName() + ", misconception: " + mutant.counterexample().wrongBehavior() + ") that an earlier repair's tests killed was not "
                        + "conclusively rechecked against this candidate, so a later change may have let it survive again. The next verified repair rechecks it first.")
                .findings().getFirst();
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
                        "The concept review did not approve the concept this exercise was generated from; the finding states what it objected to. Deciding whether the objection "
                                + "matters is a design call only you can make: accept it, regenerate from a sharper brief, or discard the draft."))
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

    /** A content digest of the repositories and grading plan an executed probe depends on, independent of map ordering. */
    static String probeInputDigest(Map<RepositoryRole, Map<String, String>> candidateFiles, @Nullable String testPlanJson) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            candidateFiles.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(repository -> repository.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
                        update(digest, repository.getKey().name());
                        update(digest, file.getKey());
                        update(digest, java.util.Objects.requireNonNullElse(file.getValue(), ""));
                    }));
            update(digest, java.util.Objects.requireNonNullElse(testPlanJson, ""));
            return java.util.HexFormat.of().formatHex(digest.digest());
        }
        catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Length-prefixed, so adjacent values cannot run together into the same digest input. */
    private static void update(java.security.MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
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
                .map(finding -> demandsUngradeableTechnique(finding) ? new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE, finding.requirement(),
                        "No assertion through the public API can observe this, so it cannot be repaired by strengthening the tests: " + finding.detail(), finding.location())
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
