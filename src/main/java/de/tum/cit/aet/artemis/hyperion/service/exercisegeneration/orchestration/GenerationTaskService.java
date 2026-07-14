package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationRevertService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationIncompleteException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationRecoveryService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Runs generation or adaptation asynchronously, streams progress, persists accepted output, and isolates recoverable rejected output for review. Every path closes the
 * {@link GenerationOutcome} so its sandbox is destroyed.
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

    private final HyperionGenerationBudgetService generationBudgetService;

    private final ExerciseGenerationRevertService generationRevertService;

    private final TaskScheduler taskScheduler;

    private final Duration maxJobDuration;

    private final long maxTokensPerJob;

    private final Duration ownerHeartbeatInterval;

    public GenerationTaskService(GenerationOrchestrationService orchestrator, GenerationPersistenceService persistenceService, GenerationRecoveryService recoveryService,
            HyperionWebsocketService websocket, GenerationJobService jobService, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionGenerationBudgetService generationBudgetService, ExerciseGenerationRevertService generationRevertService,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler, @Value("${artemis.hyperion.agent.max-job-duration:PT30M}") Duration maxJobDuration,
            @Value("${artemis.hyperion.agent.max-tokens-per-job:3000000}") long maxTokensPerJob,
            @Value("${artemis.hyperion.agent.owner-heartbeat-interval:PT15S}") Duration ownerHeartbeatInterval) {
        this.orchestrator = orchestrator;
        this.persistenceService = persistenceService;
        this.recoveryService = recoveryService;
        this.websocket = websocket;
        this.jobService = jobService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.generationBudgetService = generationBudgetService;
        this.generationRevertService = generationRevertService;
        this.taskScheduler = taskScheduler;
        this.maxJobDuration = maxJobDuration;
        this.maxTokensPerJob = maxTokensPerJob;
        this.ownerHeartbeatInterval = ownerHeartbeatInterval;
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
        AtomicBoolean deadlineExceeded = new AtomicBoolean(false);
        AtomicBoolean tokenBudgetExceeded = new AtomicBoolean(false);
        AtomicBoolean heartbeatLost = new AtomicBoolean(false);
        ScheduledFuture<?> deadlineFuture = null;
        ScheduledFuture<?> heartbeatFuture = null;
        try {
            // The event carries an exercise loaded on the request thread; on this async executor thread its lazy associations (buildConfig, template/solution participations) are
            // detached, so touching them (e.g. buildConfig.getBranch() during seeding) would throw LazyInitializationException. Re-load it with exactly those associations eagerly
            // initialized — and fail closed with a clear terminal error if it has since been deleted, rather than falling back to the detached entity and re-triggering that
            // exception.
            if (jobService.isCancelled(jobId)) {
                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, "Generation was cancelled. Nothing was changed."));
                return;
            }
            if (!jobService.isActiveJob(exerciseId, jobId)) {
                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, "Generation was superseded or expired. Nothing was changed."));
                return;
            }
            if (isDeadlineExceeded(event.deadlineAt())) {
                deadlineExceeded.set(true);
                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, cancellationMessage(true, false, false)));
                return;
            }
            ProgrammingExercise exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exerciseId).orElse(null);
            if (exercise == null) {
                log.error("Exercise generation job {} aborted: programming exercise {} no longer exists", jobId, exerciseId);
                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed: the exercise no longer exists."));
                return;
            }
            deadlineFuture = scheduleDeadline(exerciseId, jobId, deadlineExceeded, event.deadlineAt());
            heartbeatFuture = scheduleHeartbeat(exerciseId, jobId, heartbeatLost);
            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "Starting exercise generation"));
            Consumer<ChatResponse> usageSink = budgetedUsageSink(jobService.tokenUsageSink(courseIdOf(exercise), exerciseId, user.getId()), exerciseId, jobId, tokenBudgetExceeded);
            try (GenerationOutcome outcome = orchestrator.generate(exercise, user, userPrompt, jobId, event.mode(), () -> jobService.isCancelled(jobId) || heartbeatLost.get(),
                    emitter::progress, fileSnapshotSink, usageSink)) {
                switch (outcome.loopResult().status()) {
                    case CANCELLED -> emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED,
                            cancellationMessage(deadlineExceeded.get(), tokenBudgetExceeded.get(), heartbeatLost.get())));
                    case ERROR -> emitter.milestone(
                            ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, outcome.errorMessage() != null ? outcome.errorMessage() : "Generation failed."));
                    // A budget-exhausted run is still verified: it may have produced an acceptable exercise before the turn cap, or a recoverable near-miss.
                    case COMPLETED, BUDGET_EXHAUSTED -> {
                        // Verification already captured every artifact needed below. Release the scarce build-agent sandbox before Git persistence and CI synchronization.
                        outcome.close();
                        if (!jobService.enterNonCancellablePhase(exerciseId, jobId)) {
                            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED,
                                    cancellationMessage(deadlineExceeded.get(), tokenBudgetExceeded.get(), heartbeatLost.get())));
                            return;
                        }
                        ExerciseGenerationVerdictDTO verdict = toVerdict(outcome.verification());
                        if (outcome.isAccepted()) {
                            emitter.progress("Checks passed. Saving the exercise.");
                            try {
                                ProgrammingExercise exerciseToPersist = reloadDraftExerciseBeforeLiveMutation(exerciseId);
                                String originalProblemStatement = event.expectedProblemStatement();
                                String originalTitle = event.expectedTitle();
                                GenerationPersistenceService.PersistResult persistResult = persistenceService.persist(exerciseToPersist, user, outcome, originalProblemStatement,
                                        originalTitle, jobId, () -> !deadlineExceeded.get() && !heartbeatLost.get() && jobService.isOwnedActiveJob(exerciseId, jobId));
                                generationRevertService.recordBaseline(exerciseToPersist, jobId, event.mode(), persistResult.prePersistHeads(), persistResult.postPersistHeads(),
                                        originalProblemStatement, originalTitle, persistResult.persistedProblemStatement(), persistResult.persistedTitle(),
                                        persistResult.repositoryBranch());
                                int advisoryCount = recoveryService.surfaceAdvisoryFindings(exerciseToPersist, user, outcome.specFidelityReport());
                                String advisory = advisoryCount == 1 ? " 1 review note was added for your attention."
                                        : advisoryCount > 1 ? " " + advisoryCount + " review notes were added for your attention." : "";
                                String savedMessage = event.mode() == GenerationMode.ADAPT ? "The exercise was adapted and saved. Review the changes."
                                        : "The exercise was generated and saved. Review the changes.";
                                emitter.milestone(ExerciseGenerationEventDTO.done(savedMessage + advisory, ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, verdict, true));
                            }
                            catch (GenerationIncompleteException e) {
                                log.error("Persisting verified generated exercise {} left the save incomplete", exerciseId, e);
                                emitter.milestone(ExerciseGenerationEventDTO.done(
                                        "Verification passed, but saving did not complete. Some generated changes may already have been saved; manual review is required.",
                                        ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, verdict));
                            }
                            catch (RuntimeException e) {
                                log.error("Failed to persist generated exercise {}", exerciseId, e);
                                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Verification passed, but saving the exercise failed."));
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
                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed."));
            }
        }
        finally {
            cancelScheduled(deadlineFuture);
            cancelScheduled(heartbeatFuture);
            clearJobAndReleaseBudget(exerciseId, jobId, event);
        }
    }

    private void clearJobAndReleaseBudget(long exerciseId, String jobId, GenerationStartedEvent event) {
        try {
            jobService.clearJob(exerciseId, jobId);
        }
        finally {
            releaseBudgetReservation(event);
        }
    }

    private void releaseBudgetReservation(GenerationStartedEvent event) {
        generationBudgetService.releaseReservation(event.budgetReservationId());
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
        boolean scopeBlocked = outcome.specFidelityReport().hasBlockingFindings();
        boolean verifiedWithQualityFindings = outcome.verification() != null && outcome.verification().accepted() && outcome.specFidelityReport().hasFindings();
        String scopeReason = outcome.specFidelityReport().findings().stream().filter(
                finding -> finding.kind() == SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE || finding.kind() == SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE)
                .map(SpecFidelityReport.Finding::detail).collect(Collectors.joining(" "));
        String qualityReason = outcome.specFidelityReport().findings().stream().map(SpecFidelityReport.Finding::detail).collect(Collectors.joining(" "));
        String reason = scopeBlocked ? scopeReason
                : verifiedWithQualityFindings ? qualityReason
                        : outcome.verification() != null ? outcome.verification().report() : "The exercise could not be completed within the budget.";
        try {
            emitter.progress(scopeBlocked ? "Build and grading checks passed, but the adaptation-scope review requires manual review. Saving an isolated draft."
                    : verifiedWithQualityFindings ? "Build and grading checks passed, but the quality review requires manual review. Saving an isolated draft."
                            : "Verification did not pass. Saving the best-effort draft and recording what to review.");
            GenerationRecoveryService.RecoveryResult result = recoveryService.recover(exercise, user, outcome, jobId, () -> jobService.isOwnedActiveJob(exerciseId, jobId));
            int issueCount = result.reviewThreadCount();
            // Rejected drafts are isolated from the live exercise, regardless of whether this was a new generation or an adaptation. The instructor can inspect the draft branch
            // without accidentally publishing unverified code.
            String repositories = result.savedRepositories().stream().sorted().map(RepositoryType::getName).collect(Collectors.joining(", "));
            String placement = " The live exercise was left unchanged; generated repository files for " + repositories + " were saved to branch '" + result.draftBranch()
                    + "'. The generated problem statement was not saved. Review the notes and merge the repository branch manually in an external Git client if you want it.";
            // recover only throws when persist itself failed, so reaching here means the repository draft is saved: always NEEDS_REVIEW. issueCount < 0 means a degraded save
            // (review comments
            // could not be attached), which the message states explicitly.
            String outcomeSummary = scopeBlocked
                    ? "Build and grading checks passed, but the adaptation changed content outside the requested scope or its scope could not be verified."
                    : verifiedWithQualityFindings ? "Build and grading checks passed, but the quality review found unresolved exercise-quality gaps."
                            : "The generated draft did not pass verification.";
            String message = issueCount < 0
                    ? outcomeSummary + " The review notes could not be attached automatically — open the exercise and review it manually before grading." + placement + " " + reason
                    : outcomeSummary + " " + issueCount + " issue(s) to review were added to the exercise." + placement + " " + reason;
            emitter.milestone(ExerciseGenerationEventDTO.done(message, ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW, verdict, false));
        }
        catch (RuntimeException e) {
            // Recovery failed at the isolated-branch persist step. Earlier repository draft branches may already exist, so report PARTIAL and avoid claiming a clean draft.
            log.error("Recovery of non-accepted generation outcome failed for exercise {} (draft persist did not complete)", exerciseId, e);
            emitter.milestone(ExerciseGenerationEventDTO.done(
                    reason + " Saving the draft for review did not complete; any partial hyperion-draft branch must be reviewed or deleted manually.",
                    ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, verdict));
        }
    }

    private ScheduledFuture<?> scheduleDeadline(long exerciseId, String jobId, AtomicBoolean deadlineExceeded, Instant deadlineAt) {
        Instant effectiveDeadlineAt = effectiveDeadlineAt(deadlineAt);
        if (effectiveDeadlineAt == null) {
            return null;
        }
        return taskScheduler.schedule(() -> {
            deadlineExceeded.set(true);
            jobService.requestSystemCancellation(exerciseId, jobId);
        }, effectiveDeadlineAt);
    }

    private ScheduledFuture<?> scheduleHeartbeat(long exerciseId, String jobId, AtomicBoolean heartbeatLost) {
        if (ownerHeartbeatInterval == null || ownerHeartbeatInterval.isZero() || ownerHeartbeatInterval.isNegative()) {
            return null;
        }
        return taskScheduler.scheduleWithFixedDelay(() -> {
            if (!jobService.heartbeat(exerciseId, jobId)) {
                heartbeatLost.set(true);
                jobService.requestSystemCancellation(exerciseId, jobId);
            }
        }, ownerHeartbeatInterval);
    }

    private Consumer<ChatResponse> budgetedUsageSink(Consumer<ChatResponse> delegate, long exerciseId, String jobId, AtomicBoolean tokenBudgetExceeded) {
        if (maxTokensPerJob <= 0) {
            return delegate;
        }
        AtomicLong tokensUsed = new AtomicLong();
        return response -> {
            delegate.accept(response);
            long total = tokensUsed.addAndGet(LLMTokenUsageService.totalTokens(response));
            if (total >= maxTokensPerJob && tokenBudgetExceeded.compareAndSet(false, true)) {
                jobService.requestSystemCancellation(exerciseId, jobId);
            }
        };
    }

    private static void cancelScheduled(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private boolean isDeadlineExceeded(Instant deadlineAt) {
        return deadlineAt != null && !Instant.now().isBefore(deadlineAt);
    }

    private Instant effectiveDeadlineAt(Instant admissionDeadlineAt) {
        if (admissionDeadlineAt != null) {
            return admissionDeadlineAt;
        }
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            return null;
        }
        return Instant.now().plus(maxJobDuration);
    }

    private static String cancellationMessage(boolean deadlineExceeded, boolean tokenBudgetExceeded, boolean heartbeatLost) {
        if (deadlineExceeded) {
            return "Generation stopped because it exceeded the configured time limit. Nothing was changed.";
        }
        if (tokenBudgetExceeded) {
            return "Generation stopped because it exceeded the configured token budget. Nothing was changed.";
        }
        if (heartbeatLost) {
            return "Generation stopped because this node lost ownership of the job. Nothing was changed.";
        }
        return "Generation was cancelled. Nothing was changed.";
    }

    private static Long courseIdOf(ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course == null ? null : course.getId();
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
