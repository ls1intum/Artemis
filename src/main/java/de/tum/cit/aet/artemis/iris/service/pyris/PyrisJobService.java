package de.tum.cit.aet.artemis.iris.service.pyris;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
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
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;

/**
 * The PyrisJobService class is responsible for managing Pyris jobs in the Artemis system.
 * It provides methods for adding, removing, and retrieving Pyris jobs.
 * The class also handles generating job ID tokens and validating tokens from request headers based on these tokens.
 * The jobs live in a distributed map obtained from {@link DistributedDataProvider}, so the provider in use is
 * whatever {@code artemis.distributed-data.provider} selects.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisJobService {

    /**
     * Shared deliberately: {@link SecureRandom} is thread-safe, and constructing one re-seeds from the system
     * entropy source on every call.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DistributedDataProvider distributedDataProvider;

    @Nullable
    private DistributedMap<String, PyrisJob> jobMap;

    @Nullable
    private DistributedMap<String, String> struggleInFlightMap;

    @Nullable
    private DistributedMap<String, String> struggleCooldownMap;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${eureka.instance.instanceId:unknown}")
    private String instanceId;

    @Value("${artemis.iris.jobs.timeout:300}")
    private int jobTimeout; // in seconds

    @Value("${artemis.iris.jobs.ingestion.timeout:10800}")
    private int ingestionJobTimeout; // in seconds (default 3h: covers transcription + ingestion of long lectures)

    private final IrisProactiveProperties proactiveProperties;

    public PyrisJobService(DistributedDataProvider distributedDataProvider, IrisProactiveProperties proactiveProperties) {
        this.distributedDataProvider = distributedDataProvider;
        this.proactiveProperties = proactiveProperties;
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
            this.jobMap = this.distributedDataProvider.getExpiringMap("pyris-job-map", Duration.ofSeconds(jobTimeout));
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
     * read then a write, so two triggers racing on the same key cannot both be admitted. The returned token is what
     * a later refund has to present.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the student is struggling on
     * @param intent     the slot intent, canonicalised into the key
     * @return the token that paid the charge, or empty while a cooldown is running for this key
     */
    public Optional<String> chargeStruggleCooldown(long userId, long exerciseId, @Nullable String intent) {
        var token = generateJobIdToken();
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
     * Creates a token for an arbitrary job, runs the provided function with the token as an argument,
     * and stores the job in the job map.
     *
     * @param tokenToJobFunction the function to run with the token
     * @return the generated token
     */
    public String createTokenForJob(Function<String, PyrisJob> tokenToJobFunction) {
        var token = generateJobIdToken();
        var job = tokenToJobFunction.apply(token);
        getPyrisJobMap().put(token, job);
        return token;
    }

    public String addChatJob(long courseId, long sessionId, Long entityId, Long userMessageId) {
        var token = generateJobIdToken();
        var job = new ChatJob(token, courseId, sessionId, entityId, null, userMessageId, null);
        getPyrisJobMap().put(token, job);
        return token;
    }

    /**
     * adds a tutor suggestion job to the job map
     *
     * @param postId    Id of the post the suggestion is created for
     * @param courseId  Id of the course the post belongs to
     * @param sessionId Id of the session the suggestion is created for
     * @return the token of the job
     */
    public String addTutorSuggestionJob(Long postId, Long courseId, Long sessionId) {
        var token = generateJobIdToken();
        var job = new TutorSuggestionJob(token, postId, courseId, sessionId, null, null, null);
        getPyrisJobMap().put(token, job);
        return token;
    }

    /**
     * Adds an autonomous tutor job to the job map.
     * This job is used for the autonomous tutor pipeline that responds to student posts.
     *
     * @param postId   Id of the post being responded to
     * @param courseId Id of the course the post belongs to
     * @return the token of the job
     */
    public String addAutonomousTutorJob(Long postId, Long courseId) {
        var token = generateJobIdToken();
        var job = new AutonomousTutorJob(token, postId, courseId);
        getPyrisJobMap().put(token, job);
        return token;
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
     * @return the minted job token, or empty if a run is already in flight for {@code (userId, exerciseId)}
     */
    public Optional<String> addStruggleInterventionJobIfNonePending(long courseId, long userId, long exerciseId, @Nullable String intent, @Nullable String episodeId,
            @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode) {
        var token = generateJobIdToken();
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
            // Shares the job map with every other pipeline. The map is only ever read by token, never iterated, so
            // a build that does not know this record never reads one. What makes the release compatible is
            // DistributedDataSchema.VERSION being raised to 2, which puts an older build on the previous namespace.
            getPyrisJobMap().put(token, job);
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
     * @param token      the reserving job token
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
     * @param token      the reserving job token
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
     * @param token      the reserving job token
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
     *
     * @param userLogin the login of the user who initiated the search
     * @param runId     the client-generated UUID that identifies this job and is echoed in WebSocket callbacks
     */
    public void addGlobalSearchAnswerJob(String userLogin, String runId) {
        var job = new GlobalSearchAnswerJob(runId, userLogin);
        getPyrisJobMap().put(runId, job);
    }

    /**
     * Adds a new lecture ingestion webhook job to the job map with a timeout.
     *
     * @param courseId      the ID of the course associated with the webhook job
     * @param lectureId     the ID of the lecture associated with the webhook job
     * @param lectureUnitId the ID of the lecture unit associated with the webhook job
     * @return a unique token identifying the created webhook job
     */
    public String addLectureIngestionWebhookJob(long courseId, long lectureId, long lectureUnitId) {
        var token = generateJobIdToken();
        var job = new LectureIngestionWebhookJob(token, courseId, lectureId, lectureUnitId);
        getPyrisJobMap().put(token, job, Duration.ofSeconds(ingestionJobTimeout));
        return token;
    }

    /**
     * Adds a new faq ingestion webhook job to the job map with a timeout.
     *
     * @param courseId the ID of the course associated with the webhook job
     * @param faqId    the ID of the faq associated with the webhook job
     * @return a unique token identifying the created webhook job
     */
    public String addFaqIngestionWebhookJob(long courseId, long faqId) {
        var token = generateJobIdToken();
        var job = new FaqIngestionWebhookJob(token, courseId, faqId);
        getPyrisJobMap().put(token, job, Duration.ofSeconds(ingestionJobTimeout));
        return token;
    }

    /**
     * Remove a job from the job map.
     *
     * @param job the job to remove
     */
    public void removeJob(PyrisJob job) {
        getPyrisJobMap().remove(job.jobId());
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
     * Get the job of a token.
     *
     * @param token the token
     * @return the job
     */
    public PyrisJob getJob(String token) {
        return getPyrisJobMap().get(token);
    }

    /**
     * This method is used to authenticate an incoming request from Pyris.
     * 1. Reads the authentication token from the request headers.
     * 2. Retrieves the PyrisJob object associated with the provided token.
     * 3. Throws an AccessForbiddenException if the token is invalid or not provided.
     * <p>
     * The token was previously generated via {@link #createTokenForJob(Function)}
     *
     * @param request  the HttpServletRequest object representing the incoming request
     * @param jobClass the class of the PyrisJob object to cast the retrieved job to
     * @param <Job>    the type of the PyrisJob object
     * @return the PyrisJob object associated with the token
     * @throws AccessForbiddenException if the token is invalid or not provided
     */
    public <Job extends PyrisJob> Job getAndAuthenticateJobFromHeaderElseThrow(HttpServletRequest request, Class<Job> jobClass) {
        var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!authHeader.startsWith(Constants.BEARER_PREFIX)) {
            throw new AccessForbiddenException("No valid token provided");
        }
        var token = authHeader.substring(7);
        var job = getJob(token);
        if (job == null) {
            throw new AccessForbiddenException("No valid token provided");
        }
        if (!jobClass.isInstance(job)) {
            throw new ConflictException("Run ID is not a " + jobClass.getSimpleName(), "Job", "invalidRunId");
        }
        return jobClass.cast(job);
    }

    /**
     * Generates a unique job ID token.
     * The token is generated by combining the server URL, instance ID, current timestamp, and a random string.
     *
     * @return the generated (URL-safe) job token
     */
    private String generateJobIdToken() {
        // Include instance name, node id, timestamp and random string
        var randomStringBuilder = new StringBuilder();
        randomStringBuilder.append(serverUrl);
        randomStringBuilder.append('-');
        randomStringBuilder.append(instanceId);
        randomStringBuilder.append('-');
        randomStringBuilder.append(System.currentTimeMillis());
        randomStringBuilder.append('-');
        for (int i = 0; i < 10; i++) {
            var randomChar = SECURE_RANDOM.nextInt(62);
            if (randomChar < 10) {
                randomStringBuilder.append(randomChar);
            }
            else if (randomChar < 36) {
                randomStringBuilder.append((char) (randomChar - 10 + 'a'));
            }
            else {
                randomStringBuilder.append((char) (randomChar - 36 + 'A'));
            }
        }
        return randomStringBuilder.toString().replace("https://", "").replace("http://", "").replace(":", "_").replace(".", "_").replace("/", "_");
    }
}
