package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.SETUP_COMMIT_MESSAGE;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.*;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.util.structureoraclegenerator.OracleGenerator;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final FileService fileService;

    private final GitService gitService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ParticipationService participationService;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    private final ResourceLoaderService resourceLoaderService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ResultRepository resultRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService,
            Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ParticipationService participationService,
            ResultRepository resultRepository, UserRepository userRepository, AuthorizationCheckService authCheckService, ResourceLoaderService resourceLoaderService,
            GroupNotificationService groupNotificationService, InstanceMessageSendService instanceMessageSendService, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.resourceLoaderService = resourceLoaderService;
        this.groupNotificationService = groupNotificationService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
    }

    /**
     * Setups the context of a new programming exercise. This includes:
     * <ul>
     *     <li>The VCS project</li>
     *     <li>All repositories (test, exercise, solution)</li>
     *     <li>The template and solution participation</li>
     *     <li>VCS webhooks</li>
     *     <li>Bamboo build plans</li>
     * </ul>
     *
     * The exercise gets set up in the following order:
     * <ol>
     *     <li>Create all repositories for the new exercise</li>
     *     <li>Setup template and push it to the repositories</li>
     *     <li>Setup new build plans for exercise</li>
     *     <li>Add all webhooks</li>
     *     <li>Init scheduled jobs for exercise maintenance</li>
     * </ol>
     *
     * @param programmingExercise The programmingExercise that should be setup
     * @return The newly setup exercise
     * @throws InterruptedException If something during the communication with the remote Git repository went wrong
     * @throws GitAPIException      If something during the communication with the remote Git repository went wrong
     * @throws IOException          If the template files couldn't be read
     */
    @Transactional // ok because we create many objects in a rather complex way and need a rollback in case of exceptions
    public ProgrammingExercise createProgrammingExercise(ProgrammingExercise programmingExercise) throws InterruptedException, GitAPIException, IOException {
        programmingExercise.generateAndSetProjectKey();
        final User user = userRepository.getUser();

        createRepositoriesForNewExercise(programmingExercise);
        initParticipations(programmingExercise);
        setURLsAndBuildPlanIDsForNewExercise(programmingExercise);

        // Save participations to get the ids required for the webhooks
        connectBaseParticipationsToExerciseAndSave(programmingExercise);

        connectAuxiliaryRepositoriesToExercise(programmingExercise);

        setupExerciseTemplate(programmingExercise, user);

        // Save programming exercise to prevent transient exception
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        setupBuildPlansForNewExercise(programmingExercise);

        // save to get the id required for the webhook
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        // The creation of the webhooks must occur after the initial push, because the participation is
        // not yet saved in the database, so we cannot save the submission accordingly (see ProgrammingSubmissionService.notifyPush)
        versionControlService.get().addWebHooksForExercise(programmingExercise);

        scheduleOperations(programmingExercise.getId());

        // Notify tutors only if this a course exercise
        if (programmingExercise.isCourseExercise()) {
            groupNotificationService.notifyTutorGroupAboutExerciseCreated(programmingExercise);
        }

        return programmingExercise;
    }

    public void scheduleOperations(Long programmingExerciseId) {
        instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExerciseId);
    }

    public void cancelScheduledOperations(Long programmingExerciseId) {
        instanceMessageSendService.sendProgrammingExerciseScheduleCancel(programmingExerciseId);
    }

    /**
     * Creates build plans for a new programming exercise.
     * 1. Create the project for the exercise on the CI Server
     * 2. Create template and solution build plan in this project
     * 3. Configure CI permissions
     *
     * @param programmingExercise Programming exercise for the the build plans should be generated. The programming
     *                            exercise should contain a fully initialized template and solution participation.
     */
    public void setupBuildPlansForNewExercise(ProgrammingExercise programmingExercise) {
        String projectKey = programmingExercise.getProjectKey();
        // Get URLs for repos
        var exerciseRepoUrl = programmingExercise.getVcsTemplateRepositoryUrl();
        var testsRepoUrl = programmingExercise.getVcsTestRepositoryUrl();
        var solutionRepoUrl = programmingExercise.getVcsSolutionRepositoryUrl();

        continuousIntegrationService.get().createProjectForExercise(programmingExercise);
        // template build plan
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUrl, testsRepoUrl, solutionRepoUrl);
        // solution build plan
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, SOLUTION.getName(), solutionRepoUrl, testsRepoUrl, solutionRepoUrl);

        // Give appropriate permissions for CI projects
        continuousIntegrationService.get().removeAllDefaultProjectPermissions(projectKey);

        giveCIProjectPermissions(programmingExercise);
    }

    /**
     * This method connects the new programming exercise with the template and solution participation
     *
     * @param programmingExercise the new programming exercise
     */
    public void connectBaseParticipationsToExerciseAndSave(ProgrammingExercise programmingExercise) {
        var templateParticipation = programmingExercise.getTemplateParticipation();
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        templateParticipation.setProgrammingExercise(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        templateParticipation = templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionParticipation = solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        programmingExercise.setTemplateParticipation(templateParticipation);
        programmingExercise.setSolutionParticipation(solutionParticipation);
    }

    private void connectAuxiliaryRepositoriesToExercise(ProgrammingExercise exercise) {
        List<AuxiliaryRepository> savedRepositories = new ArrayList<>(exercise.getAuxiliaryRepositories().stream().filter(repo -> repo.getId() != null).toList());
        exercise.getAuxiliaryRepositories().stream().filter(repository -> repository.getId() == null).forEach(repository -> {
            // We have to disconnect the exercise from the auxiliary repository
            // since the auxiliary repositories of an exercise are represented as
            // a sorted collection (list).
            repository.setExercise(null);
            repository = auxiliaryRepositoryRepository.save(repository);
            repository.setExercise(exercise);
            savedRepositories.add(repository);
        });
        exercise.setAuxiliaryRepositories(savedRepositories);
    }

    private void setURLsAndBuildPlanIDsForNewExercise(ProgrammingExercise programmingExercise) {
        final var projectKey = programmingExercise.getProjectKey();
        final var templateParticipation = programmingExercise.getTemplateParticipation();
        final var solutionParticipation = programmingExercise.getSolutionParticipation();
        final var templatePlanId = programmingExercise.generateBuildPlanId(TEMPLATE);
        final var solutionPlanId = programmingExercise.generateBuildPlanId(SOLUTION);
        final var exerciseRepoName = programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = programmingExercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = programmingExercise.generateRepositoryName(RepositoryType.TESTS);

        templateParticipation.setBuildPlanId(templatePlanId); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(solutionPlanId);
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, testRepoName).toString());
    }

    private void setURLsForAuxiliaryRepositoriesOfExercise(ProgrammingExercise programmingExercise) {
        programmingExercise.getAuxiliaryRepositories().forEach(repo -> repo.setRepositoryUrl(
                versionControlService.get().getCloneRepositoryUrl(programmingExercise.getProjectKey(), programmingExercise.generateRepositoryName(repo.getName())).toString()));
    }

    /**
     * Setup the exercise template by determining the files needed for the template and copying them.
     *
     * @param programmingExercise the programming exercise that should be set up
     * @param user                the User that performed the action (used as Git commit author)
     */
    private void setupExerciseTemplate(ProgrammingExercise programmingExercise, User user) throws GitAPIException, InterruptedException {

        // Get URLs for repos
        var exerciseRepoUrl = programmingExercise.getVcsTemplateRepositoryUrl();
        var testsRepoUrl = programmingExercise.getVcsTestRepositoryUrl();
        var solutionRepoUrl = programmingExercise.getVcsSolutionRepositoryUrl();

        // Checkout repositories
        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl, true);
        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl, true);
        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl, true);

        // Get path, files and prefix for the programming-language dependent files. They are copied first.
        String programmingLanguage = programmingExercise.getProgrammingLanguage().toString().toLowerCase();
        String programmingLanguageTemplate = getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage());
        String exercisePath = programmingLanguageTemplate + "/exercise/**/*.*";
        String solutionPath = programmingLanguageTemplate + "/solution/**/*.*";
        String testPath = programmingLanguageTemplate + "/test/**/*.*";

        Resource[] exerciseResources = resourceLoaderService.getResources(exercisePath);
        Resource[] testResources = resourceLoaderService.getResources(testPath);
        Resource[] solutionResources = resourceLoaderService.getResources(solutionPath);

        String exercisePrefix = programmingLanguage + "/exercise";
        String testPrefix = programmingLanguage + "/test";
        String solutionPrefix = programmingLanguage + "/solution";

        // Initialize project type dependent resources with null as they might not be used
        Resource[] projectTypeExerciseResources = null;
        Resource[] projectTypeTestResources = null;
        Resource[] projectTypeSolutionResources = null;

        String projectTypeExercisePrefix = null;
        String projectTypeTestPrefix = null;
        String projectTypeSolutionPrefix = null;

        // Find the project type specific files if present
        if (programmingExercise.getProjectType() != null && !ProjectType.PLAIN.equals(programmingExercise.getProjectType())) {
            // Get path, files and prefix for the project-type dependent files. They are copied last and can overwrite the resources from the programming language.
            String programmingLanguageProjectTypePath = getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), programmingExercise.getProjectType());
            String projectType = programmingExercise.getProjectType().name().toLowerCase();
            String projectTypePrefix = programmingLanguage + "/" + projectType;

            exercisePath = programmingLanguageProjectTypePath + "/exercise/**/*.*";
            solutionPath = programmingLanguageProjectTypePath + "/solution/**/*.*";
            testPath = programmingLanguageProjectTypePath + "/test/**/*.*";

            if (ProjectType.XCODE.equals(programmingExercise.getProjectType())) {
                // For Xcode we don't share source code, so we only copy files once
                exercisePrefix = projectTypePrefix + "/exercise";
                testPrefix = projectTypePrefix + "/test";
                solutionPrefix = projectTypePrefix + "/solution";

                exerciseResources = resourceLoaderService.getResources(exercisePath);
                testResources = resourceLoaderService.getResources(testPath);
                solutionResources = resourceLoaderService.getResources(solutionPath);
            }
            else {
                projectTypeExercisePrefix = projectTypePrefix + "/exercise";
                projectTypeTestPrefix = projectTypePrefix + "/test";
                projectTypeSolutionPrefix = projectTypePrefix + "/solution";

                projectTypeExerciseResources = resourceLoaderService.getResources(exercisePath);
                projectTypeTestResources = resourceLoaderService.getResources(testPath);
                projectTypeSolutionResources = resourceLoaderService.getResources(solutionPath);
            }
        }

        try {
            setupTemplateAndPush(exerciseRepo, exerciseResources, exercisePrefix, projectTypeExerciseResources, projectTypeExercisePrefix, "Exercise", programmingExercise, user);
            // The template repo can be re-written so we can unprotect the default branch.
            var templateVcsRepositoryUrl = programmingExercise.getVcsTemplateRepositoryUrl();
            String templateVcsRepositoryDefaultBranch = versionControlService.get().getDefaultBranchOfRepository(templateVcsRepositoryUrl);
            versionControlService.get().unprotectBranch(templateVcsRepositoryUrl, templateVcsRepositoryDefaultBranch);

            setupTemplateAndPush(solutionRepo, solutionResources, solutionPrefix, projectTypeSolutionResources, projectTypeSolutionPrefix, "Solution", programmingExercise, user);
            setupTestTemplateAndPush(testRepo, testResources, testPrefix, projectTypeTestResources, projectTypeTestPrefix, "Test", programmingExercise, user);

        }
        catch (Exception ex) {
            // if any exception occurs, try to at least push an empty commit, so that the
            // repositories can be used by the build plans
            log.warn("An exception occurred while setting up the repositories", ex);
            gitService.commitAndPush(exerciseRepo, "Empty Setup by Artemis", user);
            gitService.commitAndPush(testRepo, "Empty Setup by Artemis", user);
            gitService.commitAndPush(solutionRepo, "Empty Setup by Artemis", user);
        }
    }

    public String getProgrammingLanguageProjectTypePath(ProgrammingLanguage programmingLanguage, ProjectType projectType) {
        return getProgrammingLanguageTemplatePath(programmingLanguage) + "/" + projectType.name().toLowerCase();
    }

    public String getProgrammingLanguageTemplatePath(ProgrammingLanguage programmingLanguage) {
        return "templates/" + programmingLanguage.name().toLowerCase();
    }

    private void createRepositoriesForNewExercise(ProgrammingExercise programmingExercise) throws GitAPIException, InterruptedException {
        final String projectKey = programmingExercise.getProjectKey();
        versionControlService.get().createProjectForExercise(programmingExercise); // Create project
        versionControlService.get().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE), null); // Create template repository
        versionControlService.get().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.TESTS), null); // Create tests repository
        versionControlService.get().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.SOLUTION), null); // Create solution repository

        // Create auxiliary repositories
        createAndInitializeAuxiliaryRepositories(projectKey, programmingExercise);
    }

    private void createAndInitializeAuxiliaryRepositories(String projectKey, ProgrammingExercise programmingExercise) throws GitAPIException, InterruptedException {
        for (AuxiliaryRepository repo : programmingExercise.getAuxiliaryRepositories()) {
            String repositoryName = programmingExercise.generateRepositoryName(repo.getName());
            versionControlService.get().createRepository(projectKey, repositoryName, null);
            repo.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(programmingExercise.getProjectKey(), repositoryName).toString());
            Repository vcsRepository = gitService.getOrCheckoutRepository(repo.getVcsRepositoryUrl(), true);
            gitService.commitAndPush(vcsRepository, SETUP_COMMIT_MESSAGE, null);
        }
    }

    /**
     * @param programmingExercise the changed programming exercise with its new values
     * @param notificationText    optional text about the changes for a notification
     * @return the updates programming exercise from the database
     */
    public ProgrammingExercise updateProgrammingExercise(ProgrammingExercise programmingExercise, @Nullable String notificationText) {

        setURLsForAuxiliaryRepositoriesOfExercise(programmingExercise);
        connectAuxiliaryRepositoriesToExercise(programmingExercise);

        ProgrammingExercise savedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);

        // TODO: in case of an exam exercise, this is not necessary
        scheduleOperations(programmingExercise.getId());

        // Only send notification for course exercises
        if (notificationText != null && programmingExercise.isCourseExercise()) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(savedProgrammingExercise, notificationText);
        }

        return savedProgrammingExercise;
    }

    /**
     * This methods sets the values (initialization date and initialization state) of the template and solution participation.
     * If either participation is null, a new one will be created.
     *
     * @param programmingExercise The programming exercise
     */
    public void initParticipations(ProgrammingExercise programmingExercise) {
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        var templateParticipation = programmingExercise.getTemplateParticipation();

        if (templateParticipation == null) {
            templateParticipation = new TemplateProgrammingExerciseParticipation();
            programmingExercise.setTemplateParticipation(templateParticipation);
        }
        if (solutionParticipation == null) {
            solutionParticipation = new SolutionProgrammingExerciseParticipation();
            programmingExercise.setSolutionParticipation(solutionParticipation);
        }

        solutionParticipation.setInitializationState(InitializationState.INITIALIZED);
        templateParticipation.setInitializationState(InitializationState.INITIALIZED);
        solutionParticipation.setInitializationDate(ZonedDateTime.now());
        templateParticipation.setInitializationDate(ZonedDateTime.now());
    }

    // Copy template and push, if no file is in the directory

    /**
     * Copy template and push, if no file is currently in the repository.
     *
     * @param repository           The repository to push to
     * @param resources            An array of resources that should be copied. Might be overwritten by projectTypeResources.
     * @param prefix               A prefix that should be replaced for all Resources inside the resources.
     * @param projectTypeResources An array of resources that should be copied AFTER the resources array has been copied. Can be null.
     * @param projectTypePrefix    A prefix that should be replaced for all Resources inside the projectTypeResources.
     * @param templateName         The name of the template
     * @param programmingExercise  the programming exercise
     * @param user                 The user that triggered the action (used as Git commit author)
     * @throws Exception           An exception in case something went wrong
     */
    private void setupTemplateAndPush(Repository repository, Resource[] resources, String prefix, @Nullable Resource[] projectTypeResources, String projectTypePrefix,
            String templateName, ProgrammingExercise programmingExercise, User user) throws Exception {
        if (gitService.listFiles(repository).size() == 0) { // Only copy template if repo is empty
            fileService.copyResources(resources, prefix, repository.getLocalPath().toAbsolutePath().toString(), true);
            // Also copy project type specific files AFTERWARDS (so that they might overwrite the default files)
            if (projectTypeResources != null) {
                fileService.copyResources(projectTypeResources, projectTypePrefix, repository.getLocalPath().toAbsolutePath().toString(), true);
            }

            replacePlaceholders(programmingExercise, repository);
            commitAndPushRepository(repository, templateName + "-Template pushed by Artemis", user);
        }
    }

    /**
     * Set up the test repository. This method differentiates non sequential and sequential test repositories (more than 1 test job).
     *
     * @param repository          The repository to be set up
     * @param resources           The resources which should get added to the template
     * @param prefix              The prefix for the path to which the resources should get copied to
     * @param templateName        The name of the template
     * @param programmingExercise The related programming exercise for which the template should get created
     * @param user                the user who has initiated the generation of the programming exercise
     * @throws Exception If anything goes wrong
     */
    private void setupTestTemplateAndPush(Repository repository, Resource[] resources, String prefix, Resource[] projectTypeResources, String projectTypePrefix,
            String templateName, ProgrammingExercise programmingExercise, User user) throws Exception {
        // Only copy template if repo is empty
        if (gitService.listFiles(repository).size() == 0
                && (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA || programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.KOTLIN)) {
            // First get files that are not dependent on the project type
            String templatePath = getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage()) + "/test";

            String projectTemplatePath = templatePath + "/projectTemplate/**/*.*";
            Resource[] projectTemplate = resourceLoaderService.getResources(projectTemplatePath);
            fileService.copyResources(projectTemplate, prefix, repository.getLocalPath().toAbsolutePath().toString(), false);

            // These resources might override the programming language dependent resources as they are project type dependent.
            ProjectType projectType = programmingExercise.getProjectType();
            if (projectType != null) {
                String projectTypeTemplatePath = getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType) + "/test";

                String projectTypeProjectTemplatePath = projectTypeTemplatePath + "/projectTemplate/**/*.*";

                try {
                    Resource[] projectTypeProjectTemplate = resourceLoaderService.getResources(projectTypeProjectTemplatePath);
                    fileService.copyResources(projectTypeProjectTemplate, projectTypePrefix, repository.getLocalPath().toAbsolutePath().toString(), false);
                }
                catch (FileNotFoundException ignored) {
                }
            }

            Map<String, Boolean> sectionsMap = new HashMap<>();

            // Keep or delete static code analysis configuration in pom.xml
            sectionsMap.put("static-code-analysis", Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()));

            if (!programmingExercise.hasSequentialTestRuns()) {
                String testFilePath = templatePath + "/testFiles/**/*.*";
                Resource[] testFileResources = resourceLoaderService.getResources(testFilePath);
                String packagePath = Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "test", "${packageNameFolder}").toAbsolutePath().toString();

                sectionsMap.put("non-sequential", true);
                sectionsMap.put("sequential", false);

                fileService.replacePlaceholderSections(Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "pom.xml").toAbsolutePath().toString(), sectionsMap);

                fileService.copyResources(testFileResources, prefix, packagePath, false);

                // Possibly overwrite files if the project type is defined
                if (projectType != null) {
                    String projectTypeTemplatePath = getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType) + "/test";

                    try {
                        Resource[] projectTypeTestFileResources = resourceLoaderService.getResources(projectTypeTemplatePath);
                        // filter non existing resources to avoid exceptions
                        List<Resource> existingProjectTypeTestFileResources = new ArrayList<>();
                        for (Resource resource : projectTypeTestFileResources) {
                            if (resource.exists()) {
                                existingProjectTypeTestFileResources.add(resource);
                            }
                        }
                        if (!existingProjectTypeTestFileResources.isEmpty()) {
                            fileService.copyResources(existingProjectTypeTestFileResources.toArray(new Resource[] {}), projectTypePrefix, packagePath, false);
                        }
                    }
                    catch (FileNotFoundException ignored) {
                    }
                }

                // Copy static code analysis config files
                if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
                    String staticCodeAnalysisConfigPath = templatePath + "/staticCodeAnalysisConfig/**/*.*";
                    Resource[] staticCodeAnalysisResources = resourceLoaderService.getResources(staticCodeAnalysisConfigPath);
                    fileService.copyResources(staticCodeAnalysisResources, prefix, repository.getLocalPath().toAbsolutePath().toString(), true);
                }
            }
            else {
                String stagePomXmlPath = templatePath + "/stagePom.xml";
                if (new java.io.File(projectTemplatePath + "/stagePom.xml").exists()) {
                    stagePomXmlPath = projectTemplatePath + "/stagePom.xml";
                }
                Resource stagePomXml = resourceLoaderService.getResource(stagePomXmlPath);

                // This is done to prepare for a feature where instructors/tas can add multiple build stages.
                List<String> sequentialTestTasks = new ArrayList<>();
                sequentialTestTasks.add("structural");
                sequentialTestTasks.add("behavior");

                sectionsMap.put("non-sequential", false);
                sectionsMap.put("sequential", true);

                fileService.replacePlaceholderSections(Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "pom.xml").toAbsolutePath().toString(), sectionsMap);

                for (String buildStage : sequentialTestTasks) {

                    Path buildStagePath = Paths.get(repository.getLocalPath().toAbsolutePath().toString(), buildStage);
                    Files.createDirectory(buildStagePath);

                    String buildStageResourcesPath = templatePath + "/testFiles/" + buildStage + "/**/*.*";
                    Resource[] buildStageResources = resourceLoaderService.getResources(buildStageResourcesPath);

                    Files.createDirectory(Paths.get(buildStagePath.toAbsolutePath().toString(), "test"));
                    Files.createDirectory(Paths.get(buildStagePath.toAbsolutePath().toString(), "test", "${packageNameFolder}"));

                    String packagePath = Paths.get(buildStagePath.toAbsolutePath().toString(), "test", "${packageNameFolder}").toAbsolutePath().toString();

                    Files.copy(stagePomXml.getInputStream(), Paths.get(buildStagePath.toAbsolutePath().toString(), "pom.xml"));
                    fileService.copyResources(buildStageResources, prefix, packagePath, false);

                    // Possibly overwrite files if the project type is defined
                    if (projectType != null) {
                        buildStageResourcesPath = projectTemplatePath + "/testFiles/" + buildStage + "/**/*.*";
                        try {
                            buildStageResources = resourceLoaderService.getResources(buildStageResourcesPath);
                            fileService.copyResources(buildStageResources, prefix, packagePath, false);
                        }
                        catch (FileNotFoundException ignored) {
                        }
                    }
                }
            }

            replacePlaceholders(programmingExercise, repository);
            commitAndPushRepository(repository, templateName + "-Template pushed by Artemis", user);
        }
        else {
            // If there is no special test structure for a programming language, just copy all the test files.
            setupTemplateAndPush(repository, resources, prefix, projectTypeResources, projectTypePrefix, templateName, programmingExercise, user);
        }
    }

    /**
     * Replace placeholders in repository files (e.g. ${placeholder}).
     *
     * @param programmingExercise The related programming exercise
     * @param repository          The repository in which the placeholders should get replaced
     * @throws IOException If replacing the directory name, or file variables throws an exception
     */
    public void replacePlaceholders(ProgrammingExercise programmingExercise, Repository repository) throws IOException {
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA || programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.KOTLIN) {
            fileService.replaceVariablesInDirectoryName(repository.getLocalPath().toAbsolutePath().toString(), "${packageNameFolder}", programmingExercise.getPackageFolderName());
        }
        else if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.SWIFT) {
            fileService.replaceVariablesInDirectoryName(repository.getLocalPath().toAbsolutePath().toString(), "${packageNameFolder}", programmingExercise.getPackageName());
            fileService.replaceVariablesInFileName(repository.getLocalPath().toAbsolutePath().toString(), "${packageNameFile}", programmingExercise.getPackageName());
        }

        Map<String, String> replacements = new HashMap<>();

        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA || programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.KOTLIN
                || programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.SWIFT) {
            replacements.put("${packageName}", programmingExercise.getPackageName());
        }
        // there is no need in python to replace package names

        replacements.put("${exerciseNamePomXml}", programmingExercise.getTitle().replaceAll(" ", "-")); // Used e.g. in artifactId
        replacements.put("${exerciseName}", programmingExercise.getTitle());
        replacements.put("${studentWorkingDirectory}", Constants.STUDENT_WORKING_DIRECTORY);
        replacements.put("${packaging}", programmingExercise.hasSequentialTestRuns() ? "pom" : "jar");
        fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), replacements);
    }

    /**
     * Stage, commit and push.
     *
     * @param repository The repository to which the changes should get pushed
     * @param message    The commit message
     * @param user       the user who has initiated the generation of the programming exercise
     * @throws GitAPIException If committing, or pushing to the repo throws an exception
     */
    public void commitAndPushRepository(Repository repository, String message, User user) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, message, user);
        repository.setFiles(null); // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
    }

    /**
     * Updates the timeline attributes of the given programming exercise
     * @param updatedProgrammingExercise containing the changes that have to be saved
     * @param notificationText optional text for a notification to all students about the update
     * @return the updated ProgrammingExercise object.
     */
    public ProgrammingExercise updateTimeline(ProgrammingExercise updatedProgrammingExercise, @Nullable String notificationText) {

        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(updatedProgrammingExercise.getId());
        programmingExercise.setReleaseDate(updatedProgrammingExercise.getReleaseDate());
        programmingExercise.setDueDate(updatedProgrammingExercise.getDueDate());
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(updatedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        programmingExercise.setAssessmentType(updatedProgrammingExercise.getAssessmentType());
        programmingExercise.setAssessmentDueDate(updatedProgrammingExercise.getAssessmentDueDate());
        ProgrammingExercise savedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(updatedProgrammingExercise, notificationText);
        }
        return savedProgrammingExercise;
    }

    /**
     * Updates the problem statement of the given programming exercise.
     *
     * @param programmingExercise   The ProgrammingExercise of which the problem statement is updated.
     * @param problemStatement      markdown of the problem statement.
     * @param notificationText      optional text for a notification to all students about the update
     * @return the updated ProgrammingExercise object.
     * @throws EntityNotFoundException if there is no ProgrammingExercise for the given id.
     */
    public ProgrammingExercise updateProblemStatement(ProgrammingExercise programmingExercise, String problemStatement, @Nullable String notificationText)
            throws EntityNotFoundException {

        programmingExercise.setProblemStatement(problemStatement);
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(updatedProgrammingExercise, notificationText);
        }
        return updatedProgrammingExercise;
    }

    /**
     * This method calls the StructureOracleGenerator, generates the string out of the JSON representation of the structure oracle of the programming exercise and returns true if
     * the file was updated or generated, false otherwise. This can happen if the contents of the file have not changed.
     *
     * @param solutionRepoURL The URL of the solution repository.
     * @param exerciseRepoURL The URL of the exercise repository.
     * @param testRepoURL     The URL of the tests repository.
     * @param testsPath       The path to the tests folder, e.g. the path inside the repository where the structure oracle file will be saved in.
     * @param user            The user who has initiated the action
     * @return True, if the structure oracle was successfully generated or updated, false if no changes to the file were made.
     * @throws IOException          If the URLs cannot be converted to actual {@link Path paths}
     * @throws InterruptedException If the checkout fails
     * @throws GitAPIException      If the checkout fails
     */
    public boolean generateStructureOracleFile(VcsRepositoryUrl solutionRepoURL, VcsRepositoryUrl exerciseRepoURL, VcsRepositoryUrl testRepoURL, String testsPath, User user)
            throws IOException, GitAPIException, InterruptedException {
        Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepoURL, true);
        Repository exerciseRepository = gitService.getOrCheckoutRepository(exerciseRepoURL, true);
        Repository testRepository = gitService.getOrCheckoutRepository(testRepoURL, true);

        gitService.resetToOriginHead(solutionRepository);
        gitService.pullIgnoreConflicts(solutionRepository);
        gitService.resetToOriginHead(exerciseRepository);
        gitService.pullIgnoreConflicts(exerciseRepository);
        gitService.resetToOriginHead(testRepository);
        gitService.pullIgnoreConflicts(testRepository);

        Path solutionRepositoryPath = solutionRepository.getLocalPath().toRealPath();
        Path exerciseRepositoryPath = exerciseRepository.getLocalPath().toRealPath();
        Path structureOraclePath = Paths.get(testRepository.getLocalPath().toRealPath().toString(), testsPath, "test.json");

        String structureOracleJSON = OracleGenerator.generateStructureOracleJSON(solutionRepositoryPath, exerciseRepositoryPath);
        return saveAndPushStructuralOracle(user, testRepository, structureOraclePath, structureOracleJSON);
    }

    private boolean saveAndPushStructuralOracle(User user, Repository testRepository, Path structureOraclePath, String structureOracleJSON) throws IOException {
        // If the oracle file does not already exist, then save the generated string to the file.
        // If it does, check if the contents of the existing file are the same as the generated one.
        // If they are, do not push anything and inform the user about it.
        // If not, then update the oracle file by rewriting it and push the changes.
        if (!Files.exists(structureOraclePath)) {
            try {
                Files.write(structureOraclePath, structureOracleJSON.getBytes());
                gitService.stageAllChanges(testRepository);
                gitService.commitAndPush(testRepository, "Generate the structure oracle file.", user);
                return true;
            }
            catch (GitAPIException e) {
                log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                return false;
            }
        }
        else {
            Byte[] existingContents = ArrayUtils.toObject(Files.readAllBytes(structureOraclePath));
            Byte[] newContents = ArrayUtils.toObject(structureOracleJSON.getBytes());

            if (Arrays.deepEquals(existingContents, newContents)) {
                log.info("No changes to the oracle detected.");
                return false;
            }
            else {
                try {
                    Files.write(structureOraclePath, structureOracleJSON.getBytes());
                    gitService.stageAllChanges(testRepository);
                    gitService.commitAndPush(testRepository, "Update the structure oracle file.", user);
                    return true;
                }
                catch (GitAPIException e) {
                    log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                    return false;
                }
            }
        }
    }

    /**
     * Delete a programming exercise, including its template and solution participations.
     *
     * @param programmingExerciseId     id of the programming exercise to delete.
     * @param deleteBaseReposBuildPlans if true will also delete build plans and projects.
     */
    @Transactional // ok
    public void delete(Long programmingExerciseId, boolean deleteBaseReposBuildPlans) {
        // TODO: This method does not accept a programming exercise to solve issues with nested Transactions.
        // It would be good to refactor the delete calls and move the validity checks down from the resources to the service methods (e.g. EntityNotFound).
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));
        final var templateRepositoryUrlAsUrl = programmingExercise.getVcsTemplateRepositoryUrl();
        final var solutionRepositoryUrlAsUrl = programmingExercise.getVcsSolutionRepositoryUrl();
        final var testRepositoryUrlAsUrl = programmingExercise.getVcsTestRepositoryUrl();

        // This cancels scheduled tasks (like locking/unlocking repositories)
        // As the programming exercise might already be deleted once the scheduling node receives the message, only the
        // id is used to cancel the scheduling. No interaction with the database is required.
        cancelScheduledOperations(programmingExercise.getId());

        if (deleteBaseReposBuildPlans) {
            final var templateBuildPlanId = programmingExercise.getTemplateBuildPlanId();
            if (templateBuildPlanId != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanId);
            }
            final var solutionBuildPlanId = programmingExercise.getSolutionBuildPlanId();
            if (solutionBuildPlanId != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanId);
            }
            continuousIntegrationService.get().deleteProject(programmingExercise.getProjectKey());

            if (programmingExercise.getTemplateRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(templateRepositoryUrlAsUrl);
            }
            if (programmingExercise.getSolutionRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(solutionRepositoryUrlAsUrl);
            }
            if (programmingExercise.getTestRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(testRepositoryUrlAsUrl);
            }

            // We also want to delete any auxiliary repositories
            programmingExercise.getAuxiliaryRepositories().forEach(repo -> {
                if (repo.getRepositoryUrl() != null) {
                    versionControlService.get().deleteRepository(repo.getVcsRepositoryUrl());
                }
            });

            versionControlService.get().deleteProject(programmingExercise.getProjectKey());
        }
        /*
         * Always delete the local copies of the repository because they can (in theory) be restored by cloning again, but they block the creation of new programming exercises with
         * the same short name as a deleted one. The instructors might have missed selecting deleteBaseReposBuildPlans, and delete those manually later. This however leaves no
         * chance to remove the Artemis-local repositories on the server. In summary, they should and can always be deleted.
         */
        if (programmingExercise.getTemplateRepositoryUrl() != null) {
            gitService.deleteLocalRepository(templateRepositoryUrlAsUrl);
        }
        if (programmingExercise.getSolutionRepositoryUrl() != null) {
            gitService.deleteLocalRepository(solutionRepositoryUrlAsUrl);
        }
        if (programmingExercise.getTestRepositoryUrl() != null) {
            gitService.deleteLocalRepository(testRepositoryUrlAsUrl);
        }

        SolutionProgrammingExerciseParticipation solutionProgrammingExerciseParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateProgrammingExerciseParticipation = programmingExercise.getTemplateParticipation();
        if (solutionProgrammingExerciseParticipation != null) {
            participationService.deleteResultsAndSubmissionsOfParticipation(solutionProgrammingExerciseParticipation.getId());
        }
        if (templateProgrammingExerciseParticipation != null) {
            participationService.deleteResultsAndSubmissionsOfParticipation(templateProgrammingExerciseParticipation.getId());
        }
        // This will also delete the template & solution participation.
        programmingExerciseRepository.delete(programmingExercise);
    }

    public boolean hasAtLeastOneStudentResult(ProgrammingExercise programmingExercise) {
        // Is true if the exercise is released and has at least one result.
        // We can't use the resultService here due to a circular dependency issue.
        return resultRepository.existsByParticipation_ExerciseId(programmingExercise.getId());
    }

    public ProgrammingExercise save(ProgrammingExercise programmingExercise) {
        return programmingExerciseRepository.save(programmingExercise);
    }

    /**
     * Search for all programming exercises fitting a {@link PageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user   The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ProgrammingExercise> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        var sorting = Sort.by(Exercise.ExerciseSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sorting = search.getSortingOrder() == SortingOrder.ASCENDING ? sorting.ascending() : sorting.descending();
        final var sorted = PageRequest.of(search.getPage() - 1, search.getPageSize(), sorting);
        final var searchTerm = search.getSearchTerm();

        final var exercisePage = authCheckService.isAdmin(user)
                ? programmingExerciseRepository.findByTitleIgnoreCaseContainingAndShortNameNotNullOrCourse_TitleIgnoreCaseContainingAndShortNameNotNull(searchTerm, searchTerm,
                        sorted)
                : programmingExerciseRepository.findByTitleInExerciseOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), sorted);

        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * add project permissions to project of the build plans of the given exercise
     *
     * @param exercise the exercise whose build plans projects should be configured with permissions
     */
    public void giveCIProjectPermissions(ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        final var editorGroup = course.getEditorGroupName();
        final var teachingAssistantGroup = course.getTeachingAssistantGroupName();

        List<String> adminGroups = new ArrayList<>();
        adminGroups.add(course.getInstructorGroupName());
        if (StringUtils.isNotEmpty(editorGroup)) {
            adminGroups.add(editorGroup);
        }

        continuousIntegrationService.get().giveProjectPermissions(exercise.getProjectKey(), adminGroups, List.of(CIPermission.CREATE, CIPermission.READ, CIPermission.ADMIN));
        if (teachingAssistantGroup != null) {
            continuousIntegrationService.get().giveProjectPermissions(exercise.getProjectKey(), List.of(teachingAssistantGroup), List.of(CIPermission.READ));
        }
    }

    /**
     * Unlock all repositories of the programming exercise
     *
     * @param exerciseId of the exercise
     */
    public void unlockAllRepositories(Long exerciseId) {
        instanceMessageSendService.sendUnlockAllRepositories(exerciseId);
    }

    /**
     * Lock all repositories of the programming exercise
     *
     * @param exerciseId of the exercise
     */
    public void lockAllRepositories(Long exerciseId) {
        instanceMessageSendService.sendLockAllRepositories(exerciseId);
    }
}
