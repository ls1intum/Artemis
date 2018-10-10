package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URL;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final GitService gitService;
    private final VersionControlService versionControlService;
    private final ContinuousIntegrationService continuousIntegrationService;
    private final ContinuousIntegrationUpdateService continuousIntegrationUpdateService;

    public ProgrammingExerciseService(GitService gitService, VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService,
                                      ContinuousIntegrationUpdateService continuousIntegrationUpdateService) {
        this.gitService = gitService;
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

        versionControlService.createRepository(programmingExercise.getVCSProjectKey(), programmingExercise.getShortName(), null); // Create exercise repository
        versionControlService.createRepository(programmingExercise.getVCSProjectKey(), "tests", null); // Create tests repository
        versionControlService.createRepository(programmingExercise.getVCSProjectKey(), "solution", null); // Create solution repository

        // Permissions
        Course course = programmingExercise.getCourse();
        versionControlService.grantProjectPermissions(programmingExercise.getVCSProjectKey(), course.getInstructorGroupName(), course.getTeachingAssistantGroupName()); // TODO: do we have to check if the client changed some values in the course object?

        // Add a file to the repositories
        URL exerciseRepoUrl = versionControlService.getCloneURL(programmingExercise.getVCSProjectKey(), programmingExercise.getShortName());
        URL testsRepoUrl = versionControlService.getCloneURL(programmingExercise.getVCSProjectKey(), "tests");
        URL solutionRepoUrl = versionControlService.getCloneURL(programmingExercise.getVCSProjectKey(), "solution");

        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl);
        File exerciseRepoFile = new File(exerciseRepo.getLocalPath().toFile(), ".gitkeep");
        exerciseRepoFile.createNewFile();
        gitService.stageAllChanges(exerciseRepo);
        gitService.commitAndPush(exerciseRepo, "Exercise-Template pushed by ArTEMiS");

        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl);
        File testRepoFile = new File(testRepo.getLocalPath().toFile(), ".gitkeep");
        testRepoFile.createNewFile();
        gitService.stageAllChanges(testRepo);
        gitService.commitAndPush(testRepo, "Test-Template pushed by ArTEMiS");

        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl);
        File solutionRepoFile = new File(solutionRepo.getLocalPath().toFile(), ".gitkeep");
        solutionRepoFile.createNewFile();
        gitService.stageAllChanges(solutionRepo);
        gitService.commitAndPush(solutionRepo, "Solution-Template pushed by ArTEMiS");

        // We have to wait to have pushed one commit to each repository as we can only create the buildPlans then (https://confluence.atlassian.com/bamkb/cannot-create-linked-repository-or-plan-repository-942840872.html)
        continuousIntegrationService.createProject(programmingExercise.getCIProjectKey());
        continuousIntegrationService.createBaseBuildPlanForExercise(programmingExercise, "BASE", programmingExercise.getShortName()); // plan for the exercise (students)
        continuousIntegrationService.createBaseBuildPlanForExercise(programmingExercise, "SOLUTION", "solution"); // plan for the solution (instructors) with solution repository // TODO: check if hardcoding is ok

        continuousIntegrationService.grantProjectPermissions(programmingExercise.getCIProjectKey(), course.getInstructorGroupName(), course.getTeachingAssistantGroupName());
    }

}
