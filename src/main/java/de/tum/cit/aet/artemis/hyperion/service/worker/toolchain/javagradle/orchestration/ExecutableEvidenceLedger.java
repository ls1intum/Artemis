package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.DifferentialVerifier;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

/**
 * The executable review evidence one {@link GenerationAttemptLoop} run carries across its repair rounds, and the sandbox probes that keep it current: semantic mutants the
 * graded suite let survive, the ones the in-flight repair must kill, the ones an earlier repair killed and every later verification rechecks for regression, the verdicts of
 * printed-transcript probes, and the reference and positive contract witnesses awaiting a pass, an adjudication or adoption. Model-proposed mutants and witnesses become
 * evidence only after environment execution.
 * <p>
 * Per run and not thread-safe, like the loop that owns it. The loop decides when to verify, review and repair; this ledger decides what the executed evidence says about the
 * candidate and keeps it from vanishing as progress when a repair changes its reference source.
 */
final class ExecutableEvidenceLedger {

    private static final int MAX_TRACKED_SEMANTIC_MUTANTS = 6;

    private final DifferentialVerifier verifier;

    private final SpecFidelityCritic specFidelityCritic;

    private final InteractiveSandbox sandbox;

    private final String sessionId;

    private final GenerationInput exercise;

    private final Consumer<ChatResponse> usageSink;

    private final BooleanSupplier cancelled;

    /** The run's progress line, so probe results read the same as every other stage. */
    private final Consumer<String> emitter;

    private List<SemanticMutant> semanticMutantsAwaitingKill = List.of();

    private List<SemanticMutant> semanticMutantsPendingRepair = List.of();

    private List<SemanticMutant> semanticMutantsAwaitingRecheck = List.of();

    private List<SemanticMutant.Exclusion> semanticMutantHistory = List.of();

    /** Mutant probes of the latest executable review that neither survived nor were killed; reported on the next review-round event, never as evidence. */
    private int inconclusiveMutantProbes;

    private List<ContractWitness> referenceWitnessesAwaitingPass = List.of();

    private List<ContractWitness> referenceWitnessesAwaitingAdjudication = List.of();

    private List<ContractWitness> positiveWitnessesAwaitingAdjudication = List.of();

    // Independently approved reference-pass/starter-fail witnesses remain available across unrelated repair surfaces. A full-artifact re-review must not erase their evidence.
    private List<ContractWitness> contractWitnessesPendingAdoption = List.of();

    /** Mutants the previous candidate's suite let survive and the in-flight repair's suite killed in an executed recheck. */
    private int mutantsKilledByCurrentRepair;

    /**
     * Mutants an earlier repair killed. Every later verification rechecks them, keyed to the current source, and fails while one survives again, so a regression is judged on
     * the latest candidate rather than remembered.
     */
    private List<SemanticMutant> semanticMutantsKilledByRepair = List.of();

    /**
     * Prior survivors the latest executable review could not give a verdict: rechecks that were inconclusive, including those keyed to a changed source, and survivors whose
     * file was deleted. They are neither killed nor drained: the repair-progress comparison counts them, and the review reports them as unavailable evidence until an executed
     * recheck resolves them.
     */
    private List<SemanticMutant> survivorsWithoutVerdict = List.of();

    /** Digest of the repositories and grading plan the printed-transcript probes last executed against. */
    @Nullable
    private String transcriptProbeDigest;

    /**
     * Conclusive printed-transcript verdicts for {@link #transcriptProbeDigest}, by probed solution path. The probe is deterministic, so unchanged sources and tests give the same
     * verdict; an unchanged candidate reuses it instead of running the reference main and a graded build again.
     */
    private Map<String, SemanticMutantOutcome> transcriptProbeVerdicts = Map.of();

    ExecutableEvidenceLedger(DifferentialVerifier verifier, SpecFidelityCritic specFidelityCritic, InteractiveSandbox sandbox, String sessionId, GenerationInput exercise,
            Consumer<ChatResponse> usageSink, BooleanSupplier cancelled, Consumer<String> emitter) {
        this.verifier = verifier;
        this.specFidelityCritic = specFidelityCritic;
        this.sandbox = sandbox;
        this.sessionId = sessionId;
        this.exercise = exercise;
        this.usageSink = usageSink;
        this.cancelled = cancelled;
        this.emitter = emitter;
    }

    /**
     * Rechecks a mechanically verified candidate against the mutants the in-flight repair must kill and, while a repair is in flight, the mutants an earlier repair killed.
     * Targets keyed to a changed reference source stay pending for the executable review instead of vanishing as progress. A repair that killed at least one target is kept
     * and carries the rest forward as findings; one that killed none, or whose suite lets an earlier kill survive again, is rejected.
     *
     * @param result                the mechanically verified result
     * @param candidateFiles        the verified candidate's repositories
     * @param testPlanSnapshot      the verified candidate's grading plan
     * @param repairInFlight        whether a semantic repair produced this candidate, so earlier kills are rechecked for regression
     * @param seededStructuralTests the exact server-owned structural authority
     * @param restoreCandidate      restores the verified candidate between probes
     * @return {@code result} itself, or a rejection carrying the regression and unkilled-target reasons
     */
    VerificationResult recheckAfterVerification(VerificationResult result, Map<RepositoryRole, Map<String, String>> candidateFiles, @Nullable String testPlanSnapshot,
            boolean repairInFlight, SeededStructuralTests seededStructuralTests, Runnable restoreCandidate) {
        Map<String, String> currentSolution = candidateFiles.getOrDefault(RepositoryRole.SOLUTION, Map.of());
        List<SemanticMutant> applicableMutants = semanticMutantsAwaitingKill.stream()
                .filter(mutant -> mutant.originalSolutionSource().equals(currentSolution.get(mutant.solutionPath()))).toList();
        List<SemanticMutant> staleTargets = semanticMutantsAwaitingKill.stream().filter(mutant -> !applicableMutants.contains(mutant)).toList();
        if (!staleTargets.isEmpty()) {
            // Not a kill and not an acceptance check against this source: the review rechecks them against the current solution, so they cannot vanish as progress.
            semanticMutantsPendingRepair = java.util.stream.Stream.concat(semanticMutantsPendingRepair.stream(), staleTargets.stream()).distinct().toList();
            emitter.accept("Kept " + staleTargets.size() + " semantic mutant acceptance check(s) pending because the repair changed their reference source; the executable review "
                    + "rechecks them against the current solution.");
        }
        List<SemanticMutant> regressionChecks = !repairInFlight ? List.of() : semanticMutantsKilledByRepair.stream().map(mutant -> {
            String current = currentSolution.get(mutant.solutionPath());
            return current == null || (mutant.printsTranscriptOnly() && !current.equals(mutant.originalSolutionSource())) ? null : mutant.rekeyedTo(current);
        }).filter(Objects::nonNull).filter(mutant -> !applicableMutants.contains(mutant)).toList();
        semanticMutantsAwaitingKill = List.of();
        if (applicableMutants.isEmpty() && regressionChecks.isEmpty()) {
            return result;
        }
        List<SemanticMutantOutcome> allOutcomes = recheckSemanticMutants(candidateFiles, testPlanSnapshot, seededStructuralTests,
                java.util.stream.Stream.concat(applicableMutants.stream(), regressionChecks.stream()).toList(), restoreCandidate);
        // Judged on this recheck alone: a correction that kills a regressed mutant again clears the regression.
        List<SemanticMutant> regressed = allOutcomes.stream().filter(outcome -> regressionChecks.contains(outcome.mutant()))
                .filter(outcome -> outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE).map(SemanticMutantOutcome::mutant).toList();
        List<SemanticMutantOutcome> targetOutcomes = allOutcomes.stream().filter(outcome -> applicableMutants.contains(outcome.mutant())).toList();
        GenerationReviewSupport.SemanticMutantRecheck recheck = GenerationReviewSupport.semanticMutantRecheck(targetOutcomes);
        List<SemanticMutant> killed = targetOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.KILLED_BY_GRADED_SUITE).map(SemanticMutantOutcome::mutant)
                .toList();
        mutantsKilledByCurrentRepair += killed.size();
        semanticMutantsKilledByRepair = java.util.stream.Stream.concat(semanticMutantsKilledByRepair.stream(), killed.stream()).distinct().limit(MAX_TRACKED_SEMANTIC_MUTANTS)
                .toList();
        // A repair that killed a targeted mutant is kept even when others still survive; they become the next round's findings rather than a reason to discard the kill.
        boolean unkilledTargetsBlock = !recheck.unresolvedMutants().isEmpty() && mutantsKilledByCurrentRepair == 0;
        if (regressed.isEmpty() && !unkilledTargetsBlock) {
            if (!recheck.unresolvedMutants().isEmpty()) {
                semanticMutantsPendingRepair = java.util.stream.Stream.concat(semanticMutantsPendingRepair.stream(), recheck.unresolvedMutants().stream()).distinct().toList();
                emitter.accept("The repair killed " + mutantsKilledByCurrentRepair + " targeted mutant(s); carrying the " + recheck.unresolvedMutants().size()
                        + " still unresolved forward as review findings.");
            }
            return result;
        }
        semanticMutantsAwaitingKill = recheck.unresolvedMutants();
        List<String> reasons = new ArrayList<>(result.reasons());
        if (unkilledTargetsBlock) {
            reasons.addAll(recheck.failureReasons());
        }
        regressed.forEach(mutant -> reasons.add("The graded suite no longer kills the semantic mutant for rule " + mutant.ruleId() + " (" + mutant.counterexample().wrongBehavior()
                + ") that an earlier repair's tests killed. Restore an assertion that kills it; keep the verified solution unchanged."));
        return new VerificationResult(false, result.solutionPassed(), result.templateFailed(), result.testCount(), List.copyOf(reasons), result.templateFailureEvidence());
    }

    /** Records that no executable review ran for this candidate, so every prior survivor still counts as one without a verdict. */
    SpecFidelityReport withoutExecutableReview(SpecFidelityReport report) {
        survivorsWithoutVerdict = java.util.stream.Stream.concat(semanticMutantsPendingRepair.stream(), semanticMutantsAwaitingRecheck.stream()).distinct().toList();
        return preserveExecutableEvidenceState(report);
    }

    /**
     * Converts model-proposed mutants and witnesses into evidence only after environment execution. Prior survivors are rechecked against the current solution, printed-
     * transcript probes run or reuse their verdict, fresh mutants and witnesses fill the remaining capacity, and the review's findings are reconciled with the outcomes.
     *
     * @param report                the fresh review of the candidate
     * @param producedFiles         the candidate's repositories as read back
     * @param candidateFiles        an immutable copy of them, which {@code restoreCandidate} materializes
     * @param testPlanJson          the candidate's grading plan
     * @param specDocumentSnapshot  the approved specification the probes are authored against
     * @param seededStructuralTests the exact server-owned structural authority
     * @param restoreCandidate      restores the verified candidate between probes
     * @return the review reconciled with the executed evidence
     */
    SpecFidelityReport review(SpecFidelityReport report, Map<RepositoryRole, Map<String, String>> producedFiles, Map<RepositoryRole, Map<String, String>> candidateFiles,
            @Nullable String testPlanJson, String specDocumentSnapshot, SeededStructuralTests seededStructuralTests, Runnable restoreCandidate) {
        Map<String, String> testsFiles = producedFiles.getOrDefault(RepositoryRole.TESTS, Map.of());
        Map<String, String> solutionFiles = producedFiles.getOrDefault(RepositoryRole.SOLUTION, Map.of());
        List<SemanticMutant> priorUnresolvedMutants = java.util.stream.Stream.of(semanticMutantsPendingRepair, semanticMutantsAwaitingRecheck).flatMap(List::stream).distinct()
                .limit(MAX_TRACKED_SEMANTIC_MUTANTS).toList();
        List<SemanticMutant> applicablePendingMutants = priorUnresolvedMutants.stream()
                .filter(mutant -> mutant.originalSolutionSource().equals(solutionFiles.get(mutant.solutionPath()))).toList();
        // A survivor whose file a repair changed is rechecked against the current source rather than retired: retiring it would read as a drained blocker. A printed-
        // transcript probe is re-derived from the current source below, and a deleted file leaves nothing to execute.
        List<SemanticMutant> stalePendingMutants = priorUnresolvedMutants.stream().filter(mutant -> !applicablePendingMutants.contains(mutant)).toList();
        List<SemanticMutant> rekeyedPendingMutants = stalePendingMutants.stream().filter(mutant -> !mutant.printsTranscriptOnly())
                .filter(mutant -> solutionFiles.get(mutant.solutionPath()) != null).map(mutant -> mutant.rekeyedTo(solutionFiles.get(mutant.solutionPath()))).toList();
        List<SemanticMutant> recheckedPendingMutants = java.util.stream.Stream.concat(applicablePendingMutants.stream(), rekeyedPendingMutants.stream()).toList();
        List<SemanticMutantOutcome> pendingMutantOutcomes = recheckedPendingMutants.isEmpty() ? List.of()
                : recheckSemanticMutants(candidateFiles, testPlanJson, seededStructuralTests, recheckedPendingMutants, restoreCandidate);
        List<SemanticMutant> rekeyedWithoutVerdict = pendingMutantOutcomes.stream().filter(outcome -> rekeyedPendingMutants.contains(outcome.mutant()))
                .filter(outcome -> outcome.disposition() == Disposition.INCONCLUSIVE).map(SemanticMutantOutcome::mutant).toList();
        List<SemanticMutant> unexecutableStaleMutants = stalePendingMutants.stream().filter(mutant -> !mutant.printsTranscriptOnly())
                .filter(mutant -> solutionFiles.get(mutant.solutionPath()) == null).toList();
        if (!rekeyedWithoutVerdict.isEmpty() || !unexecutableStaleMutants.isEmpty()) {
            emitter.accept("Kept " + (rekeyedWithoutVerdict.size() + unexecutableStaleMutants.size()) + " pending semantic mutant(s) that no longer execute against the changed "
                    + "reference source as unresolved evidence; the executable reviewer will propose replacements against the current solution.");
        }
        // Deterministic and model-free, so it runs on every reviewed candidate; a pending probe of the same class was just rechecked above, and a class probed against
        // these exact sources and tests reuses its verdict.
        Set<String> pendingTranscriptProbes = applicablePendingMutants.stream().filter(SemanticMutant::printsTranscriptOnly).map(SemanticMutant::solutionPath)
                .collect(Collectors.toUnmodifiableSet());
        List<SemanticMutantOutcome> reusedTranscriptOutcomes = transcriptVerdicts(candidateFiles, testPlanJson).entrySet().stream()
                .filter(verdict -> !pendingTranscriptProbes.contains(verdict.getKey())).map(Map.Entry::getValue).toList();
        Set<String> skippedTranscriptProbes = java.util.stream.Stream.concat(pendingTranscriptProbes.stream(), transcriptVerdicts(candidateFiles, testPlanJson).keySet().stream())
                .collect(Collectors.toUnmodifiableSet());
        List<SemanticMutantOutcome> probedTranscriptOutcomes = verifier.probeOutputOnlyMains(sandbox, sessionId, exercise, testsFiles,
                producedFiles.getOrDefault(RepositoryRole.TEMPLATE, Map.of()), solutionFiles, testPlanJson, seededStructuralTests, skippedTranscriptProbes, restoreCandidate);
        rememberTranscriptVerdicts(probedTranscriptOutcomes);
        List<SemanticMutantOutcome> transcriptOutcomes = java.util.stream.Stream.concat(reusedTranscriptOutcomes.stream(), probedTranscriptOutcomes.stream()).toList();
        List<SemanticMutant> staleTranscriptWithoutVerdict = staleTranscriptProbesWithoutVerdict(stalePendingMutants, transcriptOutcomes, testsFiles, solutionFiles, testPlanJson,
                seededStructuralTests, restoreCandidate);
        int freshCapacity = Math.max(0, MAX_TRACKED_SEMANTIC_MUTANTS - (int) java.util.stream.Stream.concat(pendingMutantOutcomes.stream(), transcriptOutcomes.stream())
                .filter(outcome -> outcome.disposition() != Disposition.KILLED_BY_GRADED_SUITE).count());
        List<SemanticMutant> authoredPendingMutants = applicablePendingMutants.stream().filter(mutant -> !mutant.printsTranscriptOnly()).toList();
        List<SemanticMutant> freshMutantCandidates = freshCapacity == 0 ? List.of()
                : specFidelityCritic
                        .authorSemanticMutants(specDocumentSnapshot, solutionFiles,
                                GenerationReviewSupport.withPriorSemanticMutants(report.findings(),
                                        java.util.stream.Stream.of(authoredPendingMutants, rekeyedWithoutVerdict, unexecutableStaleMutants).flatMap(List::stream).toList()),
                                semanticMutantHistory, usageSink, cancelled)
                        .stream().filter(candidate -> !applicablePendingMutants.contains(candidate)).limit(freshCapacity).toList();
        List<SemanticMutantOutcome> freshMutantOutcomes = freshMutantCandidates.isEmpty() ? List.of()
                : verifier.evaluateSemanticMutants(sandbox, sessionId, exercise, testsFiles, solutionFiles, seededStructuralTests, freshMutantCandidates, restoreCandidate);
        semanticMutantHistory = GenerationReviewSupport.rememberExecutedMutants(semanticMutantHistory, freshMutantOutcomes);
        List<SemanticMutantOutcome> mutantOutcomes = java.util.stream.Stream.of(pendingMutantOutcomes, transcriptOutcomes, freshMutantOutcomes).flatMap(List::stream).toList();
        List<SemanticMutant> validatedMutants = mutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE)
                .map(SemanticMutantOutcome::mutant).distinct().toList();
        List<SemanticMutant> inconclusivePendingMutants = pendingMutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.INCONCLUSIVE)
                .map(SemanticMutantOutcome::mutant).toList();
        survivorsWithoutVerdict = java.util.stream.Stream.of(inconclusivePendingMutants, unexecutableStaleMutants, staleTranscriptWithoutVerdict).flatMap(List::stream).distinct()
                .toList();
        semanticMutantsPendingRepair = validatedMutants;
        // Preserve every prior survivor without a verdict, including one keyed to a changed or deleted source, as unavailable evidence until an executed recheck resolves it;
        // never upgrade it to a current survivor.
        semanticMutantsAwaitingRecheck = survivorsWithoutVerdict;
        inconclusiveMutantProbes = (int) mutantOutcomes.stream().filter(outcome -> outcome.disposition() == Disposition.INCONCLUSIVE).count();

        Set<String> pendingWitnessNames = referenceWitnessesAwaitingPass.stream().map(ContractWitness::testName).collect(Collectors.toSet());
        Set<String> pendingReferenceAdjudicationNames = referenceWitnessesAwaitingAdjudication.stream().map(ContractWitness::testName).collect(Collectors.toSet());
        Set<String> pendingAdoptionAdjudicationNames = positiveWitnessesAwaitingAdjudication.stream().map(ContractWitness::testName).collect(Collectors.toSet());
        Set<String> approvedAdoptionNames = contractWitnessesPendingAdoption.stream().map(ContractWitness::testName).collect(Collectors.toSet());
        List<ContractWitness> candidates = ReferenceWitnessEvidence.candidates(referenceWitnessesAwaitingPass, referenceWitnessesAwaitingAdjudication,
                positiveWitnessesAwaitingAdjudication, contractWitnessesPendingAdoption, specFidelityCritic.authorContractWitnesses(specDocumentSnapshot,
                        GenerationReviewSupport.renderArtifactSources(testsFiles), GenerationReviewSupport.renderArtifactSources(solutionFiles), usageSink, cancelled));
        Set<String> mutatedRules = validatedMutants.stream().map(SemanticMutant::ruleId).collect(Collectors.toUnmodifiableSet());
        List<ContractWitnessOutcome> witnessOutcomes = verifier.evaluateContractWitnesses(sandbox, sessionId, exercise, testsFiles, seededStructuralTests, candidates,
                restoreCandidate);
        List<ContractWitnessOutcome> proposedAdjudications = ReferenceWitnessEvidence.adjudicationCandidates(freshMutantOutcomes, witnessOutcomes, pendingWitnessNames,
                approvedAdoptionNames, mutatedRules);
        SpecFidelityCritic.ReferenceWitnessReview referenceReview = proposedAdjudications.isEmpty() ? SpecFidelityCritic.ReferenceWitnessReview.empty()
                : Optional.ofNullable(specFidelityCritic.adjudicateReferenceWitnesses(specDocumentSnapshot, GenerationReviewSupport.renderArtifactSources(solutionFiles),
                        proposedAdjudications, usageSink, cancelled)).orElseGet(SpecFidelityCritic.ReferenceWitnessReview::empty);
        Set<String> alreadySourceApproved = java.util.stream.Stream.concat(pendingWitnessNames.stream(), approvedAdoptionNames.stream()).collect(Collectors.toUnmodifiableSet());
        contractWitnessesPendingAdoption = java.util.stream.Stream
                .concat(ReferenceWitnessEvidence.approvedForAdoption(witnessOutcomes, alreadySourceApproved).stream(), referenceReview.adoptableWitnesses().stream()).distinct()
                .toList();
        ReferenceWitnessEvidence.State witnessState = ReferenceWitnessEvidence.reconcile(proposedAdjudications, witnessOutcomes, pendingWitnessNames,
                pendingReferenceAdjudicationNames, pendingAdoptionAdjudicationNames, referenceReview);
        referenceWitnessesAwaitingPass = witnessState.awaitingPass();
        referenceWitnessesAwaitingAdjudication = witnessState.awaitingReferenceAdjudication();
        positiveWitnessesAwaitingAdjudication = witnessState.awaitingAdoptionAdjudication();
        emitter.accept(new GenerationReviewSupport.ExecutableProbeSummary(mutantOutcomes, witnessOutcomes, contractWitnessesPendingAdoption.size(),
                referenceWitnessesAwaitingPass.size(), referenceWitnessesAwaitingAdjudication.size(), positiveWitnessesAwaitingAdjudication.size()).render());
        List<SpecFidelityReport.Finding> combined = new ArrayList<>(SemanticEvidenceReconciler.reconcile(report, mutantOutcomes));
        combined.addAll(referenceReview.findings());
        contractWitnessesPendingAdoption.stream().filter(witness -> combined.stream().noneMatch(
                finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE && (finding.requirement() + "\n" + finding.detail()).contains(witness.testName())))
                .map(GenerationReviewSupport::approvedContractWitnessAvailable).forEach(combined::add);
        GenerationReviewSupport.addReferenceUnavailability(combined, witnessState.omittedReferenceAdjudication().size(), witnessState.pendingPassInconclusive().size(),
                witnessState.referenceAdjudicationInconclusive().size());
        survivorsWithoutVerdict.forEach(mutant -> combined.add(GenerationReviewSupport.semanticMutantRecheckUnavailable(mutant)));
        witnessState.stillFailing().forEach(witness -> combined.add(GenerationReviewSupport.referenceDefectStillFailing(witness)));
        validatedMutants.forEach(mutant -> combined.add(GenerationReviewSupport.semanticMutantFinding(mutant)));
        positiveWitnessesAwaitingAdjudication.stream().map(GenerationReviewSupport::positiveWitnessAdjudicationUnavailable).filter(finding -> !combined.contains(finding))
                .forEach(combined::add);
        return preserveReferenceWitnessState(new SpecFidelityReport(List.copyOf(combined)));
    }

    /**
     * The printed-transcript survivors proven against an earlier source of their class that still have no verdict. A conclusive probe of the current class replaces one; without
     * it, one is resolved only when execution shows that no weighted test reads what {@code main} prints. The rest stay unresolved in their original form, because their
     * print-only variant no longer matches the class.
     */
    private List<SemanticMutant> staleTranscriptProbesWithoutVerdict(List<SemanticMutant> stalePendingMutants, List<SemanticMutantOutcome> transcriptOutcomes,
            Map<String, String> testsFiles, Map<String, String> solutionFiles, @Nullable String testPlanJson, SeededStructuralTests seededStructuralTests,
            Runnable restoreCandidate) {
        Set<String> conclusivelyProbed = transcriptOutcomes.stream().filter(outcome -> outcome.disposition() != Disposition.INCONCLUSIVE)
                .map(outcome -> outcome.mutant().solutionPath()).collect(Collectors.toUnmodifiableSet());
        List<SemanticMutant> unreplaced = stalePendingMutants.stream().filter(SemanticMutant::printsTranscriptOnly)
                .filter(probe -> !conclusivelyProbed.contains(probe.solutionPath())).toList();
        if (unreplaced.isEmpty()) {
            return List.of();
        }
        Set<SemanticMutant> resolved = verifier
                .resolveStaleOutputOnlyMains(sandbox, sessionId, exercise, testsFiles, solutionFiles, testPlanJson, seededStructuralTests, unreplaced, restoreCandidate).stream()
                .filter(outcome -> outcome.disposition() == Disposition.KILLED_BY_GRADED_SUITE).map(SemanticMutantOutcome::mutant).collect(Collectors.toUnmodifiableSet());
        List<SemanticMutant> withoutVerdict = unreplaced.stream().filter(probe -> !resolved.contains(probe)).toList();
        if (!withoutVerdict.isEmpty()) {
            emitter.accept("Kept " + withoutVerdict.size() + " print-only main survivor(s) whose class the repair changed as unresolved evidence: no probe of the current class "
                    + "gave a verdict.");
        }
        return withoutVerdict;
    }

    /**
     * Starts a semantic repair round. An executable-oracle repair turns the validated survivors into the mutants its candidate must kill.
     *
     * @param repairsExecutableOracle whether the round repairs a weak executable test oracle
     */
    void beginRepair(boolean repairsExecutableOracle) {
        mutantsKilledByCurrentRepair = 0;
        if (repairsExecutableOracle) {
            semanticMutantsAwaitingKill = semanticMutantsPendingRepair;
            semanticMutantsPendingRepair = List.of();
        }
    }

    int mutantsKilledByCurrentRepair() {
        return mutantsKilledByCurrentRepair;
    }

    List<SemanticMutant> survivorsWithoutVerdict() {
        return survivorsWithoutVerdict;
    }

    boolean hasMutantsAwaitingRecheck() {
        return !semanticMutantsAwaitingRecheck.isEmpty();
    }

    /** Returns the inconclusive probe count of the latest executable review and resets it, so each count is reported on exactly one review-round event. */
    int takeInconclusiveMutantProbes() {
        int count = inconclusiveMutantProbes;
        inconclusiveMutantProbes = 0;
        return count;
    }

    /**
     * Rechecks executed mutants against the current candidate. A printed-transcript probe is judged by the weighted tests of the current grading plan; one probed against
     * these exact sources and tests reuses its verdict.
     */
    private List<SemanticMutantOutcome> recheckSemanticMutants(Map<RepositoryRole, Map<String, String>> candidateFiles, @Nullable String testPlanJson,
            SeededStructuralTests seededStructuralTests, List<SemanticMutant> mutants, Runnable restoreCandidate) {
        Map<String, String> solutionFiles = candidateFiles.getOrDefault(RepositoryRole.SOLUTION, Map.of());
        Map<String, SemanticMutantOutcome> known = transcriptVerdicts(candidateFiles, testPlanJson);
        List<SemanticMutantOutcome> settled = new ArrayList<>();
        List<SemanticMutant> executed = new ArrayList<>();
        List<SemanticMutant> transcriptProbes = new ArrayList<>();
        for (SemanticMutant mutant : mutants) {
            SemanticMutantOutcome reusable = known.get(mutant.solutionPath());
            if (!mutant.printsTranscriptOnly()) {
                executed.add(mutant);
            }
            else if (reusable != null && reusable.mutant().equals(mutant)) {
                settled.add(reusable);
            }
            else {
                transcriptProbes.add(mutant);
            }
        }
        List<SemanticMutantOutcome> outcomes = new ArrayList<>();
        if (!executed.isEmpty()) {
            outcomes.addAll(verifier.checkSemanticMutants(sandbox, sessionId, exercise, solutionFiles, seededStructuralTests, executed, restoreCandidate));
        }
        if (!transcriptProbes.isEmpty()) {
            outcomes.addAll(verifier.recheckOutputOnlyMains(sandbox, sessionId, exercise, candidateFiles.getOrDefault(RepositoryRole.TESTS, Map.of()), solutionFiles, testPlanJson,
                    seededStructuralTests, transcriptProbes, restoreCandidate));
        }
        rememberTranscriptVerdicts(outcomes);
        return java.util.stream.Stream.concat(settled.stream(), outcomes.stream()).toList();
    }

    /** The printed-transcript verdicts that still hold for this candidate; switches the cache to the candidate's digest, dropping verdicts for other inputs. */
    private Map<String, SemanticMutantOutcome> transcriptVerdicts(Map<RepositoryRole, Map<String, String>> candidateFiles, @Nullable String testPlanJson) {
        String digest = GenerationReviewSupport.probeInputDigest(candidateFiles, testPlanJson);
        if (!digest.equals(transcriptProbeDigest)) {
            transcriptProbeDigest = digest;
            transcriptProbeVerdicts = Map.of();
        }
        return transcriptProbeVerdicts;
    }

    private void rememberTranscriptVerdicts(List<SemanticMutantOutcome> outcomes) {
        Map<String, SemanticMutantOutcome> verdicts = new LinkedHashMap<>(transcriptProbeVerdicts);
        outcomes.stream().filter(outcome -> outcome.mutant().printsTranscriptOnly() && outcome.disposition() != Disposition.INCONCLUSIVE)
                .forEach(outcome -> verdicts.put(outcome.mutant().solutionPath(), outcome));
        transcriptProbeVerdicts = Map.copyOf(verdicts);
    }

    SpecFidelityReport preserveExecutableEvidenceState(SpecFidelityReport report) {
        return GenerationReviewSupport.preserveSemanticMutantState(preserveReferenceWitnessState(report), semanticMutantsPendingRepair, semanticMutantsAwaitingRecheck);
    }

    private SpecFidelityReport preserveReferenceWitnessState(SpecFidelityReport report) {
        SpecFidelityReport preserved = GenerationReviewSupport.preserveReferenceWitnessState(report, referenceWitnessesAwaitingPass, referenceWitnessesAwaitingAdjudication);
        return GenerationReviewSupport.preservePositiveWitnessState(preserved, positiveWitnessesAwaitingAdjudication);
    }
}
