package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.config.HyperionGenerationTimeouts;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationInputDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderRetryPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationFileUpdate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationTokenUsageService.GenerationUsage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Allows one generation or undo per exercise, with cancellation and reconnect state. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    static final String JOB_MAP_NAME = de.tum.cit.aet.artemis.core.config.Constants.HYPERION_GENERATION_JOBS_MAP;

    static final String CANCEL_MAP_NAME = "hyperion-exercise-generation-cancellations";

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    static final String REVERT_JOB_PREFIX = "revert-";

    static final String GENERATION_PIPELINE_ID = "HYPERION_EXERCISE_GENERATION";

    private static final String USER_CANCELLATION_MESSAGE = "Generation was cancelled. Nothing was changed.";

    private static final String SYSTEM_CANCELLATION_MESSAGE = "Generation was cancelled by an administrator. Nothing was changed.";

    static final Duration DEFAULT_TERMINAL_REPLAY_TTL = Duration.ofHours(4);

    private final DistributedDataProvider distributedDataProvider;

    private final ApplicationEventPublisher eventPublisher;

    private final GenerationTokenUsageService llmTokenUsageService;

    private final HyperionGenerationBudgetService generationBudgetService;

    private final Duration staleJobTimeout;

    private final Duration maxJobDuration;

    /** The longest deadline any configured effort profile can hand a run. Also bounds cancellation retention; unnamed-profile runs still use {@link #maxJobDuration}. */
    @Nullable
    private final Duration longestConfiguredJobDuration;

    private final GenerationCancelHooks cancelHooks;

    private final Duration terminalReplayTtl;

    private final boolean exactProviderUsage;

    private String localNodeId;

    private DistributedMap<String, JobInfo> jobMap;

    private DistributedMap<String, Boolean> cancellationMap;

    private GenerationJobReplayStore replayStore;

    private GenerationJobReaper reaper;

    private GenerationJobCleanup cleanup;

    @Autowired
    public GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, GenerationTokenUsageService llmTokenUsageService,
            HyperionGenerationBudgetService generationBudgetService, HyperionAgentProperties agentProperties, HyperionEffortProfileService effortProfiles,
            @Qualifier("taskExecutor") Executor cancellationExecutor, @Value("${artemis.hyperion.generation.terminal-replay-ttl:PT4H}") Duration terminalReplayTtl,
            @Value("${spring.ai.openai.max-retries:1}") int providerMaxRetries, @Value("${spring.ai.openai.chat.max-retries:#{null}}") @Nullable Integer chatProviderMaxRetries) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, agentProperties.getStaleJobTimeout(), agentProperties.getMaxJobDuration(),
                cancellationExecutor, terminalReplayTtl, ProviderRetryPolicy.effectiveSdkRetries(providerMaxRetries, chatProviderMaxRetries) == 0,
                effortProfiles.longestMaxJobDuration());
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, GenerationTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor,
            Duration terminalReplayTtl) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, cancellationExecutor, terminalReplayTtl, true,
                maxJobDuration);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, GenerationTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor,
            Duration terminalReplayTtl, boolean exactProviderUsage) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, cancellationExecutor, terminalReplayTtl,
                exactProviderUsage, maxJobDuration);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, GenerationTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor,
            Duration terminalReplayTtl, boolean exactProviderUsage, @Nullable Duration longestConfiguredJobDuration) {
        this.longestConfiguredJobDuration = longestConfiguredJobDuration;
        this.distributedDataProvider = distributedDataProvider;
        this.eventPublisher = eventPublisher;
        this.llmTokenUsageService = llmTokenUsageService;
        this.generationBudgetService = generationBudgetService;
        this.staleJobTimeout = staleJobTimeout;
        this.maxJobDuration = maxJobDuration;
        this.cancelHooks = new GenerationCancelHooks(distributedDataProvider, cancellationExecutor);
        this.terminalReplayTtl = terminalReplayTtl;
        this.exactProviderUsage = exactProviderUsage;
    }

    public GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, GenerationTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, cancellationExecutor,
                DEFAULT_TERMINAL_REPLAY_TTL);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, GenerationTokenUsageService llmTokenUsageService) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, null, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, GenerationTokenUsageService llmTokenUsageService,
            Duration staleJobTimeout, Duration maxJobDuration) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, null, staleJobTimeout, maxJobDuration, Runnable::run);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, GenerationTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, Runnable::run);
    }

    public Consumer<ChatResponse> tokenUsageSink(@Nullable Long courseId, @Nullable Long exerciseId, @Nullable Long userId) {
        return tokenUsageSink(courseId, exerciseId, userId, null, null);
    }

    public Consumer<ChatResponse> tokenUsageSink(@Nullable Long courseId, @Nullable Long exerciseId, @Nullable Long userId, @Nullable String generationJobId) {
        return tokenUsageSink(courseId, exerciseId, userId, generationJobId, null);
    }

    /**
     * Creates a sink that persists provider usage for a generation job.
     * <p>
     * {@code liveUsageSink} receives the same recorded request, so a caller that reports the run's spend while it is still going reads the prices and the token split that were
     * actually recorded instead of resolving them a second time.
     *
     * @param courseId        the course, if known
     * @param exerciseId      the exercise, if known
     * @param userId          the user, if known
     * @param generationJobId the generation job, if known
     * @param liveUsageSink   observer of each recorded request, if the caller reports usage while the run is in flight
     * @return the usage sink
     */
    public Consumer<ChatResponse> tokenUsageSink(@Nullable Long courseId, @Nullable Long exerciseId, @Nullable Long userId, @Nullable String generationJobId,
            @Nullable Consumer<GenerationUsage> liveUsageSink) {
        return chatResponse -> {
            boolean recorded = llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                    builder -> builder.withCourse(courseId).withExercise(exerciseId).withUser(userId), request -> {
                        if (liveUsageSink != null) {
                            liveUsageSink.accept(request);
                        }
                        if (generationJobId != null) {
                            replayStore.recordUsage(generationJobId, request);
                            recordPersistedUsage(exerciseId, generationJobId, (long) request.numInputTokens() + request.numOutputTokens());
                        }
                    });
            if (!recorded) {
                throw new TokenUsageAccountingException();
            }
        };
    }

    void recordToolCalls(String generationJobId, long count) {
        replayStore.recordToolCalls(generationJobId, count);
    }

    private void recordPersistedUsage(@Nullable Long exerciseId, String generationJobId, long tokens) {
        if (generationBudgetService == null || exerciseId == null) {
            return;
        }
        try {
            JobInfo job = jobMap.get(key(exerciseId));
            if (job != null && job.jobId().equals(generationJobId)) {
                generationBudgetService.recordPersistedUsage(job.budgetReservationId(), tokens);
            }
        }
        catch (RuntimeException exception) {
            log.warn("Could not reduce the transient budget reservation for generation job {}; admission remains conservative", generationJobId, exception);
        }
    }

    static final class TokenUsageAccountingException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** Initializes distributed job state and validates timeout settings. */
    @PostConstruct
    public void init() {
        HyperionGenerationTimeouts.validateMaxJobDuration(maxJobDuration);
        Duration longestJobDuration = longestConfiguredJobDuration == null || longestConfiguredJobDuration.compareTo(maxJobDuration) < 0 ? maxJobDuration
                : longestConfiguredJobDuration;
        HyperionGenerationTimeouts.validateStaleJobTimeout(staleJobTimeout, longestJobDuration);
        HyperionGenerationTimeouts.validateTerminalReplayTtl(terminalReplayTtl, longestJobDuration);
        jobMap = distributedDataProvider.getMap(JOB_MAP_NAME);
        cancellationMap = distributedDataProvider.getExpiringMap(CANCEL_MAP_NAME, longestJobDuration);
        replayStore = new GenerationJobReplayStore(distributedDataProvider, terminalReplayTtl, longestJobDuration, cancellationMap);
        cleanup = new GenerationJobCleanup(jobMap, cancellationMap, replayStore, (exerciseId, jobId) -> publishExerciseState(exerciseId, jobId, false));
        cancelHooks.subscribe();
        localNodeId = UUID.randomUUID().toString();
        reaper = new GenerationJobReaper(this, jobMap, cancellationMap, replayStore, generationBudgetService, staleJobTimeout);
    }

    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode) {
        return startJob(user, exercise, userPrompt, mode, null);
    }

    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId) {
        return startJob(user, exercise, userPrompt, mode, budgetReservationId, null);
    }

    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId, @Nullable String sourceBrief) {
        return startJob(user, exercise, userPrompt, mode, budgetReservationId, sourceBrief, null);
    }

    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId, @Nullable String sourceBrief,
            @Nullable HyperionGenerationSettings settings) {
        return startJob(user, exercise, userPrompt, mode, budgetReservationId, sourceBrief, settings, null);
    }

    /**
     * Starts a generation job after claiming the exercise slot.
     *
     * @param user                the requesting user
     * @param exercise            the target exercise
     * @param userPrompt          the authoring prompt
     * @param mode                the generation mode
     * @param budgetReservationId the budget reservation, if present
     * @param sourceBrief         the original brief, if present
     * @param settings            the resolved generation settings, if present
     * @param input               the original instructor input retained with the run
     * @return the generation job id
     */
    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId, @Nullable String sourceBrief,
            @Nullable HyperionGenerationSettings settings, @Nullable ExerciseGenerationInputDTO input) {
        return startJob(user, exercise, userPrompt, mode, budgetReservationId, sourceBrief, settings, input, null, eventPublisher::publishEvent);
    }

    /**
     * Reserves the destination before its database transaction commits, without starting remote work.
     * The caller must dispatch after commit or abandon this exact job after rollback.
     *
     * @param user                requesting editor
     * @param exercise            new destination
     * @param userPrompt          resolved transformation
     * @param budgetReservationId reserved provider budget
     * @param settings            resolved effort
     * @param input               owner-visible input
     * @param preparation         source-copy context
     * @return deferred start event
     */
    public GenerationStartedEvent prepareVariantJob(User user, ProgrammingExercise exercise, String userPrompt, String budgetReservationId, HyperionGenerationSettings settings,
            ExerciseGenerationInputDTO input, GenerationVariantPreparation preparation) {
        var event = new java.util.concurrent.atomic.AtomicReference<GenerationStartedEvent>();
        startJob(user, exercise, userPrompt, GenerationMode.ADAPT, budgetReservationId, null, settings, input, preparation, event::set);
        return event.get();
    }

    /**
     * Dispatches a committed destination. Rejection is a visible failed run, not an untracked orphan draft.
     *
     * @param event reserved start event, dispatched once after database commit
     * @return whether the executor accepted responsibility for the run
     */
    public boolean dispatchPreparedJob(GenerationStartedEvent event) {
        try {
            eventPublisher.publishEvent(event);
            return true;
        }
        catch (RuntimeException failure) {
            log.warn("Could not dispatch prepared generation job {}", event.jobId(), failure);
            recordEvent(event.exercise().getId(), event.jobId(), ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR,
                    "The authoring executor could not start. The unused destination draft remains available for inspection or deletion."), true);
            clearJob(event.exercise().getId(), event.jobId());
            recordDispatchFailure(event.jobId());
            return false;
        }
    }

    private String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId, @Nullable String sourceBrief,
            @Nullable HyperionGenerationSettings settings, @Nullable ExerciseGenerationInputDTO input, @Nullable GenerationVariantPreparation preparation,
            Consumer<GenerationStartedEvent> dispatch) {
        String jobId = UUID.randomUUID().toString();
        String key = key(exercise.getId());
        Instant startedAt = Instant.now();
        Instant deadlineAt = startedAt.plus(settings == null ? maxJobDuration : settings.maxJobDuration());
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        JobInfo newJob = new JobInfo(jobId, user.getLogin(), exercise.getId(), startedAt, deadlineAt, localNodeId, startedAt, preparation == null, budgetReservationId, mode,
                exercise.getTitle(), course == null ? null : course.getId());
        var startedReplay = new AtomicReference<GenerationJobReplayStore.StartedReplay>();
        boolean publicStatePublished = false;
        try {
            claimSlot(key, newJob, "Exercise generation is already running for this exercise", "exerciseGenerationRunning");
            replayStore.initializeStart(exercise.getId(), jobId, user.getLogin(), mode, settings == null ? null : settings.name(), input, startedReplay::set);
            if (!exactProviderUsage) {
                replayStore.markUsageIncomplete(jobId);
            }
            publishExerciseState(exercise.getId(), jobId, true);
            publicStatePublished = true;
            var event = new GenerationStartedEvent(jobId, user, exercise, userPrompt, mode, exercise.getProblemStatement(), exercise.getTitle(), deadlineAt, budgetReservationId,
                    sourceBrief, settings, preparation);
            eventPublisher.publishEvent(new GenerationAdmittedEvent(event));
            dispatch.accept(event);
        }
        catch (RejectedExecutionException e) {
            cleanup.unpublished(newJob, startedReplay.get());
            recordDispatchFailure(jobId);
            if (publicStatePublished) {
                publishExerciseState(exercise.getId(), jobId, false);
            }
            log.warn("Exercise generation executor rejected job {} for exercise {}; cleanup was requested", jobId, exercise.getId());
            throw new ServiceUnavailableAlertException("The system is currently busy with too many exercise generations. Please try again in a few minutes.", ENTITY_NAME,
                    "exerciseGenerationCapacityExceeded");
        }
        catch (RuntimeException e) {
            cleanup.unpublished(newJob, startedReplay.get());
            recordDispatchFailure(jobId);
            if (publicStatePublished) {
                publishExerciseState(exercise.getId(), jobId, false);
            }
            throw e;
        }
        return jobId;
    }

    /**
     * Rejects an occupied exercise before checking worker capacity. A stale authoring run is asked to stop, but its reservation remains until completion or operator recovery.
     *
     * @param exerciseId the exercise id whose slot should be checked
     */
    public void rejectIfActiveJob(long exerciseId) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo existing = jobMap.get(key);
            if (existing == null) {
                return;
            }
            Instant now = Instant.now();
            if (reaper.shouldClearAsStale(existing, reaper.staleBefore(now))) {
                reaper.requestStaleCancellation(key, existing, now);
            }
            throw new ConflictException("Exercise generation is already running for this exercise", ENTITY_NAME, "exerciseGenerationRunning");
        }
        finally {
            unlockJobSlot(key);
        }
    }

    private void recordDispatchFailure(String jobId) {
        try {
            eventPublisher.publishEvent(new GenerationDispatchFailedEvent(jobId));
        }
        catch (RuntimeException failure) {
            log.error("Could not record dispatch failure for authoring run {}", jobId, failure);
        }
    }

    private void claimSlot(String key, JobInfo newJob, String conflictMessage, String errorKey) {
        lockJobSlot(key);
        try {
            JobInfo existing = jobMap.get(key);
            if (existing != null) {
                Instant now = Instant.now();
                if (reaper.shouldClearAsStale(existing, reaper.staleBefore(now))) {
                    reaper.requestStaleCancellation(key, existing, now);
                }
                throw new ConflictException(conflictMessage, ENTITY_NAME, errorKey);
            }
            if (jobMap.putIfAbsent(key, newJob) != null) {
                throw new ConflictException(conflictMessage, ENTITY_NAME, errorKey);
            }
        }
        finally {
            unlockJobSlot(key);
        }
    }

    public boolean recordEvent(long exerciseId, String jobId, ExerciseGenerationEventDTO event, boolean terminal) {
        return replayStore.recordEvent(exerciseId, jobId, event, terminal);
    }

    public boolean recordFileUpdate(long exerciseId, String jobId, GenerationFileUpdate update) {
        return replayStore.recordFileUpdate(exerciseId, jobId, update);
    }

    boolean recordFileChange(long exerciseId, String jobId, ExerciseGenerationFileChangeDTO change) {
        return recordFileUpdate(exerciseId, jobId, new GenerationFileUpdate(change, null));
    }

    public boolean recordSpecDocument(long exerciseId, String jobId, String specDocument) {
        return replayStore.recordSpecDocument(exerciseId, jobId, specDocument);
    }

    public Optional<ExerciseGenerationStatusDTO> getStatus(User user, ProgrammingExercise exercise) {
        return replayStore.getStatus(user, exercise);
    }

    /**
     * Retains a terminal run's unsaved candidate so its work stays inspectable. Best effort: a retention failure must never change the outcome the instructor is told about.
     *
     * @param exerciseId the exercise the run belonged to
     * @param jobId      the run that produced the candidate
     * @param userLogin  the instructor who started the run
     * @param artifacts  the bounded candidate snapshot
     */
    public void retainUnsavedArtifacts(long exerciseId, String jobId, String userLogin, ExerciseGenerationRetainedArtifactsDTO artifacts) {
        try {
            replayStore.retainUnsavedArtifacts(exerciseId, jobId, userLogin, artifacts);
        }
        catch (RuntimeException e) {
            log.warn("Could not retain the unsaved candidate of exercise generation job {}", jobId, e);
        }
    }

    public Optional<ExerciseGenerationRetainedArtifactsDTO> getRetainedArtifacts(User user, ProgrammingExercise exercise) {
        return replayStore.getRetainedArtifacts(user, exercise);
    }

    public void markTokenAccountingIncomplete(String jobId) {
        replayStore.markUsageIncomplete(jobId);
    }

    public void sealTokenAccountingOnWorkerExit(long exerciseId, String jobId) {
        replayStore.sealUsageOnWorkerExit(exerciseId, jobId);
    }

    void recordAgentTurn(String generationJobId) {
        replayStore.recordAgentTurn(generationJobId);
    }

    void recordAttempt(String generationJobId) {
        replayStore.recordAttempt(generationJobId);
    }

    public void discardRetainedRun(long exerciseId, String jobId) {
        replayStore.discardRetainedRun(exerciseId, jobId);
    }

    /**
     * Cancels a job owned by the requesting user. The slot remains claimed until the worker returns, and cancellation is refused after persistence begins.
     *
     * @param exerciseId the exercise id
     * @param jobId      the job id to cancel
     * @param user       the requesting user; must be the instructor who started the job
     * @return whether a matching, cancellable job was cancelled
     */
    public boolean requestCancellation(long exerciseId, String jobId, User user) {
        return requestCancellation(exerciseId, jobId, user, USER_CANCELLATION_MESSAGE);
    }

    public boolean requestSystemCancellation(long exerciseId, String jobId) {
        return requestSystemCancellation(exerciseId, jobId, SYSTEM_CANCELLATION_MESSAGE);
    }

    /** Cancels for a server-side guard, respecting the save phase without requiring caller ownership. */
    boolean requestSystemCancellation(long exerciseId, String jobId, String message) {
        return requestCancellation(exerciseId, jobId, null, message);
    }

    private boolean requestCancellation(long exerciseId, String jobId, @Nullable User user, String message) {
        String key = key(exerciseId);
        ExerciseGenerationEventDTO cancellationEvent;
        String userLogin;
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId)) {
                return false;
            }
            if (!job.cancellable()) {
                log.debug("Ignored cancellation request for job {}: it already entered the non-cancellable persistence phase", jobId);
                return false;
            }
            GenerationJobReplayStore.CancellationReplayState replayState = replayStore.cancellationReplayState(job);
            if (replayState == null || (user != null && !replayState.userLogin().equals(user.getLogin()))) {
                return false;
            }
            if (replayState.done()) {
                return user != null && (job.cancellationRequested() || isCancelled(jobId));
            }
            if (!job.cancellationRequested()) {
                try {
                    if (!jobMap.replace(key, job, job.withCancellationRequested())) {
                        return false;
                    }
                }
                catch (RuntimeException unavailable) {
                    log.warn("Could not confirm cancellation of generation job {}", jobId, unavailable);
                    throw new ServiceUnavailableAlertException("Cancellation could not be confirmed. Please retry.", ENTITY_NAME, "exerciseGenerationCancellationUnavailable");
                }
            }
            cancellationMap.put(job.jobId(), Boolean.TRUE);
            cancellationEvent = replayStore.appendCancellation(job, message);
            userLogin = replayState.userLogin();
        }
        finally {
            unlockJobSlot(key);
        }
        if (cancellationEvent == null) {
            return false;
        }
        publishCancellation(userLogin, jobId, cancellationEvent);
        interruptCluster(jobId);
        return true;
    }

    private void publishCancellation(String userLogin, String jobId, ExerciseGenerationEventDTO cancellationEvent) {
        try {
            eventPublisher.publishEvent(new GenerationCancellationEvent(userLogin, jobId, cancellationEvent));
        }
        catch (RuntimeException e) {
            log.warn("Could not publish the live cancellation event for generation job {}", jobId, e);
        }
    }

    void interruptCluster(String jobId) {
        cancelHooks.interruptCluster(jobId);
    }

    /**
     * Makes cancellation and saving mutually exclusive for this exercise.
     *
     * @param exerciseId the exercise id
     * @param jobId      the job id
     * @return whether this node still owns an uncancelled job
     */
    public boolean enterNonCancellablePhase(long exerciseId, String jobId) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId) || localNodeId == null || (job.ownerNodeId() != null && !job.ownerNodeId().equals(localNodeId))) {
                return false;
            }
            if (job.cancellationRequested() || isCancelled(jobId)) {
                // Cancellation already won this job under the same lock: the run is terminal as CANCELLED, so this must not flip it non-cancellable or let the caller persist.
                return false;
            }
            if (!jobMap.replace(key, job, job.withHeartbeat(Instant.now()).withCancellable(false))) {
                // Another operation changed the reservation; do not save.
                return false;
            }
        }
        finally {
            unlockJobSlot(key);
        }
        // The sandbox phase is over; there is no longer an in-flight tool/build operation that a cancel hook may safely interrupt.
        cancelHooks.deregister(jobId);
        return true;
    }

    public boolean hasActiveJob(long exerciseId) {
        return jobMap.get(key(exerciseId)) != null;
    }

    public boolean isActiveJob(long exerciseId, String jobId) {
        JobInfo job = jobMap.get(key(exerciseId));
        return job != null && job.jobId().equals(jobId);
    }

    /**
     * Checks whether this JVM still owns the active exercise mutation slot. This is a best-effort stale-writer guard before durable Git/DB writes; repository head checks and DB
     * compare-and-set guards remain the authoritative clobber protection for external resources.
     *
     * @param exerciseId the exercise id whose job should be checked
     * @param jobId      the expected active job id
     * @return true if this JVM still owns the active job
     */
    public boolean isOwnedActiveJob(long exerciseId, String jobId) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            return job != null && job.jobId().equals(jobId) && localNodeId != null && (job.ownerNodeId() == null || job.ownerNodeId().equals(localNodeId));
        }
        finally {
            unlockJobSlot(key);
        }
    }

    /**
     * Refreshes the owning worker's liveness independently of progress events. A false return means this process no longer owns the job and must stop before durable mutations.
     *
     * @param exerciseId the exercise id whose job should be refreshed
     * @param jobId      the expected active job id
     * @return true if the heartbeat was recorded
     */
    public boolean heartbeat(long exerciseId, String jobId) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId) || localNodeId == null || (job.ownerNodeId() != null && !job.ownerNodeId().equals(localNodeId))) {
                return false;
            }
            if (generationBudgetService != null && !generationBudgetService.refreshReservation(job.budgetReservationId())) {
                return false;
            }
            // A delayed heartbeat must not replace a newer run.
            return jobMap.replace(key, job, job.withHeartbeat(Instant.now()));
        }
        finally {
            unlockJobSlot(key);
        }
    }

    /**
     * Atomically claims the same per-exercise mutation slot used by generation/adaptation for a destructive adaptation revert. This closes the check-then-act race where a revert
     * could observe "no active job" and a generation could start before the repositories are reset.
     *
     * @param user       the requesting instructor
     * @param exerciseId the exercise id
     * @return an opaque slot token that must be passed to {@link #clearRevertSlot(long, String)}
     */
    public String claimRevertSlot(User user, long exerciseId) {
        return GenerationRecoverySlots.claim(jobMap, user, exerciseId, localNodeId,
                job -> claimSlot(key(exerciseId), job, "Exercise generation or undo is already running.", "exerciseGenerationRunning"));
    }

    /**
     * Keeps a completed, uncertain save reserved until an administrator reconciles its repositories.
     *
     * @param exerciseId exercise to protect
     * @param jobId      exact completed generation job
     */
    public void retainIncompleteGenerationSlot(long exerciseId, String jobId) {
        cleanup.incomplete(exerciseId, jobId, localNodeId);
    }

    public void retainRevertRecoverySlot(long exerciseId, String token) {
        GenerationRecoverySlots.retain(jobMap, exerciseId, token);
    }

    public boolean isRevertRecoveryPending(long exerciseId) {
        return GenerationRecoverySlots.isPending(jobMap.get(key(exerciseId)));
    }

    public boolean isRevertRecoveryRetry(String token) {
        return token.startsWith(GenerationRecoverySlots.RETRY_PREFIX);
    }

    /**
     * Returns an interrupted-save or stale-authoring reservation for manual inspection.
     *
     * @param exerciseId exercise to inspect
     * @return the exact reservation, without determining whether its worker has stopped
     */
    public Optional<WedgedSlotInfo> getWedgedSlotInfo(long exerciseId) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || job.cancellable() && !reaper.shouldClearAsStale(job, reaper.staleBefore(Instant.now()))) {
                return Optional.empty();
            }
            return Optional.of(new WedgedSlotInfo(exerciseId, job.jobId(), slotKind(job), job.ownerNodeId(), job.startedAt(), false));
        }
        finally {
            unlockJobSlot(key);
        }
    }

    /**
     * Releases an exact reservation after an administrator has stopped the old worker and reconciled any incomplete writes.
     * This operation does not establish process liveness or provide automatic failover.
     *
     * @param exerciseId exercise to recover
     * @param token      exact token returned by the inspection operation
     * @return whether the matching reservation was released
     */
    public boolean recoverWedgedSlot(long exerciseId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(token) || job.cancellable() && !reaper.shouldClearAsStale(job, reaper.staleBefore(Instant.now()))) {
                return false;
            }
            if (isGenerationJob(job)) {
                return reaper.stopActiveJob(key, job, Instant.now());
            }
            return jobMap.remove(key, job);
        }
        finally {
            unlockJobSlot(key);
        }
    }

    private static WedgedSlotKind slotKind(JobInfo job) {
        if (GenerationRecoverySlots.isPending(job)) {
            return WedgedSlotKind.REVERT_RECOVERY;
        }
        return job.jobId().startsWith(REVERT_JOB_PREFIX) ? WedgedSlotKind.REVERT : WedgedSlotKind.GENERATION;
    }

    /**
     * Releases a revert slot claimed with {@link #claimRevertSlot(User, long)}. Value-guarded so a delayed cleanup cannot clear a newer generation job.
     *
     * @param exerciseId the exercise id
     * @param token      the token returned from {@link #claimRevertSlot(User, long)}
     */
    public void clearRevertSlot(long exerciseId, String token) {
        String key = key(exerciseId);
        JobInfo current = jobMap.get(key);
        if (current != null && current.jobId().equals(token)) {
            jobMap.remove(key, current);
        }
    }

    /**
     * Makes an initialized variant cancellable only after repository creation has stopped mutating its destination.
     *
     * @param exerciseId initialized destination
     * @param jobId      exact current job
     * @return false when ownership was lost
     */
    public boolean allowCancellationAfterPreparation(long exerciseId, String jobId) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo current = jobMap.get(key);
            return current != null && current.jobId().equals(jobId) && localNodeId.equals(current.ownerNodeId()) && jobMap.replace(key, current, current.withCancellable(true));
        }
        finally {
            unlockJobSlot(key);
        }
    }

    public void registerCancelHook(String jobId, Runnable hook) {
        cancelHooks.register(jobId, hook, isCancelled(jobId));
    }

    public void deregisterCancelHook(String jobId) {
        cancelHooks.deregister(jobId);
    }

    public boolean isCancelled(String jobId) {
        return Boolean.TRUE.equals(cancellationMap.get(jobId));
    }

    /**
     * Releases a completed job while retaining its transcript and fileChanges for reconnect replay.
     *
     * @param exerciseId the exercise whose job completed
     * @param jobId      the completed job identifier
     */
    public void clearJob(long exerciseId, String jobId) {
        cleanup.finished(exerciseId, jobId);
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    void retryPendingCleanup() {
        cleanup.retry();
    }

    /** Requests cancellation when a generation stops sending heartbeats; does not release an active worker or save. */
    @Scheduled(fixedDelayString = "${artemis.hyperion.agent.stale-job-scan-ms:60000}")
    public void clearStaleJobs() {
        reaper.sweep();
    }

    static boolean isGenerationJob(JobInfo job) {
        return !GenerationRecoverySlots.isGenerationRecovery(job) && !job.jobId().startsWith(REVERT_JOB_PREFIX);
    }

    void publishExerciseState(long exerciseId, String jobId, boolean running) {
        try {
            eventPublisher.publishEvent(new ExerciseGenerationStateChangedEvent(new ExerciseGenerationStateDTO(exerciseId, jobId, running)));
        }
        catch (RuntimeException e) {
            log.warn("Could not publish shared generation state for exercise {} and job {}", exerciseId, jobId, e);
        }
    }

    private static String key(long exerciseId) {
        return String.valueOf(exerciseId);
    }

    /** Orders cancellation, saving and cleanup for one exercise. No lock is held while the worker runs. */
    void lockJobSlot(String key) {
        jobMap.lock(key);
    }

    void unlockJobSlot(String key) {
        jobMap.unlock(key);
    }

    /**
     * What kind of work claimed a non-cancellable slot, so an operator knows what to confirm quiescent before recovering it. {@link #REVERT_RECOVERY} is the guard a partial
     * undo leaves behind: already quiescent, but the repositories are inconsistent until an undo retry or a manual reconciliation restores them.
     */
    public enum WedgedSlotKind {
        GENERATION, REVERT, REVERT_RECOVERY
    }

    /** Manual recovery details. The legacy ownerLeftCluster field is false because generation does not inspect cluster topology. */
    public record WedgedSlotInfo(long exerciseId, String token, WedgedSlotKind kind, @Nullable String ownerNodeId, Instant startedAt, boolean ownerLeftCluster) {
    }

    /**
     * The slot entry for one exercise. {@code mode}, {@code exerciseTitle} and {@code courseId} are display context for the administrator overview and are only set for
     * generation runs; undo reservations leave them {@code null}. Drain active generation before upgrading nodes; mixed versions of this record are unsupported.
     */
    public record JobInfo(String jobId, String userLogin, long exerciseId, Instant startedAt, @Nullable Instant deadlineAt, @Nullable String ownerNodeId,
            @Nullable Instant lastHeartbeatAt, boolean cancellable, @Nullable String budgetReservationId, @Nullable GenerationMode mode, @Nullable String exerciseTitle,
            @Nullable Long courseId, boolean cancellationRequested) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public JobInfo(String jobId, String userLogin, long exerciseId, Instant startedAt, @Nullable Instant deadlineAt, @Nullable String ownerNodeId,
                @Nullable Instant lastHeartbeatAt, boolean cancellable, @Nullable String budgetReservationId, @Nullable GenerationMode mode, @Nullable String exerciseTitle,
                @Nullable Long courseId) {
            this(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, lastHeartbeatAt, cancellable, budgetReservationId, mode, exerciseTitle, courseId, false);
        }

        public JobInfo(String jobId, String userLogin, long exerciseId, Instant startedAt, @Nullable Instant deadlineAt, @Nullable String ownerNodeId,
                @Nullable Instant lastHeartbeatAt, boolean cancellable, @Nullable String budgetReservationId) {
            this(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, lastHeartbeatAt, cancellable, budgetReservationId, null, null, null);
        }

        JobInfo withCancellationRequested() {
            return new JobInfo(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, lastHeartbeatAt, cancellable, budgetReservationId, mode, exerciseTitle, courseId,
                    true);
        }

        Instant lastHeartbeatOrStartedAt() {
            return lastHeartbeatAt == null ? startedAt : lastHeartbeatAt;
        }

        JobInfo withHeartbeat(Instant heartbeatAt) {
            return new JobInfo(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, heartbeatAt, cancellable, budgetReservationId, mode, exerciseTitle, courseId,
                    cancellationRequested);
        }

        JobInfo withCancellable(boolean newCancellable) {
            return new JobInfo(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, lastHeartbeatAt, newCancellable, budgetReservationId, mode, exerciseTitle,
                    courseId, cancellationRequested);
        }
    }

    public record JobTranscript(String jobId, String userLogin, long exerciseId, GenerationMode mode, List<ExerciseGenerationEventDTO> events, boolean done,
            @Nullable String specDocument, @Nullable String effortProfile, @Nullable ExerciseGenerationInputDTO input) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public JobTranscript(String jobId, String userLogin, long exerciseId, GenerationMode mode, List<ExerciseGenerationEventDTO> events, boolean done,
                @Nullable String specDocument, @Nullable String effortProfile) {
            this(jobId, userLogin, exerciseId, mode, events, done, specDocument, effortProfile, null);
        }

        JobTranscript withEvents(List<ExerciseGenerationEventDTO> newEvents, boolean newDone, @Nullable String newSpecDocument) {
            return new JobTranscript(jobId, userLogin, exerciseId, mode, newEvents, newDone, newSpecDocument, effortProfile, input);
        }
    }

    public record JobFileChangeIndex(String jobId, String userLogin, List<ExerciseGenerationFileChangeDTO> changes) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** A terminal run's unsaved candidate, bound to the owner who started it so only they can read it back. */
    public record JobArtifacts(String jobId, String userLogin, ExerciseGenerationRetainedArtifactsDTO artifacts) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
