package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.hibernate.LazyInitializationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;

/**
 * Build-verification helper for exercise-variant generation: polls the build result for a commit and
 * evaluates whether the repository-type-specific target was reached (solution must pass 100%, template must
 * fail with tests present, tests build must be successful) — exactly the deterministic gate the variant
 * verifier needs.
 *
 * NOTE: this deliberately MIRRORS the private polling logic in {@code HyperionCodeGenerationExecutionService}
 * instead of extracting it: the codegen service is actively developed by other students and must not be
 * modified. Keep the target-result semantics here in sync with {@code hasReachedTargetResult} there if they
 * ever change.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class VariantBuildVerificationService {

    private static final Logger log = LoggerFactory.getLogger(VariantBuildVerificationService.class);

    private static final long TIMEOUT = 180_000; // 3 minutes

    private static final long POLL_INTERVAL = 3_000; // 3 seconds

    /**
     * Terminal states of one build-result poll.
     */
    public enum BuildResultState {
        SUCCESS, FAILED, TIMED_OUT, PARTICIPATION_NOT_FOUND, CI_TRIGGER_FAILED,
    }

    /**
     * Result of polling for a build result.
     *
     * @param result the build result, or {@code null} when none was obtained
     * @param state  how the poll ended
     */
    public record BuildResultOutcome(Result result, BuildResultState state) {
    }

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    private final GitService gitService;

    private final ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService;

    /**
     * Participations whose wait timed out, mapped to the instant the wait gave up. The build they were waiting for
     * was abandoned but keeps running in CI, so the next wait for that participation must discard the straggler it
     * eventually produces: that result reports on the repository as it was BEFORE the repair round, yet it completes
     * inside the new wait's freshness window and would otherwise be accepted as its result. Builds per participation
     * are sequential (see the freshness note on {@link #waitForBuildResult}), so discarding exactly one result per
     * abandoned build is enough. The recorded instant is what tells a still-pending straggler from one that already
     * landed while the repair round ran — see {@link #stragglerAlreadyLanded}. Consumed on the next wait for that
     * participation.
     */
    private final Map<Long, Instant> abandonedBuildWaits = new ConcurrentHashMap<>();

    public VariantBuildVerificationService(TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository, GitService gitService, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService) {
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.gitService = gitService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingFeedbackSynthesizerService = programmingFeedbackSynthesizerService;
    }

    /**
     * Triggers the build of one repository and returns the handle to wait for it with. The trigger time is taken
     * before the request, so a result that completes immediately still passes the freshness bound.
     *
     * @param exercise       the exercise whose repository is built
     * @param repositoryUri  the repository whose last commit is built
     * @param repositoryType which repository the build belongs to (TESTS builds use the solution participation)
     * @return the pending build to hand to {@link #waitForBuildResults}
     * @throws ContinuousIntegrationException when the build could not be triggered
     */
    public PendingBuild triggerBuild(ProgrammingExercise exercise, LocalVCRepositoryUri repositoryUri, RepositoryType repositoryType) {
        String commitHash = gitService.getLastCommitHash(repositoryUri);
        Instant triggeredAt = Instant.now();
        ProgrammingExerciseParticipation participation = switch (repositoryType) {
            case TEMPLATE -> programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(exercise.getId());
            default -> programmingExerciseParticipationService.retrieveSolutionParticipation(exercise);
        };
        continuousIntegrationTriggerService.triggerBuild(participation, commitHash, repositoryType);
        return new PendingBuild(commitHash, triggeredAt);
    }

    /**
     * Polls for the participation's latest build result completed after {@code notBefore} and evaluates it
     * against the repository-type target.
     *
     * The poll deliberately matches by PARTICIPATION + freshness, not by the built commit's hash: once a
     * TEST-type submission exists for the current tests commit, Artemis attaches every subsequent
     * template/solution build result to THAT submission (the grading service matches per-submission-type commit
     * hashes) and never creates a submission carrying the built commit's hash — a hash-based poll then times out
     * forever while green results accumulate (observed live on exercise 54). Freshness identifies our build:
     * builds per participation are strictly sequential within one variant job (tool calls and verify gates run
     * one after another), and parallel jobs operate on distinct exercise clones with distinct participations.
     *
     * @param exercise       the exercise whose participation is polled
     * @param commitHash     the commit the build was triggered for (log context only)
     * @param repositoryType which repository the build belongs to (TESTS builds use the solution participation)
     * @param notBefore      only results completed after this instant are accepted — pass the trigger time,
     *                           otherwise a stale pre-trigger result would be returned immediately
     *                           (build-dependency constraint)
     * @return the outcome; {@link BuildResultState#SUCCESS} iff the target result was reached
     * @throws InterruptedException when the polling thread is interrupted
     */
    public BuildResultOutcome waitForBuildResult(ProgrammingExercise exercise, String commitHash, RepositoryType repositoryType, Instant notBefore) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        ProgrammingExerciseParticipation participation = resolveParticipation(exercise, repositoryType);
        if (participation == null) {
            log.warn("Could not find participation for repoType {} in exercise {}", repositoryType, exercise.getId());
            return new BuildResultOutcome(null, BuildResultState.PARTICIPATION_NOT_FOUND);
        }

        int pollCount = 0;
        // A previous wait for this participation timed out; the first fresh result here may be that abandoned
        // build's straggler, which was produced before this round's changes (see the field's javadoc).
        Instant abandonedAt = abandonedBuildWaits.remove(participation.getId());
        boolean discardAbandonedResult = abandonedAt != null;
        Instant acceptFrom = notBefore;
        while (System.currentTimeMillis() - startTime < TIMEOUT) {
            try {
                Optional<Result> result = resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());
                if (discardAbandonedResult && result.isPresent() && stragglerAlreadyLanded(result.get(), abandonedAt, notBefore)) {
                    discardAbandonedResult = false;
                    log.debug("The {} build abandoned for exercise {} at {} already completed before this round was triggered: nothing left to discard", repositoryType,
                            exercise.getId(), abandonedAt);
                }
                if (result.isPresent() && isFreshEnough(result.get(), acceptFrom) && discardAbandonedResult) {
                    discardAbandonedResult = false;
                    acceptFrom = result.get().getCompletionDate().toInstant();
                    log.warn("Discarding the {} build result of exercise {} that completed at {}: it belongs to the build a previous wait abandoned when it timed out, so it "
                            + "does not cover this round's changes", repositoryType, exercise.getId(), acceptFrom);
                }
                else if (result.isPresent() && isFreshEnough(result.get(), acceptFrom)) {
                    attachTestCaseFeedback(result.get(), exercise, repositoryType);
                    log.debug("Found build result for commit {} after {} polls ({}ms)", commitHash, pollCount, System.currentTimeMillis() - startTime);
                    warnIfCommitMismatch(repositoryType, exercise.getId(), commitHash, result.get());
                    return new BuildResultOutcome(result.get(), hasReachedTargetResult(repositoryType, result.get()) ? BuildResultState.SUCCESS : BuildResultState.FAILED);
                }

                pollCount++;

                Thread.sleep(POLL_INTERVAL);
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            }
            catch (Exception e) {
                log.warn("Exception while polling for build result for commit {}: {}. Continuing...", commitHash, e.getMessage());
                Thread.sleep(POLL_INTERVAL);
            }
        }
        log.warn("Timed out waiting for build result for commit {} in exercise {} after {} polls ({}ms)", commitHash, exercise.getId(), pollCount, TIMEOUT);
        noteAbandonedBuild(participation.getId(), Instant.now());
        return new BuildResultOutcome(null, BuildResultState.TIMED_OUT);
    }

    /**
     * Records that a wait for this participation timed out, so the next wait for it discards the abandoned build's
     * straggler result. Package-private: a test cannot afford to reach this state through the real timeout, and it
     * needs to place the abandoned instant relative to the results it stubs.
     *
     * @param participationId the participation whose build was abandoned
     * @param abandonedAt     when the wait gave up; the abandoned build can only complete after this instant
     */
    void noteAbandonedBuild(long participationId, Instant abandonedAt) {
        abandonedBuildWaits.put(participationId, abandonedAt);
    }

    /**
     * Whether this result shows that the abandoned build already landed BEFORE this round's build was triggered, so
     * the discard mark is spent. A completion after the wait gave up but before {@code notBefore} can only be the
     * abandoned build's: the current build did not exist yet, and builds per participation are sequential. Such a
     * straggler has already been passed over by the freshness bound, and discarding on top of it would throw away
     * this round's own result and report a finished build as a timeout.
     *
     * @param result      the participation's latest result
     * @param abandonedAt when the earlier wait gave up, or {@code null} when no build was abandoned
     * @param notBefore   this round's trigger time, or {@code null} when the wait accepts any result
     * @return whether the straggler landed pre-trigger and no result of this wait must be discarded
     */
    private static boolean stragglerAlreadyLanded(Result result, @Nullable Instant abandonedAt, @Nullable Instant notBefore) {
        ZonedDateTime completionDate = result.getCompletionDate();
        if (abandonedAt == null || notBefore == null || completionDate == null) {
            return false;
        }
        Instant completedAt = completionDate.toInstant();
        return completedAt.isAfter(abandonedAt) && completedAt.isBefore(notBefore);
    }

    /**
     * A build that has been triggered and whose result is awaited by {@link #waitForBuildResults}: the commit
     * it was triggered for (log context only) and the trigger time that bounds result freshness.
     *
     * @param commitHash  the commit the build was triggered for
     * @param triggeredAt only results completed after this instant are accepted for this build
     */
    public record PendingBuild(String commitHash, Instant triggeredAt) {
    }

    /**
     * Joint variant of {@link #waitForBuildResult}: waits for the results of SEVERAL repository-type builds that
     * were triggered together, under a SINGLE shared timeout. The builds run concurrently in CI, so waiting for
     * them jointly costs about the slower build instead of the sum — this is the point of triggering both before
     * waiting for either. Each repository type is polled against its own participation with its own freshness
     * bound, so results stay attributed per type exactly as the single-build poll does.
     *
     * Intended for SOLUTION + TEMPLATE, whose participations are distinct; TESTS shares the solution
     * participation and must be built on its own (a tests change invalidates the other builds anyway).
     *
     * @param exercise the exercise whose participations are polled
     * @param pending  the awaited builds keyed by repository type
     * @return one outcome per requested repository type
     * @throws InterruptedException when the polling thread is interrupted
     */
    public Map<RepositoryType, BuildResultOutcome> waitForBuildResults(ProgrammingExercise exercise, Map<RepositoryType, PendingBuild> pending) throws InterruptedException {
        Map<RepositoryType, BuildResultOutcome> outcomes = new EnumMap<>(RepositoryType.class);
        Map<RepositoryType, ProgrammingExerciseParticipation> awaited = new EnumMap<>(RepositoryType.class);
        for (RepositoryType repositoryType : pending.keySet()) {
            ProgrammingExerciseParticipation participation = resolveParticipation(exercise, repositoryType);
            if (participation == null) {
                log.warn("Could not find participation for repoType {} in exercise {}", repositoryType, exercise.getId());
                outcomes.put(repositoryType, new BuildResultOutcome(null, BuildResultState.PARTICIPATION_NOT_FOUND));
            }
            else {
                awaited.put(repositoryType, participation);
            }
        }
        long startTime = System.currentTimeMillis();
        // Same straggler guard as the single-build wait: a type whose previous wait timed out must discard the
        // first result of this one (see participationsWithAbandonedBuild).
        Map<RepositoryType, Boolean> discardAbandonedResult = new EnumMap<>(RepositoryType.class);
        Map<RepositoryType, Instant> abandonedAt = new EnumMap<>(RepositoryType.class);
        Map<RepositoryType, Instant> acceptFrom = new EnumMap<>(RepositoryType.class);
        awaited.forEach((repositoryType, participation) -> {
            Instant abandoned = abandonedBuildWaits.remove(participation.getId());
            discardAbandonedResult.put(repositoryType, abandoned != null);
            if (abandoned != null) {
                abandonedAt.put(repositoryType, abandoned);
            }
            acceptFrom.put(repositoryType, pending.get(repositoryType).triggeredAt());
        });
        while (!awaited.isEmpty() && System.currentTimeMillis() - startTime < TIMEOUT) {
            Iterator<Map.Entry<RepositoryType, ProgrammingExerciseParticipation>> iterator = awaited.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<RepositoryType, ProgrammingExerciseParticipation> entry = iterator.next();
                RepositoryType repositoryType = entry.getKey();
                PendingBuild pendingBuild = pending.get(repositoryType);
                try {
                    Optional<Result> result = resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(entry.getValue().getId());
                    if (discardAbandonedResult.get(repositoryType) && result.isPresent()
                            && stragglerAlreadyLanded(result.get(), abandonedAt.get(repositoryType), pendingBuild.triggeredAt())) {
                        discardAbandonedResult.put(repositoryType, false);
                        log.debug("The {} build abandoned for exercise {} at {} already completed before this round was triggered: nothing left to discard", repositoryType,
                                exercise.getId(), abandonedAt.get(repositoryType));
                    }
                    if (result.isPresent() && isFreshEnough(result.get(), acceptFrom.get(repositoryType)) && discardAbandonedResult.get(repositoryType)) {
                        discardAbandonedResult.put(repositoryType, false);
                        Instant completedAt = result.get().getCompletionDate().toInstant();
                        acceptFrom.put(repositoryType, completedAt);
                        log.warn("Discarding the {} build result of exercise {} that completed at {}: it belongs to the build a previous wait abandoned when it timed out, so "
                                + "it does not cover this round's changes", repositoryType, exercise.getId(), completedAt);
                    }
                    else if (result.isPresent() && isFreshEnough(result.get(), acceptFrom.get(repositoryType))) {
                        attachTestCaseFeedback(result.get(), exercise, repositoryType);
                        warnIfCommitMismatch(repositoryType, exercise.getId(), pendingBuild.commitHash(), result.get());
                        outcomes.put(repositoryType,
                                new BuildResultOutcome(result.get(), hasReachedTargetResult(repositoryType, result.get()) ? BuildResultState.SUCCESS : BuildResultState.FAILED));
                        iterator.remove();
                    }
                }
                catch (Exception e) {
                    log.warn("Exception while polling for {} build result for commit {}: {}. Continuing...", repositoryType, pendingBuild.commitHash(), e.getMessage());
                }
            }
            if (!awaited.isEmpty()) {
                Thread.sleep(POLL_INTERVAL);
            }
        }
        awaited.forEach((repositoryType, participation) -> {
            log.warn("Timed out waiting for {} build result in exercise {} after {}ms", repositoryType, exercise.getId(), TIMEOUT);
            noteAbandonedBuild(participation.getId(), Instant.now());
            outcomes.put(repositoryType, new BuildResultOutcome(null, BuildResultState.TIMED_OUT));
        });
        return outcomes;
    }

    /**
     * Attaches the automatic test-case and SCA feedback, which lives in typed tables, as legacy {@link Feedback}
     * views so {@link #describeBuildResult} can render the per-test summary. The loaded result graph is detached,
     * so the exercise context is passed explicitly (mirrors {@code HyperionCodeGenerationExecutionService}).
     *
     * @param result         the freshly polled build result
     * @param exercise       the exercise the result belongs to
     * @param repositoryType the repository the result belongs to
     */
    private void attachTestCaseFeedback(Result result, ProgrammingExercise exercise, RepositoryType repositoryType) {
        try {
            programmingFeedbackSynthesizerService.attachSynthesizedFeedback(result, exercise, repositoryType == RepositoryType.SOLUTION || repositoryType == RepositoryType.TESTS);
        }
        catch (Exception e) {
            // Rendering detail only. Letting this reach the polling loop's catch would discard a result that IS
            // there and keep polling until the gate reports TIMED_OUT — a failed build gate over a missing summary.
            log.warn("Could not attach synthesized feedback to the {} build result of exercise {}: {}", repositoryType, exercise.getId(), e.getMessage());
        }
    }

    private ProgrammingExerciseParticipation resolveParticipation(ProgrammingExercise exercise, RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElse(null);
            // tests also use the solution participation
            case SOLUTION, TESTS -> solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElse(null);
            default -> null;
        };
    }

    /**
     * Evaluates whether the result reaches the per-repository-type target: TEMPLATE must score exactly 0 with
     * at least one executed test case, SOLUTION must score exactly 100, TESTS must build successfully.
     *
     * @param repositoryType the repository the result belongs to
     * @param result         the build result
     * @return true iff the target is reached
     */
    public boolean hasReachedTargetResult(RepositoryType repositoryType, Result result) {
        if (result == null) {
            return false;
        }
        return switch (repositoryType) {
            case TEMPLATE -> isExactScore(result, 0.0) && result.getTestCaseCount() != null && result.getTestCaseCount() > 0;
            case SOLUTION -> isExactScore(result, 100.0);
            case TESTS -> Boolean.TRUE.equals(result.isSuccessful());
            default -> false;
        };
    }

    private static boolean isExactScore(Result result, double targetScore) {
        Double score = result.getScore();
        return score != null && Double.compare(score, targetScore) == 0;
    }

    private static boolean isFreshEnough(Result result, @Nullable Instant notBefore) {
        if (notBefore == null) {
            return true;
        }
        return result.getCompletionDate() != null && result.getCompletionDate().toInstant().isAfter(notBefore);
    }

    /**
     * Logs (never rejects) when the accepted result's own commit hash differs from the one this poll actually
     * triggered. Deliberately observational, not a gate: this class already matches by PARTICIPATION + freshness
     * rather than by commit hash on purpose (see the class-level javadoc) — once a TEST-type submission exists
     * for the current tests commit, Artemis attaches every subsequent template/solution build result to THAT
     * submission and never creates one carrying the just-built commit's own hash, so a HARD hash check here
     * would reject genuinely-correct results and hang forever waiting for a hash that will never appear (exactly
     * the regression the freshness-based design was already changed to avoid — see exercise 54 in the history
     * above). This only adds visibility for the rarer, opposite failure mode — a stale straggler result from an
     * earlier, abandoned wait arriving inside a later round's freshness window — without risking that same
     * regression.
     */
    private void warnIfCommitMismatch(RepositoryType repositoryType, long exerciseId, @Nullable String expectedCommitHash, Result result) {
        if (expectedCommitHash == null || !(result.getSubmission() instanceof ProgrammingSubmission submission)) {
            return;
        }
        String actualCommitHash = submission.getCommitHash();
        if (actualCommitHash != null && !actualCommitHash.equals(expectedCommitHash)) {
            log.warn(
                    "Accepted {} build result for exercise {} whose submission commit ({}) differs from the commit this poll triggered ({}) — likely a stale result attached to "
                            + "an earlier submission (see warnIfCommitMismatch javadoc), not necessarily wrong, but worth checking if this gate's verdict looks surprising.",
                    repositoryType, exerciseId, actualCommitHash, expectedCommitHash);
        }
    }

    // Truncation limits for build/test feedback fed back to the agent: enough signal to debug failures, small enough
    // to keep the LLM context cheap (mirrors the codegen retry-prompt limits).
    private static final int MAX_FEEDBACK_SUMMARY_ITEMS = 20;

    private static final int MAX_FEEDBACK_SUMMARY_TEXT_LENGTH = 500;

    private static final int MAX_BUILD_LOG_LENGTH = 10_000;

    /**
     * Section header that introduces the raw build logs inside {@link #describeBuildResult(Result)} output.
     * Exposed so consumers that surface a finding to instructors (not to the agent) can cut the message at
     * this marker — the logs are a repair signal for the LLM, not warning-list content.
     */
    public static final String BUILD_LOGS_SECTION = "\n\nBuild logs:\n";

    /**
     * Renders a build result as the structured failure description fed back into the agent loop: build logs
     * (compiler output) plus a per-test PASSED/FAILED summary with assertion messages (closed-loop repair on real
     * signals).
     *
     * @param result the build result, or {@code null} when the build produced none
     * @return a human-readable description safe to inject into a prompt
     */
    public String describeBuildResult(@Nullable Result result) {
        if (result == null) {
            return "The build did not produce a result.";
        }
        String buildLogs = extractBuildLogs(result);
        String testFeedback = extractTestFeedbackSummary(result);
        String scoreLine = "Score: " + (result.getScore() != null ? result.getScore() + "%" : "unknown")
                + (result.getTestCaseCount() != null ? " (" + result.getPassedTestCaseCount() + "/" + result.getTestCaseCount() + " tests passed)" : "");
        if (testFeedback.isBlank()) {
            return scoreLine + BUILD_LOGS_SECTION + buildLogs;
        }
        return scoreLine + "\n\nTest results:\n" + testFeedback + BUILD_LOGS_SECTION + buildLogs;
    }

    /**
     * Names of tests that PASSED in a build result — the actual defect for a TEMPLATE_BUILD gate finding, since
     * every OTHER test failing there is the correct, expected state (the template intentionally has nothing
     * implemented yet). {@link #describeBuildResult} dumps every test PASSED/FAILED undifferentiated, which reads
     * to a repair round as a long list of things to fix — when for this one gate FAILED is the goal and only an
     * unexpected PASS is the real problem. Callers that know they are describing a template build use this to
     * point the agent at exactly what regressed instead of the whole list.
     *
     * @param result the build result, or {@code null} when the build produced none
     * @return the names of tests that unexpectedly passed, empty when none did (or feedback could not be loaded)
     */
    public List<String> unexpectedlyPassingTestNames(@Nullable Result result) {
        if (result == null) {
            return List.of();
        }
        try {
            return result.getFeedbacks().stream().filter(Objects::nonNull).filter(feedback -> feedback.getTestCase() != null)
                    .filter(feedback -> Boolean.TRUE.equals(feedback.isPositive())).map(feedback -> feedback.getTestCase().getTestName())
                    .filter(name -> name != null && !name.isBlank()).toList();
        }
        catch (LazyInitializationException e) {
            log.warn("Could not load feedback entries for result {}: {}. Cannot pinpoint unexpectedly passing tests.", result.getId(), e.getMessage());
            return List.of();
        }
    }

    private String extractBuildLogs(Result result) {
        if (!(result.getSubmission() instanceof ProgrammingSubmission programmingSubmission)) {
            return "(no build logs available)";
        }
        // The result is fetched without build logs, so the lazy association is detached here. Re-load the submission
        // with an eager build-log graph; otherwise a compile failure (the most common repair trigger) is invisible.
        try {
            Set<BuildLogEntry> buildLogEntries = programmingSubmissionRepository.findWithEagerBuildLogEntriesById(programmingSubmission.getId())
                    .map(ProgrammingSubmission::getBuildLogEntries).orElse(Set.of());
            if (!buildLogEntries.isEmpty()) {
                String logs = buildLogEntries.stream().map(BuildLogEntry::getLog).collect(Collectors.joining("\n"));
                return logs.length() > MAX_BUILD_LOG_LENGTH ? logs.substring(logs.length() - MAX_BUILD_LOG_LENGTH) : logs;
            }
        }
        catch (RuntimeException e) {
            log.warn("Could not load build log entries for submission {}: {}", programmingSubmission.getId(), e.getMessage());
        }
        return "(build logs could not be retrieved)";
    }

    private String extractTestFeedbackSummary(Result result) {
        try {
            return result.getFeedbacks().stream().filter(Objects::nonNull).filter(feedback -> feedback.getTestCase() != null)
                    .sorted((left, right) -> Boolean.compare(Boolean.TRUE.equals(left.isPositive()), Boolean.TRUE.equals(right.isPositive()))).limit(MAX_FEEDBACK_SUMMARY_ITEMS)
                    .map(VariantBuildVerificationService::formatFeedbackSummary).filter(summary -> !summary.isBlank()).collect(Collectors.joining("\n"));
        }
        catch (LazyInitializationException e) {
            log.warn("Could not load feedback entries for result {}: {}. Continuing with build logs only.", result.getId(), e.getMessage());
            return "";
        }
    }

    private static String formatFeedbackSummary(Feedback feedback) {
        String testName = Optional.ofNullable(feedback.getTestCase().getTestName()).filter(name -> !name.isBlank()).orElse("Unnamed test");
        String status = Boolean.TRUE.equals(feedback.isPositive()) ? "PASSED" : "FAILED";
        String text = Optional.ofNullable(feedback.getDetailText()).filter(detail -> !detail.isBlank()).orElse(feedback.getText());
        if (text == null || text.isBlank()) {
            return "- " + testName + ": " + status;
        }
        String summaryText = text.lines().findFirst().orElse("").trim();
        if (summaryText.length() > MAX_FEEDBACK_SUMMARY_TEXT_LENGTH) {
            summaryText = summaryText.substring(0, MAX_FEEDBACK_SUMMARY_TEXT_LENGTH) + "...";
        }
        return "- " + testName + ": " + status + " - " + summaryText;
    }
}
