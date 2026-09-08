package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
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
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.config.HyperionGenerationTimeouts;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationFileUpdate;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Coordinates distributed generation slots, cancellation, and reconnect state. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private static final String JOB_MAP_NAME = "hyperion-exercise-generation-jobs";

    private static final String CANCEL_MAP_NAME = "hyperion-exercise-generation-cancellations";

    private static final String CANCEL_TOPIC_NAME = "hyperion-exercise-generation-cancel-requests";

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private static final String REVERT_JOB_PREFIX = "revert-";

    private static final String EXTERNAL_MUTATION_JOB_PREFIX = "external-mutation-";

    static final String GENERATION_PIPELINE_ID = "HYPERION_EXERCISE_GENERATION";

    private static final String USER_CANCELLATION_MESSAGE = "Generation was cancelled. Nothing was changed.";

    private static final String SYSTEM_CANCELLATION_MESSAGE = "Generation was cancelled by an administrator. Nothing was changed.";

    static final Duration DEFAULT_TERMINAL_REPLAY_TTL = Duration.ofHours(4);

    private final DistributedDataProvider distributedDataProvider;

    private final ApplicationEventPublisher eventPublisher;

    private final LLMTokenUsageService llmTokenUsageService;

    private final HyperionGenerationBudgetService generationBudgetService;

    private final Duration staleJobTimeout;

    private final Duration maxJobDuration;

    /** The longest deadline any configured effort profile can hand a run. Validation only: {@link #maxJobDuration} remains what an unnamed-profile run is given. */
    @Nullable
    private final Duration longestConfiguredJobDuration;

    private final Executor cancellationExecutor;

    private final int expectedDataMemberCount;

    private final Duration terminalReplayTtl;

    private final boolean exactProviderUsage;

    private String localNodeId;

    private DistributedMap<String, JobInfo> jobMap;

    private DistributedMap<String, Boolean> cancellationMap;

    private GenerationJobReplayStore replayStore;

    private GenerationJobReaper reaper;

    private final ConcurrentMap<String, Runnable> cancelHooks = new ConcurrentHashMap<>();

    @Autowired
    public GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            HyperionGenerationBudgetService generationBudgetService, HyperionAgentProperties agentProperties, HyperionEffortProfileService effortProfiles,
            @Qualifier("taskExecutor") Executor cancellationExecutor, @Value("${jhipster.cache.hazelcast.expected-data-member-count:1}") int expectedDataMemberCount,
            @Value("${artemis.hyperion.generation.terminal-replay-ttl:PT4H}") Duration terminalReplayTtl, @Value("${spring.ai.openai.max-retries:1}") int providerMaxRetries) {
        // The stale-job timeout is validated against the longest deadline ANY configured effort profile can hand a run, not against the deployment default: a profile that raises
        // the deadline above the stale timeout would otherwise have its slot reclaimed by another node while it is still legitimately running.
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, agentProperties.getStaleJobTimeout(), agentProperties.getMaxJobDuration(),
                cancellationExecutor, expectedDataMemberCount, terminalReplayTtl, providerMaxRetries == 0, effortProfiles.longestMaxJobDuration());
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor,
            int expectedDataMemberCount, Duration terminalReplayTtl) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, cancellationExecutor, expectedDataMemberCount,
                terminalReplayTtl, true);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor,
            int expectedDataMemberCount, Duration terminalReplayTtl, boolean exactProviderUsage) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, cancellationExecutor, expectedDataMemberCount,
                terminalReplayTtl, exactProviderUsage, maxJobDuration);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor,
            int expectedDataMemberCount, Duration terminalReplayTtl, boolean exactProviderUsage, @Nullable Duration longestConfiguredJobDuration) {
        this.longestConfiguredJobDuration = longestConfiguredJobDuration;
        this.distributedDataProvider = distributedDataProvider;
        this.eventPublisher = eventPublisher;
        this.llmTokenUsageService = llmTokenUsageService;
        this.generationBudgetService = generationBudgetService;
        this.staleJobTimeout = staleJobTimeout;
        this.maxJobDuration = maxJobDuration;
        this.cancellationExecutor = cancellationExecutor;
        this.expectedDataMemberCount = expectedDataMemberCount;
        this.terminalReplayTtl = terminalReplayTtl;
        this.exactProviderUsage = exactProviderUsage;
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor,
            int expectedDataMemberCount) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, cancellationExecutor, expectedDataMemberCount,
                DEFAULT_TERMINAL_REPLAY_TTL);
    }

    public GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, cancellationExecutor, 1,
                DEFAULT_TERMINAL_REPLAY_TTL);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, null, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            Duration staleJobTimeout, Duration maxJobDuration) {
        this(distributedDataProvider, eventPublisher, llmTokenUsageService, null, staleJobTimeout, maxJobDuration, Runnable::run);
    }

    GenerationJobService(DistributedDataProvider distributedDataProvider, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
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
            @Nullable Consumer<LLMRequest> liveUsageSink) {
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
        if (expectedDataMemberCount < 1) {
            throw new IllegalArgumentException("jhipster.cache.hazelcast.expected-data-member-count must be at least 1");
        }
        HyperionGenerationTimeouts.validateMaxJobDuration(maxJobDuration);
        Duration longestJobDuration = longestConfiguredJobDuration == null || longestConfiguredJobDuration.compareTo(maxJobDuration) < 0 ? maxJobDuration
                : longestConfiguredJobDuration;
        HyperionGenerationTimeouts.validateStaleJobTimeout(staleJobTimeout, longestJobDuration);
        jobMap = distributedDataProvider.getMap(JOB_MAP_NAME);
        cancellationMap = distributedDataProvider.getExpiringMap(CANCEL_MAP_NAME, maxJobDuration);
        replayStore = new GenerationJobReplayStore(distributedDataProvider, terminalReplayTtl);
        DistributedTopic<CancelRequest> cancelTopic = distributedDataProvider.getTopic(CANCEL_TOPIC_NAME);
        cancelTopic.addMessageListener(message -> runLocalCancelHook(message.jobId()));
        localNodeId = distributedDataProvider.getLocalNodeId();
        reaper = new GenerationJobReaper(this, distributedDataProvider, jobMap, cancellationMap, replayStore, generationBudgetService, staleJobTimeout, maxJobDuration);
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
     * @return the generation job id
     */
    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId, @Nullable String sourceBrief,
            @Nullable HyperionGenerationSettings settings) {
        String jobId = UUID.randomUUID().toString();
        String key = key(exercise.getId());
        Instant startedAt = Instant.now();
        Instant deadlineAt = startedAt.plus(settings == null ? maxJobDuration : settings.maxJobDuration());
        JobInfo newJob = new JobInfo(jobId, user.getLogin(), exercise.getId(), startedAt, deadlineAt, localNodeId, startedAt, true, budgetReservationId);
        claimSlot(key, newJob, "Exercise generation is already running for this exercise", "exerciseGenerationRunning");
        GenerationJobReplayStore.StartedReplay startedReplay = null;
        boolean publicStatePublished = false;
        try {
            startedReplay = replayStore.initializeStart(exercise.getId(), jobId, user.getLogin(), mode, settings == null ? null : settings.name());
            if (!exactProviderUsage) {
                replayStore.markUsageIncomplete(jobId);
            }
            publishExerciseState(exercise.getId(), jobId, true);
            publicStatePublished = true;
            eventPublisher.publishEvent(new GenerationStartedEvent(jobId, user, exercise, userPrompt, mode, exercise.getProblemStatement(), exercise.getTitle(), deadlineAt,
                    budgetReservationId, sourceBrief, settings));
        }
        catch (RejectedExecutionException e) {
            rollbackUnpublishedStart(exercise.getId(), key, newJob, startedReplay);
            if (publicStatePublished) {
                publishExerciseState(exercise.getId(), jobId, false);
            }
            log.warn("Exercise generation executor rejected job {} for exercise {}; released the slot", jobId, exercise.getId());
            throw new ServiceUnavailableAlertException("The system is currently busy with too many exercise generations. Please try again in a few minutes.", ENTITY_NAME,
                    "exerciseGenerationCapacityExceeded");
        }
        catch (RuntimeException e) {
            rollbackUnpublishedStart(exercise.getId(), key, newJob, startedReplay);
            if (publicStatePublished) {
                publishExerciseState(exercise.getId(), jobId, false);
            }
            throw e;
        }
        return jobId;
    }

    /**
     * Fails with the same conflict as {@link #startJob(User, ProgrammingExercise, String, GenerationMode, String)} when a live slot exists, but first reclaims an abandoned or
     * stale cancellable slot. The REST resource uses this before checking sandbox capacity so duplicate starts report the active job, not transient capacity exhaustion.
     *
     * @param exerciseId the exercise id whose slot should be checked
     */
    public void rejectIfActiveJobCannotBeReclaimed(long exerciseId) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            verifyExpectedDataMemberTopology();
            JobInfo existing = jobMap.get(key);
            if (existing == null) {
                return;
            }
            Instant now = Instant.now();
            if (reaper.shouldClearAsStale(existing, reaper.staleBefore(now))) {
                if (reaper.reclaimStaleJob(key, existing, now)) {
                    return;
                }
            }
            throw new ConflictException("Exercise generation is already running for this exercise", ENTITY_NAME, "exerciseGenerationRunning");
        }
        finally {
            unlockJobSlot(key);
        }
    }

    private void rollbackUnpublishedStart(long exerciseId, String key, JobInfo newJob, GenerationJobReplayStore.@Nullable StartedReplay startedReplay) {
        lockJobSlot(key);
        try {
            jobMap.remove(key, newJob);
            if (startedReplay != null) {
                replayStore.restoreUnpublishedStart(exerciseId, startedReplay);
            }
        }
        finally {
            unlockJobSlot(key);
        }
    }

    private void claimSlot(String key, JobInfo newJob, String conflictMessage, String errorKey) {
        lockJobSlot(key);
        try {
            verifyExpectedDataMemberTopology();
            JobInfo existing = jobMap.get(key);
            if (existing != null) {
                Instant now = Instant.now();
                if (reaper.shouldClearAsStale(existing, reaper.staleBefore(now))) {
                    if (!reaper.reclaimStaleJob(key, existing, now)) {
                        throw new ConflictException(conflictMessage, ENTITY_NAME, errorKey);
                    }
                }
                else {
                    throw new ConflictException(conflictMessage, ENTITY_NAME, errorKey);
                }
            }
            if (jobMap.putIfAbsent(key, newJob) != null) {
                // Only reachable if the lease expired mid-section and another node claimed the slot; losing the race is correct, overwriting its claim would not be.
                throw new ConflictException(conflictMessage, ENTITY_NAME, errorKey);
            }
        }
        finally {
            unlockJobSlot(key);
        }
    }

    /**
     * Recovery's weaker topology requirement: a strict majority of the expected data members must be visible, not all of them.
     * <p>
     * Admission demands the exact count, but recovery runs because a node died, so by construction fewer members than expected are visible and the exact check would refuse every
     * recovery it was written for. A majority is what makes "the owner is absent from the membership view" trustworthy rather than a partition artefact, and it matches the
     * {@code READ_WRITE} split-brain protection configured on these maps (minimum cluster size {@code expected / 2 + 1}).
     */
    private void verifyMajorityDataMemberTopology() {
        long actualDataMemberCount = countDataMembers();
        int majority = expectedDataMemberCount / 2 + 1;
        if (actualDataMemberCount < majority || actualDataMemberCount > expectedDataMemberCount) {
            throw new ServiceUnavailableAlertException(
                    "Hyperion slot recovery requires a majority of the expected " + expectedDataMemberCount + " Hazelcast data members (at least " + majority
                            + ") to be visible, but observed " + actualDataMemberCount
                            + ". Recovering from a minority island could clear a slot whose owner is merely partitioned away, not stopped.",
                    ENTITY_NAME, "hyperionDataMemberTopologyMismatch");
        }
    }

    private long countDataMembers() {
        try {
            return distributedDataProvider.getDataNodeIds().orElseThrow().size();
        }
        catch (RuntimeException e) {
            throw new ServiceUnavailableAlertException(
                    "Hyperion coordination cannot verify the distributed data-node topology. Check jhipster.cache.hazelcast.expected-data-member-count.", ENTITY_NAME,
                    "hyperionDataMemberTopologyUnavailable");
        }
    }

    private void verifyExpectedDataMemberTopology() {
        long actualDataMemberCount = countDataMembers();
        if (actualDataMemberCount != expectedDataMemberCount) {
            throw new ServiceUnavailableAlertException(
                    "Hyperion coordination is configured for expected " + expectedDataMemberCount + " Hazelcast data members, but observed " + actualDataMemberCount
                            + ". Set jhipster.cache.hazelcast.expected-data-member-count to the number of core/data members on every node.",
                    ENTITY_NAME, "hyperionDataMemberTopologyMismatch");
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
        String key = key(exerciseId);
        ExerciseGenerationEventDTO cancellationEvent;
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
            if (replayState == null || !replayState.userLogin().equals(user.getLogin())) {
                return false;
            }
            if (replayState.done()) {
                return isCancelled(jobId);
            }
            cancellationMap.put(job.jobId(), Boolean.TRUE);
            cancellationEvent = replayStore.appendCancellation(job, USER_CANCELLATION_MESSAGE);
        }
        finally {
            unlockJobSlot(key);
        }
        if (cancellationEvent == null) {
            return false;
        }
        publishCancellation(user.getLogin(), jobId, cancellationEvent);
        interruptCluster(jobId);
        return true;
    }

    public boolean requestSystemCancellation(long exerciseId, String jobId) {
        return requestSystemCancellation(exerciseId, jobId, SYSTEM_CANCELLATION_MESSAGE);
    }

    /**
     * Cancels a job for a server-side deadline or budget guard. This uses the same persistence fence as user cancellation but does not require a user ownership check.
     */
    boolean requestSystemCancellation(long exerciseId, String jobId, String message) {
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
                log.debug("Ignored system cancellation request for job {}: it already entered the non-cancellable persistence phase", jobId);
                return false;
            }
            GenerationJobReplayStore.CancellationReplayState replayState = replayStore.cancellationReplayState(job);
            if (replayState == null || replayState.done()) {
                return false;
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
        // The hook closes over live sandbox objects, so it can only run on the node holding them: run it here and broadcast, so cancellation is prompt even when the request hits
        // a different core node than the one running the sandbox.
        runLocalCancelHook(jobId);
        try {
            distributedDataProvider.<CancelRequest>getTopic(CANCEL_TOPIC_NAME).publish(new CancelRequest(jobId));
        }
        catch (RuntimeException e) {
            log.warn("Could not publish the cluster interrupt for cancelled generation job {}; workers will still observe the authoritative cancellation", jobId, e);
        }
    }

    private void runLocalCancelHook(String jobId) {
        Runnable hook = cancelHooks.remove(jobId);
        if (hook != null) {
            dispatchCancelHook(jobId, hook);
        }
    }

    private void dispatchCancelHook(String jobId, Runnable hook) {
        try {
            cancellationExecutor.execute(() -> runCancelHook(jobId, hook));
        }
        catch (RejectedExecutionException e) {
            log.error("Cancel hook dispatch for job {} was rejected; the generation worker will still observe the authoritative cancellation", jobId, e);
        }
    }

    private void runCancelHook(String jobId, Runnable hook) {
        try {
            hook.run();
        }
        catch (RuntimeException e) {
            log.warn("Cancel hook for job {} failed", jobId, e);
        }
    }

    /**
     * Atomically fences cancellation before durable persistence. Cancellation and this transition use the same distributed lock, so only one can win.
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
            if (isCancelled(jobId)) {
                // Cancellation already won this job under the same lock: the run is terminal as CANCELLED, so this must not flip it non-cancellable or let the caller persist.
                return false;
            }
            if (!jobMap.replace(key, job, job.withHeartbeat(Instant.now()).withCancellable(false))) {
                // The slot changed under an expired lease, so this node no longer owns the job and must not enter the phase that writes to Git and the database.
                return false;
            }
        }
        finally {
            unlockJobSlot(key);
        }
        // The sandbox phase is over; there is no longer an in-flight tool/build operation that a cancel hook may safely interrupt.
        cancelHooks.remove(jobId);
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
            // Reports the heartbeat as lost if the slot changed under an expired lease, rather than overwriting whichever job now owns it.
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
        String token = REVERT_JOB_PREFIX + UUID.randomUUID();
        Instant startedAt = Instant.now();
        JobInfo newJob = new JobInfo(token, user.getLogin(), exerciseId, startedAt, null, localNodeId, startedAt, false, null);
        claimSlot(key(exerciseId), newJob, "Exercise generation is already running for this exercise; wait for it to finish before reverting an adaptation.",
                "exerciseGenerationRunning");
        return token;
    }

    /**
     * Claims the same distributed per-exercise slot as generation for one authorized external REST mutation. Holding a real slot, rather than performing a check only, prevents a
     * generation from starting between the guard and the mutation.
     *
     * @param exerciseId the exercise being mutated
     * @return an opaque token that must be released with {@link #clearExternalMutationSlot(long, String)}
     */
    public String claimExternalMutationSlot(long exerciseId) {
        String token = EXTERNAL_MUTATION_JOB_PREFIX + UUID.randomUUID();
        Instant startedAt = Instant.now();
        JobInfo mutation = new JobInfo(token, "external", exerciseId, startedAt, null, localNodeId, startedAt, false, null);
        claimSlot(key(exerciseId), mutation, "Exercise generation is running for this exercise; wait for it to finish before making changes.", "exerciseGenerationRunning");
        return token;
    }

    /**
     * Releases an external mutation slot without ever clearing a newer owner.
     *
     * @param exerciseId the exercise id
     * @param token      the token returned from {@link #claimExternalMutationSlot(long)}
     */
    public void clearExternalMutationSlot(long exerciseId, String token) {
        if (token.startsWith(EXTERNAL_MUTATION_JOB_PREFIX)) {
            clearClaimedSlot(exerciseId, token);
        }
    }

    /**
     * Returns the slot currently blocking an exercise when it is one {@link #reclaimStaleJob} refuses to release on its own, so an operator can read the exact token that
     * {@link #recoverWedgedSlot(long, String)} requires.
     * <p>
     * That is every non-cancellable slot: an external REST mutation, an adaptation revert, and a generation past its point of no return. All three block generation, revert
     * <em>and</em> ordinary REST edits of the exercise, the map has no TTL, and nothing else ever clears them.
     *
     * @param exerciseId the exercise id
     * @return the blocking slot, if the exercise currently has one that cannot be reclaimed automatically
     */
    public Optional<WedgedSlotInfo> getWedgedSlotInfo(long exerciseId) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || job.cancellable()) {
                return Optional.empty();
            }
            return Optional.of(new WedgedSlotInfo(exerciseId, job.jobId(), slotKind(job), job.ownerNodeId(), job.startedAt(), !reaper.ownerMemberIsPresent(job)));
        }
        finally {
            unlockJobSlot(key);
        }
    }

    /**
     * Value-guarded recovery for a non-cancellable slot whose owning JVM has been confirmed terminated. Cluster departure alone is insufficient because a partitioned request may
     * still be writing, so this is an audited operator action rather than something the stale-job scan does.
     * <p>
     * Accepts a generation, revert, or external-mutation token. A recovered generation slot is terminalized like a stale one, so the instructor is told the run stopped and is
     * warned to review the repositories rather than left with a job that reports as running forever.
     *
     * @param exerciseId the exercise id
     * @param token      the exact slot token to recover, as reported by {@link #getWedgedSlotInfo(long)}
     * @return whether a departed owner's matching slot was recovered
     */
    public boolean recoverWedgedSlot(long exerciseId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            verifyMajorityDataMemberTopology();
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(token) || job.cancellable() || reaper.ownerMemberIsPresent(job)) {
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
        if (job.jobId().startsWith(EXTERNAL_MUTATION_JOB_PREFIX)) {
            return WedgedSlotKind.EXTERNAL_MUTATION;
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
        clearClaimedSlot(exerciseId, token);
    }

    private void clearClaimedSlot(long exerciseId, String token) {
        String key = key(exerciseId);
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job != null && job.jobId().equals(token)) {
                jobMap.remove(key, job);
            }
        }
        finally {
            unlockJobSlot(key);
        }
    }

    public void registerCancelHook(String jobId, Runnable hook) {
        cancelHooks.put(jobId, hook);
        if (isCancelled(jobId) && cancelHooks.remove(jobId, hook)) {
            dispatchCancelHook(jobId, hook);
        }
    }

    public void deregisterCancelHook(String jobId) {
        cancelHooks.remove(jobId);
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
        String key = key(exerciseId);
        boolean released = false;
        lockJobSlot(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job != null && job.jobId().equals(jobId)) {
                jobMap.remove(key, job);
                released = true;
            }
            replayStore.retainAfterJobCleared(exerciseId, jobId);
        }
        finally {
            unlockJobSlot(key);
        }
        cancellationMap.remove(jobId);
        if (released) {
            publishExerciseState(exerciseId, jobId, false);
        }
    }

    /** Cancels stale jobs and terminalizes jobs whose owner has left the Hazelcast cluster. */
    @Scheduled(fixedDelayString = "${artemis.hyperion.agent.stale-job-scan-ms:60000}")
    public void clearStaleJobs() {
        reaper.sweep();
    }

    static boolean isExternalMutationJob(JobInfo job) {
        return job.jobId().startsWith(EXTERNAL_MUTATION_JOB_PREFIX);
    }

    static boolean isGenerationJob(JobInfo job) {
        return !job.jobId().startsWith(REVERT_JOB_PREFIX) && !job.jobId().startsWith(EXTERNAL_MUTATION_JOB_PREFIX);
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

    /**
     * Acquires the per-exercise coordination lock without a lease. Cancellation and the transition into durable
     * persistence must remain mutually exclusive even when a backend call stalls for longer than expected; expiring
     * this lock would let an old cancellation resume after a newer caller entered the non-cancellable phase.
     */
    void lockJobSlot(String key) {
        jobMap.lock(key);
    }

    void unlockJobSlot(String key) {
        jobMap.unlock(key);
    }

    /** What kind of work claimed a non-cancellable slot, so an operator knows what to confirm quiescent before recovering it. */
    public enum WedgedSlotKind {
        GENERATION, REVERT, EXTERNAL_MUTATION
    }

    /**
     * A slot the automatic stale-job scan will never release. The {@code token} is what {@link #recoverWedgedSlot(long, String)} requires, and {@code ownerLeftCluster} is a
     * precondition of that recovery.
     */
    public record WedgedSlotInfo(long exerciseId, String token, WedgedSlotKind kind, @Nullable String ownerNodeId, Instant startedAt, boolean ownerLeftCluster) {
    }

    public record JobInfo(String jobId, String userLogin, long exerciseId, Instant startedAt, @Nullable Instant deadlineAt, @Nullable String ownerNodeId,
            @Nullable Instant lastHeartbeatAt, boolean cancellable, @Nullable String budgetReservationId) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        Instant lastHeartbeatOrStartedAt() {
            return lastHeartbeatAt == null ? startedAt : lastHeartbeatAt;
        }

        JobInfo withHeartbeat(Instant heartbeatAt) {
            return new JobInfo(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, heartbeatAt, cancellable, budgetReservationId);
        }

        JobInfo withCancellable(boolean newCancellable) {
            return new JobInfo(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, lastHeartbeatAt, newCancellable, budgetReservationId);
        }
    }

    private record CancelRequest(String jobId) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record JobTranscript(String jobId, String userLogin, long exerciseId, GenerationMode mode, List<ExerciseGenerationEventDTO> events, boolean done,
            @Nullable String specDocument, @Nullable String effortProfile) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        JobTranscript withEvents(List<ExerciseGenerationEventDTO> newEvents, boolean newDone, @Nullable String newSpecDocument) {
            return new JobTranscript(jobId, userLogin, exerciseId, mode, newEvents, newDone, newSpecDocument, effortProfile);
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
