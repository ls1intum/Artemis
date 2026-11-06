package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Earlier kills one verification rechecks for regression. Each costs one mutant build per verified repair, and every repair round can add up to
     * {@link #MAX_TRACKED_SEMANTIC_MUTANTS} kills, so the cost is bounded at twice the tracked set. The history itself is never truncated: the kills least recently rechecked
     * go first, and each one not conclusively rechecked against the candidate is reported as unavailable evidence.
     */
    private static final int MAX_REGRESSION_CHECKS_PER_VERIFICATION = 2 * MAX_TRACKED_SEMANTIC_MUTANTS;

    private final DifferentialVerifier verifier;

    private final SpecFidelityCritic specFidelityCritic;

    private final InteractiveSandbox sandbox;

    private final String sessionId;

    private final GenerationInput exercise;

    private final Consumer<ChatResponse> usageSink;

    private final BooleanSupplier cancelled;

    /** The run's progress line, so probe results read the same as every other stage. */
    private final Consumer<String> emitter;

    /** Every tracked mutant's latest outcome, from which survivors, kills, unavailable evidence and repair credit are derived. */
    private final MutantEvidence evidence = new MutantEvidence();

    /** The survivors the in-flight executable-oracle repair must kill; a selection of {@link MutantEvidence#surviving()}, not a separate state. */
    private List<SemanticMutant> repairTargets = List.of();

    private List<SemanticMutant.Exclusion> semanticMutantHistory = List.of();

    /** Mutant probes of the latest executable review that neither survived nor were killed; reported on the next review-round event, never as evidence. */
    private int inconclusiveMutantProbes;

    private SpecFidelityReport assessmentFindings = SpecFidelityReport.empty();

    private List<ContractWitness> referenceWitnessesAwaitingPass = List.of();

    private List<ContractWitness> referenceWitnessesAwaitingAdjudication = List.of();

    private List<ContractWitness> positiveWitnessesAwaitingAdjudication = List.of();

    // Independently approved reference-pass/starter-fail witnesses remain available across unrelated repair surfaces. A full-artifact re-review must not erase their evidence.
    private List<ContractWitness> contractWitnessesPendingAdoption = List.of();

    /** Whether the latest review of the candidate executed probes; after a text-only review, every survivor counts as one without a verdict in the progress comparison. */
    private boolean executableReviewRan = true;

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

    record AssessmentVerdict(VerificationResult verification, List<SemanticMutant> counterexamples) {

        AssessmentVerdict {
            counterexamples = List.copyOf(counterexamples);
        }
    }

    /** The authoritative checkpoint boundary also checks candidates authored by ordinary mechanical retries, before any verified snapshot can be published. */
    AssessmentVerdict assessBeforeCheckpoint(VerificationResult result, Map<RepositoryRole, Map<String, String>> candidateFiles, @Nullable String plan,
            SeededStructuralTests seeded, Runnable restoreCandidate) {
        Map<String, SemanticMutantOutcome> known = transcriptVerdicts(candidateFiles, plan);
        List<SemanticMutantOutcome> outcomes = new ArrayList<>(known.values());
        List<SemanticMutantOutcome> probed = verifier.probeOutputOnlyMains(sandbox, sessionId, exercise, candidateFiles.getOrDefault(RepositoryRole.TESTS, Map.of()),
                candidateFiles.getOrDefault(RepositoryRole.TEMPLATE, Map.of()), candidateFiles.getOrDefault(RepositoryRole.SOLUTION, Map.of()), plan, seeded, known.keySet(),
                restoreCandidate);
        rememberTranscriptVerdicts(probed);
        outcomes.addAll(probed);
        outcomes.forEach(evidence::record);
        // Learner-test probes are regenerated for each authoritative candidate, rather than entering the model-authored mutation lifecycle.
        List<SemanticMutantOutcome> studentTestOutcomes = verifier.probeStudentTestAssertions(sandbox, sessionId, exercise,
                candidateFiles.getOrDefault(RepositoryRole.TEMPLATE, Map.of()), candidateFiles.getOrDefault(RepositoryRole.SOLUTION, Map.of()), plan, seeded, restoreCandidate);
        outcomes.addAll(studentTestOutcomes);
        List<SemanticMutant> counterexamples = outcomes.stream().filter(outcome -> outcome.disposition() == Disposition.SURVIVED_GRADED_SUITE).map(SemanticMutantOutcome::mutant)
                .toList();
        assessmentFindings = new SpecFidelityReport(counterexamples.stream().map(GenerationReviewSupport::semanticMutantFinding).toList());
        if (outcomes.stream().anyMatch(outcome -> outcome.disposition() == Disposition.INCONCLUSIVE)) {
            List<SpecFidelityReport.Finding> findings = new ArrayList<>(assessmentFindings.findings());
            String detail = "The draft passed mechanical verification, but assessment evidence is incomplete. "
                    + outcomes.stream().filter(outcome -> outcome.disposition() == Disposition.INCONCLUSIVE).map(SemanticMutantOutcome::diagnostic)
                            .filter(diagnostic -> !diagnostic.isBlank()).distinct().collect(Collectors.joining("\n"));
            findings.addAll(SpecFidelityReport.executableEvidenceUnavailable(detail).findings());
            assessmentFindings = new SpecFidelityReport(findings);
        }
        return new AssessmentVerdict(result, counterexamples);
    }

    /**
     * Rechecks a mechanically verified candidate against the mutants the in-flight repair must kill and, while a repair is in flight, the earlier kills, each in the form its
     * current source allows. Every outcome goes to {@link MutantEvidence}, from which credit, regressions and unavailable evidence follow. Targets keyed to a changed reference
     * source stay survivors for the executable review instead of vanishing as progress. A repair that killed at least one target is kept and carries the rest forward as
     * findings; one that killed none, or whose suite lets an earlier kill survive again, is rejected.
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
        evidence.beginCandidate();
        Map<String, String> currentSolution = candidateFiles.getOrDefault(RepositoryRole.SOLUTION, Map.of());
        List<SemanticMutant> applicableMutants = repairTargets.stream().filter(mutant -> mutant.originalSolutionSource().equals(currentSolution.get(mutant.solutionPath())))
                .toList();
        if (applicableMutants.size() < repairTargets.size()) {
            // Not a kill and not an acceptance check against this source: they stay survivors, and the review rechecks them against the current solution.
            emitter.accept("Kept " + (repairTargets.size() - applicableMutants.size()) + " semantic mutant acceptance check(s) pending because the repair changed their "
                    + "reference source; the executable review rechecks them against the current solution.");
        }
        repairTargets = List.of();
        Set<MutantEvidence.Variant> targeted = applicableMutants.stream().map(evidence::identity).collect(Collectors.toUnmodifiableSet());
        List<SemanticMutant> earlierKills = !repairInFlight ? List.of() : evidence.kills().stream().filter(mutant -> !targeted.contains(evidence.identity(mutant))).toList();
        // Bounded cost: the least recently checked kills go first, and the deferred rest is recorded without a verdict rather than dropped.
        earlierKills.stream().skip(MAX_REGRESSION_CHECKS_PER_VERIFICATION).forEach(evidence::recordWithoutVerdict);
        List<SemanticMutantOutcome> allOutcomes = checkAgainstCurrentSource(
                java.util.stream.Stream.concat(applicableMutants.stream(), earlierKills.stream().limit(MAX_REGRESSION_CHECKS_PER_VERIFICATION)).toList(), candidateFiles,
                testPlanSnapshot, seededStructuralTests, restoreCandidate);
        List<SemanticMutantOutcome> targetOutcomes = allOutcomes.stream().filter(outcome -> targeted.contains(evidence.identity(outcome.mutant()))).toList();
        List<SemanticMutantOutcome> regressionOutcomes = allOutcomes.stream().filter(outcome -> !targeted.contains(evidence.identity(outcome.mutant()))).toList();
        targetOutcomes.forEach(evidence::record);
        // Judged on this recheck alone: a correction that kills a regressed mutant again clears the regression.
        List<SemanticMutant> regressed = regressionOutcomes.stream().filter(evidence::record).map(SemanticMutantOutcome::mutant).toList();
        GenerationReviewSupport.SemanticMutantRecheck recheck = GenerationReviewSupport.semanticMutantRecheck(targetOutcomes);
        // A repair that killed a targeted mutant is kept even when others still survive; they become the next round's findings rather than a reason to discard the kill.
        boolean unkilledTargetsBlock = !recheck.unresolvedMutants().isEmpty() && evidence.killsSinceRepairBegan() == 0;
        if (regressed.isEmpty() && !unkilledTargetsBlock) {
            if (!recheck.unresolvedMutants().isEmpty()) {
                emitter.accept("The repair killed " + evidence.killsSinceRepairBegan() + " targeted mutant(s); carrying the " + recheck.unresolvedMutants().size()
                        + " still unresolved forward as review findings.");
            }
            return result;
        }
        repairTargets = recheck.unresolvedMutants();
        List<String> reasons = new ArrayList<>(result.reasons());
        if (unkilledTargetsBlock) {
            reasons.addAll(recheck.failureReasons());
        }
        regressed.forEach(mutant -> reasons.add("The graded suite no longer kills the semantic mutant for rule " + mutant.ruleId() + " (" + mutant.counterexample().wrongBehavior()
                + ") that an earlier repair's tests killed. Restore an assertion that kills it; keep the verified solution unchanged."));
        return new VerificationResult(false, result.solutionPassed(), result.templateFailed(), result.testCount(), List.copyOf(reasons), result.templateFailureEvidence());
    }

    /**
     * Checks tracked mutants against the current candidate, each in the form its source allows: unchanged, rekeyed to a changed file, or for a printed-transcript probe whose
     * class changed, re-derived for the current class. A mutant whose file is gone, or whose check gives no verdict, comes back inconclusive; nothing is skipped.
     */
    private List<SemanticMutantOutcome> checkAgainstCurrentSource(List<SemanticMutant> mutants, Map<RepositoryRole, Map<String, String>> candidateFiles,
            @Nullable String testPlanJson, SeededStructuralTests seededStructuralTests, Runnable restoreCandidate) {
        if (mutants.isEmpty()) {
            return List.of();
        }
        Map<String, String> solutionFiles = candidateFiles.getOrDefault(RepositoryRole.SOLUTION, Map.of());
        List<SemanticMutantOutcome> outcomes = new ArrayList<>();
        List<SemanticMutant> executable = new ArrayList<>();
        List<SemanticMutant> changedTranscriptProbes = new ArrayList<>();
        for (SemanticMutant mutant : mutants) {
            String current = solutionFiles.get(mutant.solutionPath());
            if (current == null) {
                outcomes.add(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE));
            }
            else if (mutant.printsTranscriptOnly() && !current.equals(mutant.originalSolutionSource())) {
                changedTranscriptProbes.add(mutant);
            }
            else {
                executable.add(mutant.rekeyedTo(current));
            }
        }
        if (!executable.isEmpty()) {
            outcomes.addAll(recheckSemanticMutants(candidateFiles, testPlanJson, seededStructuralTests, executable, restoreCandidate));
        }
        if (!changedTranscriptProbes.isEmpty()) {
            Set<String> probedPaths = changedTranscriptProbes.stream().map(SemanticMutant::solutionPath).collect(Collectors.toUnmodifiableSet());
            List<SemanticMutantOutcome> currentProbes = verifier.probeOutputOnlyMains(sandbox, sessionId, exercise, candidateFiles.getOrDefault(RepositoryRole.TESTS, Map.of()),
                    candidateFiles.getOrDefault(RepositoryRole.TEMPLATE, Map.of()), solutionFiles, testPlanJson, seededStructuralTests,
                    solutionFiles.keySet().stream().filter(path -> !probedPaths.contains(path)).collect(Collectors.toUnmodifiableSet()), restoreCandidate);
            outcomes.addAll(changedTranscriptOutcomes(changedTranscriptProbes, currentProbes, candidateFiles, testPlanJson, seededStructuralTests, restoreCandidate));
        }
        return outcomes;
    }

    /** Records that no executable review ran for this candidate, so every prior survivor still counts as one without a verdict. */
    SpecFidelityReport withoutExecutableReview(SpecFidelityReport report) {
        executableReviewRan = false;
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
        executableReviewRan = true;
        // Every prior survivor gets an outcome, never truncated: the capacity bound applies only to fresh proposals, after reserving room for all retained evidence.
        List<SemanticMutant> priorSurvivors = evidence.unresolvedSurvivors();
        List<SemanticMutant> applicablePendingMutants = priorSurvivors.stream().filter(mutant -> mutant.originalSolutionSource().equals(solutionFiles.get(mutant.solutionPath())))
                .toList();
        // A survivor whose file a repair changed is rechecked against the current source rather than retired: retiring it would read as a drained blocker. A printed-
        // transcript probe is re-derived for the current class below, and a deleted file leaves nothing to execute.
        List<SemanticMutant> stalePendingMutants = priorSurvivors.stream().filter(mutant -> !applicablePendingMutants.contains(mutant)).toList();
        List<SemanticMutant> staleTranscriptProbes = stalePendingMutants.stream().filter(SemanticMutant::printsTranscriptOnly)
                .filter(mutant -> solutionFiles.get(mutant.solutionPath()) != null).toList();
        List<SemanticMutantOutcome> pendingMutantOutcomes = checkAgainstCurrentSource(
                java.util.stream.Stream.concat(applicablePendingMutants.stream(), stalePendingMutants.stream().filter(mutant -> !staleTranscriptProbes.contains(mutant))).toList(),
                candidateFiles, testPlanJson, seededStructuralTests, restoreCandidate);
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
        List<SemanticMutantOutcome> staleTranscriptOutcomes = changedTranscriptOutcomes(staleTranscriptProbes, transcriptOutcomes, candidateFiles, testPlanJson,
                seededStructuralTests, restoreCandidate);
        pendingMutantOutcomes.forEach(evidence::record);
        transcriptOutcomes.forEach(evidence::record);
        staleTranscriptOutcomes.forEach(evidence::record);
        List<SemanticMutant> withoutVerdict = evidence.unverifiedSurvivors();
        if (!withoutVerdict.isEmpty()) {
            emitter.accept("Kept " + withoutVerdict.size() + " earlier semantic mutant survivor(s) without a verdict against this candidate as unresolved evidence; the executable "
                    + "reviewer will propose replacements against the current solution.");
        }
        int freshCapacity = Math.max(0, MAX_TRACKED_SEMANTIC_MUTANTS - evidence.unresolvedSurvivors().size());
        List<SemanticMutant> authoredPendingMutants = applicablePendingMutants.stream().filter(mutant -> !mutant.printsTranscriptOnly()).toList();
        List<SemanticMutant> freshMutantCandidates = freshCapacity == 0 ? List.of()
                : specFidelityCritic.authorSemanticMutants(specDocumentSnapshot, solutionFiles,
                        GenerationReviewSupport.withPriorSemanticMutants(report.findings(),
                                java.util.stream.Stream.concat(authoredPendingMutants.stream(), withoutVerdict.stream()).distinct().toList()),
                        semanticMutantHistory, usageSink, cancelled).stream().filter(candidate -> !applicablePendingMutants.contains(candidate)).limit(freshCapacity).toList();
        List<SemanticMutantOutcome> freshMutantOutcomes = freshMutantCandidates.isEmpty() ? List.of()
                : verifier.evaluateSemanticMutants(sandbox, sessionId, exercise, testsFiles, solutionFiles, seededStructuralTests, freshMutantCandidates, restoreCandidate);
        semanticMutantHistory = GenerationReviewSupport.rememberExecutedMutants(semanticMutantHistory, freshMutantOutcomes);
        freshMutantOutcomes.forEach(evidence::record);
        List<SemanticMutantOutcome> mutantOutcomes = java.util.stream.Stream.of(pendingMutantOutcomes, transcriptOutcomes, freshMutantOutcomes).flatMap(List::stream).toList();
        List<SemanticMutant> validatedMutants = evidence.surviving();
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
        evidence.unverifiedSurvivors().forEach(mutant -> combined.add(GenerationReviewSupport.semanticMutantRecheckUnavailable(mutant)));
        evidence.unverifiedKills().forEach(mutant -> combined.add(GenerationReviewSupport.regressionRecheckUnavailable(mutant)));
        witnessState.stillFailing().forEach(witness -> combined.add(GenerationReviewSupport.referenceDefectStillFailing(witness)));
        validatedMutants.forEach(mutant -> combined.add(GenerationReviewSupport.semanticMutantFinding(mutant)));
        positiveWitnessesAwaitingAdjudication.stream().map(GenerationReviewSupport::positiveWitnessAdjudicationUnavailable).filter(finding -> !combined.contains(finding))
                .forEach(combined::add);
        return preserveReferenceWitnessState(new SpecFidelityReport(List.copyOf(combined)));
    }

    /**
     * Outcomes for printed-transcript probes whose class changed since they were proven, whose print-only variant therefore no longer matches it. A conclusive probe of the
     * current class decides; without one, a {@code main} that prints nothing passing every weighted test shows that no weighted test reads its output, keyed to the current
     * class; anything else gives no verdict.
     */
    private List<SemanticMutantOutcome> changedTranscriptOutcomes(List<SemanticMutant> probes, List<SemanticMutantOutcome> currentProbes,
            Map<RepositoryRole, Map<String, String>> candidateFiles, @Nullable String testPlanJson, SeededStructuralTests seededStructuralTests, Runnable restoreCandidate) {
        if (probes.isEmpty()) {
            return List.of();
        }
        Map<String, String> solutionFiles = candidateFiles.getOrDefault(RepositoryRole.SOLUTION, Map.of());
        Map<String, SemanticMutantOutcome> conclusive = new LinkedHashMap<>();
        currentProbes.stream().filter(outcome -> outcome.disposition() != Disposition.INCONCLUSIVE).forEach(outcome -> conclusive.put(outcome.mutant().solutionPath(), outcome));
        List<SemanticMutant> unreplaced = probes.stream().filter(probe -> !conclusive.containsKey(probe.solutionPath())).toList();
        Set<SemanticMutant> resolved = unreplaced.isEmpty() ? Set.of()
                : verifier.resolveStaleOutputOnlyMains(sandbox, sessionId, exercise, solutionFiles, testPlanJson, seededStructuralTests, unreplaced, restoreCandidate).stream()
                        .filter(outcome -> outcome.disposition() == Disposition.KILLED_BY_GRADED_SUITE).map(SemanticMutantOutcome::mutant).collect(Collectors.toUnmodifiableSet());
        return probes.stream().map(probe -> {
            SemanticMutantOutcome replacement = conclusive.get(probe.solutionPath());
            if (replacement != null) {
                evidence.inheritIdentity(probe, replacement.mutant());
            }
            return replacement != null ? replacement
                    : resolved.contains(probe) ? new SemanticMutantOutcome(probe.rekeyedTo(solutionFiles.get(probe.solutionPath())), Disposition.KILLED_BY_GRADED_SUITE)
                            : new SemanticMutantOutcome(probe, Disposition.INCONCLUSIVE);
        }).toList();
    }

    /**
     * Starts a semantic repair round. An executable-oracle repair turns the current survivors into the mutants its candidate must kill.
     *
     * @param repairsExecutableOracle whether the round repairs a weak executable test oracle
     */
    void beginRepair(boolean repairsExecutableOracle) {
        evidence.beginRepair();
        repairTargets = repairsExecutableOracle ? evidence.surviving() : List.of();
    }

    /** A conclusive regression from verification or review blocks promotion, even when another target was killed. */
    boolean hasRegressions() {
        return !evidence.regressions().isEmpty();
    }

    /** Tracked survivors a candidate's suite killed since the current repair began, from the acceptance recheck and the review alike. */
    int mutantsKilledByCurrentRepair() {
        return evidence.killsSinceRepairBegan();
    }

    /** The survivors the repair-progress comparison must still count: those without a verdict, or every survivor when no executable review ran. */
    List<SemanticMutant> survivorsWithoutVerdict() {
        return executableReviewRan ? evidence.unverifiedSurvivors() : evidence.unresolvedSurvivors();
    }

    boolean hasMutantsAwaitingRecheck() {
        return !evidence.unverifiedSurvivors().isEmpty() || !evidence.unverifiedKills().isEmpty();
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

    SpecFidelityReport preserveAssessmentFindings(SpecFidelityReport report) {
        List<SpecFidelityReport.Finding> mandatory = new ArrayList<>(report.findings());
        assessmentFindings.findings().stream().filter(finding -> !mandatory.contains(finding)).forEach(mandatory::add);
        return new SpecFidelityReport(mandatory);
    }

    SpecFidelityReport preserveExecutableEvidenceState(SpecFidelityReport report) {
        report = preserveAssessmentFindings(report);
        SpecFidelityReport preserved = GenerationReviewSupport.preserveSemanticMutantState(preserveReferenceWitnessState(report), evidence.unresolvedSurvivors());
        if (evidence.unverifiedKills().isEmpty()) {
            return preserved;
        }
        List<SpecFidelityReport.Finding> findings = new ArrayList<>(preserved.findings());
        evidence.unverifiedKills().stream().map(GenerationReviewSupport::regressionRecheckUnavailable).filter(finding -> !findings.contains(finding)).forEach(findings::add);
        return new SpecFidelityReport(List.copyOf(findings));
    }

    private SpecFidelityReport preserveReferenceWitnessState(SpecFidelityReport report) {
        SpecFidelityReport preserved = GenerationReviewSupport.preserveReferenceWitnessState(report, referenceWitnessesAwaitingPass, referenceWitnessesAwaitingAdjudication);
        return GenerationReviewSupport.preservePositiveWitnessState(preserved, positiveWitnessesAwaitingAdjudication);
    }
}
