package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;

@Service
public class ProgrammingExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportService.class);

    private final ExerciseHintService exerciseHintService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseService programmingExerciseService;

    public ProgrammingExerciseImportService(ExerciseHintService exerciseHintService, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseService programmingExerciseService) {
        this.exerciseHintService = exerciseHintService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseService = programmingExerciseService;
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
    @Transactional
    public ProgrammingExercise importProgrammingExerciseBasis(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        // Set values we don't want to copy to null
        setupExerciseForImport(newExercise);
        newExercise.generateAndSetProjectKey();
        final var projectKey = newExercise.getProjectKey();
        final var templatePlanName = BuildPlanType.TEMPLATE.getName();
        final var solutionPlanName = BuildPlanType.SOLUTION.getName();

        programmingExerciseParticipationService.setupInitialSolutionParticipation(newExercise, projectKey, solutionPlanName);
        programmingExerciseParticipationService.setupInitalTemplateParticipation(newExercise, projectKey, templatePlanName);
        setupTestRepository(newExercise, projectKey);
        programmingExerciseService.initParticipations(newExercise);

        // Hints and test cases
        exerciseHintService.copyExerciseHints(templateExercise, newExercise);
        programmingExerciseRepository.save(newExercise);
        importTestCases(templateExercise, newExercise);

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
        reposToCopy.forEach(repo -> versionControlService.get().copyRepository(sourceProjectKey, repo.getSecond(), targetProjectKey, repo.getFirst().getName()));
        // Add the necessary hooks notifying Artemis about changes after commits have been pushed
        versionControlService.get().addWebHooksForExercise(newExercise);
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

        updateBaseBuildPlans(newExercise, templateParticipation, solutionParticipation, targetExerciseProjectKey);

        try {
            continuousIntegrationService.get().triggerBuild(templateParticipation);
            continuousIntegrationService.get().triggerBuild(solutionParticipation);
        }
        catch (HttpException e) {
            log.error("Unable to trigger imported build plans", e);
            throw e;
        }
    }

    private void updateBaseBuildPlans(ProgrammingExercise newExercise, TemplateProgrammingExerciseParticipation templateParticipation,
            SolutionProgrammingExerciseParticipation solutionParticipation, String targetExerciseProjectKey) {
        // update 2 repositories for the template (BASE) build plan
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, templateParticipation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTemplateRepositoryUrl(), Optional.of(List.of(ASSIGNMENT_REPO_NAME)));
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, templateParticipation.getBuildPlanId(), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUrl(), Optional.empty());

        // update 2 repositories for the solution (SOLUTION) build plan
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, solutionParticipation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, targetExerciseProjectKey,
                newExercise.getSolutionRepositoryUrl(), Optional.empty());
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, solutionParticipation.getBuildPlanId(), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUrl(), Optional.empty());
    }

    private void cloneAndEnableAllBuildPlans(ProgrammingExercise templateExercise, ProgrammingExercise newExercise) {
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();
        final var targetExerciseProjectKey = newExercise.getProjectKey();
        final var templatePlanName = BuildPlanType.TEMPLATE.getName();
        final var solutionPlanName = BuildPlanType.SOLUTION.getName();
        final var templateKey = templateExercise.getProjectKey();
        final var targetKey = newExercise.getProjectKey();
        final var targetName = newExercise.getCourse().getShortName().toUpperCase() + " " + newExercise.getTitle();

        continuousIntegrationService.get().copyBuildPlan(templateKey, templatePlanName, targetKey, targetName, templatePlanName);
        continuousIntegrationService.get().copyBuildPlan(templateKey, solutionPlanName, targetKey, targetName, solutionPlanName);
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
            copy.setAfterDueDate(testCase.isAfterDueDate());
            copy.setTestName(testCase.getTestName());
            copy.setWeight(testCase.getWeight());
            copy.setExercise(targetExercise);
            programmingExerciseTestCaseRepository.save(copy);
            return copy;
        }).collect(Collectors.toSet()));
    }

    /**
     * Sets up a new exercise for importing it by setting all values, that should either never get imported, or
     * for which we should create new entities (e.g. test cases) to null. This ensures that we do not copy
     * anything by accident.
     *
     * @param newExercise
     */
    private void setupExerciseForImport(ProgrammingExercise newExercise) {
        newExercise.setId(null);
        newExercise.setTemplateParticipation(null);
        newExercise.setSolutionParticipation(null);
        newExercise.setExerciseHints(null);
        newExercise.setTestCases(null);
        newExercise.setAttachments(null);
        newExercise.setNumberOfMoreFeedbackRequests(null);
        newExercise.setNumberOfComplaints(null);
        newExercise.setNumberOfAssessments(null);
        newExercise.setTutorParticipations(null);
        newExercise.setExampleSubmissions(null);
        newExercise.setStudentQuestions(null);
        newExercise.setStudentParticipations(null);
    }

    /**
     * Sets up the test repository for a new exercise by setting the repository URL. This does not create the actual
     * repository on the version control server!
     *
     * @param newExercise
     * @param projectKey
     */
    private void setupTestRepository(ProgrammingExercise newExercise, String projectKey) {
        final var testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        newExercise.setTestRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, testRepoName).toString());
    }
}
