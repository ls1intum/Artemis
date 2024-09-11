package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.TEST_REPO_NAME;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.domain.Repository;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.enumeration.BuildPlanType;
import de.tum.cit.aet.artemis.domain.enumeration.RepositoryType;
import de.tum.cit.aet.artemis.domain.enumeration.Visibility;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.UriService;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.service.connectors.vcs.VersionControlService;
import de.tum.cit.aet.artemis.service.hestia.ProgrammingExerciseTaskService;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportService.class);

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final GitService gitService;

    private final FileService fileService;

    private final UserRepository userRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final UriService uriService;

    private final TemplateUpgradePolicyService templateUpgradePolicyService;

    private final ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    public ProgrammingExerciseImportService(Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingExerciseTaskService programmingExerciseTaskService, GitService gitService, FileService fileService, UserRepository userRepository,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, UriService uriService, TemplateUpgradePolicyService templateUpgradePolicyService,
            ProgrammingExerciseImportBasicService programmingExerciseImportBasicService, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository) {
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.gitService = gitService;
        this.fileService = fileService;
        this.userRepository = userRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.uriService = uriService;
        this.templateUpgradePolicyService = templateUpgradePolicyService;
        this.programmingExerciseImportBasicService = programmingExerciseImportBasicService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
    }

    /**
     * Import all base repositories from one exercise. These include the template, the solution and the test
     * repository. Participation repositories from students or tutors will not get copied!
     *
     * @param templateExercise The template exercise having a reference to all base repositories
     * @param newExercise      The new exercise without any repositories
     */
    public void importRepositories(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        final var targetProjectKey = newExercise.getProjectKey();
        final var sourceProjectKey = templateExercise.getProjectKey();

        // First, create a new project for our imported exercise
        VersionControlService versionControl = versionControlService.orElseThrow();
        versionControl.createProjectForExercise(newExercise);
        // Copy all repositories
        String templateRepoName = uriService.getRepositorySlugFromRepositoryUriString(templateExercise.getTemplateRepositoryUri());
        String testRepoName = uriService.getRepositorySlugFromRepositoryUriString(templateExercise.getTestRepositoryUri());
        String solutionRepoName = uriService.getRepositorySlugFromRepositoryUriString(templateExercise.getSolutionRepositoryUri());

        String sourceBranch = versionControl.getOrRetrieveBranchOfExercise(templateExercise);

        // TODO: in case one of those operations fail, we should do error handling and revert all previous operations
        versionControl.copyRepository(sourceProjectKey, templateRepoName, sourceBranch, targetProjectKey, RepositoryType.TEMPLATE.getName());
        versionControl.copyRepository(sourceProjectKey, solutionRepoName, sourceBranch, targetProjectKey, RepositoryType.SOLUTION.getName());
        versionControl.copyRepository(sourceProjectKey, testRepoName, sourceBranch, targetProjectKey, RepositoryType.TESTS.getName());

        List<AuxiliaryRepository> auxRepos = templateExercise.getAuxiliaryRepositories();
        for (int i = 0; i < auxRepos.size(); i++) {
            AuxiliaryRepository auxRepo = auxRepos.get(i);
            var repoUri = versionControl.copyRepository(sourceProjectKey, auxRepo.getRepositoryName(), sourceBranch, targetProjectKey, auxRepo.getName()).toString();
            AuxiliaryRepository newAuxRepo = newExercise.getAuxiliaryRepositories().get(i);
            newAuxRepo.setRepositoryUri(repoUri);
            auxiliaryRepositoryRepository.save(newAuxRepo);
        }

        // Unprotect the default branch of the template exercise repo.
        VcsRepositoryUri templateVcsRepositoryUri = newExercise.getVcsTemplateRepositoryUri();
        String templateVcsRepositoryBranch = versionControl.getOrRetrieveBranchOfExercise(templateExercise);
        versionControl.unprotectBranch(templateVcsRepositoryUri, templateVcsRepositoryBranch);

        // Add the necessary hooks notifying Artemis about changes after commits have been pushed
        versionControl.addWebHooksForExercise(newExercise);

        try {
            // Adjust placeholders that were replaced during creation of template exercise
            adjustProjectNames(templateExercise, newExercise);
        }
        catch (GitAPIException | IOException e) {
            log.error("Error during adjustment of placeholders of ProgrammingExercise {}", newExercise.getTitle(), e);
        }
    }

    /**
     * Imports all base build plans for an exercise. These include the template and the solution build plan, <b>not</b>
     * any participation plans!
     *
     * @param templateExercise The template exercise which plans should get copied
     * @param newExercise      The new exercise to which all plans should get copied
     */
    public void importBuildPlans(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();
        final var targetExerciseProjectKey = newExercise.getProjectKey();

        // Clone all build plans, enable them and set up the initial participations, i.e. setting the correct repo URIs and
        // running the plan for the first time
        cloneAndEnableAllBuildPlans(templateExercise, newExercise);

        updatePlanRepositoriesInBuildPlans(newExercise, targetExerciseProjectKey, templateExercise.getTemplateRepositoryUri(), templateExercise.getSolutionRepositoryUri(),
                templateExercise.getTestRepositoryUri(), templateExercise.getAuxiliaryRepositoriesForBuildPlan());

        ContinuousIntegrationTriggerService triggerService = continuousIntegrationTriggerService.orElseThrow();
        triggerService.triggerBuild(templateParticipation);
        triggerService.triggerBuild(solutionParticipation);
    }

    private void updatePlanRepositoriesInBuildPlans(ProgrammingExercise newExercise, String targetExerciseProjectKey, String oldExerciseRepoUri, String oldSolutionRepoUri,
            String oldTestRepoUri, List<AuxiliaryRepository> oldBuildPlanAuxiliaryRepositories) {
        String newExerciseBranch = versionControlService.orElseThrow().getOrRetrieveBranchOfExercise(newExercise);

        // update 2 repositories for the BASE build plan --> adapt the triggers so that only the assignment repo (and not the tests' repo) will trigger the BASE build plan
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        continuousIntegration.updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(BuildPlanType.TEMPLATE), ASSIGNMENT_REPO_NAME,
                targetExerciseProjectKey, newExercise.getTemplateRepositoryUri(), oldExerciseRepoUri, newExerciseBranch);

        continuousIntegration.updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(BuildPlanType.TEMPLATE), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUri(), oldTestRepoUri, newExerciseBranch);

        updateAuxiliaryRepositoriesForNewExercise(newExercise.getAuxiliaryRepositoriesForBuildPlan(), oldBuildPlanAuxiliaryRepositories, BuildPlanType.TEMPLATE,
                targetExerciseProjectKey, newExercise);

        // update 2 repositories for the SOLUTION build plan
        continuousIntegration.updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(BuildPlanType.SOLUTION), ASSIGNMENT_REPO_NAME,
                targetExerciseProjectKey, newExercise.getSolutionRepositoryUri(), oldSolutionRepoUri, newExerciseBranch);
        continuousIntegration.updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(BuildPlanType.SOLUTION), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUri(), oldTestRepoUri, newExerciseBranch);

        updateAuxiliaryRepositoriesForNewExercise(newExercise.getAuxiliaryRepositoriesForBuildPlan(), oldBuildPlanAuxiliaryRepositories, BuildPlanType.SOLUTION,
                targetExerciseProjectKey, newExercise);
    }

    private void updateAuxiliaryRepositoriesForNewExercise(List<AuxiliaryRepository> newRepositories, List<AuxiliaryRepository> oldRepositories, BuildPlanType buildPlanType,
            String targetExerciseProjectKey, ProgrammingExercise newExercise) {
        for (int i = 0; i < newRepositories.size(); i++) {
            AuxiliaryRepository newAuxiliaryRepository = newRepositories.get(i);
            AuxiliaryRepository oldAuxiliaryRepository = oldRepositories.get(i);
            String auxiliaryBranch = versionControlService.orElseThrow().getOrRetrieveBranchOfExercise(newExercise);
            continuousIntegrationService.orElseThrow().updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(buildPlanType),
                    newAuxiliaryRepository.getName(), targetExerciseProjectKey, newAuxiliaryRepository.getRepositoryUri(), oldAuxiliaryRepository.getRepositoryUri(),
                    auxiliaryBranch);
        }
    }

    private void cloneAndEnableAllBuildPlans(ProgrammingExercise templateExercise, ProgrammingExercise newExercise) {
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();
        final var targetExerciseProjectKey = newExercise.getProjectKey();
        final var templatePlanName = BuildPlanType.TEMPLATE.getName();
        final var solutionPlanName = BuildPlanType.SOLUTION.getName();
        final var targetName = newExercise.getCourseViaExerciseGroupOrCourseMember().getShortName().toUpperCase() + " " + newExercise.getTitle();
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        continuousIntegration.createProjectForExercise(newExercise);
        continuousIntegration.copyBuildPlan(templateExercise, templatePlanName, newExercise, targetName, templatePlanName, false);
        continuousIntegration.copyBuildPlan(templateExercise, solutionPlanName, newExercise, targetName, solutionPlanName, true);
        continuousIntegration.givePlanPermissions(newExercise, templatePlanName);
        continuousIntegration.givePlanPermissions(newExercise, solutionPlanName);
        programmingExerciseService.giveCIProjectPermissions(newExercise);
        continuousIntegration.enablePlan(targetExerciseProjectKey, templateParticipation.getBuildPlanId());
        continuousIntegration.enablePlan(targetExerciseProjectKey, solutionParticipation.getBuildPlanId());
    }

    /**
     * Adjust project names in imported exercise for TEST, BASE and SOLUTION repositories.
     * Replace values inserted in {@link ProgrammingExerciseRepositoryService#replacePlaceholders(ProgrammingExercise, Repository)}.
     *
     * @param templateExercise the exercise from which the values that should be replaced are extracted
     * @param newExercise      the exercise from which the values that should be inserted are extracted
     * @throws GitAPIException If the checkout/push of one repository fails
     * @throws IOException     If the values in the files could not be replaced
     */
    private void adjustProjectNames(ProgrammingExercise templateExercise, ProgrammingExercise newExercise) throws GitAPIException, IOException {
        final var projectKey = newExercise.getProjectKey();

        Map<String, String> replacements = new HashMap<>();

        // Used in pom.xml
        replacements.put("<artifactId>" + templateExercise.getTitle().replaceAll(" ", "-"), "<artifactId>" + newExercise.getTitle().replaceAll(" ", "-"));

        // Used in settings.gradle
        replacements.put("rootProject.name = '" + templateExercise.getTitle().replaceAll(" ", "-"), "rootProject.name = '" + newExercise.getTitle().replaceAll(" ", "-"));

        // Used in readme.md (Gradle)
        replacements.put("testImplementation(':" + templateExercise.getTitle().replaceAll(" ", "-"), "testImplementation(':" + newExercise.getTitle().replaceAll(" ", "-"));

        // Used in .project
        replacements.put("<name>" + templateExercise.getTitle(), "<name>" + newExercise.getTitle());

        final var user = userRepository.getUser();

        adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.TEMPLATE), user);
        adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.TESTS), user);
        adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.SOLUTION), user);
    }

    /**
     * Adjust project names in imported exercise for specific repository.
     * Replace values inserted in {@link ProgrammingExerciseRepositoryService#replacePlaceholders(ProgrammingExercise, Repository)}.
     *
     * @param replacements   the replacements that should be applied
     * @param projectKey     the project key of the new exercise
     * @param repositoryName the name of the repository that should be adjusted
     * @param user           the user which performed the action (used as Git author)
     * @throws GitAPIException If the checkout/push of one repository fails
     */
    private void adjustProjectName(Map<String, String> replacements, String projectKey, String repositoryName, User user) throws GitAPIException {
        final var repositoryUri = versionControlService.orElseThrow().getCloneRepositoryUri(projectKey, repositoryName);
        Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true);
        fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath(), replacements, List.of("gradle-wrapper.jar"));
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, "Template adjusted by Artemis", true, user);
        repository.setFiles(null); // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
    }

    /**
     * Method to import a programming exercise, including all base build plans (template, solution) and repositories (template, solution, test).
     * Referenced entities, s.a. the test cases or the hints will get cloned and assigned a new id.
     *
     * @param originalProgrammingExercise         the Programming Exercise which should be used as a blueprint
     * @param newProgrammingExercise              The new exercise already containing values which should not get copied, i.e. overwritten
     * @param updateTemplate                      if the template files should be updated
     * @param recreateBuildPlans                  if the build plans should be recreated
     * @param setTestCaseVisibilityToAfterDueDate if the test case visibility should be set to {@link Visibility#AFTER_DUE_DATE}
     * @return the imported programming exercise
     */
    public ProgrammingExercise importProgrammingExercise(ProgrammingExercise originalProgrammingExercise, ProgrammingExercise newProgrammingExercise, boolean updateTemplate,
            boolean recreateBuildPlans, boolean setTestCaseVisibilityToAfterDueDate) throws JsonProcessingException {
        // remove all non-alphanumeric characters from the short name. This gets already done in the client, but we do it again here to be sure
        newProgrammingExercise.setShortName(newProgrammingExercise.getShortName().replaceAll("[^a-zA-Z0-9]", ""));
        newProgrammingExercise.generateAndSetProjectKey();
        programmingExerciseService.checkIfProjectExists(newProgrammingExercise);

        if (newProgrammingExercise.isExamExercise()) {
            // Disable feedback suggestions on exam exercises (currently not supported)
            newProgrammingExercise.setFeedbackSuggestionModule(null);
        }

        newProgrammingExercise = programmingExerciseImportBasicService.importProgrammingExerciseBasis(originalProgrammingExercise, newProgrammingExercise);
        importRepositories(originalProgrammingExercise, newProgrammingExercise);

        if (setTestCaseVisibilityToAfterDueDate) {
            Set<ProgrammingExerciseTestCase> testCases = this.programmingExerciseTestCaseRepository.findByExerciseId(newProgrammingExercise.getId());
            for (ProgrammingExerciseTestCase testCase : testCases) {
                testCase.setVisibility(Visibility.AFTER_DUE_DATE);
            }
            List<ProgrammingExerciseTestCase> updatedTestCases = programmingExerciseTestCaseRepository.saveAll(testCases);
            newProgrammingExercise.setTestCases(new HashSet<>(updatedTestCases));
        }

        // Update the template files
        if (updateTemplate) {
            TemplateUpgradeService upgradeService = templateUpgradePolicyService.getUpgradeService(newProgrammingExercise.getProgrammingLanguage());
            upgradeService.upgradeTemplate(newProgrammingExercise);
        }

        if (recreateBuildPlans) {
            // Create completely new build plans for the exercise
            programmingExerciseService.setupBuildPlansForNewExercise(newProgrammingExercise, false);
        }
        else {
            // We have removed the automatic build trigger from test to base for new programming exercises.
            // We also remove this build trigger in the case of an import as the source exercise might still have this trigger.
            // The importBuildPlans method includes this process
            importBuildPlans(originalProgrammingExercise, newProgrammingExercise);
        }

        programmingExerciseService.scheduleOperations(newProgrammingExercise.getId());

        programmingExerciseTaskService.replaceTestIdsWithNames(newProgrammingExercise);
        return newProgrammingExercise;
    }

}
