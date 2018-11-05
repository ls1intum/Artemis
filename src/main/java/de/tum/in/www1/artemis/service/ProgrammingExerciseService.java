package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final FileService fileService;
    private final GitService gitService;
    private final Optional<VersionControlService> versionControlService;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService;
    private final SubmissionRepository submissionRepository;
    private final ParticipationRepository participationRepository;

    private final ResourceLoader resourceLoader;

    public ProgrammingExerciseService(FileService fileService, GitService gitService, Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
                                      Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService, ResourceLoader resourceLoader, SubmissionRepository submissionRepository, ParticipationRepository participationRepository) {
        this.fileService = fileService;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
        this.resourceLoader = resourceLoader;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Notifies all particpation of the given programmingExercise about changes of the test cases.
     *
     * @param programmingExercise The programmingExercise where the test cases got changed
     */
    public void notifyChangedTestCases(ProgrammingExercise programmingExercise, Object requestBody) {
        for (Participation participation : programmingExercise.getParticipations()) {

            ProgrammingSubmission submission = new ProgrammingSubmission();
            submission.setType(SubmissionType.TEST);
            submission.setSubmissionDate(ZonedDateTime.now());
            participation.addSubmissions(submission);
            try {
                String lastCommitHash = versionControlService.get().getLastCommitHash(requestBody);
                submission.setCommitHash(lastCommitHash);
            } catch (Exception e) {
                log.warn("Commit hash could not be parsed for submission from participation " + participation);
            }

            submissionRepository.save(submission);
            participationRepository.save(participation);

            continuousIntegrationUpdateService.get().triggerUpdate(participation.getBuildPlanId(), false);
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
        versionControlService.get().createProjectForExercise(programmingExercise); // Create project
        versionControlService.get().createRepository(projectKey, exerciseRepoName, null); // Create template repository
        versionControlService.get().createRepository(projectKey, testRepoName, null); // Create tests repository
        versionControlService.get().createRepository(projectKey, solutionRepoName, null); // Create solution repository

        URL exerciseRepoUrl = versionControlService.get().getCloneURL(projectKey, exerciseRepoName);
        URL testsRepoUrl = versionControlService.get().getCloneURL(projectKey, testRepoName);
        URL solutionRepoUrl = versionControlService.get().getCloneURL(projectKey, solutionRepoName);

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
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, "BASE", exerciseRepoName); // plan for the exercise (students)
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, "SOLUTION", solutionRepoName); // plan for the solution (instructors) with solution repository

        programmingExercise.setBaseBuildPlanId(projectKey + "-BASE"); // Set build plan id to newly created BaseBuild plan
        programmingExercise.setBaseRepositoryUrl(versionControlService.get().getCloneURL(projectKey, exerciseRepoName).toString());
        programmingExercise.setSolutionBuildPlanId(projectKey + "-SOLUTION");
        programmingExercise.setSolutionRepositoryUrl(versionControlService.get().getCloneURL(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.get().getCloneURL(projectKey, testRepoName).toString());
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
