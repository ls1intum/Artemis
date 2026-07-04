package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

/**
 * Build-verification helper for exercise-variant generation: polls the build result for a commit and
 * evaluates whether the repository-type-specific target was reached (solution must pass 100%, template must
 * fail with tests present, tests build must be successful) — exactly the deterministic gate the variant
 * verifier needs (variants plan Section 2.6, step 1).
 *
 * NOTE: this deliberately MIRRORS the private polling logic in {@code HyperionCodeGenerationExecutionService}
 * instead of extracting it (deviation from the plan's Section 3 refactor note): the codegen service is
 * actively developed by other students and must not be modified. Keep the target-result semantics here in
 * sync with {@code hasReachedTargetResult} there if they ever change.
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
     * Polls for the build result of the given commit and evaluates it against the repository-type target.
     *
     * @param exercise       the exercise whose participation is polled
     * @param commitHash     the commit the build was triggered for
     * @param repositoryType which repository the build belongs to (TESTS builds use the solution participation)
     * @return the outcome; {@link BuildResultState#SUCCESS} iff the target result was reached
     * @throws InterruptedException when the polling thread is interrupted
     */
    public BuildResultOutcome waitForBuildResult(ProgrammingExercise exercise, String commitHash, RepositoryType repositoryType) throws InterruptedException {
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
                ProgrammingSubmission submission = programmingSubmissionRepository
                        .findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participation.getId(), commitHash);

                if (submission != null) {
                    Optional<Result> result = resultRepository.findLatestResultWithFeedbacksAndTestcasesForSubmission(submission.getId());

                    if (result.isPresent()) {
                        log.debug("Found build result for commit {} after {} polls ({}ms)", commitHash, pollCount, System.currentTimeMillis() - startTime);
                        return new BuildResultOutcome(result.get(), hasReachedTargetResult(repositoryType, result.get()) ? BuildResultState.SUCCESS : BuildResultState.FAILED);
                    }
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
}
