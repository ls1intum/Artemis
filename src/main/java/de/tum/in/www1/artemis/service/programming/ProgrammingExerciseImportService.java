package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class ProgrammingExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportService.class);

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final GitService gitService;

    private final FileService fileService;

    private final ZipFileService zipFileService;

    private final UserRepository userRepository;

    private final RepositoryService repositoryService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final UrlService urlService;

    private final TemplateUpgradePolicy templateUpgradePolicy;

    private final ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    public ProgrammingExerciseImportService(Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            ProgrammingExerciseService programmingExerciseService, GitService gitService, FileService fileService, UserRepository userRepository,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, UrlService urlService, TemplateUpgradePolicy templateUpgradePolicy,
            ProgrammingExerciseImportBasicService programmingExerciseImportBasicService, Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService,
            ZipFileService zipFileService, StaticCodeAnalysisService staticCodeAnalysisService, RepositoryService repositoryService) {
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseService = programmingExerciseService;
        this.gitService = gitService;
        this.fileService = fileService;
        this.zipFileService = zipFileService;
        this.userRepository = userRepository;
        this.repositoryService = repositoryService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.urlService = urlService;
        this.templateUpgradePolicy = templateUpgradePolicy;
        this.programmingExerciseImportBasicService = programmingExerciseImportBasicService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
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
        versionControlService.get().createProjectForExercise(newExercise);
        // Copy all repositories
        String templateRepoName = urlService.getRepositorySlugFromRepositoryUrlString(templateExercise.getTemplateRepositoryUrl());
        String testRepoName = urlService.getRepositorySlugFromRepositoryUrlString(templateExercise.getTestRepositoryUrl());
        String solutionRepoName = urlService.getRepositorySlugFromRepositoryUrlString(templateExercise.getSolutionRepositoryUrl());

        String sourceBranch = versionControlService.get().getOrRetrieveBranchOfExercise(templateExercise);

        versionControlService.get().copyRepository(sourceProjectKey, templateRepoName, sourceBranch, targetProjectKey, RepositoryType.TEMPLATE.getName());
        versionControlService.get().copyRepository(sourceProjectKey, solutionRepoName, sourceBranch, targetProjectKey, RepositoryType.SOLUTION.getName());
        versionControlService.get().copyRepository(sourceProjectKey, testRepoName, sourceBranch, targetProjectKey, RepositoryType.TESTS.getName());

        List<AuxiliaryRepository> auxiliaryRepositories = templateExercise.getAuxiliaryRepositories();
        for (int i = 0; i < auxiliaryRepositories.size(); i++) {
            AuxiliaryRepository auxiliaryRepository = auxiliaryRepositories.get(i);
            String repositoryUrl = versionControlService.get()
                    .copyRepository(sourceProjectKey, auxiliaryRepository.getRepositoryName(), sourceBranch, targetProjectKey, auxiliaryRepository.getName()).toString();
            AuxiliaryRepository newAuxiliaryRepository = newExercise.getAuxiliaryRepositories().get(i);
            newAuxiliaryRepository.setRepositoryUrl(repositoryUrl);
            auxiliaryRepositoryRepository.save(newAuxiliaryRepository);
        }

        // Unprotect the default branch of the template exercise repo.
        VcsRepositoryUrl templateVcsRepositoryUrl = newExercise.getVcsTemplateRepositoryUrl();
        String templateVcsRepositoryBranch = versionControlService.get().getOrRetrieveBranchOfExercise(templateExercise);
        versionControlService.get().unprotectBranch(templateVcsRepositoryUrl, templateVcsRepositoryBranch);

        // Add the necessary hooks notifying Artemis about changes after commits have been pushed
        versionControlService.get().addWebHooksForExercise(newExercise);

        try {
            // Adjust placeholders that were replaced during creation of template exercise
            adjustProjectNames(templateExercise, newExercise);
        }
        catch (GitAPIException | IOException e) {
            log.error("Error during adjustment of placeholders of ProgrammingExercise {}", newExercise.getTitle(), e);
        }
    }

    private void importRepositoriesFromFile(ProgrammingExercise newExercise, Path basePath, String oldExerciseShortName) throws IOException, GitAPIException, URISyntaxException {
        Repository templateRepo = gitService.getOrCheckoutRepository(new VcsRepositoryUrl(newExercise.getTemplateRepositoryUrl()), false);
        Repository solutionRepo = gitService.getOrCheckoutRepository(new VcsRepositoryUrl(newExercise.getSolutionRepositoryUrl()), false);
        Repository testRepo = gitService.getOrCheckoutRepository(new VcsRepositoryUrl(newExercise.getTestRepositoryUrl()), false);

        copyImportedExerciseContentToRepositories(templateRepo, solutionRepo, testRepo, basePath);
        replaceImportedExerciseShortName(Map.of(oldExerciseShortName, newExercise.getShortName()), List.of("gradle-wrapper.jar"), templateRepo, solutionRepo, testRepo);

        gitService.stageAllChanges(templateRepo);
        gitService.stageAllChanges(solutionRepo);
        gitService.stageAllChanges(testRepo);
        gitService.commitAndPush(templateRepo, "Import template from file", true, null);
        gitService.commitAndPush(solutionRepo, "Import solution from file", true, null);
        gitService.commitAndPush(testRepo, "Import tests from file", true, null);

    }

    private void replaceImportedExerciseShortName(Map<String, String> replacements, List<String> exclusions, Repository... repositories) throws IOException {
        for (Repository repository : repositories) {
            fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toString(), replacements, exclusions);
        }
    }

    private void copyImportedExerciseContentToRepositories(Repository templateRepo, Repository solutionRepo, Repository testRepo, Path basePath) throws IOException {
        repositoryService.deleteAllContentInRepository(templateRepo);
        repositoryService.deleteAllContentInRepository(solutionRepo);
        repositoryService.deleteAllContentInRepository(testRepo);
        copyExerciseContentToRepository(templateRepo, RepositoryType.TEMPLATE, basePath);
        copyExerciseContentToRepository(solutionRepo, RepositoryType.SOLUTION, basePath);
        copyExerciseContentToRepository(testRepo, RepositoryType.TESTS, basePath);
    }

    private void copyExerciseContentToRepository(Repository repository, RepositoryType repositoryType, Path basePath) throws IOException {
        // copy everything but the .git folder
        FileUtils.copyDirectory(retrieveRepositoryDirectoryPath(basePath, repositoryType.getName()).toFile(), repository.getLocalPath().toFile(),
                new NotFileFilter(new NameFileFilter(".git")));
        repository.setContent(null);
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

        // Clone all build plans, enable them and set up the initial participations, i.e. setting the correct repo URLs and
        // running the plan for the first time
        cloneAndEnableAllBuildPlans(templateExercise, newExercise);

        updatePlanRepositoriesInBuildPlans(newExercise, templateParticipation, solutionParticipation, targetExerciseProjectKey, templateExercise.getTemplateRepositoryUrl(),
                templateExercise.getSolutionRepositoryUrl(), templateExercise.getTestRepositoryUrl(), templateExercise.getAuxiliaryRepositoriesForBuildPlan());

        try {
            continuousIntegrationTriggerService.get().triggerBuild(templateParticipation);
            continuousIntegrationTriggerService.get().triggerBuild(solutionParticipation);
        }
        catch (ContinuousIntegrationException e) {
            log.error("Unable to trigger imported build plans", e);
            throw e;
        }
    }

    private void updatePlanRepositoriesInBuildPlans(ProgrammingExercise newExercise, TemplateProgrammingExerciseParticipation templateParticipation,
            SolutionProgrammingExerciseParticipation solutionParticipation, String targetExerciseProjectKey, String oldExerciseRepoUrl, String oldSolutionRepoUrl,
            String oldTestRepoUrl, List<AuxiliaryRepository> oldBuildPlanAuxiliaryRepositories) {
        String newExerciseBranch = versionControlService.get().getOrRetrieveBranchOfExercise(newExercise);

        // update 2 repositories for the BASE build plan --> adapt the triggers so that only the assignment repo (and not the tests' repo) will trigger the BASE build plan
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, templateParticipation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTemplateRepositoryUrl(), oldExerciseRepoUrl, newExerciseBranch, List.of(ASSIGNMENT_REPO_NAME));

        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, templateParticipation.getBuildPlanId(), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUrl(), oldTestRepoUrl, newExerciseBranch, List.of());

        updateAuxiliaryRepositoriesForNewExercise(newExercise.getAuxiliaryRepositoriesForBuildPlan(), oldBuildPlanAuxiliaryRepositories, templateParticipation,
                targetExerciseProjectKey, newExercise);

        // update 2 repositories for the SOLUTION build plan
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, solutionParticipation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, targetExerciseProjectKey,
                newExercise.getSolutionRepositoryUrl(), oldSolutionRepoUrl, newExerciseBranch, List.of());
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, solutionParticipation.getBuildPlanId(), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUrl(), oldTestRepoUrl, newExerciseBranch, List.of());

        updateAuxiliaryRepositoriesForNewExercise(newExercise.getAuxiliaryRepositoriesForBuildPlan(), oldBuildPlanAuxiliaryRepositories, solutionParticipation,
                targetExerciseProjectKey, newExercise);
    }

    private void updateAuxiliaryRepositoriesForNewExercise(List<AuxiliaryRepository> newRepositories, List<AuxiliaryRepository> oldRepositories,
            AbstractBaseProgrammingExerciseParticipation participation, String targetExerciseProjectKey, ProgrammingExercise newExercise) {
        for (int i = 0; i < newRepositories.size(); i++) {
            AuxiliaryRepository newAuxiliaryRepository = newRepositories.get(i);
            AuxiliaryRepository oldAuxiliaryRepository = oldRepositories.get(i);
            String auxiliaryBranch = versionControlService.get().getOrRetrieveBranchOfExercise(newExercise);
            continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, participation.getBuildPlanId(), newAuxiliaryRepository.getName(),
                    targetExerciseProjectKey, newAuxiliaryRepository.getRepositoryUrl(), oldAuxiliaryRepository.getRepositoryUrl(), auxiliaryBranch, List.of());
        }
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
     * @throws IOException     If the values in the files could not be replaced
     */
    private void adjustProjectName(Map<String, String> replacements, String projectKey, String repositoryName, User user) throws GitAPIException, IOException {
        final var repositoryUrl = versionControlService.get().getCloneRepositoryUrl(projectKey, repositoryName);
        Repository repository = gitService.getOrCheckoutRepository(repositoryUrl, true);
        fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), replacements, List.of("gradle-wrapper.jar"));
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, "Template adjusted by Artemis", true, user);
        repository.setFiles(null); // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
    }

    /**
     * Method to import a programming exercise, including all base build plans (template, solution) and repositories (template, solution, test).
     * Referenced entities, s.a. the test cases or the hints will get cloned and assigned a new id.
     *
     * @param originalProgrammingExercise the Programming Exercise which should be used as a blueprint
     * @param newExercise                 The new exercise already containing values which should not get copied, i.e. overwritten
     * @param updateTemplate              if the template files should be updated
     * @param recreateBuildPlans          if the build plans should be recreated
     * @return the imported programming exercise
     */
    public ProgrammingExercise importProgrammingExercise(ProgrammingExercise originalProgrammingExercise, ProgrammingExercise newExercise, boolean updateTemplate,
            boolean recreateBuildPlans) {
        newExercise.generateAndSetProjectKey();
        programmingExerciseService.checkIfProjectExists(newExercise);

        final var importedProgrammingExercise = programmingExerciseImportBasicService.importProgrammingExerciseBasis(originalProgrammingExercise, newExercise);
        importRepositories(originalProgrammingExercise, importedProgrammingExercise);

        // Update the template files
        if (updateTemplate) {
            TemplateUpgradeService upgradeService = templateUpgradePolicy.getUpgradeService(importedProgrammingExercise.getProgrammingLanguage());
            upgradeService.upgradeTemplate(importedProgrammingExercise);
        }

        if (recreateBuildPlans) {
            // Create completely new build plans for the exercise
            programmingExerciseService.setupBuildPlansForNewExercise(importedProgrammingExercise);
        }
        else {
            // We have removed the automatic build trigger from test to base for new programming exercises.
            // We also remove this build trigger in the case of an import as the source exercise might still have this trigger.
            // The importBuildPlans method includes this process
            importBuildPlans(originalProgrammingExercise, importedProgrammingExercise);
        }

        programmingExerciseService.scheduleOperations(importedProgrammingExercise.getId());
        return importedProgrammingExercise;
    }

    private Path retrieveRepositoryDirectoryPath(Path dirPath, String repoType) {
        List<Path> result;
        try (Stream<Path> walk = Files.walk(dirPath)) {
            result = walk.filter(Files::isDirectory).filter(f -> f.getFileName().toString().endsWith("-" + repoType)).filter(f -> !f.getFileName().endsWith(".zip")).toList();
        }
        catch (IOException e) {
            throw new BadRequestAlertException("Could not read the directory", "programmingExercise", "couldnotreaddirectory");
        }
        if (result.size() != 1) {
            throw new IllegalArgumentException(
                    "There are either no or more than one sub-directories containing " + repoType + " in their name. Please make sure that there is exactly one.");
        }

        return result.get(0);
    }

    private Path retrieveExerciseJsonPath(Path dirPath) throws IOException {
        List<Path> result;
        try (Stream<Path> stream = Files.walk(dirPath)) {
            // if we do not convert the file name to a string, the second filter always returns false
            // for the third filter, we need to convert it to a string as well as a path doesn't contain a file extension

            result = stream.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().startsWith("Exercise-Details"))
                    .filter(file -> file.toString().endsWith(".json")).toList();
        }

        if (result.size() != 1) {
            throw new BadRequestAlertException("There are either no or more than one json file in the directory!", "programmingExercise", "exerciseJsonNotValidOrFound");
        }
        return result.get(0);
    }

    /**
     * Imports a programming exercise from an uploaded zip file that has previously been downloaded from an Artemis instance.
     * It first extracts the contents of the zip file, then creates a programming exercise (same process as creating a new one),
     * then deletes the template content initially pushed to the repositories and copies over the extracted content
     *
     * @param programmingExerciseForImport the programming exercise that should be imported
     * @param zipFile                      the zip file that contains the exercise
     * @param course                       the course to which the exercise should be added
     * @return the imported programming exercise
     **/
    public ProgrammingExercise importProgrammingExerciseFromFile(ProgrammingExercise programmingExerciseForImport, MultipartFile zipFile, Course course)
            throws IOException, GitAPIException, URISyntaxException {
        Path importExerciseDir = Files.createTempDirectory("imported-exercise-dir");
        Path exerciseFilePath = Files.createTempFile(importExerciseDir, "exercise-for-import", ".zip");
        if (zipFile.getName().toLowerCase().endsWith(".zip")) {
            throw new BadRequestAlertException("The file is not a zip file", "programmingExercise", "fileNotZip");
        }
        zipFile.transferTo(exerciseFilePath);
        zipFileService.extractZipFileRecursively(exerciseFilePath);
        checkRepositoriesExist(importExerciseDir);
        var oldShortName = getProgrammingExerciseFromDetailsFile(importExerciseDir).getShortName();
        programmingExerciseService.validateNewProgrammingExerciseSettings(programmingExerciseForImport, course);
        ProgrammingExercise importedProgrammingExercise = programmingExerciseService.createProgrammingExercise(programmingExerciseForImport);
        if (Boolean.TRUE.equals(programmingExerciseForImport.isStaticCodeAnalysisEnabled())) {
            staticCodeAnalysisService.createDefaultCategories(importedProgrammingExercise);
        }
        importRepositoriesFromFile(importedProgrammingExercise, importExerciseDir, oldShortName);
        return importedProgrammingExercise;
    }

    private ProgrammingExercise getProgrammingExerciseFromDetailsFile(Path extractedZipPath) throws IOException {
        var exerciseJsonPath = retrieveExerciseJsonPath(extractedZipPath);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.findAndRegisterModules();

        try {
            return objectMapper.readValue(exerciseJsonPath.toFile(), ProgrammingExercise.class);
        }
        catch (IOException e) {
            throw new BadRequestAlertException("The JSON file for the programming exercise is not valid or was not found.", "programmingExercise", "exerciseJsonNotValidOrFound");
        }
    }

    private void checkRepositoriesExist(Path path) throws IOException {
        List<String> result;
        try (Stream<Path> stream = Files.walk(path)) {
            result = stream.filter(Files::isDirectory).map(f -> f.getFileName().toString())
                    .filter(name -> name.endsWith("-exercise") || name.endsWith("-tests") || name.endsWith("-solution")).toList();
        }

        if (result.size() != 3) {
            throw new BadRequestAlertException("The zip file doesn't contain the template, solution or tests repository or they do not follow the naming scheme.",
                    "programmingExercise", "repositoriesInZipNotValid");
        }

    }
}
