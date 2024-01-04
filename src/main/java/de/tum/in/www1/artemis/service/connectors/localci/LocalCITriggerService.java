package de.tum.in.www1.artemis.service.connectors.localci;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;

/**
 * Service for triggering builds on the local CI system.
 */
@Service
@Primary // only needed for the bamboo to local ci migration
@Profile("localci")
public class LocalCITriggerService implements ContinuousIntegrationTriggerService {

    private final LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService;

    public LocalCITriggerService(LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService) {
        this.localCISharedBuildJobQueueService = localCISharedBuildJobQueueService;
    }

    /**
     * Add a new build job to the queue managed by the ExecutorService and process the returned result.
     *
     * @param participation the participation of the repository which should be built and tested.
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws LocalCIException {
        triggerBuild(participation, null, false);
    }

    /**
     * Add a new build job for a specific commit to the queue managed by the ExecutorService and process the returned result.
     *
     * @param participation the participation of the repository which should be built and tested
     * @param commitHash    the commit hash of the commit that triggers the build. If it is null, the latest commit of the default branch will be built.
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, boolean isPushToTestRepository) throws LocalCIException {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        String repositoryTypeOrUserName = participation.getVcsRepositoryUrl().repositoryNameWithoutProjectKey();

        if (Objects.equals(repositoryTypeOrUserName, "exercise")) {
            repositoryTypeOrUserName = "BASE";
        }
        else if (Objects.equals(repositoryTypeOrUserName, "solution")) {
            repositoryTypeOrUserName = repositoryTypeOrUserName.toUpperCase();
        }

        // Exam exercises have a higher priority than normal exercises
        int priority = programmingExercise.isExamExercise() ? 1 : 2;

        localCISharedBuildJobQueueService.addBuildJob(participation.getBuildPlanId(), participation.getId(), repositoryTypeOrUserName, commitHash, ZonedDateTime.now(), priority,
                courseId, isPushToTestRepository);
    }
}
