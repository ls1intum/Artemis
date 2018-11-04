package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final FileService fileService;
    private final GitService gitService;
    private final VersionControlService versionControlService;
    private final ContinuousIntegrationService continuousIntegrationService;
    private final ContinuousIntegrationUpdateService continuousIntegrationUpdateService;
    private final ResourceLoader resourceLoader;

    public ProgrammingExerciseService(FileService fileService, GitService gitService, VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService,
                                      ContinuousIntegrationUpdateService continuousIntegrationUpdateService, ResourceLoader resourceLoader) {
        this.fileService = fileService;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
        this.resourceLoader = resourceLoader;
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
        String projectKey = programmingExercise.getProjectKey();
        String exerciseRepoName = programmingExercise.getShortName() + "-exercise";
        String testRepoName = programmingExercise.getShortName() + "-tests";
        String solutionRepoName = programmingExercise.getShortName() + "-solution";

        // Create VCS repositories
        versionControlService.createProjectForExercise(programmingExercise); // Create project
        versionControlService.createRepository(projectKey, exerciseRepoName, null); // Create template repository
        versionControlService.createRepository(projectKey, testRepoName, null); // Create tests repository
        versionControlService.createRepository(projectKey, solutionRepoName, null); // Create solution repository

        URL exerciseRepoUrl = versionControlService.getCloneURL(projectKey, exerciseRepoName);
        URL testsRepoUrl = versionControlService.getCloneURL(projectKey, testRepoName);
        URL solutionRepoUrl = versionControlService.getCloneURL(projectKey, solutionRepoName);

        String templatePath = "classpath:templates" + File.separator + programmingExercise.getProgrammingLanguage().toString().toLowerCase();
        Resource templateFolderResource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource(templatePath);
        String absoluteTemplatePath = templateFolderResource.getFile().getAbsolutePath();
        String exerciseTemplatePath = absoluteTemplatePath + File.separator + "exercise"; // Path where the exercise template is located (used for exercise & solution)

        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl);
        setupTemplateAndPush(exerciseRepo, exerciseTemplatePath, "Exercise", programmingExercise);

        String testTemplatePath = absoluteTemplatePath + File.separator + "test"; // Path where the test template is located
        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl);
        setupTemplateAndPush(testRepo, testTemplatePath, "Test", programmingExercise);

        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl);
        setupTemplateAndPush(solutionRepo, exerciseTemplatePath, "Solution", programmingExercise); // Solution is based on the same template as exercise

        // We have to wait to have pushed one commit to each repository as we can only create the buildPlans then (https://confluence.atlassian.com/bamkb/cannot-create-linked-repository-or-plan-repository-942840872.html)
        continuousIntegrationService.createBuildPlanForExercise(programmingExercise, "BASE", exerciseRepoName); // plan for the exercise (students)
        continuousIntegrationService.createBuildPlanForExercise(programmingExercise, "SOLUTION", solutionRepoName); // plan for the solution (instructors) with solution repository

        programmingExercise.setBaseBuildPlanId(projectKey + "-BASE"); // Set build plan id to newly created BaseBuild plan
        programmingExercise.setBaseRepositoryUrl(versionControlService.getCloneURL(projectKey, exerciseRepoName).toString());
        programmingExercise.setSolutionBuildPlanId(projectKey + "-SOLUTION");
        programmingExercise.setSolutionRepositoryUrl(versionControlService.getCloneURL(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.getCloneURL(projectKey, testRepoName).toString());
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
