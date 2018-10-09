package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final VersionControlService versionControlService;
    private final ContinuousIntegrationService continuousIntegrationService;
    private final ContinuousIntegrationUpdateService continuousIntegrationUpdateService;

    public ProgrammingExerciseService(VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService,
                                      ContinuousIntegrationUpdateService continuousIntegrationUpdateService) {
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
    }

    /**
     * Notifies all particpation of the given programmingExercise about changes of the test cases.
     *
     * @param programmingExercise The programmingExercise where the test cases got changed
     */
    public void notifyChangedTestCases(ProgrammingExercise programmingExercise) {
        for (Participation participation : programmingExercise.getParticipations()) {
            continuousIntegrationUpdateService.triggerUpdate(participation.getBuildPlanId(), false);
        }
    }

    /**
     * Setups all needed repositories etc. for the given programmingExercise.
     *
     * @param programmingExercise The programmingExercise that should be setup
     */
    public void setupProgrammingExercise(ProgrammingExercise programmingExercise) throws Exception {
        versionControlService.createProject(programmingExercise.getTitle(), programmingExercise.getVCSProjectKey()); // Create project

        versionControlService.createRepository(programmingExercise.getShortName(), programmingExercise.getVCSProjectKey(), null); // Create exercise repository
        versionControlService.createRepository("tests", programmingExercise.getVCSProjectKey(), null); // Create tests repository
        versionControlService.createRepository("solution", programmingExercise.getVCSProjectKey(), null); // Create solution repository

        continuousIntegrationService.createProject(programmingExercise.getCIProjectKey());
        continuousIntegrationService.createBaseBuildPlanForExercise(programmingExercise, programmingExercise.getShortName()); // plan for the exercise (students)
        continuousIntegrationService.createBaseBuildPlanForExercise(programmingExercise, "solution"); // plan for the solution (instructors) with solution repository // TODO: check if hardcoding is ok

        // Permissions
        Course course = programmingExercise.getCourse();
        versionControlService.grantProjectPermissions(programmingExercise.getVCSProjectKey(), course.getInstructorGroupName(), course.getTeachingAssistantGroupName());
        continuousIntegrationService.grantProjectPermissions(programmingExercise.getCIProjectKey(), course.getInstructorGroupName(), course.getTeachingAssistantGroupName());
    }

}
