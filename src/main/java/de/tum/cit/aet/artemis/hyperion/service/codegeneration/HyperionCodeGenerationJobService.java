package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.UUID;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionCodeGenerationJobService {

    private final HyperionCodeGenerationTaskRunner taskRunner;

    public HyperionCodeGenerationJobService(HyperionCodeGenerationTaskRunner taskRunner) {
        this.taskRunner = taskRunner;
    }

    /**
     * Starts a new asynchronous code generation job.
     *
     * @param user           the requesting user
     * @param exercise       the target exercise
     * @param repositoryType the target repository type
     * @return the created job id
     */
    public String startJob(User user, ProgrammingExercise exercise, RepositoryType repositoryType) {
        String jobId = UUID.randomUUID().toString();
        taskRunner.runJobAsync(jobId, user, exercise, repositoryType);
        return jobId;
    }
}
