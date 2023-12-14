package de.tum.in.www1.artemis.service.connectors.localci;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;

/**
 * Service for triggering builds on the local CI system.
 */
@Service
@Profile("localci")
public class LocalCITriggerService implements ContinuousIntegrationTriggerService {

    private final LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService;

    private final LocalCIProgrammingLanguageFeatureService localCIProgrammingLanguageFeatureService;

    public LocalCITriggerService(LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService,
            LocalCIProgrammingLanguageFeatureService localCIProgrammingLanguageFeatureService) {
        this.localCISharedBuildJobQueueService = localCISharedBuildJobQueueService;
        this.localCIProgrammingLanguageFeatureService = localCIProgrammingLanguageFeatureService;
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

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash) throws ContinuousIntegrationException {
        triggerBuild(participation, commitHash, false);
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
        ProgrammingLanguage programmingLanguage = programmingExercise.getProgrammingLanguage();
        ProjectType projectType = programmingExercise.getProjectType();
        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        // Exam exercises have a higher priority than normal exercises
        int priority = programmingExercise.isExamExercise() ? 1 : 2;

        localCISharedBuildJobQueueService.addBuildJob(participation.getBuildPlanId(), participation.getId(), commitHash, System.currentTimeMillis(), priority, courseId,
                isPushToTestRepository);
    }

}
