package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseAdaptationRevertService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationIncompleteException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationRecoveryService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Runs an agentic whole-exercise generation/adaptation session asynchronously and streams progress to the instructor over the existing Hyperion websocket topic.
 * <p>
 * It owns the end-to-end flow: drive the {@link GenerationOrchestrationService}; when the verifier accepts, hand off to {@link GenerationPersistenceService} to persist a
 * clean, verified exercise; and when it does not accept but the run produced usable work, hand off to {@link GenerationRecoveryService} to persist the best-effort draft and
 * surface
 * every verification finding as review comments so a near-miss is recoverable instead of discarded.
 * <p>
 * Every terminal state emits a clear, distinct event: {@code SUCCESS} (verified and saved), {@code NEEDS_REVIEW} (draft saved with review comments to resolve), {@code PARTIAL}
 * (nothing usable was produced, or recovery itself failed — the exercise is left untouched and the run can be retried), plus cancellation and error. A recovered draft is never
 * presented as a verified exercise: only the {@code SUCCESS} path is clean; {@code NEEDS_REVIEW} always carries the gaps the instructor must fix. The {@link GenerationOutcome} is
 * always closed here so the sandbox container is destroyed on every path.
 */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationTaskService {

    private static final Logger log = LoggerFactory.getLogger(GenerationTaskService.class);

    private static final String TOPIC_PREFIX = "exercise-generation/jobs/";

    private final GenerationOrchestrationService orchestrator;

    private final GenerationPersistenceService persistenceService;

    private final GenerationRecoveryService recoveryService;

    private final HyperionWebsocketService websocket;

    private final GenerationJobService jobService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ExerciseAdaptationRevertService adaptationRevertService;

    public GenerationTaskService(GenerationOrchestrationService orchestrator, GenerationPersistenceService persistenceService, GenerationRecoveryService recoveryService,
            HyperionWebsocketService websocket, GenerationJobService jobService, ProgrammingExerciseRepository programmingExerciseRepository,
            ExerciseAdaptationRevertService adaptationRevertService) {
        this.orchestrator = orchestrator;
        this.persistenceService = persistenceService;
        this.recoveryService = recoveryService;
        this.websocket = websocket;
        this.jobService = jobService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.adaptationRevertService = adaptationRevertService;
    }

    /**
     * Runs one generation/adaptation session, triggered by the {@link GenerationStartedEvent} the job service publishes. Runs on the dedicated generation executor (via
     * {@link Async}) so it returns the request thread immediately.
     *
     * @param event the start event carrying the job id, requesting user, target exercise, and prompt
     */
    @Async("hyperionGenerationExecutor")
    @EventListener
    public void runAsync(GenerationStartedEvent event) {
        String jobId = event.jobId();
        User user = event.user();
        String userPrompt = event.userPrompt();
        long exerciseId = event.exercise().getId();
        String login = user.getLogin();
        String topic = TOPIC_PREFIX + jobId;
        GenerationProgressEmitter emitter = new GenerationProgressEmitter((progressEvent, terminal) -> jobService.recordEvent(exerciseId, jobId, progressEvent, terminal),
                progressEvent -> websocket.send(login, topic, progressEvent));
        // Whole-file snapshots are streamed to the owner on the same per-user topic as the progress events (told apart by their FILE_SNAPSHOT type) and retained latest-per-file
        // for
        // reconnect — kept out of the replay transcript so the write stream cannot bloat it.
        Consumer<ExerciseGenerationFileSnapshotDTO> fileSnapshotSink = snapshot -> {
            jobService.recordSnapshot(exerciseId, jobId, snapshot);
            websocket.send(login, topic, snapshot);
        };
        // The event carries an exercise loaded on the request thread; on this async executor thread its lazy associations (buildConfig, template/solution participations) are
        // detached, so touching them (e.g. buildConfig.getBranch() during seeding) would throw LazyInitializationException. Re-load it with exactly those associations eagerly
        // initialized — and fail closed with a clear terminal error if it has since been deleted, rather than falling back to the detached entity and re-triggering that exception.
        ProgrammingExercise exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exerciseId).orElse(null);
        if (exercise == null) {
            log.error("Exercise generation job {} aborted: programming exercise {} no longer exists", jobId, exerciseId);
            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed: the exercise no longer exists."));
            jobService.clearJob(exerciseId, jobId);
            return;
        }
        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "Starting exercise generation"));
        try (GenerationOutcome outcome = orchestrator.generate(exercise, user, userPrompt, jobId, event.mode(), () -> jobService.isCancelled(jobId), emitter::progress,
                fileSnapshotSink)) {
            switch (outcome.loopResult().status()) {
                case CANCELLED -> emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, "Generation was cancelled. Nothing was changed."));
                case ERROR -> emitter.milestone(
                        ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, outcome.errorMessage() != null ? outcome.errorMessage() : "Generation failed."));
                // A budget-exhausted run is still verified: it may have produced an acceptable exercise before the turn cap, or a recoverable near-miss.
                case COMPLETED, BUDGET_EXHAUSTED -> {
                    ExerciseGenerationVerdictDTO verdict = toVerdict(outcome.verification());
                    if (outcome.isAccepted()) {
                        emitter.progress("Checks passed. Saving the exercise.");
                        try {
                            // persist captures each repository's pre-persist HEAD (the pre-adaptation state, since the sandbox run never touched the live repos) and returns it
                            // only
                            // after every repository committed successfully. Record a revertible baseline only for an accepted ADAPT applied in place — never for a
                            // cancelled/rejected/errored run — so a later run cannot overwrite this accepted adaptation's baseline and make it non-revertible. GENERATE has nothing
                            // to revert to.
                            ProgrammingExercise exerciseToPersist = reloadDraftExerciseBeforeLiveMutation(exerciseId);
                            String originalProblemStatement = exerciseToPersist.getProblemStatement();
                            String originalTitle = exerciseToPersist.getTitle();
                            GenerationPersistenceService.PersistResult persistResult = persistenceService.persist(exerciseToPersist, user, outcome);
                            if (event.mode() == GenerationMode.ADAPT) {
                                adaptationRevertService.recordBaseline(exerciseToPersist, jobId, persistResult.prePersistHeads(), persistResult.postPersistHeads(),
                                        originalProblemStatement, originalTitle);
                            }
                            // Advisory only: surface any spec-fidelity / coverage gaps as review comments without changing the accepted status. The differential oracle accepted
                            // the
                            // exercise; these are non-blocking notes the instructor may act on. Best-effort — a failed attach never downgrades the SUCCESS.
                            int advisoryCount = recoveryService.surfaceAdvisoryFindings(exerciseToPersist, outcome.specFidelityReport());
                            String advisory = advisoryCount > 0
                                    ? " " + advisoryCount + " advisory spec-fidelity note(s) were added for your review (these did not affect acceptance)."
                                    : "";
                            emitter.milestone(ExerciseGenerationEventDTO.done("The exercise was generated and saved. Review the changes." + advisory,
                                    ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, verdict, true));
                        }
                        catch (GenerationIncompleteException e) {
                            log.error("Persisting verified generated exercise {} left the save incomplete", exerciseId, e);
                            emitter.milestone(ExerciseGenerationEventDTO.done("Verification passed, but saving the exercise did not complete safely: " + e.getMessage(),
                                    ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, verdict));
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
     * @param jobId      the generation job id, used to name the isolated draft branch
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
            // Rejected drafts are isolated from the live exercise, regardless of whether this was a new generation or an adaptation. The instructor can inspect the draft branch
            // without accidentally publishing unverified code.
            String placement = result.draftBranch() != null
                    ? " The live exercise was left unchanged; the draft was saved to the branch '" + result.draftBranch() + "' for you to review and merge if you want it."
                    : "";
            // recover only throws when persist itself failed, so reaching here means the draft is saved: always NEEDS_REVIEW. issueCount < 0 means a degraded save (review comments
            // could not be attached), which the message states explicitly.
            String message = issueCount < 0
                    ? "A draft exercise was generated but did not pass verification, so it needs your review before use. The review notes could not be attached automatically — "
                            + "open the exercise and review it manually before grading." + placement + " " + reason
                    : "A draft exercise was generated but did not pass verification, so it needs your review before use. " + issueCount + " issue(s) to review were added to the "
                            + "exercise." + placement + " " + reason;
            emitter.milestone(ExerciseGenerationEventDTO.done(message, ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW, verdict, !result.liveExerciseUntouched()));
        }
        catch (RuntimeException e) {
            // Recovery failed at the isolated-branch persist step. Earlier repository draft branches may already exist, so report PARTIAL and avoid claiming a clean draft.
            log.error("Recovery of non-accepted generation outcome failed for exercise {} (draft persist did not complete)", exerciseId, e);
            emitter.milestone(ExerciseGenerationEventDTO.done(
                    reason + " Saving the draft for review did not complete (" + e.getMessage() + "); any partial hyperion-draft branch must be reviewed or deleted manually.",
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

    private ProgrammingExercise reloadDraftExerciseBeforeLiveMutation(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exerciseId)
                .orElseThrow(() -> new IllegalStateException("Generation cannot be saved because the exercise no longer exists."));
        if (exercise.isReleased()) {
            throw new IllegalStateException("Hyperion generation can only modify unreleased draft exercises.");
        }
        Set<StudentParticipation> studentParticipations = exercise.getStudentParticipations();
        if (studentParticipations != null && !studentParticipations.isEmpty()) {
            throw new IllegalStateException("Hyperion generation can only modify exercises without student participations.");
        }
        return exercise;
    }
}
