package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TranscriptionIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;

/**
 * The PyrisJobService class is responsible for managing Pyris jobs in the Artemis system.
 * It provides methods for adding, removing, and retrieving Pyris jobs.
 * The class also handles generating job ID tokens and validating tokens from request headers based ont these tokens.
 * It uses Hazelcast to store the jobs in a distributed map.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
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

    @Value("${artemis.iris.jobs.ingestion.timeout:3600}")
    private int ingestionJobTimeout; // in seconds

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

    public String addExerciseChatJob(Long courseId, Long exerciseId, Long sessionId) {
        var token = generateJobIdToken();
        var job = new ExerciseChatJob(token, courseId, exerciseId, sessionId, null, null, null);
        getPyrisJobMap().put(token, job);
        return token;
    }

    public String addCourseChatJob(Long courseId, Long sessionId, Long userMessageId) {
        var token = generateJobIdToken();
        var job = new CourseChatJob(token, courseId, sessionId, null, userMessageId, null);
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
     * Adds a new transcription ingestion webhook job to the job map with a timeout.
     *
     * @param courseId      the ID of the course associated with the webhook job
     * @param lectureId     the ID of the lecture associated with the webhook job
     * @param lectureUnitId the ID of the lecture Unit associated with the webhook job
     * @return a unique token identifying the created webhook job
     */
    public String addTranscriptionIngestionWebhookJob(long courseId, long lectureId, long lectureUnitId) {
        var token = generateJobIdToken();
        var job = new TranscriptionIngestionWebhookJob(token, courseId, lectureId, lectureUnitId);
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
     * Store a job in the job map.
     *
     * @param job the job to store
     */
    public void updateJob(PyrisJob job) {
        getPyrisJobMap().put(job.jobId(), job);
    }

    /**
     * Get all current jobs.
     *
     * @return the all current jobs
     */
    public Collection<PyrisJob> currentJobs() {
        return getPyrisJobMap().values();
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
