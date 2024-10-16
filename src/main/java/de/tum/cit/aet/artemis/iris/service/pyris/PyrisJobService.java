package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.iris.service.pyris.job.IngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

/**
 * The PyrisJobService class is responsible for managing Pyris jobs in the Artemis system.
 * It provides methods for adding, removing, and retrieving Pyris jobs.
 * The class also handles generating job ID tokens and validating tokens from request headers based ont these tokens.
 * It uses Hazelcast to store the jobs in a distributed map.
 */
@Service
@Profile(PROFILE_IRIS)
public class PyrisJobService {

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, PyrisJob> jobMap;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${eureka.instance.instanceId:unknown}")
    private String instanceId;

    @Value("${artemis.iris.jobs.timeout:300}")
    private int jobTimeout; // in seconds

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
        jobMap = hazelcastInstance.getMap("pyris-job-map");
    }

    /**
     * Creates a new job token, runs the provided function with the token as an argument,
     * and registers the resulting job in Hazelcast.
     * The job token is then returned for later reference.
     *
     * @param tokenToJobFunction the function to run with the token
     * @return the generated token
     */
    public String registerJob(Function<String, PyrisJob> tokenToJobFunction) {
        var token = generateJobIdToken();
        var job = tokenToJobFunction.apply(token);
        jobMap.put(token, job);
        return token;
    }

    /**
     * Adds a new ingestion webhook job to the job map with a timeout.
     *
     * @return a unique token identifying the created webhook job
     */
    public String addIngestionWebhookJob() {
        var token = generateJobIdToken();
        var job = new IngestionWebhookJob(token);
        long timeoutWebhookJob = 60;
        TimeUnit unitWebhookJob = TimeUnit.MINUTES;
        jobMap.put(token, job, timeoutWebhookJob, unitWebhookJob);
        return token;
    }

    /**
     * Remove a job from the job map.
     *
     * @param token the token
     */
    public void removeJob(String token) {
        jobMap.remove(token);
    }

    /**
     * Get the job of a token.
     *
     * @param token the token
     * @return the job
     */
    public PyrisJob getJob(String token) {
        return jobMap.get(token);
    }

    /**
     * This method is used to authenticate an incoming request from Pyris.
     * 1. Reads the authentication token from the request headers.
     * 2. Retrieves the PyrisJob object associated with the provided token.
     * 3. Throws an AccessForbiddenException if the token is invalid or not provided.
     * <p>
     * The token was previously generated via {@link #registerJob(Function)}
     *
     * @param request  the HttpServletRequest object representing the incoming request
     * @param jobClass the class of the PyrisJob object to cast the retrieved job to
     * @param <Job>    the type of the PyrisJob object
     * @return the PyrisJob object associated with the token
     * @throws AccessForbiddenException if the token is invalid or not provided
     */
    public <Job extends PyrisJob> Job getAndAuthenticateJobFromHeaderElseThrow(HttpServletRequest request, Class<Job> jobClass) {
        var authHeader = request.getHeader("Authorization");
        if (!authHeader.startsWith("Bearer ")) {
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
