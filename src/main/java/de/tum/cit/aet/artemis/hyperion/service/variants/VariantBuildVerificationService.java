package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

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

    private final GitService gitService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    public VariantBuildVerificationService(GitService gitService, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository) {
        this.gitService = gitService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Blocks until the remote HEAD equals the expected commit hash or the timeout elapses.
     *
     * @param repositoryUri repository to poll
     * @param expectedHash  commit hash that must be visible remotely
     * @param timeoutMs     maximum time to wait
     * @throws InterruptedException when the polling thread is interrupted
     */
    public void waitUntilRemoteHasCommit(LocalVCRepositoryUri repositoryUri, String expectedHash, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                String head = gitService.getLastCommitHash(repositoryUri);
                if (expectedHash != null && expectedHash.equals(head)) {
                    return;
                }
            }
            catch (Exception ignored) {
            }
            Thread.sleep(300);
        }
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
        ProgrammingExerciseParticipation participation = switch (repositoryType) {
            case TEMPLATE -> templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElse(null);
            // tests also use solution participation
            case SOLUTION, TESTS -> solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElse(null);
            default -> null;
        };
        if (participation == null) {
            log.warn("Could not find participation for repoType {} in exercise {}", repositoryType, exercise.getId());
            return new BuildResultOutcome(null, BuildResultState.PARTICIPATION_NOT_FOUND);
        }

        int pollCount = 0;
        while (System.currentTimeMillis() - startTime < TIMEOUT) {
            try {
                Optional<Result> result = resultRepository.findFirstWithSubmissionAndFeedbacksAndTestCasesByParticipationIdOrderByCompletionDateDesc(participation.getId());
                if (result.isPresent() && isFreshEnough(result.get(), notBefore)) {
                    log.debug("Found build result for commit {} after {} polls ({}ms)", commitHash, pollCount, System.currentTimeMillis() - startTime);
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
        return new BuildResultOutcome(null, BuildResultState.TIMED_OUT);
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

    private String extractBuildLogs(Result result) {
        if (!(result.getSubmission() instanceof ProgrammingSubmission programmingSubmission)) {
            return "(no build logs available)";
        }
        // The result is fetched without build logs, so the lazy association is detached here. Re-load the submission
        // with an eager build-log graph; otherwise a compile failure (the most common repair trigger) is invisible.
        try {
            List<BuildLogEntry> buildLogEntries = programmingSubmissionRepository.findWithEagerBuildLogEntriesById(programmingSubmission.getId())
                    .map(ProgrammingSubmission::getBuildLogEntries).orElse(List.of());
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
