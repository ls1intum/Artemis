package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;

@Service
public class ProgrammingExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportService.class);

    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    private final ExerciseHintService exerciseHintService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseService programmingExerciseService;

    private final GitService gitService;

    private final FileService fileService;

    private final UserService userService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    public ProgrammingExerciseImportService(ExerciseHintService exerciseHintService, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseService programmingExerciseService, GitService gitService, FileService fileService,
            UserService userService, StaticCodeAnalysisService staticCodeAnalysisService) {
        this.exerciseHintService = exerciseHintService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseService = programmingExerciseService;
        this.gitService = gitService;
        this.fileService = fileService;
        this.userService = userService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
    }

    /**
     * Imports a programming exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except for repositories, or build plans on a remote version control server, or
     * continuous integration server. <br>
     * There are however, a couple of things that will never get copied:
     * <ul>
     *     <li>The ID</li>
     *     <li>The template and solution participation</li>
     *     <li>The number of complaints, assessments and more feedback requests</li>
     *     <li>The tutor/student participations</li>
     *     <li>The questions asked by students</li>
     *     <li>The example submissions</li>
     * </ul>
     *
     * @param templateExercise The template exercise which should get imported
     * @param newExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @Transactional // ok because we create many objects in a rather complex way and need a rollback in case of exceptions
    public ProgrammingExercise importProgrammingExerciseBasis(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        // Set values we don't want to copy to null
        setupExerciseForImport(newExercise);

        programmingExerciseParticipationService.setupInitialSolutionParticipation(newExercise);
        programmingExerciseParticipationService.setupInitalTemplateParticipation(newExercise);
        setupTestRepository(newExercise);
        programmingExerciseService.initParticipations(newExercise);

        // Hints, test cases and static code analysis categories
        exerciseHintService.copyExerciseHints(templateExercise, newExercise);
        programmingExerciseRepository.save(newExercise);
        importTestCases(templateExercise, newExercise);

        // Copy or create SCA categories
        if (Boolean.TRUE.equals(newExercise.isStaticCodeAnalysisEnabled() && Boolean.TRUE.equals(templateExercise.isStaticCodeAnalysisEnabled()))) {
            importStaticCodeAnalysisCategories(templateExercise, newExercise);
        }
        else if (Boolean.TRUE.equals(newExercise.isStaticCodeAnalysisEnabled()) && !Boolean.TRUE.equals(templateExercise.isStaticCodeAnalysisEnabled())) {
            staticCodeAnalysisService.createDefaultCategories(newExercise);
        }

        // An exam exercise can only be in individual mode
        if (!newExercise.hasCourse()) {
            newExercise.setMode(ExerciseMode.INDIVIDUAL);
            newExercise.setTeamAssignmentConfig(null);
        }

        return newExercise;
    }

    /**
     * Import all base repositories from one exercise. These include the template, the solution and the test
     * repository. Participation repositories from students or tutors will not get copied!
     *
     * @param templateExercise The template exercise having a reference to all base repositories
     * @param newExercise The new exercise without any repositories
     */
    public void importRepositories(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        final var targetProjectKey = newExercise.getProjectKey();
        final var sourceProjectKey = templateExercise.getProjectKey();

        // First, create a new project for our imported exercise
        versionControlService.get().createProjectForExercise(newExercise);
        // Copy all repositories
        final var reposToCopy = List.of(Pair.of(RepositoryType.TEMPLATE, templateExercise.getTemplateRepositoryName()),
                Pair.of(RepositoryType.SOLUTION, templateExercise.getSolutionRepositoryName()), Pair.of(RepositoryType.TESTS, templateExercise.getTestRepositoryName()));

        // create a unique folder to prevent issues in follow-up requests
        String targetPath = fileService.getUniquePathString(repoDownloadClonePath);
        reposToCopy.forEach(repo -> versionControlService.get().copyRepository(sourceProjectKey, repo.getSecond(), targetProjectKey, repo.getFirst().getName(), targetPath));
        // Delete source project folder which contained all cloned source repos
        try {
            FileUtils.deleteDirectory(new File(targetPath));
        }
        catch (IOException e) {
            log.warn("The project root folder '" + targetPath + "' couldn't be deleted.");
        }

        // Unprotect the master branch of the template exercise repo.
        versionControlService.get().unprotectBranch(newExercise.getVcsTemplateRepositoryUrl(), "master");

        // Add the necessary hooks notifying Artemis about changes after commits have been pushed
        versionControlService.get().addWebHooksForExercise(newExercise);

        try {
            // Adjust placeholders that were replaced during creation of template exercise
            adjustProjectNames(templateExercise, newExercise);
        }
        catch (GitAPIException | IOException | InterruptedException e) {
            log.error("Error during adjustment of placeholders of ProgrammingExercise {}", newExercise.getTitle(), e);
        }
    }

    /**
     * Imports all base build plans for an exercise. These include the template and the solution build plan, <b>not</b>
     * any participation plans!
     *
     * @param templateExercise The template exercise which plans should get copied
     * @param newExercise The new exercise to which all plans should get copied
     * @throws HttpException If the copied build plans could not get triggered
     */
    public void importBuildPlans(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) throws HttpException {
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();
        final var targetExerciseProjectKey = newExercise.getProjectKey();

        // Clone all build plans, enable them and setup the initial participations, i.e. setting the correct repo URLs and
        // running the plan for the first time
        cloneAndEnableAllBuildPlans(templateExercise, newExercise);

        updatePlanRepositoriesInBuildPlans(newExercise, templateParticipation, solutionParticipation, targetExerciseProjectKey, templateExercise.getTemplateRepositoryUrl(),
                templateExercise.getSolutionRepositoryUrl(), templateExercise.getTestRepositoryUrl());

        try {
            continuousIntegrationService.get().triggerBuild(templateParticipation);
            continuousIntegrationService.get().triggerBuild(solutionParticipation);
        }
        catch (HttpException e) {
            log.error("Unable to trigger imported build plans", e);
            throw e;
        }
    }

    private void updatePlanRepositoriesInBuildPlans(ProgrammingExercise newExercise, TemplateProgrammingExerciseParticipation templateParticipation,
            SolutionProgrammingExerciseParticipation solutionParticipation, String targetExerciseProjectKey, String oldExerciseRepoUrl, String oldSolutionRepoUrl,
            String oldTestRepoUrl) {
        // update 2 repositories for the template (BASE) build plan --> adapt the triggers so that only the assignment repo (and not the tests repo) will trigger the BASE build
        // plan
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, templateParticipation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTemplateRepositoryUrl(), oldExerciseRepoUrl, Optional.of(List.of(ASSIGNMENT_REPO_NAME)));
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, templateParticipation.getBuildPlanId(), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUrl(), oldTestRepoUrl, Optional.empty());

        // update 2 repositories for the solution (SOLUTION) build plan
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, solutionParticipation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, targetExerciseProjectKey,
                newExercise.getSolutionRepositoryUrl(), oldSolutionRepoUrl, Optional.empty());
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, solutionParticipation.getBuildPlanId(), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUrl(), oldTestRepoUrl, Optional.empty());
    }

    private void cloneAndEnableAllBuildPlans(ProgrammingExercise templateExercise, ProgrammingExercise newExercise) {
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();
        final var targetExerciseProjectKey = newExercise.getProjectKey();
        final var templatePlanName = BuildPlanType.TEMPLATE.getName();
        final var solutionPlanName = BuildPlanType.SOLUTION.getName();
        final var templateKey = templateExercise.getProjectKey();
        final var targetKey = newExercise.getProjectKey();
        final var targetName = newExercise.getCourseViaExerciseGroupOrCourseMember().getShortName().toUpperCase() + " " + newExercise.getTitle();
        continuousIntegrationService.get().createProjectForExercise(newExercise);
        continuousIntegrationService.get().copyBuildPlan(templateKey, templatePlanName, targetKey, targetName, templatePlanName, false);
        continuousIntegrationService.get().copyBuildPlan(templateKey, solutionPlanName, targetKey, targetName, solutionPlanName, true);
        continuousIntegrationService.get().givePlanPermissions(newExercise, templatePlanName);
        continuousIntegrationService.get().givePlanPermissions(newExercise, solutionPlanName);
        programmingExerciseService.giveCIProjectPermissions(newExercise);
        continuousIntegrationService.get().enablePlan(targetExerciseProjectKey, templateParticipation.getBuildPlanId());
        continuousIntegrationService.get().enablePlan(targetExerciseProjectKey, solutionParticipation.getBuildPlanId());
    }

    /**
     * Copied test cases from one exercise to another. The test cases will get new IDs, thus being saved as a new entity.
     * The remaining contents stay the same, especially the weights.
     *
     * @param templateExercise The template exercise which test cases should get copied
     * @param targetExercise The new exercise to which all test cases should get copied to
     */
    private void importTestCases(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise) {
        targetExercise.setTestCases(templateExercise.getTestCases().stream().map(testCase -> {
            final var copy = new ProgrammingExerciseTestCase();

            // Copy everything except for the referenced exercise
            copy.setActive(testCase.isActive());
            copy.setVisibility(testCase.getVisibility());
            copy.setTestName(testCase.getTestName());
            copy.setWeight(testCase.getWeight());
            copy.setBonusMultiplier(testCase.getBonusMultiplier());
            copy.setBonusPoints(testCase.getBonusPoints());
            copy.setExercise(targetExercise);
            programmingExerciseTestCaseRepository.save(copy);
            return copy;
        }).collect(Collectors.toSet()));
    }

    /**
     * Copies static code analysis categories from one exercise to another by creating new entities and copying the
     * appropriate fields.
     *
     * @param templateExercise with static code analysis categories which should get copied
     * @param targetExercise for which static code analysis categories will be copied
     */
    private void importStaticCodeAnalysisCategories(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise) {
        targetExercise.setStaticCodeAnalysisCategories(templateExercise.getStaticCodeAnalysisCategories().stream().map(originalCategory -> {
            var categoryCopy = new StaticCodeAnalysisCategory();
            categoryCopy.setName(originalCategory.getName());
            categoryCopy.setPenalty(originalCategory.getPenalty());
            categoryCopy.setMaxPenalty(originalCategory.getMaxPenalty());
            categoryCopy.setState(originalCategory.getState());
            categoryCopy.setProgrammingExercise(targetExercise);

            staticCodeAnalysisCategoryRepository.save(categoryCopy);
            return categoryCopy;
        }).collect(Collectors.toSet()));
    }

    /**
     * Sets up a new exercise for importing it by setting all values, that should either never get imported, or
     * for which we should create new entities (e.g. test cases) to null. This ensures that we do not copy
     * anything by accident.
     *
     * @param newExercise the new exercises that should be created during import
     */
    private void setupExerciseForImport(ProgrammingExercise newExercise) {
        newExercise.setId(null);
        newExercise.setTemplateParticipation(null);
        newExercise.setSolutionParticipation(null);
        newExercise.setExerciseHints(null);
        newExercise.setTestCases(null);
        newExercise.setStaticCodeAnalysisCategories(null);
        newExercise.setAttachments(null);
        newExercise.setNumberOfMoreFeedbackRequests(null);
        newExercise.setNumberOfComplaints(null);
        newExercise.setTotalNumberOfAssessments(null);
        newExercise.setTutorParticipations(null);
        newExercise.setExampleSubmissions(null);
        newExercise.setStudentQuestions(null);
        newExercise.setStudentParticipations(null);

        if (newExercise.isTeamMode()) {
            newExercise.getTeamAssignmentConfig().setId(null);
        }
    }

    /**
     * Sets up the test repository for a new exercise by setting the repository URL. This does not create the actual
     * repository on the version control server!
     *
     * @param newExercise the new exercises that should be created during import
     */
    private void setupTestRepository(ProgrammingExercise newExercise) {
        final var testRepoName = newExercise.generateRepositoryName(RepositoryType.TESTS);
        newExercise.setTestRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(newExercise.getProjectKey(), testRepoName).toString());
    }

    /**
     * Adjust project names in imported exercise for TEST, BASE and SOLUTION repositories.
     * Replace values inserted in {@link ProgrammingExerciseService#replacePlaceholders(ProgrammingExercise, Repository)}.
     * @param templateExercise the exercise from which the values that should be replaced are extracted
     * @param newExercise the exercise from which the values that should be inserted are extracted
     * @throws GitAPIException If the checkout/push of one repository fails
     * @throws InterruptedException If the checkout of one repository fails
     * @throws IOException If the values in the files could not be replaced
     */
    private void adjustProjectNames(ProgrammingExercise templateExercise, ProgrammingExercise newExercise) throws GitAPIException, InterruptedException, IOException {
        final var projectKey = newExercise.getProjectKey();

        Map<String, String> replacements = new HashMap<>();

        // Used in pom.xml
        replacements.put("<artifactId>" + templateExercise.getTitle().replaceAll(" ", "-"), "<artifactId>" + newExercise.getTitle().replaceAll(" ", "-"));

        // Used in .project
        replacements.put("<name>" + templateExercise.getTitle(), "<name>" + newExercise.getTitle());

        final var user = userService.getUser();

        adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.TEMPLATE), user);
        adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.TESTS), user);
        adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.SOLUTION), user);
    }

    /**
     * Adjust project names in imported exercise for specific repository.
     * Replace values inserted in {@link ProgrammingExerciseService#replacePlaceholders(ProgrammingExercise, Repository)}.
     * @param replacements the replacements that should be applied
     * @param projectKey the project key of the new exercise
     * @param repositoryName the name of the repository that should be adjusted
     * @param user the user which performed the action (used as Git author)
     * @throws GitAPIException If the checkout/push of one repository fails
     * @throws InterruptedException If the checkout of one repository fails
     * @throws IOException If the values in the files could not be replaced
     */
    private void adjustProjectName(Map<String, String> replacements, String projectKey, String repositoryName, User user)
            throws GitAPIException, IOException, InterruptedException {
        final var repositoryUrl = versionControlService.get().getCloneRepositoryUrl(projectKey, repositoryName);
        Repository repository = gitService.getOrCheckoutRepository(repositoryUrl, true);
        fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), replacements);
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, "Template adjusted by Artemis", user);
        repository.setFiles(null); // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
    }
}
