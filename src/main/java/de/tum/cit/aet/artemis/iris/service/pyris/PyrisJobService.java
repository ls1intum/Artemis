package de.tum.cit.aet.artemis.iris.service.pyris;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
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
 * The class also handles generating job ID tokens and validating tokens from request headers based ont these tokens.
 * It uses Hazelcast to store the jobs in a distributed map.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisJobService {

    private final HazelcastInstance hazelcastInstance;

    @Nullable
    private IMap<String, PyrisJob> jobMap;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${eureka.instance.instanceId:unknown}")
    private String instanceId;

    @Value("${artemis.iris.jobs.timeout:300}")
    private int jobTimeout; // in seconds

    @Value("${artemis.iris.jobs.ingestion.timeout:10800}")
    private int ingestionJobTimeout; // in seconds (default 3h: covers transcription + ingestion of long lectures)

    public PyrisJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Initializes the PyrisJobService by configuring the Hazelcast map for Pyris jobs.
     * Sets the time-to-live for the map entries to the specified jobTimeout value.
     */
    @PostConstruct
    public void init() {
        var mapConfig = hazelcastInstance.getConfig().getMapConfig("pyris-job-map");
        mapConfig.setTimeToLiveSeconds(jobTimeout);
        // Crash self-heal backstop: a reservation whose run never completes (node crash) expires with the job TTL.
        hazelcastInstance.getConfig().getMapConfig("struggle-inflight-map").setTimeToLiveSeconds(jobTimeout);
    }

    /**
     * Lazy init: Retrieves the Hazelcast map that stores Pyris jobs.
     * If the map is not initialized, it initializes it.
     *
     * @return the IMap containing Pyris jobs
     */
    private IMap<String, PyrisJob> getPyrisJobMap() {
        if (this.jobMap == null) {
            this.jobMap = this.hazelcastInstance.getMap("pyris-job-map");
        }
        return this.jobMap;
    }

    /**
     * Retrieves the Hazelcast map that holds the single-flight in-flight markers for struggle-intervention runs,
     * keyed by {@link #struggleInFlightKey(long, long)} (value = the reserving job token).
     *
     * @return the IMap of {@code (userId:exerciseId) -> token} reservations
     */
    private IMap<String, String> getStruggleInFlightMap() {
        return hazelcastInstance.getMap("struggle-inflight-map");
    }

    private static String struggleInFlightKey(long userId, long exerciseId) {
        return userId + ":" + exerciseId;
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
     * Cluster-atomically reserve the single-flight slot for {@code (userId, exerciseId)} and mint a struggle
     * job (spec §11). Returns the new token, or empty if a run is already in flight for that pair. The reservation
     * TTL matches the job TTL, so a crashed run self-heals. If writing the job map fails, the reservation is rolled
     * back (token-conditional) so the slot is never leaked.
     *
     * @param courseId      the course the run belongs to
     * @param userId        the struggling student
     * @param exerciseId    the exercise the student is struggling on
     * @param intent        the slot intent forwarded from the inbound request; null on legacy paths
     * @param episodeId     the client-allocated episode UUID for async correlation; null when no episode was sent
     * @param confirmReason the close-mode discriminator; null unless intent is {@code confirm_close}
     * @param requestToken  the client-minted scoped-cancel UUID (A10); null on legacy paths
     * @return the minted job token, or empty if a run is already in flight for {@code (userId, exerciseId)}
     */
    public Optional<String> addStruggleInterventionJobIfNonePending(long courseId, long userId, long exerciseId, @Nullable String intent, @Nullable String episodeId,
            @Nullable String confirmReason, @Nullable String requestToken) {
        var token = generateJobIdToken();
        var key = struggleInFlightKey(userId, exerciseId);
        String existing = getStruggleInFlightMap().putIfAbsent(key, token, jobTimeout, TimeUnit.SECONDS);
        if (existing != null) {
            return Optional.empty();
        }
        try {
            getPyrisJobMap().put(token, new StruggleInterventionJob(token, courseId, exerciseId, userId, intent, episodeId, confirmReason, requestToken));
        }
        catch (RuntimeException e) {
            getStruggleInFlightMap().remove(key, token); // roll back OUR reservation only (token-conditional)
            throw e;
        }
        return Optional.of(token);
    }

    /**
     * Release a reserved struggle slot + its job on a LOCAL send failure (no callback will arrive). Idempotent
     * and token-conditional: only clears the in-flight marker if it still holds THIS token, so it cannot wipe a
     * newer reservation for the same {@code (user, exercise)}.
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
     * Release ONLY the in-flight marker (token-conditional), leaving the job map untouched. Called on the terminal
     * callback AFTER {@code handleDecision} has finished (Task 12): the job-map entry was already removed up front
     * (so the trailing-duplicate callback 403s), but the marker must outlive the session-materialization + persist
     * + push, otherwise a concurrent second trigger could race in and create a duplicate session/bubble (spec §11).
     *
     * @param token      the reserving job token
     * @param userId     the struggling student
     * @param exerciseId the exercise the student is struggling on
     */
    public void releaseStruggleInFlightMarker(String token, long userId, long exerciseId) {
        getStruggleInFlightMap().remove(struggleInFlightKey(userId, exerciseId), token);
    }

    /**
     * Scoped cancel: remove the pending struggle job and its in-flight marker ONLY IF the job's stamped
     * {@code requestToken} equals the provided token. If no pending job exists, or the token does not match,
     * this is an idempotent noop (the slot is left intact).
     *
     * <p>
     * This prevents {@code cancel(A)} from accidentally removing a since-started run B that carries a different
     * token. Run B is only removed by its own scoped cancel or by the normal completion path.
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
        String pendingToken = getStruggleInFlightMap().get(key);
        if (pendingToken == null) {
            return;  // no pending job, idempotent noop
        }
        var job = getPyrisJobMap().get(pendingToken);
        if (!(job instanceof StruggleInterventionJob sij)) {
            return;  // job expired or wrong type, noop
        }
        if (!requestToken.equals(sij.requestToken())) {
            return;  // token mismatch: cancel(A) must never remove a since-started B
        }
        // Scoped match: remove the job entry and the in-flight marker
        getPyrisJobMap().remove(pendingToken);
        getStruggleInFlightMap().remove(key, pendingToken);
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
        getPyrisJobMap().put(token, job, ingestionJobTimeout, TimeUnit.SECONDS);
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
        getPyrisJobMap().put(token, job, ingestionJobTimeout, TimeUnit.SECONDS);
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
        getPyrisJobMap().put(job.jobId(), job, ttl, TimeUnit.SECONDS);
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
        var secureRandom = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            var randomChar = secureRandom.nextInt(62);
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
