package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
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
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationRevertService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationIncompleteException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationReviewService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.LanguageGenerationProfile;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Runs generation or adaptation asynchronously, streams progress, and persists mechanically verified output for instructor review. Every path closes the
 * {@link GenerationOutcome} so its sandbox is destroyed. Unverified output is never persisted.
 */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationTaskService {

    private static final Logger log = LoggerFactory.getLogger(GenerationTaskService.class);

    private static final String TOPIC_PREFIX = "exercise-generation/jobs/";

    private final GenerationOrchestrationService orchestrator;

    private final GenerationPersistenceService persistenceService;

    private final GenerationReviewService reviewService;

    private final HyperionWebsocketService websocket;

    private final GenerationJobService jobService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final HyperionGenerationBudgetService generationBudgetService;

    private final ExerciseGenerationRevertService generationRevertService;

    private final TaskScheduler taskScheduler;

    private final Duration maxJobDuration;

    private final long maxTokensPerJob;

    private final Duration ownerHeartbeatInterval;

    public GenerationTaskService(GenerationOrchestrationService orchestrator, GenerationPersistenceService persistenceService, GenerationReviewService reviewService,
            HyperionWebsocketService websocket, GenerationJobService jobService, ProgrammingExerciseRepository programmingExerciseRepository,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, HyperionGenerationBudgetService generationBudgetService,
            ExerciseGenerationRevertService generationRevertService, @Qualifier("taskScheduler") TaskScheduler taskScheduler,
            @Value("${artemis.hyperion.agent.max-job-duration:PT30M}") Duration maxJobDuration, @Value("${artemis.hyperion.agent.max-tokens-per-job:3000000}") long maxTokensPerJob,
            @Value("${artemis.hyperion.agent.owner-heartbeat-interval:PT15S}") Duration ownerHeartbeatInterval) {
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-job-duration must be positive");
        }
        if (maxTokensPerJob <= 0) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-tokens-per-job must be positive");
        }
        if (ownerHeartbeatInterval == null || ownerHeartbeatInterval.isZero() || ownerHeartbeatInterval.isNegative() || ownerHeartbeatInterval.compareTo(maxJobDuration) >= 0) {
            throw new IllegalArgumentException("artemis.hyperion.agent.owner-heartbeat-interval must be positive and shorter than max-job-duration");
        }
        this.orchestrator = orchestrator;
        this.persistenceService = persistenceService;
        this.reviewService = reviewService;
        this.websocket = websocket;
        this.jobService = jobService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
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
        // File changes share the progress topic and are retained latest-per-path for reconnect.
        Consumer<ExerciseGenerationFileChangeDTO> fileChangeSink = change -> {
            if (jobService.recordFileChange(exerciseId, jobId, change)) {
                websocket.send(login, topic, change);
            }
        };
        AtomicBoolean deadlineExceeded = new AtomicBoolean(false);
        AtomicBoolean tokenBudgetExceeded = new AtomicBoolean(false);
        AtomicBoolean tokenAccountingFailed = new AtomicBoolean(false);
        AtomicBoolean heartbeatLost = new AtomicBoolean(false);
        ScheduledFuture<?> deadlineFuture = null;
        ScheduledFuture<?> heartbeatFuture = null;
        try {
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
            // The event's exercise was loaded on the request thread, so on this executor thread its lazy associations (buildConfig, template/solution participations) are
            // detached and touching one during seeding would throw. Re-load it with exactly those associations initialized, and fail closed if it has since been deleted rather
            // than falling back to the detached entity.
            ProgrammingExercise exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exerciseId).orElse(null);
            if (exercise == null) {
                log.error("Exercise generation job {} aborted: programming exercise {} no longer exists", jobId, exerciseId);
                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation failed: the exercise no longer exists."));
                return;
            }
            if (!LanguageGenerationProfile.isSupported(exercise, !auxiliaryRepositoryRepository.findByExerciseId(exerciseId).isEmpty())) {
                emitter.milestone(
                        ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "Generation stopped because the exercise configuration is no longer supported."));
                return;
            }
            deadlineFuture = scheduleDeadline(deadlineExceeded, event.deadlineAt());
            heartbeatFuture = scheduleHeartbeat(exerciseId, jobId, heartbeatLost);
            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "Starting exercise generation"));
            Consumer<ChatResponse> usageSink = budgetedUsageSink(jobService.tokenUsageSink(courseIdOf(exercise), exerciseId, user.getId()), exerciseId, jobId, tokenBudgetExceeded,
                    tokenAccountingFailed);
            BooleanSupplier cancelled = () -> jobService.isCancelled(jobId) || deadlineExceeded.get() || tokenBudgetExceeded.get() || tokenAccountingFailed.get()
                    || heartbeatLost.get();
            GenerationOutcome generated = event.sourceBrief() == null
                    ? orchestrator.generate(exercise, user, userPrompt, jobId, event.mode(), cancelled, emitter::progress, fileChangeSink, usageSink)
                    : orchestrator.generate(exercise, user, userPrompt, jobId, event.mode(), cancelled, emitter::progress, fileChangeSink, usageSink, event.sourceBrief());
            try (GenerationOutcome outcome = generated) {
                // Before any terminal branch below, so specification quality stays inspectable through the status/replay API even for a run that is never saved.
                if (outcome.specDocument() != null) {
                    jobService.recordSpecDocument(exerciseId, jobId, outcome.specDocument());
                }
                if (tokenAccountingFailed.get()) {
                    emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED,
                            "Generation stopped because token usage could not be accounted for. Nothing was changed."));
                    return;
                }
                if (tokenBudgetExceeded.get()) {
                    // The budget controls provider spend, so it stops further model calls but must not discard work already paid for: saving consumes no provider tokens. Only
                    // a run without a mechanically verified candidate ends here.
                    if (!outcome.isMechanicallyVerified()) {
                        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, cancellationMessage(false, true, false)));
                        return;
                    }
                    emitter.progress("The token budget was reached; keeping and saving the already-verified exercise instead of discarding it.");
                }
                if (deadlineExceeded.get()) {
                    // Same rule as the token budget above: the deadline stops further model work, it does not invalidate a candidate that already passed verification, and
                    // persisting one neither re-runs nor re-bills it. Only a deadline hit with no verified checkpoint discards.
                    if (!outcome.isMechanicallyVerified()) {
                        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, cancellationMessage(true, false, false)));
                        return;
                    }
                    emitter.progress("The time budget was reached; keeping and saving the already-verified exercise for review instead of discarding it.");
                }
                switch (outcome.loopResult().status()) {
                    case CANCELLED -> emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED,
                            cancellationMessage(deadlineExceeded.get(), tokenBudgetExceeded.get(), heartbeatLost.get())));
                    case ERROR -> {
                        String message = outcome.hasCapturedArtifacts() ? "Generation stopped before mechanical verification completed. Nothing was saved."
                                : outcome.errorMessage() != null ? outcome.errorMessage() : "Generation failed.";
                        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, message));
                    }
                    // A budget-exhausted run may still have produced a mechanically verified exercise before the turn cap.
                    case COMPLETED, BUDGET_EXHAUSTED -> {
                        // Verification already captured every artifact needed below. Release the scarce build-agent sandbox before Git persistence and CI synchronization.
                        outcome.close();
                        ExerciseGenerationVerdictDTO verdict = toVerdict(outcome.verification());
                        if (!outcome.isMechanicallyVerified()) {
                            String report = outcome.verification() == null ? "No mechanical verification result was produced." : outcome.verification().report();
                            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR,
                                    "Generation did not pass mechanical verification. Nothing was saved. " + conciseReason(report)));
                            break;
                        }
                        if (!jobService.enterNonCancellablePhase(exerciseId, jobId)) {
                            // enterNonCancellablePhase returns false for exactly two reasons, resolved atomically under the same distributed job-map lock as
                            // requestCancellation/requestSystemCancellation: either a cancellation already won the race (the transcript is already terminal as CANCELLED, so
                            // this run must be reported the same way, never as a save failure), or ownership of the job was genuinely lost.
                            if (jobService.isCancelled(jobId)) {
                                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, "Generation was cancelled. Nothing was changed."));
                            }
                            else {
                                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR,
                                        "The generated exercise passed verification but could not be saved because job ownership was lost."));
                            }
                            return;
                        }
                        // A verified candidate is a save obligation from here on: neither user cancellation nor the generation deadline may discard it. Git, CI, and repository
                        // operations carry their own bounded timeouts, and ownership and draft-state checks still fence every mutation.
                        cancelScheduled(deadlineFuture);
                        deadlineFuture = null;
                        emitter.progress("Checks passed. Saving the exercise.");
                        ProgrammingExercise exerciseToPersist;
                        GenerationPersistenceService.PersistResult persistResult;
                        try {
                            exerciseToPersist = reloadDraftExerciseBeforeLiveMutation(exerciseId);
                            persistResult = persistenceService.persist(exerciseToPersist, user, outcome, event.expectedProblemStatement(), event.expectedTitle(), jobId,
                                    event.mode(), () -> canContinueSave(exerciseId, jobId, heartbeatLost) && isStillDraftWithoutParticipations(exerciseId),
                                    () -> generationRevertService.invalidateBaseline(exerciseId));
                        }
                        catch (GenerationIncompleteException e) {
                            log.error("Persisting verified generated exercise {} left the save incomplete", exerciseId, e);
                            if (!e.liveExerciseChanged()) {
                                emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR,
                                        "The generated exercise passed verification but could not be saved. All changes were reverted."));
                                return;
                            }
                            emitter.milestone(ExerciseGenerationEventDTO.done(
                                    "Saving did not complete. Some changes may already have been saved; manual review is required. Automatic revert to the previous state is no longer available for this exercise.",
                                    ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, verdict, e.liveExerciseChanged(), e.savedRepositoryCommits().entrySet().stream()
                                            .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().name().toLowerCase(Locale.ROOT), Map.Entry::getValue))));
                            return;
                        }
                        catch (RuntimeException e) {
                            log.error("Failed to persist generated exercise {}", exerciseId, e);
                            if (!canContinueSave(exerciseId, jobId, heartbeatLost)) {
                                reportContinuationFailure(emitter);
                                return;
                            }
                            emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR,
                                    "The generated exercise passed verification but could not be saved. Nothing was changed."));
                            return;
                        }
                        if (!canContinueSave(exerciseId, jobId, heartbeatLost)) {
                            reportUncertainLiveSave(verdict, emitter);
                            return;
                        }
                        if (isNoOpPersist(persistResult)) {
                            emitter.milestone(ExerciseGenerationEventDTO.done("The generated exercise already matched the current exercise. No changes were needed.",
                                    ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, verdict, false, Map.of()));
                            return;
                        }
                        boolean revertUnavailable = !generationRevertService.recordBaseline(exerciseToPersist, jobId, event.mode(), persistResult.prePersistHeads(),
                                persistResult.postPersistHeads(), event.expectedProblemStatement(), event.expectedTitle(), persistResult.persistedProblemStatement(),
                                persistResult.persistedTitle(), persistResult.repositoryBranch());
                        if (!canContinueSave(exerciseId, jobId, heartbeatLost)) {
                            reportUncertainLiveSave(verdict, emitter);
                            return;
                        }
                        Map<RepositoryType, String> savedRepositoryHeads = new EnumMap<>(RepositoryType.class);
                        savedRepositoryHeads.putAll(outcome.seedRepositoryHeads());
                        savedRepositoryHeads.putAll(persistResult.postPersistHeads());
                        int reviewNoteCount;
                        if (outcome.specFidelityReport().findings().isEmpty()) {
                            reviewNoteCount = reviewService.attachFindings(exerciseToPersist, user, outcome.specFidelityReport());
                        }
                        else if (persistResult.savedExerciseVersionId() == null) {
                            log.warn("Could not attach generation review findings to exercise {} because its save did not create an identifiable exercise version", exerciseId);
                            reviewNoteCount = GenerationReviewService.REVIEW_COMMENTS_FAILED;
                        }
                        else {
                            reviewNoteCount = reviewService.attachFindings(exerciseToPersist, user, outcome.specFidelityReport(), persistResult.savedExerciseVersionId(),
                                    Map.copyOf(savedRepositoryHeads));
                        }
                        String reviewNotes = reviewNoteCount == GenerationReviewService.REVIEW_COMMENTS_FAILED
                                ? " Review notes could not be attached; inspect the generated exercise manually."
                                : reviewNoteCount == 1 ? " 1 review note was added for your attention."
                                        : reviewNoteCount > 1 ? " " + reviewNoteCount + " review notes were added for your attention." : "";
                        String savedMessage = event.mode() == GenerationMode.ADAPT ? "The exercise was adapted and saved. Review the changes."
                                : "The exercise was generated and saved. Review the changes.";
                        if (revertUnavailable) {
                            savedMessage += " Automatic revert is unavailable for this run.";
                        }
                        if (!canContinueSave(exerciseId, jobId, heartbeatLost)) {
                            reportUncertainLiveSave(verdict, emitter);
                            return;
                        }
                        boolean instructorReviewRequired = outcome.specFidelityReport().hasBlockingFindings();
                        if (instructorReviewRequired) {
                            savedMessage += " Automated quality review found issues that require instructor review.";
                        }
                        emitter.milestone(ExerciseGenerationEventDTO.done(savedMessage + reviewNotes,
                                instructorReviewRequired ? ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW : ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, verdict,
                                true,
                                persistResult.postPersistHeads().entrySet().stream()
                                        .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().name().toLowerCase(Locale.ROOT), Map.Entry::getValue)),
                                persistResult.savedExerciseVersionId()));
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
            clearJobAndReleaseBudget(exerciseId, jobId, event, tokenAccountingFailed.get());
        }
    }

    private void clearJobAndReleaseBudget(long exerciseId, String jobId, GenerationStartedEvent event, boolean tokenAccountingFailed) {
        try {
            jobService.clearJob(exerciseId, jobId);
        }
        finally {
            if (tokenAccountingFailed) {
                generationBudgetService.retainReservationForBudgetWindow(event.budgetReservationId());
            }
            else {
                releaseBudgetReservation(event);
            }
        }
    }

    private void releaseBudgetReservation(GenerationStartedEvent event) {
        generationBudgetService.releaseReservation(event.budgetReservationId());
    }

    private static String conciseReason(String reason) {
        String normalized = reason.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 600 ? normalized : normalized.substring(0, 597) + "...";
    }

    /**
     * The deadline is a wall-clock SAFETY control, not a user stop. It halts further model work — the cancelled supplier reads {@code deadlineExceeded}, so the run stops at the
     * next poll — but deliberately does not cancel the job: that would make {@link GenerationJobService#enterNonCancellablePhase} refuse and discard a mechanically verified
     * candidate that is already a save obligation. The terminal branch decides save-vs-discard from whether a verified checkpoint survived.
     */
    private ScheduledFuture<?> scheduleDeadline(AtomicBoolean deadlineExceeded, Instant deadlineAt) {
        Instant effectiveDeadlineAt = effectiveDeadlineAt(deadlineAt);
        if (effectiveDeadlineAt == null) {
            return null;
        }
        return taskScheduler.schedule(() -> deadlineExceeded.set(true), effectiveDeadlineAt);
    }

    private ScheduledFuture<?> scheduleHeartbeat(long exerciseId, String jobId, AtomicBoolean heartbeatLost) {
        if (ownerHeartbeatInterval == null || ownerHeartbeatInterval.isZero() || ownerHeartbeatInterval.isNegative()) {
            return null;
        }
        return taskScheduler.scheduleWithFixedDelay(() -> {
            try {
                if (jobService.heartbeat(exerciseId, jobId)) {
                    return;
                }
            }
            catch (RuntimeException e) {
                log.warn("Could not prove ownership while refreshing the heartbeat for generation job {}; aborting the run while heartbeat retries continue", jobId, e);
            }
            markOwnershipLost(exerciseId, jobId, heartbeatLost);
        }, ownerHeartbeatInterval);
    }

    private boolean stillOwnsMutationSlot(long exerciseId, String jobId, AtomicBoolean heartbeatLost) {
        if (heartbeatLost.get()) {
            return false;
        }
        try {
            return jobService.isOwnedActiveJob(exerciseId, jobId);
        }
        catch (RuntimeException e) {
            log.warn("Could not validate ownership of the mutation slot for generation job {}; aborting further side effects", jobId, e);
            markOwnershipLost(exerciseId, jobId, heartbeatLost);
            return false;
        }
    }

    private boolean canContinueSave(long exerciseId, String jobId, AtomicBoolean heartbeatLost) {
        return stillOwnsMutationSlot(exerciseId, jobId, heartbeatLost);
    }

    private void markOwnershipLost(long exerciseId, String jobId, AtomicBoolean heartbeatLost) {
        heartbeatLost.set(true);
        try {
            jobService.requestSystemCancellation(exerciseId, jobId, cancellationMessage(false, false, true));
        }
        catch (RuntimeException e) {
            log.warn("Could not publish system cancellation after generation job {} lost ownership; the local cancellation guard remains closed", jobId, e);
        }
    }

    private static void reportContinuationFailure(GenerationProgressEmitter emitter) {
        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR,
                "Generation stopped because job ownership could not be verified. Further changes were aborted."));
    }

    private static void reportUncertainLiveSave(ExerciseGenerationVerdictDTO verdict, GenerationProgressEmitter emitter) {
        emitter.milestone(ExerciseGenerationEventDTO.done(
                "The live save may be incomplete or the save may already have completed, but the job could not safely continue; manual review is required. No further durable side effects were started after the stop was detected.",
                ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, verdict, true));
    }

    private ProviderUsageSink budgetedUsageSink(Consumer<ChatResponse> delegate, long exerciseId, String jobId, AtomicBoolean tokenBudgetExceeded,
            AtomicBoolean tokenAccountingFailed) {
        AtomicLong tokensUsed = new AtomicLong();
        return new ProviderUsageSink() {

            @Override
            public void accept(ChatResponse response) {
                try {
                    delegate.accept(response);
                }
                catch (GenerationJobService.TokenUsageAccountingException e) {
                    markUncertain();
                    return;
                }

                if (maxTokensPerJob <= 0) {
                    return;
                }
                long total = tokensUsed.addAndGet(LLMTokenUsageService.totalTokens(response));
                if (total >= maxTokensPerJob && tokenBudgetExceeded.compareAndSet(false, true)) {
                    // Only the local flag: the orchestrator's cancelled-supplier reads it and stops all further model calls. Deliberately not requestSystemCancellation, which
                    // would mark the job cancelled and make enterNonCancellablePhase refuse the save of an already-paid-for verified candidate.
                    log.info("Exercise generation job {} reached its token budget; stopping after the current model response", jobId);
                }
            }

            @Override
            public void markUncertain() {
                if (tokenAccountingFailed.compareAndSet(false, true)) {
                    log.warn("Exercise generation job {} stopped because provider token usage could not be determined", jobId);
                    jobService.requestSystemCancellation(exerciseId, jobId, "Generation stopped because token usage could not be accounted for. Nothing was changed.");
                }
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

    /**
     * A persist that committed no repository and changed no problem statement or title: the generated candidate already matched the live exercise. Recording an automatic-revert
     * baseline or claiming {@code liveExerciseChanged} for such a run would misrepresent a no-op as a real save, and would discard an earlier run's still-valid baseline.
     */
    private static boolean isNoOpPersist(GenerationPersistenceService.PersistResult persistResult) {
        return persistResult.postPersistHeads().isEmpty() && !persistResult.metadataChanged();
    }

    private static ExerciseGenerationVerdictDTO toVerdict(VerificationResult verification) {
        if (verification == null) {
            return null;
        }
        return new ExerciseGenerationVerdictDTO(verification.mechanicallyVerified(), verification.solutionPassed(), verification.templateFailed(), verification.testCount(),
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

    private boolean isStillDraftWithoutParticipations(long exerciseId) {
        return programmingExerciseRepository.isUnreleasedAndWithoutStudentParticipations(exerciseId);
    }
}
