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
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final FileService fileService;
    private final GitService gitService;
    private final VersionControlService versionControlService;
    private final ContinuousIntegrationService continuousIntegrationService;
    private final ContinuousIntegrationUpdateService continuousIntegrationUpdateService;

    public ProgrammingExerciseService(FileService fileService, GitService gitService, VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService,
                                      ContinuousIntegrationUpdateService continuousIntegrationUpdateService) {
        this.fileService = fileService;
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

        URL exerciseRepoUrl = versionControlService.getCloneURL(programmingExercise.getVCSProjectKey(), programmingExercise.getShortName());
        URL testsRepoUrl = versionControlService.getCloneURL(programmingExercise.getVCSProjectKey(), "tests");
        URL solutionRepoUrl = versionControlService.getCloneURL(programmingExercise.getVCSProjectKey(), "solution");

        String templatePath = System.getProperty("user.dir") + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "config" + File.separator + "templates"; // TODO: check if this works
        String frameworkPath = templatePath + File.separator + programmingExercise.getProgrammingLanguage().toLowerCase() + File.separator + programmingExercise.getBuildTool().toLowerCase(); // Path, depending on programming language and build tools

        String exerciseTemplatePath = frameworkPath + File.separator + "exercise"; // Path where the exercise template is located (used for exercise & solution)

        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl);
        setupTemplateAndPush(exerciseRepo, exerciseTemplatePath, "Exercise", programmingExercise);

        String testTemplatePath = frameworkPath + File.separator + "test"; // Path where the test template is located
        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl);
        setupTemplateAndPush(testRepo, testTemplatePath, "Test", programmingExercise);

        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl);
        setupTemplateAndPush(solutionRepo, exerciseTemplatePath, "Solution", programmingExercise); // Solution is based on the same template as exercise

        // We have to wait to have pushed one commit to each repository as we can only create the buildPlans then (https://confluence.atlassian.com/bamkb/cannot-create-linked-repository-or-plan-repository-942840872.html)
        continuousIntegrationService.createProject(programmingExercise.getCIProjectKey());
        continuousIntegrationService.createBaseBuildPlanForExercise(programmingExercise, "BASE", programmingExercise.getShortName()); // plan for the exercise (students)
        continuousIntegrationService.createBaseBuildPlanForExercise(programmingExercise, "SOLUTION", "solution"); // plan for the solution (instructors) with solution repository // TODO: check if hardcoding is ok

        continuousIntegrationService.grantProjectPermissions(programmingExercise.getCIProjectKey(), course.getInstructorGroupName(), course.getTeachingAssistantGroupName());
        programmingExercise.setBaseBuildPlanId(programmingExercise.getCIProjectKey() + "-BASE"); // Set build plan id to newly created BaseBuild plan
        programmingExercise.setBaseRepositoryUrl(versionControlService.getCloneURL(programmingExercise.getVCSProjectKey(), programmingExercise.getShortName()).toString());
    }

    // Copy template and push, if no file is in the directory
    private void setupTemplateAndPush(Repository repository, String templatePath, String templateName, ProgrammingExercise programmingExercise) throws Exception {
        if (gitService.listFiles(repository).size() == 0) { // Only copy template if repo is empty
            fileService.copyDirectory(templatePath, repository.getLocalPath().toAbsolutePath().toString());
            fileService.replaceVariablesInDirectoryName(repository.getLocalPath().toAbsolutePath().toString(), "${packageNameFolder}", programmingExercise.getPackageFolderName());

            List<String> fileTargets = new ArrayList<>();
            List<String> fileReplacements = new ArrayList<>();
            // This is based on the correct order and assumes that boths lists have the same length, it replaces fileTargets.get(i) with fileReplacements.get(i)

            fileTargets.add("${packageName}");
            fileReplacements.add(programmingExercise.getPackageName());

            fileTargets.add("${exerciseNameCompact}");
            fileReplacements.add(programmingExercise.getShortName().toLowerCase()); // Used e.g. in artifactId

            fileTargets.add("${exerciseName}");
            fileReplacements.add(programmingExercise.getTitle());

            fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), fileTargets, fileReplacements);

            gitService.stageAllChanges(repository);
            gitService.commitAndPush(repository, templateName + "-Template pushed by ArTEMiS");
            repository.setFiles(null); // Clear cache to avoid multiple commits when ArTEMiS server is not restarted between attempts
        }
    }

}
