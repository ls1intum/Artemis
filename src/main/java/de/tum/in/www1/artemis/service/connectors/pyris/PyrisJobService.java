package de.tum.in.www1.artemis.service.connectors.pyris;

import java.security.SecureRandom;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.service.connectors.pyris.job.PyrisJob;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
@Profile("iris")
public class PyrisJobService {

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, PyrisJob> jobMap;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${eureka.instance.instanceId:unknown}")
    private String instanceId;

    @Value("${artemis.iris.jobs.timeout:300}")
    private int jobTimeout; // in seconds

    public PyrisJobService(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Add listeners for build job queue changes.
     */
    @PostConstruct
    public void init() {
        var mapConfig = hazelcastInstance.getConfig().getMapConfig("pyris-job-map");
        mapConfig.setTimeToLiveSeconds(jobTimeout);
        jobMap = hazelcastInstance.getMap("pyris-job-map");
    }

    /**
     * Add a job to the job map.
     *
     * @param job the job
     * @return the token
     */
    public String addJob(PyrisJob job) {
        var token = generateJobIdToken();
        job.setId(token);
        jobMap.put(token, job);
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
     * Get the job from the request headers. Throws an exception if the token is invalid.
     *
     * @param request the request
     * @return the job
     */
    public PyrisJob getJobFromHeader(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (!authHeader.startsWith("Bearer ")) {
            throw new AccessForbiddenException("No valid token provided");
        }
        var token = authHeader.substring(7);
        var job = getJob(token);
        if (job == null) {
            throw new AccessForbiddenException("No valid token provided");
        }
        return job;
    }

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
