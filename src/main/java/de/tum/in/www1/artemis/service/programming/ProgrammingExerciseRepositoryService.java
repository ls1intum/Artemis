package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.SETUP_COMMIT_MESSAGE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.web.rest.SubmissionPolicyResource;

@Service
public class ProgrammingExerciseRepositoryService {

    private static final String TEST_FILES_PATH = "testFiles";

    private static final String TEST_DIR = "test";

    private static final String POM_XML = "pom.xml";

    private static final String BUILD_GRADLE = "build.gradle";

    private static final String PACKAGE_NAME_FOLDER_PLACEHOLDER = "${packageNameFolder}";

    private static final String PACKAGE_NAME_FILE_PLACEHOLDER = "${packageNameFile}";

    private static final String PACKAGE_NAME_PLACEHOLDER = "${packageName}";

    private static final String APP_NAME_PLACEHOLDER = "${appName}";

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseRepositoryService.class);

    private final FileService fileService;

    private final GitService gitService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ResourceLoaderService resourceLoaderService;

    private final Optional<VersionControlService> versionControlService;

    public ProgrammingExerciseRepositoryService(FileService fileService, GitService gitService, InstanceMessageSendService instanceMessageSendService,
            ResourceLoaderService resourceLoaderService, Optional<VersionControlService> versionControlService) {
        this.fileService = fileService;
        this.gitService = gitService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.resourceLoaderService = resourceLoaderService;
        this.versionControlService = versionControlService;
    }

    /**
     * Stage, commit and push.
     *
     * @param repository  The repository to which the changes should get pushed
     * @param message     The commit message
     * @param emptyCommit whether an empty commit should be created or not
     * @param user        the user who has initiated the generation of the programming exercise
     * @throws GitAPIException If committing, or pushing to the repo throws an exception
     */
    void commitAndPushRepository(final Repository repository, final String message, final boolean emptyCommit, final User user) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, message, emptyCommit, user);

        // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
        repository.setFiles(null);
    }

    /**
     * Set up the exercise template by determining the files needed for the template and copying them. Commit and push the changes to all repositories for this programming
     * exercise.
     *
     * @param programmingExercise the programming exercise that should be set up
     * @param exerciseCreator     the User that performed the action (used as Git commit author)
     */
    void setupExerciseTemplate(final ProgrammingExercise programmingExercise, final User exerciseCreator) throws GitAPIException {
        final RepositoryResources exerciseResources = getRepositoryResources(programmingExercise, RepositoryType.TEMPLATE);
        final RepositoryResources solutionResources = getRepositoryResources(programmingExercise, RepositoryType.SOLUTION);
        final RepositoryResources testResources = getRepositoryResources(programmingExercise, RepositoryType.TESTS);

        setupRepositories(programmingExercise, exerciseCreator, exerciseResources, solutionResources, testResources);
    }

    private record RepositoryResources(Repository repository, Resource[] resources, Path prefix, Resource[] projectTypeResources, Path projectTypePrefix) {
    }

    /**
     * Collects all the required resources to fill the initial repository for a new exercise.
     *
     * @param programmingExercise The exercise for which the repository is set up.
     * @param repositoryType      The type of the repository which is set up.
     * @return All required information about the required resources to set up the repository.
     * @throws GitAPIException Thrown in case cloning the initial empty repository fails.
     */
    private RepositoryResources getRepositoryResources(final ProgrammingExercise programmingExercise, final RepositoryType repositoryType) throws GitAPIException {
        final String programmingLanguage = programmingExercise.getProgrammingLanguage().toString().toLowerCase(Locale.ROOT);
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path projectTypeTemplateDir = getTemplateDirectoryForRepositoryType(repositoryType);

        final VcsRepositoryUrl repoUrl = programmingExercise.getRepositoryURL(repositoryType);
        final Repository repo = gitService.getOrCheckoutRepository(repoUrl, true);

        // Get path, files and prefix for the programming-language dependent files. They are copied first.
        final Path generalTemplatePath = ProgrammingExerciseService.getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage())
                .resolve(projectTypeTemplateDir);
        Resource[] resources = resourceLoaderService.getResources(generalTemplatePath);

        Path prefix = Path.of(programmingLanguage).resolve(projectTypeTemplateDir);

        Resource[] projectTypeResources = null;
        Path projectTypePrefix = null;

        if (projectType != null && !ProjectType.PLAIN.equals(projectType)) {
            // Get path, files and prefix for the project-type dependent files. They are copied last and can overwrite the resources from the programming language.
            final Path programmingLanguageProjectTypePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(),
                    projectType);
            final String projectTypePath = projectType.name().toLowerCase();
            final Path generalProjectTypePrefix = Path.of(programmingLanguage, projectTypePath);
            final Path projectTypeSpecificPrefix = generalProjectTypePrefix.resolve(projectTypeTemplateDir);
            final Path projectTypeTemplatePath = programmingLanguageProjectTypePath.resolve(projectTypeTemplateDir);

            final Resource[] projectTypeSpecificResources = resourceLoaderService.getResources(projectTypeTemplatePath);

            if (ProjectType.XCODE.equals(projectType)) {
                // For Xcode, we don't share source code, so we only copy files once
                prefix = projectTypeSpecificPrefix;
                resources = projectTypeSpecificResources;
            }
            else {
                projectTypePrefix = projectTypeSpecificPrefix;
                projectTypeResources = projectTypeSpecificResources;
            }
        }

        return new RepositoryResources(repo, resources, prefix, projectTypeResources, projectTypePrefix);
    }

    private Path getTemplateDirectoryForRepositoryType(final RepositoryType repositoryType) {
        if (RepositoryType.TESTS.equals(repositoryType)) {
            return Path.of(TEST_DIR);
        }
        else {
            return Path.of(repositoryType.getName());
        }
    }

    /**
     * Sets up the three initial repositories for a new exercise.
     *
     * @param programmingExercise The exercise that should be set up.
     * @param exerciseCreator     The user that wants to create the exercise
     * @param exerciseResources   The resources for the template repository.
     * @param solutionResources   The resources for the solution repository.
     * @param testResources       The resources for the repository containing the tests.
     * @throws GitAPIException Thrown in case pushing a repository fails.
     */
    private void setupRepositories(final ProgrammingExercise programmingExercise, final User exerciseCreator, final RepositoryResources exerciseResources,
            final RepositoryResources solutionResources, final RepositoryResources testResources) throws GitAPIException {
        try {
            setupTemplateAndPush(exerciseResources, "Exercise", programmingExercise, exerciseCreator);
            // The template repo can be re-written, so we can unprotect the default branch.
            final var templateVcsRepositoryUrl = programmingExercise.getVcsTemplateRepositoryUrl();
            final String templateBranch = versionControlService.orElseThrow().getOrRetrieveBranchOfExercise(programmingExercise);
            versionControlService.get().unprotectBranch(templateVcsRepositoryUrl, templateBranch);

            setupTemplateAndPush(solutionResources, "Solution", programmingExercise, exerciseCreator);
            setupTestTemplateAndPush(testResources, programmingExercise, exerciseCreator);
        }
        catch (Exception ex) {
            // if any exception occurs, try to at least push an empty commit, so that the
            // repositories can be used by the build plans
            log.warn("An exception occurred while setting up the repositories", ex);

            final String commitMessage = "Empty Setup by Artemis";
            gitService.commitAndPush(exerciseResources.repository, commitMessage, true, exerciseCreator);
            gitService.commitAndPush(testResources.repository, commitMessage, true, exerciseCreator);
            gitService.commitAndPush(solutionResources.repository, commitMessage, true, exerciseCreator);
        }
    }

    /**
     * Creates the three initial repositories, and any required auxiliary repositories in the version control system for a new exercise.
     *
     * @param programmingExercise A new programming exercise.
     * @throws GitAPIException Thrown in case creating a repository fails.
     */
    void createRepositoriesForNewExercise(final ProgrammingExercise programmingExercise) throws GitAPIException {
        final String projectKey = programmingExercise.getProjectKey();
        versionControlService.orElseThrow().createProjectForExercise(programmingExercise); // Create project
        versionControlService.orElseThrow().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE), null); // Create template repository
        versionControlService.orElseThrow().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.TESTS), null); // Create tests repository
        versionControlService.orElseThrow().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.SOLUTION), null); // Create solution repository

        // Create auxiliary repositories
        createAndInitializeAuxiliaryRepositories(projectKey, programmingExercise);
    }

    private void createAndInitializeAuxiliaryRepositories(final String projectKey, final ProgrammingExercise programmingExercise) throws GitAPIException {
        for (final AuxiliaryRepository repo : programmingExercise.getAuxiliaryRepositories()) {
            final String repositoryName = programmingExercise.generateRepositoryName(repo.getName());
            versionControlService.orElseThrow().createRepository(projectKey, repositoryName, null);
            repo.setRepositoryUrl(versionControlService.orElseThrow().getCloneRepositoryUrl(programmingExercise.getProjectKey(), repositoryName).toString());

            final Repository vcsRepository = gitService.getOrCheckoutRepository(repo.getVcsRepositoryUrl(), true);
            gitService.commitAndPush(vcsRepository, SETUP_COMMIT_MESSAGE, true, null);
        }
    }

    /**
     * Copy template and push, if no file is currently in the repository.
     *
     * @param templateName        The name of the template
     * @param programmingExercise the programming exercise
     * @param user                The user that triggered the action (used as Git commit author)
     * @throws IOException     Thrown in case resources could be copied into the local repository.
     * @throws GitAPIException Thrown in case pushing to the version control system failed.
     */
    private void setupTemplateAndPush(final RepositoryResources repositoryResources, final String templateName, final ProgrammingExercise programmingExercise, final User user)
            throws IOException, GitAPIException {
        // Only copy template if repo is empty
        if (!gitService.listFiles(repositoryResources.repository).isEmpty()) {
            return;
        }

        final Path repoLocalPath = getRepoAbsoluteLocalPath(repositoryResources.repository);

        fileService.copyResources(repositoryResources.resources, repositoryResources.prefix, repoLocalPath, true);
        // Also copy project type specific files AFTERWARDS (so that they might overwrite the default files)
        if (repositoryResources.projectTypeResources != null) {
            fileService.copyResources(repositoryResources.projectTypeResources, repositoryResources.projectTypePrefix, repoLocalPath, true);
        }

        replacePlaceholders(programmingExercise, repositoryResources.repository);
        commitAndPushRepository(repositoryResources.repository, templateName + "-Template pushed by Artemis", true, user);
    }

    private static Path getRepoAbsoluteLocalPath(final Repository repository) {
        return repository.getLocalPath().toAbsolutePath();
    }

    /**
     * Set up the test repository. This method differentiates non-sequential and sequential test repositories (more than 1 test job).
     *
     * @param resources           The resources which should get added to the template
     * @param programmingExercise The related programming exercise for which the template should get created
     * @param user                the user who has initiated the generation of the programming exercise
     * @throws IOException     Thrown in case copying files fails.
     * @throws GitAPIException Thrown in case pushing the updates to the version control system fails.
     */
    private void setupTestTemplateAndPush(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final User user) throws IOException, GitAPIException {
        // Only copy template if repo is empty
        if (gitService.listFiles(resources.repository).isEmpty()
                && (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA || programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.KOTLIN)) {
            setupJVMTestTemplateAndPush(resources, programmingExercise, user);
        }
        else {
            // If there is no special test structure for a programming language, just copy all the test files.
            setupTemplateAndPush(resources, "Test", programmingExercise, user);
        }
    }

    /**
     * Sets up the test repository for a programming exercise using a JVM-based programming language.
     *
     * @param resources           The resources the repository should be filled with.
     * @param programmingExercise The programming exercise the new repository belongs to.
     * @param user                The user that is creating the exercise.
     * @throws IOException     Thrown in case copying files fails.
     * @throws GitAPIException Thrown in case pushing the updates to the version control system fails.
     */
    private void setupJVMTestTemplateAndPush(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final User user)
            throws IOException, GitAPIException {
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path repoLocalPath = getRepoAbsoluteLocalPath(resources.repository);

        // First get files that are not dependent on the project type
        final Path templatePath = ProgrammingExerciseService.getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage()).resolve(TEST_DIR);

        // Java both supports Gradle and Maven as a test template
        Path projectTemplatePath = templatePath;
        if (projectType != null && projectType.isGradle()) {
            projectTemplatePath = projectTemplatePath.resolve("gradle");
        }
        else {
            projectTemplatePath = projectTemplatePath.resolve("maven");
        }
        projectTemplatePath = projectTemplatePath.resolve("projectTemplate");

        final Resource[] projectTemplate = resourceLoaderService.getResources(projectTemplatePath);
        // keep the folder structure
        fileService.copyResources(projectTemplate, Path.of("projectTemplate"), repoLocalPath, true);

        // These resources might override the programming language dependent resources as they are project type dependent.
        if (projectType != null) {
            setupJVMTestTemplateProjectTypeResources(resources, programmingExercise, repoLocalPath);
        }

        final Map<String, Boolean> sectionsMap = new HashMap<>();
        // Keep or delete static code analysis configuration in the build configuration file
        sectionsMap.put("static-code-analysis", Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()));
        // Keep or delete testwise coverage configuration in the build file
        sectionsMap.put("record-testwise-coverage", Boolean.TRUE.equals(programmingExercise.isTestwiseCoverageEnabled()));

        if (programmingExercise.hasSequentialTestRuns()) {
            setupTestTemplateSequentialTestRuns(resources, templatePath, projectTemplatePath, projectType, sectionsMap);
        }
        else {
            setupTestTemplateRegularTestRuns(resources, programmingExercise, templatePath, sectionsMap);
        }

        replacePlaceholders(programmingExercise, resources.repository);
        commitAndPushRepository(resources.repository, "Test-Template pushed by Artemis", true, user);
    }

    /**
     * Copies project type specific resources into the test repository.
     *
     * @param resources           The resources for the test repository.
     * @param programmingExercise The programming exercise the repository belongs to.
     * @param repoLocalPath       The local path where the repository can be found.
     * @throws IOException Thrown in case copying the resources to the repository fails.
     */
    private void setupJVMTestTemplateProjectTypeResources(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final Path repoLocalPath)
            throws IOException {
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path projectTypeTemplatePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType)
                .resolve(TEST_DIR);
        final Path projectTypeProjectTemplatePath = projectTypeTemplatePath.resolve("projectTemplate");

        try {
            final Resource[] projectTypeProjectTemplate = resourceLoaderService.getResources(projectTypeProjectTemplatePath);
            fileService.copyResources(projectTypeProjectTemplate, resources.projectTypePrefix, repoLocalPath, false);
        }
        catch (FileNotFoundException fileNotFoundException) {
            log.debug("Could not copy resource to template", fileNotFoundException);
        }
    }

    /**
     * Sets up the test repository for an exercise using regular (= non-sequential) test runs.
     *
     * @param resources           The resources for the test repository.
     * @param programmingExercise The programming exercise the repository belongs to.
     * @param templatePath        The local path in which the templates files can be found.
     * @param sectionsMap         Defines which parts of the template files should be copied based on the chosen exercise features.
     * @throws IOException Thrown in case copying some resource to the local repo fails.
     */
    private void setupTestTemplateRegularTestRuns(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final Path templatePath,
            final Map<String, Boolean> sectionsMap) throws IOException {
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path repoLocalPath = getRepoAbsoluteLocalPath(resources.repository);
        final Path testFilePath = templatePath.resolve(TEST_FILES_PATH);
        final Resource[] testFileResources = resourceLoaderService.getResources(testFilePath);
        final Path packagePath = repoLocalPath.resolve(TEST_DIR).resolve(PACKAGE_NAME_FOLDER_PLACEHOLDER).toAbsolutePath();

        sectionsMap.put("non-sequential", true);
        sectionsMap.put("sequential", false);

        setupBuildToolProjectFile(repoLocalPath, projectType, sectionsMap);

        fileService.copyResources(testFileResources, resources.prefix, packagePath, false);

        if (projectType != null) {
            overwriteProjectTypeSpecificFiles(resources, programmingExercise, packagePath);
        }

        // Copy static code analysis config files
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
            setupStaticCodeAnalysisConfigFiles(resources, templatePath, repoLocalPath);
        }
    }

    /**
     * Fills in placeholders in the build tool project definition file based on the enabled exercise features.
     *
     * @param repoLocalPath  The local path to the repository.
     * @param projectType    The exercise project type.
     * @param activeFeatures The active features in the exercise.
     */
    private void setupBuildToolProjectFile(final Path repoLocalPath, final ProjectType projectType, final Map<String, Boolean> activeFeatures) {
        final String projectFileFileName;
        if (projectType != null && projectType.isGradle()) {
            projectFileFileName = BUILD_GRADLE;
        }
        else {
            projectFileFileName = POM_XML;
        }

        fileService.replacePlaceholderSections(repoLocalPath.resolve(projectFileFileName).toAbsolutePath().toString(), activeFeatures);
    }

    private void setupStaticCodeAnalysisConfigFiles(final RepositoryResources resources, final Path templatePath, final Path repoLocalPath) throws IOException {
        final Path staticCodeAnalysisConfigPath = templatePath.resolve("staticCodeAnalysisConfig");
        final Resource[] staticCodeAnalysisResources = resourceLoaderService.getResources(staticCodeAnalysisConfigPath);
        fileService.copyResources(staticCodeAnalysisResources, resources.prefix, repoLocalPath, true);
    }

    private void overwriteProjectTypeSpecificFiles(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final Path packagePath) throws IOException {
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path projectTypeTemplatePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType)
                .resolve(TEST_DIR);

        try {
            final Resource[] projectTypeTestFileResources = resourceLoaderService.getResources(projectTypeTemplatePath);
            // filter non-existing resources to avoid exceptions
            final List<Resource> existingProjectTypeTestFileResources = new ArrayList<>();
            for (final Resource resource : projectTypeTestFileResources) {
                if (resource.exists()) {
                    existingProjectTypeTestFileResources.add(resource);
                }
            }

            if (!existingProjectTypeTestFileResources.isEmpty()) {
                fileService.copyResources(existingProjectTypeTestFileResources.toArray(new Resource[] {}), resources.projectTypePrefix, packagePath, false);
            }
        }
        catch (FileNotFoundException fileNotFoundException) {
            log.debug("Could not copy resource to template", fileNotFoundException);
        }
    }

    /**
     * Sets up the test repository for an exercise using sequential test runs.
     *
     * @param resources           The resources for the test repository.
     * @param templatePath        The local path in which the templates files can be found.
     * @param projectTemplatePath The local path in which the project type specific templates files can be found.
     * @param projectType         The project type of the exercise.
     * @param sectionsMap         Defines which parts of the template files should be copied based on the chosen exercise features.
     * @throws IOException Thrown in case copying some resource to the local repo fails.
     */
    private void setupTestTemplateSequentialTestRuns(final RepositoryResources resources, final Path templatePath, final Path projectTemplatePath, final ProjectType projectType,
            final Map<String, Boolean> sectionsMap) throws IOException {
        sectionsMap.put("non-sequential", false);
        sectionsMap.put("sequential", true);

        // maven configuration should be set for kotlin and older exercises where no project type has been introduced where no project type is defined
        final boolean isMaven = ProjectType.isMavenProject(projectType);

        final String projectFileName;
        if (isMaven) {
            projectFileName = POM_XML;
        }
        else {
            projectFileName = BUILD_GRADLE;
        }

        final Path repoLocalPath = getRepoAbsoluteLocalPath(resources.repository);

        fileService.replacePlaceholderSections(repoLocalPath.resolve(projectFileName).toAbsolutePath().toString(), sectionsMap);

        final Optional<Resource> stagePomXml = getStagePomXml(templatePath, projectTemplatePath, isMaven);

        // This is done to prepare for a feature where instructors/tas can add multiple build stages.
        setupBuildStage(resources.prefix, templatePath, projectTemplatePath, projectType, repoLocalPath, stagePomXml, BuildStage.STRUCTURAL);
        setupBuildStage(resources.prefix, templatePath, projectTemplatePath, projectType, repoLocalPath, stagePomXml, BuildStage.BEHAVIOR);
    }

    private Optional<Resource> getStagePomXml(final Path templatePath, final Path projectTemplatePath, final boolean isMaven) {
        if (!isMaven) {
            return Optional.empty();
        }

        final Path stagePomXmlName = Path.of("stagePom.xml");

        Path stagePomXmlPath = templatePath.resolve(stagePomXmlName);
        if (projectTemplatePath.resolve(stagePomXmlName).toFile().exists()) {
            stagePomXmlPath = projectTemplatePath.resolve(stagePomXmlName);
        }

        return Optional.ofNullable(resourceLoaderService.getResource(stagePomXmlPath));
    }

    private enum BuildStage {

        STRUCTURAL, BEHAVIOR;

        Path getTemplateDirectory() {
            return switch (this) {
                case STRUCTURAL -> Path.of("structural");
                case BEHAVIOR -> Path.of("behavior");
            };
        }
    }

    /**
     * Sets up the resources specific to exercises that use multiple build stages.
     *
     * @param resourcePrefix      The root of the general template directory.
     * @param templatePath        The path in which the template test files can be found.
     * @param projectTemplatePath The path in which the template test files can be found in case the exercise has a project type.
     * @param projectType         The project type of the exercise.
     * @param repoLocalPath       The local path of the repository for the exercise.
     * @param stagePomXml         A {@code pom.xml} file for this build stage.
     * @param buildStage          The build stage that should be set up.
     * @throws IOException Thrown in case reading template files, or writing them to the local repository fails.
     */
    private void setupBuildStage(final Path resourcePrefix, final Path templatePath, final Path projectTemplatePath, final ProjectType projectType, final Path repoLocalPath,
            final Optional<Resource> stagePomXml, final BuildStage buildStage) throws IOException {
        final Path buildStageTemplateSubDirectory = buildStage.getTemplateDirectory();
        final Path buildStagePath = repoLocalPath.resolve(buildStageTemplateSubDirectory);
        Files.createDirectory(buildStagePath);

        Files.createDirectory(buildStagePath.toAbsolutePath().resolve(TEST_DIR));
        Files.createDirectory(buildStagePath.toAbsolutePath().resolve(TEST_DIR).resolve(PACKAGE_NAME_FOLDER_PLACEHOLDER));

        final Path packagePath = buildStagePath.toAbsolutePath().resolve(TEST_DIR).resolve(PACKAGE_NAME_FOLDER_PLACEHOLDER).toAbsolutePath();

        // staging project files are only required for maven
        final boolean isMaven = ProjectType.isMavenProject(projectType);
        if (isMaven && stagePomXml.isPresent()) {
            Files.copy(stagePomXml.get().getInputStream(), buildStagePath.resolve(POM_XML));
        }

        final Path buildStageResourcesPath = templatePath.resolve(TEST_FILES_PATH).resolve(buildStageTemplateSubDirectory);
        final Resource[] buildStageResources = resourceLoaderService.getResources(buildStageResourcesPath);
        fileService.copyResources(buildStageResources, resourcePrefix, packagePath, false);

        if (projectType != null) {
            overwriteStageFilesForProjectType(resourcePrefix, projectTemplatePath, buildStageTemplateSubDirectory, packagePath);
        }
    }

    private void overwriteStageFilesForProjectType(final Path resourcePrefix, final Path projectTemplatePath, final Path buildStageTemplateSubDirectory, final Path packagePath)
            throws IOException {
        final Path buildStageResourcesPath = projectTemplatePath.resolve(TEST_FILES_PATH).resolve(buildStageTemplateSubDirectory);
        try {
            final Resource[] buildStageResources = resourceLoaderService.getResources(buildStageResourcesPath);
            fileService.copyResources(buildStageResources, resourcePrefix, packagePath, false);
        }
        catch (FileNotFoundException fileNotFoundException) {
            log.debug("Could not copy resource to template", fileNotFoundException);
        }
    }

    /**
     * Replace placeholders in repository files (e.g. ${placeholder}).
     *
     * @param programmingExercise The related programming exercise
     * @param repository          The repository in which the placeholders should get replaced
     * @throws IOException If replacing the directory name, or file variables throws an exception
     */
    void replacePlaceholders(final ProgrammingExercise programmingExercise, final Repository repository) throws IOException {
        final Map<String, String> replacements = new HashMap<>();
        final ProgrammingLanguage programmingLanguage = programmingExercise.getProgrammingLanguage();

        switch (programmingLanguage) {
            case JAVA, KOTLIN -> {
                fileService.replaceVariablesInDirectoryName(getRepoAbsoluteLocalPath(repository).toString(), PACKAGE_NAME_FOLDER_PLACEHOLDER,
                        programmingExercise.getPackageFolderName());
                replacements.put(PACKAGE_NAME_PLACEHOLDER, programmingExercise.getPackageName());
            }
            case SWIFT -> replaceSwiftPlaceholders(replacements, programmingExercise, repository);
            default -> {
                // no special package name replacements needed for other programming languages
            }
        }

        replacements.put("${exerciseNamePomXml}", programmingExercise.getTitle().replace(" ", "-")); // Used e.g. in artifactId
        replacements.put("${exerciseName}", programmingExercise.getTitle());
        replacements.put("${studentWorkingDirectory}", Constants.STUDENT_WORKING_DIRECTORY);
        replacements.put("${packaging}", programmingExercise.hasSequentialTestRuns() ? "pom" : "jar");
        fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), replacements, List.of("gradle-wrapper.jar"));
    }

    /**
     * Replaces Swift specific placeholders in repository files.
     *
     * @param replacements        A set of known replacements that can be reused in other files.
     * @param programmingExercise The exercise for which the replacements are performed.
     * @param repository          The repository in which the replacements are performed.
     * @throws IOException Thrown if accessing repository files fails.
     */
    private void replaceSwiftPlaceholders(final Map<String, String> replacements, final ProgrammingExercise programmingExercise, final Repository repository) throws IOException {
        final String repositoryLocalPath = getRepoAbsoluteLocalPath(repository).toString();
        final String packageName = programmingExercise.getPackageName();

        if (ProjectType.PLAIN.equals(programmingExercise.getProjectType())) {
            fileService.replaceVariablesInDirectoryName(repositoryLocalPath, PACKAGE_NAME_FOLDER_PLACEHOLDER, packageName);
            fileService.replaceVariablesInFileName(repositoryLocalPath, PACKAGE_NAME_FILE_PLACEHOLDER, packageName);

            replacements.put(PACKAGE_NAME_PLACEHOLDER, packageName);
        }
        else if (ProjectType.XCODE.equals(programmingExercise.getProjectType())) {
            fileService.replaceVariablesInDirectoryName(repositoryLocalPath, APP_NAME_PLACEHOLDER, packageName);
            fileService.replaceVariablesInFileName(repositoryLocalPath, APP_NAME_PLACEHOLDER, packageName);

            replacements.put(APP_NAME_PLACEHOLDER, packageName);
        }
    }

    /**
     * Locks or unlocks the participations and repositories if necessary due to the changes in the programming exercise.
     * This might be because of changes in the release date or due date, or because of a change in whether offline IDEs are allowed or not.
     * As of now the submission policy cannot be changed here. See {@link SubmissionPolicyResource#updateSubmissionPolicy(Long, SubmissionPolicy)} for that.
     *
     * @param programmingExerciseBeforeUpdate the original exercise with unchanged values
     * @param updatedProgrammingExercise      the updated exercise with new values
     */
    public void handleRepoAccessRightChanges(final ProgrammingExercise programmingExerciseBeforeUpdate, final ProgrammingExercise updatedProgrammingExercise) {

        final ZonedDateTime now = ZonedDateTime.now();

        // Figure out if we have to lock repositories and participations.
        // This is the case if the updated configuration further restricts when students can submit.

        // Case 1: The exercise start date was unset or in the past and the update moves it into the future.
        boolean stricterStartDate = programmingExerciseBeforeUpdate.isReleased() && !updatedProgrammingExercise.isReleased();

        if (stricterStartDate) {
            // In this case we don't have to consider any of the other attributes. No repository or participation should be unlocked if the start date is in the future.
            instanceMessageSendService.sendLockAllStudentRepositoriesAndParticipations(programmingExerciseBeforeUpdate.getId());
            return;
        }

        // Case 2: The exercise due date was unset or in the future and is moved to the past.
        boolean stricterDueDate = (programmingExerciseBeforeUpdate.getDueDate() == null || programmingExerciseBeforeUpdate.getDueDate().isAfter(now))
                && (updatedProgrammingExercise.getDueDate() != null && updatedProgrammingExercise.getDueDate().isBefore(now));

        // Case 3: Offline IDE usage was allowed and is now disallowed.
        // Note: isAllowOfflineIde() == null means that the offline IDE is allowed.
        boolean oldExerciseAllowsIdeUsage = !Boolean.FALSE.equals(programmingExerciseBeforeUpdate.isAllowOfflineIde());
        boolean updatedExerciseAllowsIdeUsage = !Boolean.FALSE.equals(updatedProgrammingExercise.isAllowOfflineIde());
        boolean stricterIdeUsage = oldExerciseAllowsIdeUsage && !updatedExerciseAllowsIdeUsage;

        if (stricterIdeUsage) {
            // Lock all repositories but leave the participations untouched as the locked state of the participations is independent of whether offline IDE usage is allowed or not.
            instanceMessageSendService.sendLockAllStudentRepositories(programmingExerciseBeforeUpdate.getId());
        }

        if (stricterDueDate) {
            if (stricterIdeUsage) {
                // Repositories were already locked in the step before. Only lock the participations that are not allowed to submit under the updated configuration, i.e. with a due
                // date in the past.
                instanceMessageSendService.sendLockAllStudentParticipationsWithEarlierDueDate(programmingExerciseBeforeUpdate.getId());
            }
            else {
                // Lock all repositories and participations that are not allowed to submit under the updated configuration.
                instanceMessageSendService.sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(programmingExerciseBeforeUpdate.getId());
            }
        }

        // Figure out if we have to unlock repositories and participations.
        // This is the case if the updated configuration relaxes when students can submit.

        // Case 1: The exercise start date was in the future and is moved to the past or is unset.
        boolean moreLenientStartDate = !programmingExerciseBeforeUpdate.isReleased() && updatedProgrammingExercise.isReleased();

        // Case 2: The exercise due date was in the past and is moved to the future or is unset.
        boolean moreLenientDueDate = (programmingExerciseBeforeUpdate.getDueDate() != null && programmingExerciseBeforeUpdate.getDueDate().isBefore(now))
                && (updatedProgrammingExercise.getDueDate() == null || updatedProgrammingExercise.getDueDate().isAfter(now));

        // Case 3: Offline IDE usage was disallowed and is now allowed.
        boolean moreLenientIdeUsage = !oldExerciseAllowsIdeUsage && updatedExerciseAllowsIdeUsage;

        if (moreLenientIdeUsage) {
            if (moreLenientStartDate || moreLenientDueDate) {
                // In this case unlock all repositories and participations within the time frame.
                instanceMessageSendService.sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
            }
            else {
                // If the start date or the due date were not changed, the participations are not affected, and we only unlock the repositories.
                instanceMessageSendService.sendUnlockAllStudentRepositoriesWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
            }
        }
        else if (moreLenientStartDate || moreLenientDueDate) {
            // The offline IDE usage was not changed, but the start date or the due date were changed.
            // Unlock the participations that are allowed to submit under the updated configuration, i.e. the current date is within the working time frame.
            // But only unlock the repositories in addition to the participations if offline IDE usage is allowed.
            if (updatedExerciseAllowsIdeUsage) {
                instanceMessageSendService.sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
            }
            else {
                instanceMessageSendService.sendUnlockAllStudentParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
            }
        }
    }

    /**
     * Deletes all repositories belonging to the exercise on the version control system.
     *
     * @param programmingExercise The programming exercise for which the repositories should be deleted.
     */
    void deleteRepositories(final ProgrammingExercise programmingExercise) {
        if (programmingExercise.getTemplateRepositoryUrl() != null) {
            final var templateRepositoryUrlAsUrl = programmingExercise.getVcsTemplateRepositoryUrl();
            versionControlService.orElseThrow().deleteRepository(templateRepositoryUrlAsUrl);
        }
        if (programmingExercise.getSolutionRepositoryUrl() != null) {
            final var solutionRepositoryUrlAsUrl = programmingExercise.getVcsSolutionRepositoryUrl();
            versionControlService.orElseThrow().deleteRepository(solutionRepositoryUrlAsUrl);
        }
        if (programmingExercise.getTestRepositoryUrl() != null) {
            final var testRepositoryUrlAsUrl = programmingExercise.getVcsTestRepositoryUrl();
            versionControlService.orElseThrow().deleteRepository(testRepositoryUrlAsUrl);
        }

        // We also want to delete any auxiliary repositories
        programmingExercise.getAuxiliaryRepositories().forEach(repo -> {
            if (repo.getRepositoryUrl() != null) {
                versionControlService.orElseThrow().deleteRepository(repo.getVcsRepositoryUrl());
            }
        });

        versionControlService.orElseThrow().deleteProject(programmingExercise.getProjectKey());
    }

    /**
     * Deletes the local clones of the exercise template, test, and solution repos.
     * <p>
     * Does <emph>not</emph> change the repositories on the version control system.
     * <p>
     * Always delete the local copies of the repository when deleting an exercise because they can (in theory) be
     * restored by cloning again, but they block the creation of new programming exercises with the same short name as
     * a deleted one. The instructors might have missed selecting deleteBaseReposBuildPlans, and delete those manually later
     * This however leaves no chance to remove the Artemis-local repositories on the server.
     * In summary, they should and can always be deleted.
     *
     * @param programmingExercise The exercise for which the local repository copies should be deleted.
     */
    void deleteLocalRepoCopies(final ProgrammingExercise programmingExercise) {
        if (programmingExercise.getTemplateRepositoryUrl() != null) {
            final var templateRepositoryUrlAsUrl = programmingExercise.getVcsTemplateRepositoryUrl();
            gitService.deleteLocalRepository(templateRepositoryUrlAsUrl);
        }
        if (programmingExercise.getSolutionRepositoryUrl() != null) {
            final var solutionRepositoryUrlAsUrl = programmingExercise.getVcsSolutionRepositoryUrl();
            gitService.deleteLocalRepository(solutionRepositoryUrlAsUrl);
        }
        if (programmingExercise.getTestRepositoryUrl() != null) {
            final var testRepositoryUrlAsUrl = programmingExercise.getVcsTestRepositoryUrl();
            gitService.deleteLocalRepository(testRepositoryUrlAsUrl);
        }
    }
}
