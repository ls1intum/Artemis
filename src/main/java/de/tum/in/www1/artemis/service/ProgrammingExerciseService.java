package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationUpdateService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.config.Constants.TEST_CASE_CHANGED_API_PATH;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;
    private final FileService fileService;
    private final GitService gitService;
    private final Optional<VersionControlService> versionControlService;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService;
    private final SubmissionRepository submissionRepository;
    private final ParticipationRepository participationRepository;

    private final ResourceLoader resourceLoader;

    @Value("${server.url}")
    private String ARTEMIS_BASE_URL;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService, Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
                                      Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService, ResourceLoader resourceLoader, SubmissionRepository submissionRepository, ParticipationRepository participationRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
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
            submission.setSubmitted(true);
            submission.setParticipation(participation);
            try {
                String lastCommitHash = versionControlService.get().getLastCommitHash(requestBody);
                log.info("create new programmingSubmission with commitHash: " + lastCommitHash);
                submission.setCommitHash(lastCommitHash);
            } catch (Exception ex) {
                log.error("Commit hash could not be parsed for submission from participation " + participation, ex);
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
    public ProgrammingExercise setupProgrammingExercise(ProgrammingExercise programmingExercise) throws Exception {
        String projectKey = programmingExercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-exercise";
        String testRepoName = projectKey.toLowerCase() + "-tests";
        String solutionRepoName = projectKey.toLowerCase() + "-solution";

        // Create VCS repositories
        versionControlService.get().createProjectForExercise(programmingExercise); // Create project
        versionControlService.get().createRepository(projectKey, exerciseRepoName, null); // Create template repository
        versionControlService.get().createRepository(projectKey, testRepoName, null); // Create tests repository
        versionControlService.get().createRepository(projectKey, solutionRepoName, null); // Create solution repository

        Participation templateParticipation = programmingExercise.getTemplateParticipation();
        if (templateParticipation == null) {
            templateParticipation = new Participation();
            programmingExercise.setTemplateParticipation(templateParticipation);
        }
        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        if (solutionParticipation == null) {
            solutionParticipation = new Participation();
            programmingExercise.setSolutionParticipation(solutionParticipation);
        }

        solutionParticipation.setInitializationState(InitializationState.INITIALIZED);
        templateParticipation.setInitializationState(InitializationState.INITIALIZED);
        solutionParticipation.setInitializationDate(ZonedDateTime.now());
        templateParticipation.setInitializationDate(ZonedDateTime.now());

        templateParticipation.setBuildPlanId(projectKey + "-BASE"); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneURL(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(projectKey + "-SOLUTION");
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneURL(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.get().getCloneURL(projectKey, testRepoName).toString());

        // The creation of the webhooks must occur before the initial push to ensure that the initial commit creates a result
        versionControlService.get().addWebHook(templateParticipation.getRepositoryUrlAsUrl(), ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + templateParticipation.getId(), "ArTEMiS WebHook");
        versionControlService.get().addWebHook(solutionParticipation.getRepositoryUrlAsUrl(), ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + solutionParticipation.getId(), "ArTEMiS WebHook");

        URL exerciseRepoUrl = versionControlService.get().getCloneURL(projectKey, exerciseRepoName);
        URL testsRepoUrl = versionControlService.get().getCloneURL(projectKey, testRepoName);
        URL solutionRepoUrl = versionControlService.get().getCloneURL(projectKey, solutionRepoName);

        String programmingLanguage = programmingExercise.getProgrammingLanguage().toString().toLowerCase();

        String templatePath = "classpath:templates/java";
        String exercisePath = templatePath + "/exercise/**/*.*";
        String solutionPath = templatePath + "/solution/**/*.*";
        String testPath = templatePath + "/test/**/*.*";

        Resource[] exerciseResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(exercisePath);
        Resource[] testResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(testPath);
        Resource[] solutionResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(solutionPath);

        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl);
        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl);
        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl);

        try {
            String exercisePrefix = programmingLanguage + File.separator + "exercise";
            String testPrefix = programmingLanguage + File.separator + "test";
            String solutionPrefix = programmingLanguage + File.separator + "solution";
            setupTemplateAndPush(exerciseRepo, exerciseResources, exercisePrefix,"Exercise", programmingExercise);
            setupTemplateAndPush(testRepo, testResources, testPrefix,"Test", programmingExercise);
            setupTemplateAndPush(solutionRepo, solutionResources, solutionPrefix,"Solution", programmingExercise);

        } catch (Exception ex) {
            //if any exception occurs, try to at least push an empty commit, so that the repositories can be used by the build plans
            log.warn("An exception occurred while setting up the repositories", ex);
            gitService.commitAndPush(exerciseRepo, "Setup");
            gitService.commitAndPush(testRepo, "Setup");
            gitService.commitAndPush(solutionRepo, "Setup");
        }
        // We have to wait to have pushed one commit to each repository as we can only create the buildPlans then (https://confluence.atlassian.com/bamkb/cannot-create-linked-repository-or-plan-repository-942840872.html)
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, "BASE", exerciseRepoName, testRepoName); // plan for the exercise (students)
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, "SOLUTION", solutionRepoName, testRepoName); // plan for the solution (instructors) with solution repository

        // save to get the id required for the webhook
        participationRepository.save(templateParticipation);
        participationRepository.save(solutionParticipation);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        versionControlService.get().addWebHook(testsRepoUrl, ARTEMIS_BASE_URL + TEST_CASE_CHANGED_API_PATH + programmingExercise.getId(), "ArTEMiS Tests WebHook");
        return programmingExercise;
    }

    // Copy template and push, if no file is in the directory
    private void setupTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise) throws Exception {
        if (gitService.listFiles(repository).size() == 0) { // Only copy template if repo is empty
            fileService.copyResources(resources, prefix, repository.getLocalPath().toAbsolutePath().toString());
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

            //TODO: for some reason, this code does not replace the elements in the file test.json

            fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), fileTargets, fileReplacements);

            gitService.stageAllChanges(repository);
            gitService.commitAndPush(repository, templateName + "-Template pushed by ArTEMiS");
            repository.setFiles(null); // Clear cache to avoid multiple commits when ArTEMiS server is not restarted between attempts
        }
    }

    /**
     * migrates the programming exercises to use templateParticipation and solutionParticipation
     */
    public void migrateAllProgrammingExercises() {

        List<ProgrammingExercise> allProgrammingExercises = programmingExerciseRepository.findAll();

        for (ProgrammingExercise programmingExercise : allProgrammingExercises) {
            if (programmingExercise.getTemplateParticipation() == null) {
                Participation templateParticipation = new Participation();
                templateParticipation.setRepositoryUrl(programmingExercise.getTemplateRepositoryUrlOld());
                templateParticipation.setBuildPlanId(programmingExercise.getTemplateBuildPlanIdOld());
                programmingExercise.setTemplateParticipation(templateParticipation);
                programmingExercise.setTemplateRepositoryUrlOld(null);
                programmingExercise.setTemplateBuildPlanIdOld(null);
                participationRepository.save(templateParticipation);
                programmingExerciseRepository.save(programmingExercise);
            }
            if (programmingExercise.getSolutionParticipation() == null) {
                Participation solutionParticipation = new Participation();
                solutionParticipation.setRepositoryUrl(programmingExercise.getSolutionRepositoryUrlOld());
                solutionParticipation.setBuildPlanId(programmingExercise.getSolutionBuildPlanIdOld());
                programmingExercise.setSolutionParticipation(solutionParticipation);
                programmingExercise.setSolutionRepositoryUrlOld(null);
                programmingExercise.setSolutionBuildPlanIdOld(null);
                participationRepository.save(solutionParticipation);
                programmingExerciseRepository.save(programmingExercise);
            }
        }
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the template Participation
     *
     * @param participation The template participation
     * @return The ProgrammingExercise where the given Participation is the template Participation
     */
    public ProgrammingExercise getExerciseForTemplateParticipation(Participation participation) {
        return programmingExerciseRepository.findOneByTemplateParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution Participation
     *
     * @param participation The solution participation
     * @return The ProgrammingExercise where the given Participation is the solution Participation
     */
    public ProgrammingExercise getExerciseForSolutionParticipation(Participation participation) {
        return programmingExerciseRepository.findOneBySolutionParticipationId(participation.getId());
    }

    /**
     * This methods sets the values (initialization date and initialization state) of the template and solution participation
     *
     * @param programmingExercise The programming exercise
     */
    public void initParticipations(ProgrammingExercise programmingExercise) {

        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        Participation templateParticipation = programmingExercise.getTemplateParticipation();

        solutionParticipation.setInitializationState(InitializationState.INITIALIZED);
        templateParticipation.setInitializationState(InitializationState.INITIALIZED);
        solutionParticipation.setInitializationDate(ZonedDateTime.now());
        templateParticipation.setInitializationDate(ZonedDateTime.now());
    }

    /**
     * This method saves the participations of the programming xercise
     *
     * @param programmingExercise The programming exercise
     */
    public void saveParticipations(ProgrammingExercise programmingExercise) {
        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        Participation templateParticipation = programmingExercise.getTemplateParticipation();

        participationRepository.save(solutionParticipation);
        participationRepository.save(templateParticipation);
    }
}
