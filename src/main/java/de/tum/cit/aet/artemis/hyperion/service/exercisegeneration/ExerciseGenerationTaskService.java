package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Runs an agentic whole-exercise generation/adaptation session asynchronously and streams progress to the instructor over the existing Hyperion websocket topic.
 * <p>
 * It owns the end-to-end flow: drive the {@link ExerciseGenerationOrchestrationService}; when the verifier accepts, hand off to {@link GenerationPersistenceService} to persist a
 * clean, verified exercise; and when it does NOT accept but the run produced usable work, hand off to {@link GenerationRecoveryService} to persist the best-effort draft and
 * surface
 * every verification finding as review comments so a near-miss is recoverable instead of discarded.
 * <p>
 * Every terminal state emits a clear, distinct event: {@code SUCCESS} (verified and saved), {@code NEEDS_REVIEW} (draft saved with review comments to resolve), {@code PARTIAL}
 * (nothing usable was produced, or recovery itself failed — the exercise is left untouched and the run can be retried), plus cancellation and error. A recovered draft is never
 * presented as a verified exercise: only the {@code SUCCESS} path is clean; {@code NEEDS_REVIEW} always carries the gaps the instructor must fix.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseGenerationTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationTaskService.class);

    private static final String TOPIC_PREFIX = "exercise-generation/jobs/";

    private final ExerciseGenerationOrchestrationService orchestrator;

    private final GenerationPersistenceService persistenceService;

    private final GenerationRecoveryService recoveryService;

    private final HyperionWebsocketService websocket;

    private final ExerciseGenerationJobService jobService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ExerciseGenerationTaskService(ExerciseGenerationOrchestrationService orchestrator, GenerationPersistenceService persistenceService,
            GenerationRecoveryService recoveryService, HyperionWebsocketService websocket, ExerciseGenerationJobService jobService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.orchestrator = orchestrator;
        this.persistenceService = persistenceService;
        this.recoveryService = recoveryService;
        this.websocket = websocket;
        this.jobService = jobService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Runs one generation/adaptation session, triggered by the {@link ExerciseGenerationStartedEvent} the job service publishes. Runs on the dedicated generation executor (via
     * {@link Async}) so it returns the request thread immediately.
     *
     * @param event the start event carrying the job id, requesting user, target exercise, and prompt
     */
    @Async("hyperionGenerationExecutor")
    @EventListener
    public void runAsync(ExerciseGenerationStartedEvent event) {
        String jobId = event.jobId();
        User user = event.user();
        String userPrompt = event.userPrompt();
        long exerciseId = event.exercise().getId();
        String login = user.getLogin();
        String topic = TOPIC_PREFIX + jobId;
        GenerationProgressEmitter emitter = new GenerationProgressEmitter((progressEvent, terminal) -> jobService.recordEvent(exerciseId, jobId, progressEvent, terminal),
                progressEvent -> websocket.send(login, topic, progressEvent));
        // The event carries an exercise loaded on the request thread; on this async executor thread its lazy associations (buildConfig, template/solution participations) are
        // detached, so touching them (e.g. buildConfig.getBranch() during seeding) would throw LazyInitializationException. Re-load it with exactly those associations eagerly
        // initialized — and fail CLOSED with a clear terminal error if it has since been deleted, rather than falling back to the detached entity and re-triggering that exception.
        ProgrammingExercise exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndBuildConfigById(exerciseId).orElse(null);
        if (exercise == null) {
            log.error("Exercise generation job {} aborted: programming exercise {} no longer exists", jobId, exerciseId);
            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed: the exercise no longer exists."));
            jobService.clearJob(exerciseId, jobId);
            return;
        }
        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "Starting exercise generation"));
        try (GenerationOutcome outcome = orchestrator.generate(exercise, user, userPrompt, jobId, () -> jobService.isCancelled(jobId), emitter::progress)) {
            switch (outcome.loopResult().status()) {
                case CANCELLED -> emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, "Generation was cancelled. Nothing was changed."));
                case ERROR -> emitter.milestone(
                        ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, outcome.errorMessage() != null ? outcome.errorMessage() : "Generation failed."));
                default -> {
                    ExerciseGenerationVerdictDTO verdict = toVerdict(outcome.verification());
                    if (outcome.isAccepted()) {
                        emitter.progress("Checks passed. Saving the exercise.");
                        try {
                            persistenceService.persist(exercise, user, outcome);
                            // Advisory only: surface any spec-fidelity / coverage gaps as review comments WITHOUT changing the accepted status. The differential oracle accepted
                            // the
                            // exercise; these are non-blocking notes the instructor may act on. Best-effort — a failed attach never downgrades the SUCCESS.
                            int advisoryCount = recoveryService.surfaceAdvisoryFindings(exercise, outcome.specFidelityReport());
                            String advisory = advisoryCount > 0
                                    ? " " + advisoryCount + " advisory spec-fidelity note(s) were added for your review (these did not affect acceptance)."
                                    : "";
                            emitter.milestone(ExerciseGenerationEventDTO.done("The exercise was generated and saved. Review the changes." + advisory,
                                    ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, verdict));
                        }
                        catch (RuntimeException e) {
                            log.error("Failed to persist generated exercise {}", exerciseId, e);
                            emitter.milestone(
                                    ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Verification passed but saving the exercise failed: " + e.getMessage()));
                        }
                    }
                    else {
                        recoverOrReportPartial(exercise, user, exerciseId, jobId, outcome, verdict, emitter);
                    }
                }
            }
        }
        catch (RuntimeException e) {
            log.error("Exercise generation job {} failed", jobId, e);
            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed: " + e.getMessage()));
        }
        finally {
            jobService.clearJob(exerciseId, jobId);
        }
    }

    /**
     * Handles a non-accepted (but clean, non-cancelled, non-error) terminal state via {@link GenerationRecoveryService#recover}, emitting {@code NEEDS_REVIEW} when the draft was
     * persisted (with {@code issueCount < 0} signalling its review comments could not be attached) and falling back to {@code PARTIAL} only when the persist itself failed.
     *
     * @param exercise   the target exercise
     * @param user       the requesting instructor (commit and review-comment author)
     * @param exerciseId the exercise id (for logging)
     * @param jobId      the generation job id, used to name the isolated draft branch for an adapt target
     * @param outcome    the non-accepted outcome holding the produced files, verification report, and agent note
     * @param verdict    the structured verdict mirrored to the client
     * @param emitter    the progress emitter for the live transcript
     */
    private void recoverOrReportPartial(ProgrammingExercise exercise, User user, long exerciseId, String jobId, GenerationOutcome outcome, ExerciseGenerationVerdictDTO verdict,
            GenerationProgressEmitter emitter) {
        String reason = outcome.verification() != null ? outcome.verification().report() : "The exercise could not be completed within the budget.";
        try {
            emitter.progress("Verification did not pass. Saving the best-effort draft and recording what to review.");
            GenerationRecoveryService.RecoveryResult result = recoveryService.recover(exercise, user, outcome, jobId);
            int issueCount = result.reviewThreadCount();
            // Where the draft landed: for an adapt of a working exercise it is diverted to an isolated branch and the LIVE exercise is left untouched (it keeps working); for a
            // from-scratch target it is committed to the exercise in place. The message makes this explicit so the instructor knows whether their working exercise was preserved.
            String placement = result.liveExerciseUntouched()
                    ? " Your existing working exercise was left unchanged; the draft was saved to the branch '" + result.draftBranch()
                            + "' for you to review and merge if you want it."
                    : "";
            // recover only throws when persist itself failed, so reaching here means the draft is saved: always NEEDS_REVIEW. issueCount < 0 means a degraded save (review comments
            // could not be attached), which the message states explicitly.
            String message = issueCount < 0
                    ? "A draft exercise was generated but did not pass verification, so it needs your review before use. The review notes could not be attached automatically — "
                            + "open the exercise and review it manually before grading." + placement + " " + reason
                    : "A draft exercise was generated but did not pass verification, so it needs your review before use. " + issueCount + " issue(s) to review were added to the "
                            + "exercise." + placement + " " + reason;
            emitter.milestone(ExerciseGenerationEventDTO.done(message, ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW, verdict));
        }
        catch (RuntimeException e) {
            // Recovery failed at the PERSIST step: nothing durable was saved, so report PARTIAL (the instructor can retry).
            log.error("Recovery of non-accepted generation outcome failed for exercise {} (draft could not be persisted)", exerciseId, e);
            emitter.milestone(ExerciseGenerationEventDTO.done(reason + " Saving the draft for review failed (" + e.getMessage() + ").",
                    ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, verdict));
        }
    }

    private static ExerciseGenerationVerdictDTO toVerdict(VerificationResult verification) {
        if (verification == null) {
            return null;
        }
        return new ExerciseGenerationVerdictDTO(verification.accepted(), verification.solutionPassed(), verification.templateFailed(), verification.testCount(),
                verification.reasons());
    }
}
