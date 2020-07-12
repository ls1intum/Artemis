package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
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

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ParticipationService participationService;

    private final ResultRepository resultRepository;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    private final ResourceLoader resourceLoader;

    private final InstanceMessageSendService instanceMessageSendService;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService,
            Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ParticipationService participationService,
            ResultRepository resultRepository, UserService userService, AuthorizationCheckService authCheckService, ResourceLoader resourceLoader,
            GroupNotificationService groupNotificationService, InstanceMessageSendService instanceMessageSendService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resourceLoader = resourceLoader;
        this.groupNotificationService = groupNotificationService;
        this.instanceMessageSendService = instanceMessageSendService;
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
     * @throws GitAPIException If something during the communication with the remote Git repositroy went wrong
     * @throws IOException If the template files couldn't be read
     */
    @Transactional
    public ProgrammingExercise setupProgrammingExercise(ProgrammingExercise programmingExercise) throws InterruptedException, GitAPIException, IOException {
        programmingExercise.generateAndSetProjectKey();
        final var user = userService.getUser();
        final var projectKey = programmingExercise.getProjectKey();
        // TODO: the following code is used quite often and should be done in only one place
        final var exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        final var testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        final var solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        final var exerciseRepoUrl = versionControlService.get().getCloneRepositoryUrl(projectKey, exerciseRepoName).getURL();
        final var testsRepoUrl = versionControlService.get().getCloneRepositoryUrl(projectKey, testRepoName).getURL();
        final var solutionRepoUrl = versionControlService.get().getCloneRepositoryUrl(projectKey, solutionRepoName).getURL();

        createRepositoriesForNewExercise(programmingExercise, exerciseRepoName, testRepoName, solutionRepoName);
        initParticipations(programmingExercise);
        setURLsAndBuildPlanIDsForNewExercise(programmingExercise, exerciseRepoName, testRepoName, solutionRepoName);

        // Save participations to get the ids required for the webhooks
        connectBaseParticipationsToExerciseAndSave(programmingExercise);

        setupExerciseTemplate(programmingExercise, user, exerciseRepoUrl, testsRepoUrl, solutionRepoUrl);
        setupBuildPlansForNewExercise(programmingExercise, exerciseRepoUrl, testsRepoUrl, solutionRepoUrl);

        // save to get the id required for the webhook
        programmingExercise = programmingExerciseRepository.saveAndFlush(programmingExercise);

        // The creation of the webhooks must occur after the initial push, because the participation is
        // not yet saved in the database, so we cannot save the submission accordingly (see ProgrammingSubmissionService.notifyPush)
        versionControlService.get().addWebHooksForExercise(programmingExercise);

        scheduleOperations(programmingExercise.getId());

        // Notify tutors only if this a course exercise
        if (programmingExercise.hasCourse()) {
            groupNotificationService.notifyTutorGroupAboutExerciseCreated(programmingExercise);
        }

        return programmingExercise;
    }

    public void scheduleOperations(Long programmingExerciseId) {
        instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExerciseId);
    }

    private void setupBuildPlansForNewExercise(ProgrammingExercise programmingExercise, URL exerciseRepoUrl, URL testsRepoUrl, URL solutionRepoUrl) {
        String projectKey = programmingExercise.getProjectKey();
        continuousIntegrationService.get().createProjectForExercise(programmingExercise);
        // template build plan
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUrl, testsRepoUrl);
        // solution build plan
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, SOLUTION.getName(), solutionRepoUrl, testsRepoUrl);

        // Give appropriate permissions for CI projects
        continuousIntegrationService.get().removeAllDefaultProjectPermissions(projectKey);

        giveCIProjectPermissions(programmingExercise);
    }

    /**
     *  This method connects the new programming exercise with the template and solution participation
     * @param programmingExercise the new programming exercise
     */
    public void connectBaseParticipationsToExerciseAndSave(ProgrammingExercise programmingExercise) {
        final var templateParticipation = programmingExercise.getTemplateParticipation();
        final var solutionParticipation = programmingExercise.getSolutionParticipation();
        templateParticipation.setProgrammingExercise(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
    }

    private void setURLsAndBuildPlanIDsForNewExercise(ProgrammingExercise programmingExercise, String exerciseRepoName, String testRepoName, String solutionRepoName) {
        final var projectKey = programmingExercise.getProjectKey();
        final var templateParticipation = programmingExercise.getTemplateParticipation();
        final var solutionParticipation = programmingExercise.getSolutionParticipation();
        final var templatePlanName = TEMPLATE.getName();
        final var solutionPlanName = SOLUTION.getName();

        templateParticipation.setBuildPlanId(projectKey + "-" + templatePlanName); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(projectKey + "-" + solutionPlanName);
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, testRepoName).toString());
    }

    private void setupExerciseTemplate(ProgrammingExercise programmingExercise, User user, URL exerciseRepoUrl, URL testsRepoUrl, URL solutionRepoUrl)
            throws IOException, GitAPIException, InterruptedException {
        String programmingLanguage = programmingExercise.getProgrammingLanguage().toString().toLowerCase();

        String templatePath = "classpath:templates/" + programmingLanguage;
        String exercisePath = templatePath + "/exercise/**/*.*";
        String solutionPath = templatePath + "/solution/**/*.*";
        String testPath = templatePath + "/test/**/*.*";

        Resource[] exerciseResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(exercisePath);
        Resource[] testResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(testPath);
        Resource[] solutionResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(solutionPath);

        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl, true);
        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl, true);
        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl, true);

        try {
            String exercisePrefix = programmingLanguage + "/exercise";
            String testPrefix = programmingLanguage + "/test";
            String solutionPrefix = programmingLanguage + "/solution";
            setupTemplateAndPush(exerciseRepo, exerciseResources, exercisePrefix, "Exercise", programmingExercise, user);
            setupTemplateAndPush(solutionRepo, solutionResources, solutionPrefix, "Solution", programmingExercise, user);
            setupTestTemplateAndPush(testRepo, testResources, testPrefix, "Test", programmingExercise, user);

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

    private void createRepositoriesForNewExercise(ProgrammingExercise programmingExercise, String templateRepoName, String testRepoName, String solutionRepoName) {
        final var projectKey = programmingExercise.getProjectKey();
        versionControlService.get().createProjectForExercise(programmingExercise); // Create project
        versionControlService.get().createRepository(projectKey, templateRepoName, null); // Create template repository
        versionControlService.get().createRepository(projectKey, testRepoName, null); // Create tests repository
        versionControlService.get().createRepository(projectKey, solutionRepoName, null); // Create solution repository
    }

    /**
     *
     * @param programmingExercise the changed programming exercise with its new values
     * @param notificationText optional text about the changes for a notification
     * @return the updates programming exercise from the database
     */
    public ProgrammingExercise updateProgrammingExercise(ProgrammingExercise programmingExercise, @Nullable String notificationText) {
        ProgrammingExercise savedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);

        scheduleOperations(programmingExercise.getId());

        // Only send notification for course exercises
        if (notificationText != null && programmingExercise.hasCourse()) {
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
    private void setupTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise, User user)
            throws Exception {
        if (gitService.listFiles(repository).size() == 0) { // Only copy template if repo is empty
            fileService.copyResources(resources, prefix, repository.getLocalPath().toAbsolutePath().toString(), true);
            replacePlaceholders(programmingExercise, repository);
            commitAndPushRepository(repository, templateName, user);
        }
    }

    /**
     * Set up the test repository. This method differentiates non sequential and sequential test repositories (more than 1 test job).
     *
     * @param repository The repository to be set up
     * @param resources The resources which should get added to the template
     * @param prefix The prefix for the path to which the resources should get copied to
     * @param templateName The name of the template
     * @param programmingExercise The related programming exercise for which the template should get created
     * @param user the user who has initiated the generation of the programming exercise
     * @throws Exception If anything goes wrong
     */
    private void setupTestTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise, User user)
            throws Exception {
        if (gitService.listFiles(repository).size() == 0 && programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) { // Only copy template if repo is empty
            String templatePath = "classpath:templates/" + programmingExercise.getProgrammingLanguage().toString().toLowerCase() + "/test";

            String projectTemplatePath = templatePath + "/projectTemplate/**/*.*";
            String testUtilsPath = templatePath + "/testutils/**/*.*";

            Resource[] testUtils = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(testUtilsPath);
            Resource[] projectTemplate = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(projectTemplatePath);

            Map<String, Boolean> sectionsMap = new HashMap<>();

            fileService.copyResources(projectTemplate, prefix, repository.getLocalPath().toAbsolutePath().toString(), false);

            if (!programmingExercise.hasSequentialTestRuns()) {
                String testFilePath = templatePath + "/testFiles" + "/**/*.*";
                Resource[] testFileResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(testFilePath);

                sectionsMap.put("non-sequential", true);
                sectionsMap.put("sequential", false);

                fileService.replacePlaceholderSections(Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "pom.xml").toAbsolutePath().toString(), sectionsMap);

                String packagePath = Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "test", "${packageNameFolder}").toAbsolutePath().toString();
                fileService.copyResources(testUtils, prefix, packagePath, true);
                fileService.copyResources(testFileResources, prefix, packagePath, false);
            }
            else {
                String stagePomXmlPath = templatePath + "/stagePom.xml";
                Resource stagePomXml = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource(stagePomXmlPath);
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
                    Resource[] buildStageResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(buildStageResourcesPath);

                    Files.createDirectory(Paths.get(buildStagePath.toAbsolutePath().toString(), "test"));
                    Files.createDirectory(Paths.get(buildStagePath.toAbsolutePath().toString(), "test", "${packageNameFolder}"));

                    String packagePath = Paths.get(buildStagePath.toAbsolutePath().toString(), "test", "${packageNameFolder}").toAbsolutePath().toString();

                    Files.copy(stagePomXml.getInputStream(), Paths.get(buildStagePath.toAbsolutePath().toString(), "pom.xml"));
                    fileService.copyResources(testUtils, prefix, packagePath, true);
                    fileService.copyResources(buildStageResources, prefix, packagePath, false);
                }
            }

            replacePlaceholders(programmingExercise, repository);
            commitAndPushRepository(repository, templateName, user);
        }
        else {
            // If there is no special test structure for a programming language, just copy all the test files.
            setupTemplateAndPush(repository, resources, prefix, templateName, programmingExercise, user);
        }
    }

    /**
     * Replace placeholders in repository files (e.g. ${placeholder}).
     * @param programmingExercise The related programming exercise
     * @param repository The repository in which the placeholders should get replaced
     * @throws IOException If replacing the directory name, or file variables throws an exception
     */
    public void replacePlaceholders(ProgrammingExercise programmingExercise, Repository repository) throws IOException {
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            fileService.replaceVariablesInDirectoryName(repository.getLocalPath().toAbsolutePath().toString(), "${packageNameFolder}", programmingExercise.getPackageFolderName());
        }

        List<String> fileTargets = new ArrayList<>();
        List<String> fileReplacements = new ArrayList<>();
        // This is based on the correct order and assumes that boths lists have the same
        // length, it
        // replaces fileTargets.get(i) with fileReplacements.get(i)

        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            fileTargets.add("${packageName}");
            fileReplacements.add(programmingExercise.getPackageName());
        }
        // there is no need in python to replace package names

        fileTargets.add("${exerciseNamePomXml}");
        fileReplacements.add(programmingExercise.getTitle().replaceAll(" ", "-")); // Used e.g. in artifactId

        fileTargets.add("${exerciseName}");
        fileReplacements.add(programmingExercise.getTitle());

        fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), fileTargets, fileReplacements);
    }

    /**
     * Stage, commit and push.
     * @param repository The repository to which the changes should get pushed
     * @param templateName The template name which should be put in the commit message
     * @throws GitAPIException If committing, or pushing to the repo throws an exception
     * @param user the user who has initiated the generation of the programming exercise
     */
    public void commitAndPushRepository(Repository repository, String templateName, User user) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, templateName + "-Template pushed by Artemis", user);
        repository.setFiles(null); // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the template Participation
     *
     * @param participation The template participation
     * @return The ProgrammingExercise where the given Participation is the template Participation
     */
    public ProgrammingExercise getExercise(TemplateProgrammingExerciseParticipation participation) {
        return programmingExerciseRepository.findOneByTemplateParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution Participation
     *
     * @param participation The solution participation
     * @return The ProgrammingExercise where the given Participation is the solution Participation
     */
    public ProgrammingExercise getExercise(SolutionProgrammingExerciseParticipation participation) {
        return programmingExerciseRepository.findOneBySolutionParticipationId(participation.getId());
    }

    /**
     * Find a programming exercise by its id.
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findWithTemplateParticipationAndSolutionParticipationById(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
        }
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded studentParticipations.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findByIdWithEagerStudentParticipations(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found");
        }
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded studentParticipations and submissions
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findByIdWithEagerStudentParticipationsAndSubmissions(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found");
        }
    }

    /**
     * Find a programming exercise by its exerciseId, including all test cases, also perform security checks
     *
     * @param exerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     * @throws IllegalAccessException  the retriever does not have the permissions to fetch information related to the programming exercise.
     */
    public ProgrammingExercise findWithTestCasesById(Long exerciseId) throws EntityNotFoundException, IllegalAccessException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTestCasesById(exerciseId);
        if (programmingExercise.isPresent()) {
            Course course = programmingExercise.get().getCourseViaExerciseGroupOrCourseMember();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                throw new IllegalAccessException();
            }
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found");
        }
    }

    /**
     * Combine all commits of the given repository into one.
     * @param repoUrl of the repository to combine.
     * @throws InterruptedException If the checkout fails
     * @throws GitAPIException If the checkout fails
     */
    public void combineAllCommitsOfRepositoryIntoOne(URL repoUrl) throws InterruptedException, GitAPIException {
        Repository exerciseRepository = gitService.getOrCheckoutRepository(repoUrl, true);
        gitService.combineAllCommitsIntoInitialCommit(exerciseRepository);
    }

    /**
     * Updates the problem statement of the given programming exercise.
     *
     * @param programmingExerciseId ProgrammingExercise Id.
     * @param problemStatement markdown of the problem statement.
     * @param notificationText optional text for a notification to all students about the update
     * @return the updated ProgrammingExercise object.
     * @throws EntityNotFoundException if there is no ProgrammingExercise for the given id.
     * @throws IllegalAccessException if the user does not have permissions to access the ProgrammingExercise.
     */
    public ProgrammingExercise updateProblemStatement(Long programmingExerciseId, String problemStatement, @Nullable String notificationText)
            throws EntityNotFoundException, IllegalAccessException {
        Optional<ProgrammingExercise> programmingExerciseOpt = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExerciseId);
        if (programmingExerciseOpt.isEmpty()) {
            throw new EntityNotFoundException("Programming exercise not found with id: " + programmingExerciseId);
        }
        ProgrammingExercise programmingExercise = programmingExerciseOpt.get();
        User user = userService.getUserWithGroupsAndAuthorities();

        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new IllegalAccessException("User with login " + user.getLogin() + " is not authorized to access programming exercise with id: " + programmingExerciseId);
        }
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
     * @throws IOException If the URLs cannot be converted to actual {@link Path paths}
     * @throws InterruptedException If the checkout fails
     * @throws GitAPIException If the checkout fails
     */
    public boolean generateStructureOracleFile(URL solutionRepoURL, URL exerciseRepoURL, URL testRepoURL, String testsPath, User user)
            throws IOException, GitAPIException, InterruptedException {
        Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepoURL, true);
        Repository exerciseRepository = gitService.getOrCheckoutRepository(exerciseRepoURL, true);
        Repository testRepository = gitService.getOrCheckoutRepository(testRepoURL, true);

        gitService.resetToOriginMaster(solutionRepository);
        gitService.pullIgnoreConflicts(solutionRepository);
        gitService.resetToOriginMaster(exerciseRepository);
        gitService.pullIgnoreConflicts(exerciseRepository);
        gitService.resetToOriginMaster(testRepository);
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
     * @param programmingExerciseId id of the programming exercise to delete.
     * @param deleteBaseReposBuildPlans if true will also delete build plans and projects.
     */
    @Transactional
    public void delete(Long programmingExerciseId, boolean deleteBaseReposBuildPlans) {
        // TODO: This method does not accept a programming exercise to solve issues with nested Transactions.
        // It would be good to refactor the delete calls and move the validity checks down from the resources to the service methods (e.g. EntityNotFound).
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExerciseId).get();
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
                final var templateRepositoryUrlAsUrl = programmingExercise.getTemplateRepositoryUrlAsUrl();
                versionControlService.get().deleteRepository(templateRepositoryUrlAsUrl);
                gitService.deleteLocalRepository(templateRepositoryUrlAsUrl);
            }
            if (programmingExercise.getSolutionRepositoryUrl() != null) {
                final var solutionRepositoryUrlAsUrl = programmingExercise.getSolutionRepositoryUrlAsUrl();
                versionControlService.get().deleteRepository(solutionRepositoryUrlAsUrl);
                gitService.deleteLocalRepository(solutionRepositoryUrlAsUrl);
            }
            if (programmingExercise.getTestRepositoryUrl() != null) {
                final var testRepositoryUrlAsUrl = programmingExercise.getTestRepositoryUrlAsUrl();
                versionControlService.get().deleteRepository(testRepositoryUrlAsUrl);
                gitService.deleteLocalRepository(testRepositoryUrlAsUrl);
            }
            versionControlService.get().deleteProject(programmingExercise.getProjectKey());
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

    /**
     * Returns the list of programming exercises with a buildAndTestStudentSubmissionsAfterDueDate in future.
     * @return List<ProgrammingExercise>
     */
    public List<ProgrammingExercise> findAllWithBuildAndTestAfterDueDateInFuture() {
        return programmingExerciseRepository.findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(ZonedDateTime.now());
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
     * @param user The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ProgrammingExercise> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        var sorting = Sort.by(Exercise.ExerciseSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sorting = search.getSortingOrder() == SortingOrder.ASCENDING ? sorting.ascending() : sorting.descending();
        final var sorted = PageRequest.of(search.getPage() - 1, search.getPageSize(), sorting);
        final var searchTerm = search.getSearchTerm();

        final var exercisePage = authCheckService.isAdmin()
                ? programmingExerciseRepository.findByTitleIgnoreCaseContainingAndShortNameNotNullOrCourse_TitleIgnoreCaseContainingAndShortNameNotNull(searchTerm, searchTerm,
                        sorted)
                : programmingExerciseRepository.findByTitleInExerciseOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), sorted);

        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * add project permissions to project of the build plans of the given exercise
     * @param exercise the exercise whose build plans projects should be configured with permissions
     */
    public void giveCIProjectPermissions(ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        final var instructorGroup = course.getInstructorGroupName();
        final var teachingAssistantGroup = course.getTeachingAssistantGroupName();

        continuousIntegrationService.get().giveProjectPermissions(exercise.getProjectKey(), List.of(instructorGroup),
                List.of(CIPermission.CREATE, CIPermission.READ, CIPermission.ADMIN));
        if (teachingAssistantGroup != null) {
            continuousIntegrationService.get().giveProjectPermissions(exercise.getProjectKey(), List.of(teachingAssistantGroup), List.of(CIPermission.READ));
        }
    }

    /**
     * Check if the repository of the given participation is locked.
     * This is the case when the participation is a ProgrammingExerciseStudentParticipation, the buildAndTestAfterDueDate of the exercise is set and the due date has passed.
     *
     * Locked means that the student can't make any changes to their repository anymore. While we can control this easily in the remote VCS, we need to check this manually for the local repository on the Artemis server.
     *
     * @param participation ProgrammingExerciseParticipation
     * @return true if repository is locked, false if not.
     */
    public boolean isParticipationRepositoryLocked(ProgrammingExerciseParticipation participation) {
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
            return programmingExercise.getDueDate() != null && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null
                    && programmingExercise.getDueDate().isBefore(ZonedDateTime.now());
        }
        return false;
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of programming submissions which should be assessed
     * We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    public long countSubmissionsByExerciseIdSubmitted(Long exerciseId) {
        long start = System.currentTimeMillis();
        var count = programmingExerciseRepository.countSubmissionsByExerciseIdSubmitted(exerciseId);
        log.debug("countSubmissionsByExerciseIdSubmitted took " + (System.currentTimeMillis() - start) + "ms");
        return count;
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of assessed programming submissions
     * We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    public long countAssessmentsByExerciseIdSubmitted(Long exerciseId) {
        long start = System.currentTimeMillis();
        var count = programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted(exerciseId);
        log.debug("countAssessmentsByExerciseIdSubmitted took " + (System.currentTimeMillis() - start) + "ms");
        return count;
    }

    /**
     * @param courseId the course we are interested in
     * @return the number of programming submissions which should be assessed, so we ignore exercises with only automatic assessment
     * We don't need to check for the submission date, because students cannot participate in programming exercises with manual assessment after their due date
     */
    public long countSubmissionsByCourseIdSubmitted(Long courseId) {
        long start = System.currentTimeMillis();
        var count = programmingExerciseRepository.countSubmissionsByCourseIdSubmitted(courseId);
        log.debug("countSubmissionsByCourseIdSubmitted took " + (System.currentTimeMillis() - start) + "ms");
        return count;
    }

    /**
     * Sets the transient attribute "isLocalSimulation" if the exercises is a programming exercise
     * and the testRepositoryUrl contains the String "artemislocalhost" which is the indicator that the programming exercise has
     * no connection to a version control and continuous integration server
     * @param exercise the exercise for which to set if it is a local simulation
     */
    public void checksAndSetsIfProgrammingExerciseIsLocalSimulation(Exercise exercise) {
        if (exercise instanceof ProgrammingExercise && (((ProgrammingExercise) exercise).getTestRepositoryUrl()).contains("artemislocalhost")) {
            ((ProgrammingExercise) exercise).setIsLocalSimulation(true);
        }
    }
}
