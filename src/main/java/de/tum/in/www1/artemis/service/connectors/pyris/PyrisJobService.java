package de.tum.in.www1.artemis.service.connectors.pyris;

import java.security.SecureRandom;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.service.connectors.pyris.job.PyrisJob;

@Service
public class PyrisJobService {

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, PyrisJob> jobMap;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${eureka.instance.instanceId:unknown}")
    private String instanceId;

    @Value("${artemis.iris.jobs.timeout:300}")
    private int jobTimeout; // in seconds

    @Value("${artemis.iris.jobs.git-username:pyris-fake-git-user}")
    private String gitUsername;

    @Value("${artemis.iris.jobs.git-password}")
    private Optional<String> gitPassword;

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

    public String addJob(PyrisJob job) {
        var token = generateJobIdToken();
        jobMap.put(token, job);
        return token;
    }

    public void removeJob(String token) {
        jobMap.remove(token);
    }

    public PyrisJob getJob(String token) {
        return jobMap.get(token);
    }

    public String getGitUsername() {
        return gitUsername;
    }

    public String getGitPasswordForJob(String token) {
        return gitPassword.orElse(token);
    }

    private String generateJobIdToken() {
        // Include instance name, node id, timestamp and random string
        var randomStringBuilder = new StringBuilder();
        randomStringBuilder.append(serverUrl);
        randomStringBuilder.append(instanceId);
        randomStringBuilder.append(System.currentTimeMillis());
        var secureRandom = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            var randomChar = secureRandom.nextInt(62);
            if (randomChar < 10) {
                randomStringBuilder.append(randomChar);
            }
            else if (randomChar < 36) {
                randomStringBuilder.append((char) (randomChar + 'a'));
            }
            else {
                randomStringBuilder.append((char) (randomChar + 'A'));
            }
        }
        return randomStringBuilder.toString();
    }
}
