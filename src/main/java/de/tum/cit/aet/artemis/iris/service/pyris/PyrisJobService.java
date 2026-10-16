package de.tum.cit.aet.artemis.iris.service.pyris;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.dto.IrisStruggleInterventionRequestDTO;
import de.tum.cit.aet.artemis.iris.service.IrisLectureMaterialVersionService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJobType;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitProcessingStateRepositoryApi;

/**
 * The PyrisJobService class is responsible for managing Pyris jobs in the Artemis system.
 * It provides methods for adding, removing, and retrieving Pyris jobs, and authenticates the callbacks Pyris sends for them.
 * <p>
 * A job is identified by its run id, a random UUID that is not a secret. Callbacks authenticate with a separate signed token,
 * see {@link PyrisCallbackTokenService}.
 * The jobs live in a distributed map obtained from {@link DistributedDataProvider}, so the provider in use is
 * whatever {@code artemis.distributed-data.provider} selects.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisJobService {

    private static final Logger log = LoggerFactory.getLogger(PyrisJobService.class);

    /**
     * Versioned because the job key changed from a secret to a plain run id: a node of an older version looks into the old map
     * and can never find a job of this version by its run id.
     */
    private static final String PYRIS_JOB_MAP_NAME = "pyris-job-map-v2";

    private static final String GLOBAL_SEARCH_RUN_ID_MAP_NAME = "pyris-global-search-run-ids";

    /**
     * How much longer a global search run id stays reserved than the last token issued for it, so that nodes whose clocks
     * differ by up to this much still never accept that token for a later run with the same id.
     */
    static final Duration GLOBAL_SEARCH_RUN_ID_RESERVATION_MARGIN = Duration.ofMinutes(5);

    private static final String CHAT_JOB_CLIENT_ID_MAP_NAME = "pyris-chat-job-client-id-map";

    private final DistributedDataProvider distributedDataProvider;

    private final Optional<LectureUnitProcessingStateRepositoryApi> processingStateRepositoryApi;

    private final IrisLectureMaterialVersionService materialVersionService;

    private final PyrisCallbackTokenService callbackTokenService;

    @Nullable
    private DistributedMap<String, PyrisJob> jobMap;

    @Nullable
    private DistributedMap<String, String> chatJobClientIdMap;

    @Nullable
    private DistributedMap<String, String> struggleInFlightMap;

    @Nullable
    private DistributedMap<String, String> struggleCooldownMap;

    @Nullable
    private DistributedMap<String, String> globalSearchRunIdMap;

    @Value("${artemis.iris.jobs.timeout:300}")
    private int jobTimeout; // in seconds

    @Value("${artemis.iris.jobs.ingestion.timeout:10800}")
    private int ingestionJobTimeout; // in seconds (default 3h: covers transcription + ingestion of long lectures)

    private final IrisProactiveProperties proactiveProperties;

    public PyrisJobService(DistributedDataProvider distributedDataProvider, Optional<LectureUnitProcessingStateRepositoryApi> processingStateRepositoryApi,
            IrisProactiveProperties proactiveProperties, IrisLectureMaterialVersionService materialVersionService, PyrisCallbackTokenService callbackTokenService) {
        this.distributedDataProvider = distributedDataProvider;
        this.processingStateRepositoryApi = processingStateRepositoryApi;
        this.proactiveProperties = proactiveProperties;
        this.materialVersionService = materialVersionService;
        this.callbackTokenService = callbackTokenService;
    }

    /**
     * Lazy init: retrieves the distributed map that stores Pyris jobs.
     *
     * <p>
     * The entry lifetime is requested here rather than configured on the provider, because a map-level TTL is not
     * expressible on every provider and would silently not apply on some of them.
     *
     * @return the map containing Pyris jobs
     */
    private DistributedMap<String, PyrisJob> getPyrisJobMap() {
        if (this.jobMap == null) {
            this.jobMap = this.distributedDataProvider.getExpiringMap(PYRIS_JOB_MAP_NAME, Duration.ofSeconds(jobTimeout));
        }
        return this.jobMap;
    }

    // The single-flight in-flight markers for struggle runs, keyed by #struggleInFlightKey(long, long). The entry lifetime is a crash
    // self-heal backstop, requested here rather than configured on the provider because a map-level TTL is not expressible on every one
    // of them.
    private DistributedMap<String, String> getStruggleInFlightMap() {
        if (this.struggleInFlightMap == null) {
            this.struggleInFlightMap = this.distributedDataProvider.getExpiringMap("struggle-inflight-map", Duration.ofSeconds(jobTimeout));
        }
        return this.struggleInFlightMap;
    }

    private static String struggleInFlightKey(long userId, long exerciseId) {
        return userId + ":" + exerciseId;
    }

    // The struggle admission charges, keyed by #struggleCooldownKey(long, long, String). Deliberately not the in-flight map, whose marker
    // a scoped cancel clears, which would make the charge refundable by the very client it bounds.
    private DistributedMap<String, String> getStruggleCooldownMap() {
        if (this.struggleCooldownMap == null) {
            this.struggleCooldownMap = this.distributedDataProvider.getExpiringMap("struggle-cooldown-map", proactiveProperties.getTriggerCooldown());
        }
        return this.struggleCooldownMap;
    }

    // Reservations of client-chosen global search run ids, keyed by run id, valued by the user login. A reservation outlives every token issued for
    // its run id, see #reserveGlobalSearchRunIdBeyond(String, String, Instant), so a token can never be accepted for a later run with the same id.
    private DistributedMap<String, String> getGlobalSearchRunIdMap() {
        if (this.globalSearchRunIdMap == null) {
            this.globalSearchRunIdMap = this.distributedDataProvider.getExpiringMap(GLOBAL_SEARCH_RUN_ID_MAP_NAME,
                    PyrisCallbackTokenService.INTERACTIVE_VALIDITY.plus(GLOBAL_SEARCH_RUN_ID_RESERVATION_MARGIN));
        }
        return this.globalSearchRunIdMap;
    }

    // Per student, exercise AND intent, so a confirm_close legitimately following its own decide is not blocked. The intent is
    // canonicalised because it comes from the client: raw, it would be an unbounded set of lanes.
    private static String struggleCooldownKey(long userId, long exerciseId, @Nullable String intent) {
        return userId + ":" + exerciseId + ":" + IrisStruggleInterventionRequestDTO.canonicalIntent(intent);
    }

    /**
     * How long a charge holds its key, read here so the duration stays with the map that enforces it.
     *
     * @return the struggle cooldown in seconds
     */
    public long getStruggleCooldownSeconds() {
        return proactiveProperties.getTriggerCooldown().toSeconds();
    }

    /**
     * Charge the admission cooldown for a trigger about to start a run. A single {@code putIfAbsent} rather than a
     * read then a write, so two triggers racing on the same key cannot both be admitted. The returned value is what
     * a later refund has to present. It is an internal value, never sent to Pyris or a client.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the student is struggling on
     * @param intent     the slot intent, canonicalised into the key
     * @return the token that paid the charge, or empty while a cooldown is running for this key
     */
    public Optional<String> chargeStruggleCooldown(long userId, long exerciseId, @Nullable String intent) {
        var token = UUID.randomUUID().toString();
        var previous = getStruggleCooldownMap().putIfAbsent(struggleCooldownKey(userId, exerciseId, intent), token, proactiveProperties.getTriggerCooldown());
        return previous == null ? Optional.of(token) : Optional.empty();
    }

    /**
     * Refund a charge whose run provably cost nothing upstream, meaning only the local bails before anything reaches
     * Pyris. A failure reported through the pipeline's status consumer is not refundable, because a preparation
     * failure and a connector failure arrive there as the same frame. Nor is a client-requested cancel, which is
     * attacker-controlled. Conditional on the stored token, so a late refund cannot clear a newer charge.
     *
     * @param token      the token that paid the charge
     * @param userId     the struggling student
     * @param exerciseId the exercise the student is struggling on
     * @param intent     the slot intent, canonicalised into the key
     */
    public void refundStruggleCooldown(String token, long userId, long exerciseId, @Nullable String intent) {
        getStruggleCooldownMap().remove(struggleCooldownKey(userId, exerciseId, intent), token);
    }

    /**
     * Keeps the browser tab associated with a chat job outside {@link ChatJob}. The job record is part of the persisted distributed-data wire format and changing its components
     * would make jobs written before an upgrade unreadable. This separate string map can be introduced without changing any existing stored representation.
     */
    private DistributedMap<String, String> getChatJobClientIdMap() {
        if (this.chatJobClientIdMap == null) {
            this.chatJobClientIdMap = this.distributedDataProvider.getExpiringMap(CHAT_JOB_CLIENT_ID_MAP_NAME, Duration.ofSeconds(jobTimeout));
        }
        return this.chatJobClientIdMap;
    }

    /**
     * Creates a run id for an arbitrary job, runs the provided function with the run id as an argument,
     * and stores the job in the job map.
     *
     * @param runIdToJobFunction the function to run with the run id
     * @return the run id of the job
     */
    public String addJob(Function<String, PyrisJob> runIdToJobFunction) {
        var runId = newRunId();
        putNewJob(runId, runIdToJobFunction.apply(runId), Duration.ofSeconds(jobTimeout));
        return runId;
    }

    /**
     * Adds a chat job and separately associates it with the originating browser tab.
     *
     * @param courseId      the course id
     * @param sessionId     the chat session id
     * @param entityId      the optional entity id
     * @param userMessageId the optional user message id
     * @param clientId      the optional id of the originating browser tab
     * @return the run id of the job
     */
    public String addChatJob(long courseId, long sessionId, Long entityId, Long userMessageId, String clientId) {
        var runId = newRunId();
        putNewJob(runId, new ChatJob(runId, courseId, sessionId, entityId, null, userMessageId, null), Duration.ofSeconds(jobTimeout));
        if (clientId != null) {
            getChatJobClientIdMap().put(runId, clientId);
        }
        return runId;
    }

    /**
     * @param jobId the chat job whose originating browser tab should be addressed
     * @return the client id, or {@code null} for jobs created without one or before this association existed
     */
    @Nullable
    public String getChatJobClientId(String jobId) {
        return getChatJobClientIdMap().get(jobId);
    }

    /**
     * adds a tutor suggestion job to the job map
     *
     * @param postId    Id of the post the suggestion is created for
     * @param courseId  Id of the course the post belongs to
     * @param sessionId Id of the session the suggestion is created for
     * @return the run id of the job
     */
    public String addTutorSuggestionJob(Long postId, Long courseId, Long sessionId) {
        var runId = newRunId();
        putNewJob(runId, new TutorSuggestionJob(runId, postId, courseId, sessionId, null, null, null), Duration.ofSeconds(jobTimeout));
        return runId;
    }

    /**
     * Adds an autonomous tutor job to the job map.
     * This job is used for the autonomous tutor pipeline that responds to student posts.
     *
     * @param postId   Id of the post being responded to
     * @param courseId Id of the course the post belongs to
     * @return the run id of the job
     */
    public String addAutonomousTutorJob(Long postId, Long courseId) {
        var runId = newRunId();
        putNewJob(runId, new AutonomousTutorJob(runId, postId, courseId), Duration.ofSeconds(jobTimeout));
        return runId;
    }

    /**
     * Cluster-atomically reserve the single-flight slot for {@code (userId, exerciseId)} and mint a struggle job.
     * The reservation TTL matches the job TTL, so a crashed run self-heals. If any write in the sequence fails, a
     * token-conditional rollback of both is attempted, and a rollback that fails leaves the TTL as the backstop.
     *
     * @param courseId        the course the run belongs to
     * @param userId          the struggling student
     * @param exerciseId      the exercise the student is struggling on
     * @param intent          the slot intent forwarded from the inbound request; null on legacy paths
     * @param episodeId       the client-allocated episode UUID for async correlation; null when no episode was sent
     * @param confirmReason   the close-mode discriminator; null unless intent is {@code confirm_close}
     * @param requestToken    the client-minted scoped-cancel UUID; null on legacy paths
     * @param proactivityMode the presence level ({@code pull} | {@code push}); stamped so the callback can enforce Pull; null on legacy paths
     * @return the run id of the new job, or empty if a run is already in flight for {@code (userId, exerciseId)}
     */
    public Optional<String> addStruggleInterventionJobIfNonePending(long courseId, long userId, long exerciseId, @Nullable String intent, @Nullable String episodeId,
            @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode) {
        var token = newRunId();
        var key = struggleInFlightKey(userId, exerciseId);
        var job = new StruggleInterventionJob(token, courseId, exerciseId, userId, intent, episodeId, confirmReason, requestToken, proactivityMode);
        // Reservation through re-stamp is undone as one unit. Nothing here hands the token out, so a caller that
        // catches the failure cannot release what a partial write left behind. The reservation write is inside the
        // boundary too, because a provider can apply it and still fail the call.
        try {
            String existing = getStruggleInFlightMap().putIfAbsent(key, token, Duration.ofSeconds(jobTimeout));
            if (existing != null) {
                return Optional.empty();
            }
            // Shares the job map with every other pipeline. The map is only ever read by run id, never iterated, so a
            // build that does not know this record never reads one: the run id that would find it is handed out by
            // this build alone. That is why the new record needs no distributed schema version of its own.
            putNewJob(token, job, Duration.ofSeconds(jobTimeout));
            // The marker was written before the job, so it would expire first and the run would outlive the
            // reservation protecting it. Re-stamping keeps the marker's lifetime the longer of the two.
            refreshStruggleInFlightMarker(token, userId, exerciseId);
        }
        catch (RuntimeException e) {
            undoReservation(token, job, key, e);
            throw e;
        }
        return Optional.of(token);
    }

    // Undo a half-written struggle reservation, keeping the failure that caused it as the one that escapes. Both removals are conditional
    // on what this call wrote, so neither can touch a newer run, and both are attempted independently, because a provider failing the
    // first would otherwise leave the second undone. Cleanup failures are recorded on the carrier rather than thrown, so the caller still
    // sees the original cause.
    private void undoReservation(String token, StruggleInterventionJob job, String key, RuntimeException carrier) {
        try {
            getPyrisJobMap().remove(token, job);
        }
        catch (RuntimeException removeJobFailure) {
            carrier.addSuppressed(removeJobFailure);
        }
        try {
            getStruggleInFlightMap().remove(key, token);
        }
        catch (RuntimeException removeMarkerFailure) {
            carrier.addSuppressed(removeMarkerFailure);
        }
    }

    /**
     * Release a reserved struggle slot and its job on a local send failure, where no callback will arrive.
     * Idempotent and token-conditional, so it cannot wipe a newer reservation for the same pair.
     *
     * @param token      the run id of the reserving job
     * @param userId     the struggling student
     * @param exerciseId the exercise the student is struggling on
     */
    public void releaseStruggleInFlightJob(String token, long userId, long exerciseId) {
        getPyrisJobMap().remove(token);
        getStruggleInFlightMap().remove(struggleInFlightKey(userId, exerciseId), token);
    }

    /**
     * Extend the in-flight reservation for a run that is still alive, token-conditionally. A run that outlives its
     * own marker lets a second trigger reserve the same pair while it is still going, which is the duplicate session
     * and bubble the single-flight guard exists to prevent, so every point that extends a run's life re-stamps the
     * marker.
     *
     * <p>
     * The re-put is conditional on the stored value still being this token, so a late refresh cannot resurrect a
     * reservation a newer run has retaken. A refresh that finds no marker is a silent noop: the only normal path
     * that can clear one in that window is a scoped cancel, and that cancel is meant to win.
     *
     * @param token      the run id of the reserving job
     * @param userId     the struggling student
     * @param exerciseId the exercise the student is struggling on
     */
    public void refreshStruggleInFlightMarker(String token, long userId, long exerciseId) {
        var key = struggleInFlightKey(userId, exerciseId);
        var map = getStruggleInFlightMap();
        map.lock(key);
        try {
            if (token.equals(map.get(key))) {
                map.put(key, token, Duration.ofSeconds(jobTimeout));
            }
        }
        finally {
            map.unlock(key);
        }
    }

    /**
     * Release only the in-flight marker, token-conditionally, leaving the job map untouched. Called on the terminal
     * callback after {@code handleDecision} has finished: the job entry is removed up front so a trailing duplicate
     * callback 403s, but the marker has to outlive the persist and push, or a second trigger races in.
     *
     * @param token      the run id of the reserving job
     * @param userId     the struggling student
     * @param exerciseId the exercise the student is struggling on
     */
    public void releaseStruggleInFlightMarker(String token, long userId, long exerciseId) {
        getStruggleInFlightMap().remove(struggleInFlightKey(userId, exerciseId), token);
    }

    /**
     * Scoped cancel: remove the pending struggle job and its in-flight marker only if the job's stamped
     * {@code requestToken} matches. A missing job or a non-matching token is an idempotent noop, which is what stops
     * {@code cancel(A)} from removing a since-started run B.
     *
     * <p>
     * The removal runs under {@link #runWithJobLock} and re-reads the job there, because the terminal callback runs
     * its whole remove-then-handle-then-release sequence under the same lock. Without that serialization cancel
     * could pass the token check and then release the marker while {@code handleDecision} is still persisting and
     * pushing, reopening the re-trigger race the marker exists to close.
     *
     * @param userId       the struggling student (scopes the in-flight key)
     * @param exerciseId   the exercise the student is struggling on (scopes the in-flight key)
     * @param requestToken the token that must match the pending job's stamped token; null is treated as no-match
     */
    public void removeStruggleJobIfTokenMatches(long userId, long exerciseId, @Nullable String requestToken) {
        if (requestToken == null) {
            return;
        }
        var key = struggleInFlightKey(userId, exerciseId);
        // Read outside the lock only to learn which job id to lock on; everything the removal depends on is re-read
        // under it. A stale read locks a token whose job is gone, which is a noop, and the marker remove stays
        // token-conditional.
        String pendingToken = getStruggleInFlightMap().get(key);
        if (pendingToken == null) {
            return;  // no pending job, idempotent noop
        }
        runWithJobLock(pendingToken, () -> {
            var job = getPyrisJobMap().get(pendingToken);
            if (!(job instanceof StruggleInterventionJob sij)) {
                return null;  // callback already claimed/removed the job (or it expired): nothing to cancel
            }
            if (!requestToken.equals(sij.requestToken())) {
                return null;  // token mismatch: cancel(A) must never remove a since-started B
            }
            // Still pending under our token, so remove both, token-conditionally.
            getPyrisJobMap().remove(pendingToken);
            getStruggleInFlightMap().remove(key, pendingToken);
            return null;
        });
    }

    /**
     * Adds a new global search answer job to the job map.
     * The job stores the requesting user's login so that WebSocket status updates can be routed.
     * <p>
     * The client chooses the run id, so it is reserved first: an id that was used before, by anyone, is rejected for as long
     * as a token issued for it could still be valid, and an id that names any other job is rejected as well. Otherwise a
     * client could replace another job, or a late callback of an earlier run could be accepted for this one.
     *
     * @param userLogin the login of the user who initiated the search
     * @param runId     the client-generated UUID that identifies this job and is echoed in WebSocket callbacks
     * @throws ConflictException if the run id is already in use
     */
    public void addGlobalSearchAnswerJob(String userLogin, String runId) {
        if (getGlobalSearchRunIdMap().putIfAbsent(runId, userLogin) != null) {
            throw new ConflictException("The run id was already used", "Job", "runIdInUse");
        }
        if (getPyrisJobMap().putIfAbsent(runId, new GlobalSearchAnswerJob(runId, userLogin), Duration.ofSeconds(jobTimeout)) != null) {
            throw new ConflictException("The run id was already used", "Job", "runIdInUse");
        }
    }

    /**
     * Keeps a global search run id reserved until {@code tokenExpiry} plus a margin, before a token with that expiry is
     * issued for it. Only the user who reserved the id can extend the reservation.
     *
     * @param runId       the global search run id
     * @param userLogin   the user who reserved it
     * @param tokenExpiry the expiry of the token about to be issued for the run
     * @throws ConflictException if the reservation no longer belongs to this user
     */
    public void reserveGlobalSearchRunIdBeyond(String runId, String userLogin, Instant tokenExpiry) {
        var map = getGlobalSearchRunIdMap();
        map.lock(runId);
        try {
            String owner = map.get(runId);
            if (owner != null && !owner.equals(userLogin)) {
                throw new ConflictException("The run id was already used", "Job", "runIdInUse");
            }
            Duration timeToLive = Duration.between(Instant.now(), tokenExpiry.plus(GLOBAL_SEARCH_RUN_ID_RESERVATION_MARGIN));
            map.put(runId, userLogin, timeToLive);
        }
        finally {
            map.unlock(runId);
        }
    }

    /**
     * Captures the material a global search answer may cite, before its request is dispatched to Pyris, so that continuing the answer in the chat pins its citations to
     * the material the answer was written from, as a chat run does.
     *
     * @param userLogin the login of the user who initiated the search
     * @param runId     the global search answer run
     * @param courseIds the courses the answer is scoped to
     */
    public void captureGlobalSearchMaterialVersions(String userLogin, String runId, Collection<Long> courseIds) {
        materialVersionService.captureForGlobalSearch(runId, userLogin, courseIds);
    }

    /**
     * Removes a finished global search answer job. An answer that can be continued in the chat keeps the material versions of the lecture units it cites; any other
     * ending drops them.
     *
     * @param job       the finished job
     * @param sources   the lecture sources of the answer
     * @param hasAnswer whether the run ended with an answer that can be continued in the chat
     */
    public void finishGlobalSearchAnswerJob(GlobalSearchAnswerJob job, @Nullable List<PyrisLectureSearchResultDTO> sources, boolean hasAnswer) {
        var citedSources = sources == null ? List.<PyrisLectureSearchResultDTO>of()
                : sources.stream().filter(Objects::nonNull).filter(source -> source.lectureUnit() != null && source.course() != null).toList();
        if (hasAnswer && !citedSources.isEmpty()) {
            materialVersionService.keepCitedForGlobalSearch(job.jobId(), job.userLogin(), citedSources.stream().map(source -> source.lectureUnit().id()).toList());
        }
        else {
            materialVersionService.removeGlobalSearch(job.jobId(), job.userLogin());
        }
        removeJob(job);
    }

    /**
     * Adds a new lecture ingestion webhook job to the job map with a timeout.
     *
     * @param courseId      the ID of the course associated with the webhook job
     * @param lectureId     the ID of the lecture associated with the webhook job
     * @param lectureUnitId the ID of the lecture unit associated with the webhook job
     * @return the run id of the created webhook job
     */
    public String addLectureIngestionWebhookJob(long courseId, long lectureId, long lectureUnitId) {
        var runId = newRunId();
        putNewJob(runId, new LectureIngestionWebhookJob(runId, courseId, lectureId, lectureUnitId), Duration.ofSeconds(ingestionJobTimeout));
        return runId;
    }

    /**
     * Adds a new faq ingestion webhook job to the job map with a timeout.
     *
     * @param courseId the ID of the course associated with the webhook job
     * @param faqId    the ID of the faq associated with the webhook job
     * @return the run id of the created webhook job
     */
    public String addFaqIngestionWebhookJob(long courseId, long faqId) {
        var runId = newRunId();
        putNewJob(runId, new FaqIngestionWebhookJob(runId, courseId, faqId), Duration.ofSeconds(ingestionJobTimeout));
        return runId;
    }

    /**
     * Remove a job from the job map.
     *
     * @param job the job to remove
     */
    public void removeJob(PyrisJob job) {
        getPyrisJobMap().remove(job.jobId());
        if (job instanceof ChatJob) {
            getChatJobClientIdMap().remove(job.jobId());
            materialVersionService.remove(job.jobId());
        }
    }

    /**
     * Remove a job from the job map by its run id alone, for a caller that registered the job but never
     * built (or no longer holds) the {@link PyrisJob} object itself — e.g. releasing a prepared job whose
     * claim lapsed before it could be handed to a worker.
     *
     * @param runId the run id of the job to remove
     */
    public void removeJobByRunId(String runId) {
        getPyrisJobMap().remove(runId);
    }

    /**
     * Store a job in the job map, preserving the appropriate TTL for the job type.
     * Ingestion jobs use a longer TTL since pipelines can run for over an hour.
     *
     * @param job the job to store
     */
    public void updateJob(PyrisJob job) {
        int ttl = (job instanceof LectureIngestionWebhookJob || job instanceof FaqIngestionWebhookJob) ? ingestionJobTimeout : jobTimeout;
        getPyrisJobMap().put(job.jobId(), job, Duration.ofSeconds(ttl));
        if (job instanceof ChatJob) {
            materialVersionService.refresh(job.jobId());
            var clientId = getChatJobClientIdMap().get(job.jobId());
            if (clientId != null) {
                getChatJobClientIdMap().put(job.jobId(), clientId, Duration.ofSeconds(ttl));
            }
        }
    }

    /**
     * Runs the supplied action while holding the distributed lock for the given Pyris job id.
     *
     * @param jobId    the job id whose map entry should be locked
     * @param supplier the action to run under the lock
     * @param <T>      the result type
     * @return the result returned by the supplier
     */
    public <T> T runWithJobLock(String jobId, Supplier<T> supplier) {
        var pyrisJobMap = getPyrisJobMap();
        pyrisJobMap.lock(jobId);
        try {
            return supplier.get();
        }
        finally {
            pyrisJobMap.unlock(jobId);
        }
    }

    /**
     * Get the job of a run.
     *
     * @param runId the run id
     * @return the job, or null if no such job is registered
     */
    public PyrisJob getJob(String runId) {
        return getPyrisJobMap().get(runId);
    }

    /**
     * Authenticates a callback from Pyris and returns the job it reports on.
     * <ol>
     * <li>The {@code Authorization} header must carry a Bearer token that {@link PyrisCallbackTokenService} verifies.
     * Anything else, including a plain run id, is rejected: the run id is not a secret.</li>
     * <li>The run id in the URL must be the run the token was issued for.</li>
     * <li>The job is looked up by that run id, for a lecture ingestion run also in the processing state, and must be of
     * the kind the token was issued for and of the expected class.</li>
     * </ol>
     *
     * @param request  the HttpServletRequest object representing the incoming request
     * @param runId    the run id from the callback URL
     * @param jobClass the class of the PyrisJob object to cast the retrieved job to
     * @param <Job>    the type of the PyrisJob object
     * @return the authenticated job
     * @throws AccessForbiddenException if the token is missing or invalid, or the job is gone or of another kind
     * @throws ConflictException        if the URL names another run than the token, or the job is not of the expected class
     */
    public <Job extends PyrisJob> Job getAndAuthenticateJobFromHeaderElseThrow(HttpServletRequest request, String runId, Class<Job> jobClass) {
        var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(Constants.BEARER_PREFIX)) {
            throw new AccessForbiddenException("No valid token provided");
        }
        var verified = callbackTokenService.verify(authHeader.substring(Constants.BEARER_PREFIX.length()))
                .orElseThrow(() -> new AccessForbiddenException("No valid token provided"));
        if (!verified.runId().equals(runId)) {
            throw new ConflictException("Run ID in URL does not match the authenticated run", "Job", "runIdMismatch");
        }
        PyrisJob job = getJob(verified.runId());
        if (job == null && verified.type() == PyrisJobType.LECTURE_INGESTION) {
            job = recoverIngestionJobFromDatabase(verified.runId());
        }
        if (job == null || PyrisJobType.of(job) != verified.type()) {
            throw new AccessForbiddenException("No valid token provided");
        }
        if (!jobClass.isInstance(job)) {
            throw new ConflictException("Run ID is not a " + jobClass.getSimpleName(), "Job", "invalidRunId");
        }
        return jobClass.cast(job);
    }

    /**
     * Reconstruct a lecture ingestion job from the processing state that still carries its run id.
     * <p>
     * The distributed job map entry expires after a TTL, but an ingestion run can legitimately outlive it.
     * The processing state row keeps the run id for as long as the job is in flight, so a late callback for a
     * job Artemis still tracks is accepted from the database instead of being rejected with a 403.
     * A run id that was already cleared (job superseded, recovered, or completed) finds nothing.
     * The caller has already verified a token issued for this run id; the run id alone authenticates nothing.
     *
     * @param runId the run id from the verified callback token
     * @return the reconstructed job, or null if no processing state carries this run id
     */
    @Nullable
    private PyrisJob recoverIngestionJobFromDatabase(String runId) {
        return processingStateRepositoryApi.flatMap(api -> api.findIngestionJobIdentityByToken(runId)).map(identity -> {
            log.info("Authenticated ingestion callback for unit {} from the processing state after job map expiry", identity.lectureUnitId());
            return (PyrisJob) new LectureIngestionWebhookJob(runId, identity.courseId(), identity.lectureId(), identity.lectureUnitId());
        }).orElse(null);
    }

    private static String newRunId() {
        return UUID.randomUUID().toString();
    }

    // Registers a job under a fresh run id. putIfAbsent rather than put, so that registering a job can never replace another one.
    private void putNewJob(String runId, PyrisJob job, Duration timeToLive) {
        if (getPyrisJobMap().putIfAbsent(runId, job, timeToLive) != null) {
            throw new IllegalStateException("A Pyris job with run id " + runId + " already exists");
        }
    }
}
