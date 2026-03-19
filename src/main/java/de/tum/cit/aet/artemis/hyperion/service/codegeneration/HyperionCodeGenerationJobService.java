package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionCodeGenerationJobService {

    private static final String JOB_MAP_NAME = "hyperion-code-generation-jobs";

    private static final String ENTITY_NAME = "hyperionCodeGeneration";

    private static final int JOB_TTL_SECONDS = 3600;

    private final HazelcastInstance hazelcastInstance;

    private final HyperionCodeGenerationTaskService taskService;

    private IMap<String, JobInfo> jobMap;

    public HyperionCodeGenerationJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, HyperionCodeGenerationTaskService taskService) {
        this.hazelcastInstance = hazelcastInstance;
        this.taskService = taskService;
    }

    @PostConstruct
    public void init() {
        MapConfig mapConfig = hazelcastInstance.getConfig().getMapConfig(JOB_MAP_NAME);
        mapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);
    }

    /**
     * Starts a new asynchronous code generation job.
     *
     * @param user           the requesting user
     * @param exercise       the target exercise
     * @param courseId       resolved course id for telemetry attribution
     * @param repositoryType the target repository type
     * @return the created job id
     */
    public String startJob(User user, ProgrammingExercise exercise, Long courseId, RepositoryType repositoryType) {
        JobInfo job = claimJob(user.getLogin(), exercise.getId(), repositoryType);
        String jobId = job.jobId();
        taskService.runJobAsync(jobId, user, exercise, courseId, repositoryType, () -> clearJob(exercise.getId(), jobId));
        return jobId;
    }

    /**
     * Finds an active job for the user and exercise.
     *
     * @param user     the requesting user
     * @param exercise the target exercise
     * @return active job info
     */
    public Optional<JobInfo> getActiveJob(User user, ProgrammingExercise exercise) {
        return getJobForUser(exercise.getId(), user.getLogin());
    }

    /**
     * Claims an exercise-level job slot for a new generation request.
     *
     * @param userLogin      user login requesting the job
     * @param exerciseId     exercise id
     * @param repositoryType repository type for code generation
     * @return the claimed job
     */
    private JobInfo claimJob(String userLogin, long exerciseId, RepositoryType repositoryType) {
        String jobId = UUID.randomUUID().toString();
        JobInfo newJob = new JobInfo(jobId, userLogin, exerciseId, repositoryType, Instant.now());
        JobInfo existing = getJobMap().putIfAbsent(jobKey(exerciseId), newJob);
        if (existing != null) {
            throw new ConflictException("Code generation already running for this exercise", ENTITY_NAME, "codeGenerationRunning");
        }
        return newJob;
    }

    private Optional<JobInfo> getJobForUser(long exerciseId, String userLogin) {
        JobInfo job = getJobMap().get(jobKey(exerciseId));
        if (job == null || !job.userLogin().equals(userLogin)) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    private void clearJob(long exerciseId, String jobId) {
        String key = jobKey(exerciseId);
        JobInfo job = getJobMap().get(key);
        if (job != null && job.jobId().equals(jobId)) {
            // Removal is best-effort: the entry may have been evicted/replaced, and remove(key, value) will no-op in that case.
            getJobMap().remove(key, job);
        }
    }

    private IMap<String, JobInfo> getJobMap() {
        return jobMap;
    }

    private static String jobKey(long exerciseId) {
        return String.valueOf(exerciseId);
    }

    public record JobInfo(String jobId, String userLogin, long exerciseId, RepositoryType repositoryType, Instant startedAt) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
