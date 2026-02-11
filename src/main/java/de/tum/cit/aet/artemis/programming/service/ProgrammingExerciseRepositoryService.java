package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.SETUP_COMMIT_MESSAGE;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.isMavenProject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseRepositoryService {

    private static final String TEST_FILES_PATH = "testFiles";

    private static final String TEST_DIR = "test";

    private static final String STATIC_CODE_ANALYSIS_DIR = "staticCodeAnalysis";

    private static final String POM_XML = "pom.xml";

    private static final String BUILD_GRADLE = "build.gradle";

    private static final String PACKAGE_NAME_FOLDER_PLACEHOLDER = "${packageNameFolder}";

    private static final String PACKAGE_NAME_FILE_PLACEHOLDER = "${packageNameFile}";

    private static final String PACKAGE_NAME_PLACEHOLDER = "${packageName}";

    private static final String APP_NAME_PLACEHOLDER = "${appName}";

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseRepositoryService.class);

    private final GitService gitService;

    private final UserRepository userRepository;

    private final ResourceLoaderService resourceLoaderService;

    private final Optional<VersionControlService> versionControlService;

    public ProgrammingExerciseRepositoryService(GitService gitService, UserRepository userRepository, ResourceLoaderService resourceLoaderService,
            Optional<VersionControlService> versionControlService) {
        this.gitService = gitService;
        this.userRepository = userRepository;
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
    }

    /**
     * Set up the exercise template by determining the files needed for the template and copying them. Commit and push the changes to all repositories for this programming
     * exercise.
     *
     * @param programmingExercise the programming exercise that should be set up
     * @param exerciseCreator     the User that performed the action (used as Git commit author)
     */
    void setupExerciseTemplate(final ProgrammingExercise programmingExercise, final User exerciseCreator) throws GitAPIException {
        setupExerciseTemplate(programmingExercise, exerciseCreator, false);
    }

    /**
     * Set up the exercise template by determining the files needed for the template and copying them. Commit and push the changes to all repositories for this programming
     * exercise.
     *
     * @param programmingExercise the programming exercise that should be set up
     * @param exerciseCreator     the User that performed the action (used as Git commit author)
     * @param emptyRepositories   if true, clear sources in template, solution, and test repositories after setup
     */
    void setupExerciseTemplate(final ProgrammingExercise programmingExercise, final User exerciseCreator, boolean emptyRepositories) throws GitAPIException {
        Objects.requireNonNull(programmingExercise, "ProgrammingExercise must not be null");
        Objects.requireNonNull(exerciseCreator, "Exercise creator must not be null");
        final RepositoryResources exerciseResources = getRepositoryResources(programmingExercise, RepositoryType.TEMPLATE);
        final RepositoryResources solutionResources = getRepositoryResources(programmingExercise, RepositoryType.SOLUTION);
        final RepositoryResources testResources = getRepositoryResources(programmingExercise, RepositoryType.TESTS);

        setupRepositories(programmingExercise, exerciseCreator, exerciseResources, solutionResources, testResources);

        if (emptyRepositories) {
            clearRepositorySources(exerciseResources.repository, RepositoryType.TEMPLATE, exerciseCreator);
            clearRepositorySources(solutionResources.repository, RepositoryType.SOLUTION, exerciseCreator);
            // Keep tests as a fallback in case AI generation is aborted or fails.
        }
    }

    private record RepositoryResources(Repository repository, Resource[] resources, Path prefix, Resource[] projectTypeResources, Path projectTypePrefix,
            Resource[] staticCodeAnalysisResources, Path staticCodeAnalysisPrefix) {
    }

    /**
     * Clears the repository sources while keeping build scaffolding in place.
     *
     * @param repository      the repository to clean
     * @param repositoryType  the repository type for logging and commit message
     * @param exerciseCreator the user performing the cleanup
     */
    void clearRepositorySources(final Repository repository, final RepositoryType repositoryType, final User exerciseCreator) throws GitAPIException {
        final String repositoryLabel = repositoryType.name().toLowerCase(Locale.ROOT);
        Path sourcePath = repository.getLocalPath().resolve("src");
        if (!Files.exists(sourcePath)) {
            log.warn("Skipping AI empty setup: no src directory found in {} repository {}", repositoryLabel, repository.getRemoteRepositoryUri());
            return;
        }
        try {
            FileUtils.cleanDirectory(sourcePath.toFile());
            Path keepFile = sourcePath.resolve(".gitkeep");
            if (!Files.exists(keepFile)) {
                FileUtils.writeStringToFile(keepFile.toFile(), "", StandardCharsets.UTF_8);
            }
            commitAndPushRepository(repository, "Cleared " + repositoryLabel + " sources for AI generation", true, exerciseCreator);
        }
        catch (IOException ex) {
            log.error("Failed to clean {} sources for AI generation", repositoryLabel, ex);
            GitAPIException exception = new GitAPIException("Failed to clean " + repositoryLabel + " sources for AI generation: " + ex.getMessage()) {
            };
            exception.initCause(ex);
            throw exception;
        }
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
        final Path repositoryTypeTemplateDir = getTemplateDirectoryForRepositoryType(repositoryType);

        final LocalVCRepositoryUri repoUri = programmingExercise.getRepositoryURI(repositoryType);
        final Repository repo = gitService.getOrCheckoutRepository(repoUri, false, true);

        // Get path, files and prefix for the programming-language dependent files. They are copied first.
        final Path generalTemplatePath = ProgrammingExerciseService.getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage())
                .resolve(repositoryTypeTemplateDir);
        Resource[] resources = resourceLoaderService.getFileResources(generalTemplatePath);

        Path prefix = Path.of(programmingLanguage).resolve(repositoryTypeTemplateDir);

        Resource[] projectTypeResources = null;
        Path projectTypePrefix = null;

        if (projectType != null && !ProjectType.PLAIN.equals(projectType)) {
            // Get path, files and prefix for the project-type dependent files. They are copied last and can overwrite the resources from the programming language.
            final Path programmingLanguageProjectTypePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(),
                    projectType);
            final String projectTypePath = projectType.name().toLowerCase();
            final Path generalProjectTypePrefix = Path.of(programmingLanguage, projectTypePath);
            final Path projectTypeSpecificPrefix = generalProjectTypePrefix.resolve(repositoryTypeTemplateDir);
            final Path projectTypeTemplatePath = programmingLanguageProjectTypePath.resolve(repositoryTypeTemplateDir);

            final Resource[] projectTypeSpecificResources = resourceLoaderService.getFileResources(projectTypeTemplatePath);

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

        Resource[] staticCodeAnalysisResources = null;
        Path staticCodeAnalysisPrefix = null;

        // SCA template resources only exist for the test repository and only for certain languages.
        // Java/Kotlin have their SCA config in test/staticCodeAnalysisConfig which is already included in the regular test templates.
        // Other languages (Python, TypeScript, etc.) have separate staticCodeAnalysis/test directories.
        if (programmingExercise.isStaticCodeAnalysisEnabled() && RepositoryType.TESTS.equals(repositoryType)
                && hasSeparateStaticCodeAnalysisTemplateFiles(programmingExercise.getProgrammingLanguage())) {
            Path programmingLanguageStaticCodeAnalysisPath = ProgrammingExerciseService.getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage())
                    .resolve(STATIC_CODE_ANALYSIS_DIR);
            final Path staticCodeAnalysisTemplatePath = programmingLanguageStaticCodeAnalysisPath.resolve(repositoryTypeTemplateDir);

            staticCodeAnalysisResources = resourceLoaderService.getFileResources(staticCodeAnalysisTemplatePath);
            staticCodeAnalysisPrefix = Path.of(programmingLanguage, STATIC_CODE_ANALYSIS_DIR).resolve(repositoryTypeTemplateDir);
        }

        return new RepositoryResources(repo, resources, prefix, projectTypeResources, projectTypePrefix, staticCodeAnalysisResources, staticCodeAnalysisPrefix);
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
     * Checks if the given programming language has separate static code analysis template files.
     * Languages like Python, TypeScript, JavaScript, etc. have a separate staticCodeAnalysis/test directory.
     * Java and Kotlin have their SCA config in test/staticCodeAnalysisConfig which is included in the regular test templates.
     *
     * @param programmingLanguage The programming language to check.
     * @return true if the language has separate SCA template files, false otherwise.
     */
    private boolean hasSeparateStaticCodeAnalysisTemplateFiles(ProgrammingLanguage programmingLanguage) {
        return switch (programmingLanguage) {
            // Languages that have a separate staticCodeAnalysis directory with template files
            case PYTHON, TYPESCRIPT, JAVASCRIPT, C_PLUS_PLUS, DART, R, RUBY -> true;
            default -> false;
        };
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
        versionControlService.orElseThrow().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE)); // Create template repository
        versionControlService.orElseThrow().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.TESTS)); // Create tests repository
        versionControlService.orElseThrow().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.SOLUTION)); // Create solution repository

        // Create auxiliary repositories
        createAndInitializeAuxiliaryRepositories(projectKey, programmingExercise);
    }

    /**
     * Creates and initializes all auxiliary repositories for a new programming exercise.
     *
     * @param projectKey          The key of the project the exercise belongs to. Can be found in the programming exercise.
     * @param programmingExercise The programming exercise for which the auxiliary repositories should be created.
     * @throws GitAPIException Thrown in case creating a repository fails.
     */
    private void createAndInitializeAuxiliaryRepositories(final String projectKey, final ProgrammingExercise programmingExercise) throws GitAPIException {
        for (final AuxiliaryRepository repo : programmingExercise.getAuxiliaryRepositories()) {
            final String repositoryName = programmingExercise.generateRepositoryName(repo.getName());
            versionControlService.orElseThrow().createRepository(projectKey, repositoryName);
            repo.setRepositoryUri(versionControlService.orElseThrow().getCloneRepositoryUri(programmingExercise.getProjectKey(), repositoryName).toString());

            final Repository vcsRepository = gitService.getOrCheckoutRepository(repo.getVcsRepositoryUri(), true, true);
            gitService.commitAndPush(vcsRepository, SETUP_COMMIT_MESSAGE, true, null);
        }
    }

    /**
     * Handles the VC part of auxiliary repositories when updating a programming exercise. Does not modify the database.
     *
     * @param programmingExerciseBeforeUpdate The programming exercise before the update
     * @param updatedProgrammingExercise      The programming exercise after the update
     */
    public void handleAuxiliaryRepositoriesWhenUpdatingExercises(ProgrammingExercise programmingExerciseBeforeUpdate, ProgrammingExercise updatedProgrammingExercise) {
        // Create new VC repositories for new auxiliary repositories
        updatedProgrammingExercise.getAuxiliaryRepositories().stream().filter(repo -> (repo.getId() == null)).forEach(repo -> {
            try {
                createAndInitializeAuxiliaryRepository(updatedProgrammingExercise, repo);
            }
            catch (GitAPIException ex) {
                log.error("Could not create new auxiliary repository", ex);
            }
        });

        // Remove VC repositories for deleted auxiliary repositories
        programmingExerciseBeforeUpdate.getAuxiliaryRepositories().stream()
                .filter(repo -> updatedProgrammingExercise.getAuxiliaryRepositories().stream().noneMatch(updatedRepo -> repo.getId().equals(updatedRepo.getId())))
                .forEach(this::removeAuxiliaryRepository);
    }

    /**
     * Creates and initializes a specific auxiliary repository if not existent.
     *
     * @param programmingExercise The programming exercise for which the repository should be created.
     * @throws GitAPIException Thrown in case creating the repository fails.
     */
    private void createAndInitializeAuxiliaryRepository(final ProgrammingExercise programmingExercise, final AuxiliaryRepository repo) throws GitAPIException {
        final String repositoryName = programmingExercise.generateRepositoryName(repo.getName());
        versionControlService.orElseThrow().createRepository(programmingExercise.getProjectKey(), repositoryName);
        repo.setRepositoryUri(versionControlService.orElseThrow().getCloneRepositoryUri(programmingExercise.getProjectKey(), repositoryName).toString());

        final Repository vcsRepository = gitService.getOrCheckoutRepository(repo.getVcsRepositoryUri(), true, true);
        gitService.commitAndPush(vcsRepository, SETUP_COMMIT_MESSAGE, true, null);
    }

    /**
     * Deletes an auxiliary repository from the version control system if existent. Performs no DB changes.
     *
     * @param auxiliaryRepository The auxiliary repository to delete.
     */
    private void removeAuxiliaryRepository(AuxiliaryRepository auxiliaryRepository) {
        if (auxiliaryRepository.getId() == null) {
            throw new IllegalArgumentException("Cannot delete auxiliary repository without id");
        }
        versionControlService.orElseThrow().deleteRepository(auxiliaryRepository.getVcsRepositoryUri());
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
    private void setupTemplateAndPush(RepositoryResources resources, String templateName, ProgrammingExercise programmingExercise, User user) throws IOException, GitAPIException {
        // Only copy template if repo is empty
        if (!gitService.getFiles(resources.repository).isEmpty()) {
            return;
        }

        final Path repoLocalPath = getRepoAbsoluteLocalPath(resources.repository);

        FileUtil.copyResources(resources.resources, resources.prefix, repoLocalPath, true);
        // Also copy project type and static code analysis specific files AFTERWARDS (so that they might overwrite the default files)
        if (resources.projectTypeResources != null) {
            FileUtil.copyResources(resources.projectTypeResources, resources.projectTypePrefix, repoLocalPath, true);
        }
        if (resources.staticCodeAnalysisResources != null) {
            FileUtil.copyResources(resources.staticCodeAnalysisResources, resources.staticCodeAnalysisPrefix, repoLocalPath, true);
        }

        replacePlaceholders(programmingExercise, resources.repository);
        commitAndPushRepository(resources.repository, templateName + "-Template pushed by Artemis", true, user);
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
        if (gitService.getFiles(resources.repository).isEmpty()
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
        // Java supports multiple variants as test template
        final Path projectTemplatePath = getJavaProjectTemplatePath(templatePath, projectType);

        final Resource[] projectTemplate = resourceLoaderService.getFileResources(projectTemplatePath);
        // keep the folder structure
        FileUtil.copyResources(projectTemplate, Path.of("projectTemplate"), repoLocalPath, true);

        // These resources might override the programming language dependent resources as they are project type dependent.
        if (projectType != null) {
            setupJVMTestTemplateProjectTypeResources(resources, programmingExercise, repoLocalPath);
        }

        if (ProjectType.MAVEN_BLACKBOX.equals(projectType)) {
            Path dejagnuLibFolderPath = repoLocalPath.resolve("testsuite").resolve("lib");
            FileUtil.replaceVariablesInFilename(dejagnuLibFolderPath, PACKAGE_NAME_FILE_PLACEHOLDER, programmingExercise.getPackageName());
        }

        final Map<String, Boolean> sectionsMap = new HashMap<>();
        // Keep or delete static code analysis configuration in the build configuration file
        sectionsMap.put("static-code-analysis", Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()));

        if (programmingExercise.getBuildConfig().hasSequentialTestRuns()) {
            setupTestTemplateSequentialTestRuns(resources, templatePath, projectTemplatePath, projectType, sectionsMap);
        }
        else {
            setupTestTemplateRegularTestRuns(resources, programmingExercise, templatePath, sectionsMap);
        }

        replacePlaceholders(programmingExercise, resources.repository);
        commitAndPushRepository(resources.repository, "Test-Template pushed by Artemis", true, user);
    }

    private static Path getJavaProjectTemplatePath(final Path templatePath, final ProjectType projectType) {
        Path projectTemplatePath = templatePath;

        if (projectType != null && projectType.isGradle()) {
            projectTemplatePath = projectTemplatePath.resolve("gradle");
        }
        else if (ProjectType.MAVEN_BLACKBOX.equals(projectType)) {
            projectTemplatePath = projectTemplatePath.resolve("blackbox");
        }
        else {
            projectTemplatePath = projectTemplatePath.resolve("maven");
        }

        return projectTemplatePath.resolve("projectTemplate");
    }

    /**
     * Copies project type specific resources into the test repository.
     *
     * @param resources           The resources for the test repository.
     * @param programmingExercise The programming exercise the repository belongs to.
     * @param repoLocalPath       The local path where the repository can be found.
     * @throws IOException Thrown in case copying the resources to the repository fails.
     */
    private void setupJVMTestTemplateProjectTypeResources(RepositoryResources resources, ProgrammingExercise programmingExercise, Path repoLocalPath) throws IOException {
        final ProjectType projectType = programmingExercise.getProjectType();
        if (projectType == null) {
            return; // No project type, so no project type specific files
        }
        final Path projectTypeTemplatePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType)
                .resolve(TEST_DIR);
        final Path projectTypeProjectTemplatePath = projectTypeTemplatePath.resolve("projectTemplate");

        try {
            final Resource[] projectTypeProjectTemplate = resourceLoaderService.getFileResources(projectTypeProjectTemplatePath);
            FileUtil.copyResources(projectTypeProjectTemplate, resources.projectTypePrefix, repoLocalPath, false);
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
        final Resource[] testFileResources = resourceLoaderService.getFileResources(testFilePath);
        final Path packagePath = repoLocalPath.resolve(TEST_DIR).resolve(PACKAGE_NAME_FOLDER_PLACEHOLDER).toAbsolutePath();

        sectionsMap.put("non-sequential", true);
        sectionsMap.put("sequential", false);

        setupBuildToolProjectFile(repoLocalPath, projectType, sectionsMap);

        if (programmingExercise.getProjectType() != ProjectType.MAVEN_BLACKBOX) {
            FileUtil.copyResources(testFileResources, resources.prefix, packagePath, false);
        }

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

        FileUtil.replacePlaceholderSections(repoLocalPath.resolve(projectFileFileName).toAbsolutePath(), activeFeatures);
    }

    private void setupStaticCodeAnalysisConfigFiles(final RepositoryResources resources, final Path templatePath, final Path repoLocalPath) throws IOException {
        final Path staticCodeAnalysisConfigPath = templatePath.resolve("staticCodeAnalysisConfig");
        final Resource[] staticCodeAnalysisResources = resourceLoaderService.getFileResources(staticCodeAnalysisConfigPath);
        FileUtil.copyResources(staticCodeAnalysisResources, resources.prefix, repoLocalPath, true);
    }

    private void overwriteProjectTypeSpecificFiles(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final Path packagePath) throws IOException {
        final ProjectType projectType = programmingExercise.getProjectType();
        if (projectType == null) {
            return; // No project type, so no project type specific files
        }
        final Path projectTypeTemplatePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType)
                .resolve(TEST_DIR);

        try {
            final Resource[] projectTypeTestFileResources = resourceLoaderService.getFileResources(projectTypeTemplatePath);
            // filter non-existing resources to avoid exceptions
            final List<Resource> existingProjectTypeTestFileResources = new ArrayList<>();
            for (final Resource resource : projectTypeTestFileResources) {
                if (resource.exists()) {
                    existingProjectTypeTestFileResources.add(resource);
                }
            }

            if (!existingProjectTypeTestFileResources.isEmpty()) {
                FileUtil.copyResources(existingProjectTypeTestFileResources.toArray(new Resource[] {}), resources.projectTypePrefix, packagePath, false);
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
        final boolean isMaven = isMavenProject(projectType);

        final String projectFileName;
        if (isMaven) {
            projectFileName = POM_XML;
        }
        else {
            projectFileName = BUILD_GRADLE;
        }

        final Path repoLocalPath = getRepoAbsoluteLocalPath(resources.repository);

        FileUtil.replacePlaceholderSections(repoLocalPath.resolve(projectFileName).toAbsolutePath(), sectionsMap);

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
        final boolean isMaven = isMavenProject(projectType);
        if (isMaven && stagePomXml.isPresent()) {
            FileUtils.copyInputStreamToFile(stagePomXml.get().getInputStream(), buildStagePath.resolve(POM_XML).toFile());
        }

        final Path buildStageResourcesPath = templatePath.resolve(TEST_FILES_PATH).resolve(buildStageTemplateSubDirectory);
        final Resource[] buildStageResources = resourceLoaderService.getFileResources(buildStageResourcesPath);
        FileUtil.copyResources(buildStageResources, resourcePrefix, packagePath, false);

        if (projectType != null) {
            overwriteStageFilesForProjectType(resourcePrefix, projectTemplatePath, buildStageTemplateSubDirectory, packagePath);
        }
    }

    private void overwriteStageFilesForProjectType(final Path resourcePrefix, final Path projectTemplatePath, final Path buildStageTemplateSubDirectory, final Path packagePath)
            throws IOException {
        final Path buildStageResourcesPath = projectTemplatePath.resolve(TEST_FILES_PATH).resolve(buildStageTemplateSubDirectory);
        try {
            final Resource[] buildStageResources = resourceLoaderService.getFileResources(buildStageResourcesPath);
            FileUtil.copyResources(buildStageResources, resourcePrefix, packagePath, false);
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
                FileUtil.replaceVariablesInDirectoryName(getRepoAbsoluteLocalPath(repository), PACKAGE_NAME_FOLDER_PLACEHOLDER, programmingExercise.getPackageFolderName());
                replacements.put(PACKAGE_NAME_PLACEHOLDER, programmingExercise.getPackageName());
            }
            case SWIFT -> replaceSwiftPlaceholders(replacements, programmingExercise, repository);
            case GO, DART -> replacements.put(PACKAGE_NAME_PLACEHOLDER, programmingExercise.getPackageName());
            default -> {
                // no special package name replacements needed for other programming languages
            }
        }

        replacements.put("${exerciseNamePomXml}", programmingExercise.getTitle().replace(" ", "-")); // Used e.g. in artifactId
        replacements.put("${exerciseName}", programmingExercise.getTitle());
        replacements.put("${packaging}", programmingExercise.getBuildConfig().hasSequentialTestRuns() ? "pom" : "jar");

        var buildConfig = programmingExercise.getBuildConfig();

        // replace checkout directory placeholders
        String studentWorkingDirectory = !StringUtils.isBlank(buildConfig.getAssignmentCheckoutPath()) ? buildConfig.getAssignmentCheckoutPath() : Constants.ASSIGNMENT_REPO_NAME;
        if (studentWorkingDirectory.startsWith("/")) {
            studentWorkingDirectory = studentWorkingDirectory.substring(1);
        }
        String testWorkingDirectory = buildConfig.getTestCheckoutPath() != null && !buildConfig.getTestCheckoutPath().isBlank() ? buildConfig.getTestCheckoutPath()
                : Constants.TEST_REPO_NAME;
        String solutionWorkingDirectory = buildConfig.getSolutionCheckoutPath() != null && !buildConfig.getSolutionCheckoutPath().isBlank() ? buildConfig.getSolutionCheckoutPath()
                : Constants.SOLUTION_REPO_NAME;

        if (programmingLanguage == ProgrammingLanguage.PYTHON) {
            replacements.put(Constants.ASSIGNMENT_REPO_PARENT_PLACEHOLDER, studentWorkingDirectory.replace("/", "."));
        }
        else {
            replacements.put(Constants.ASSIGNMENT_REPO_PARENT_PLACEHOLDER, studentWorkingDirectory);
        }
        replacements.put(Constants.ASSIGNMENT_REPO_PLACEHOLDER, "/" + studentWorkingDirectory + "/src");
        replacements.put(Constants.TEST_REPO_PLACEHOLDER, testWorkingDirectory);
        replacements.put(Constants.SOLUTION_REPO_PLACEHOLDER, solutionWorkingDirectory);
        var projectType = programmingExercise.getProjectType();
        if ((programmingLanguage == ProgrammingLanguage.JAVA && projectType != null && projectType.isGradle()) || programmingLanguage == ProgrammingLanguage.RUST) {
            replacements.put(Constants.ASSIGNMENT_REPO_PLACEHOLDER_NO_SLASH, studentWorkingDirectory + "/src");
        }
        FileUtil.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath(), replacements, List.of("gradle-wrapper.jar"));
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
        final Path repositoryLocalPath = getRepoAbsoluteLocalPath(repository);
        final String packageName = programmingExercise.getPackageName();
        // The client already provides a clean package name, but we have to make sure that no one abuses the API for injection.
        // So usually, the name should not change.
        final String cleanPackageName = packageName.replaceAll("[^a-zA-Z\\d]", "");

        if (ProjectType.PLAIN.equals(programmingExercise.getProjectType())) {
            FileUtil.replaceVariablesInDirectoryName(repositoryLocalPath, PACKAGE_NAME_FOLDER_PLACEHOLDER, cleanPackageName);
            FileUtil.replaceVariablesInFilename(repositoryLocalPath, PACKAGE_NAME_FILE_PLACEHOLDER, cleanPackageName);

            replacements.put(PACKAGE_NAME_PLACEHOLDER, cleanPackageName);
        }
        else if (ProjectType.XCODE.equals(programmingExercise.getProjectType())) {
            FileUtil.replaceVariablesInDirectoryName(repositoryLocalPath, APP_NAME_PLACEHOLDER, cleanPackageName);
            FileUtil.replaceVariablesInFilename(repositoryLocalPath, APP_NAME_PLACEHOLDER, cleanPackageName);

            replacements.put(APP_NAME_PLACEHOLDER, cleanPackageName);
        }
    }

    /**
     * Deletes all repositories belonging to the exercise on the version control system.
     *
     * @param programmingExercise The programming exercise for which the repositories should be deleted.
     */
    void deleteRepositories(final ProgrammingExercise programmingExercise) {
        if (programmingExercise.getTemplateRepositoryUri() != null) {
            final var templateRepositoryUriAsUrl = programmingExercise.getVcsTemplateRepositoryUri();
            versionControlService.orElseThrow().deleteRepository(templateRepositoryUriAsUrl);
        }
        if (programmingExercise.getSolutionRepositoryUri() != null) {
            final var solutionRepositoryUriAsUrl = programmingExercise.getVcsSolutionRepositoryUri();
            versionControlService.orElseThrow().deleteRepository(solutionRepositoryUriAsUrl);
        }
        if (programmingExercise.getTestRepositoryUri() != null) {
            final var testRepositoryUriAsUrl = programmingExercise.getVcsTestRepositoryUri();
            versionControlService.orElseThrow().deleteRepository(testRepositoryUriAsUrl);
        }

        // We also want to delete any auxiliary repositories
        programmingExercise.getAuxiliaryRepositories().forEach(repo -> {
            if (repo.getRepositoryUri() != null) {
                versionControlService.orElseThrow().deleteRepository(repo.getVcsRepositoryUri());
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
        if (programmingExercise.getTemplateRepositoryUri() != null) {
            final var templateRepositoryUriAsUrl = programmingExercise.getVcsTemplateRepositoryUri();
            gitService.deleteLocalRepository(templateRepositoryUriAsUrl);
        }
        if (programmingExercise.getSolutionRepositoryUri() != null) {
            final var solutionRepositoryUriAsUrl = programmingExercise.getVcsSolutionRepositoryUri();
            gitService.deleteLocalRepository(solutionRepositoryUriAsUrl);
        }
        if (programmingExercise.getTestRepositoryUri() != null) {
            final var testRepositoryUriAsUrl = programmingExercise.getVcsTestRepositoryUri();
            gitService.deleteLocalRepository(testRepositoryUriAsUrl);
        }
    }

    /**
     * Creates a map of replacements that should be applied to the repository files when exercise name is changed.
     *
     * @param oldRepositoryName   the name of the repository that should be replaced
     * @param newRepositoryName   the name of the repository that should be used for the replacement
     * @param programmingLanguage the programming language of the exercise
     * @return a map of replacements that should be applied
     */
    private static Map<String, String> replacementMapping(String oldRepositoryName, String newRepositoryName, ProgrammingLanguage programmingLanguage) {
        String oldRepositoryNamePomXml = oldRepositoryName.replaceAll(" ", "-");
        String newRepositoryNamePomXml = newRepositoryName.replaceAll(" ", "-");

        Map<String, String> replacements = new HashMap<>();

        switch (programmingLanguage) {
            case JAVA, KOTLIN -> {
                // Maven specific
                replacements.put("<artifactId>" + oldRepositoryNamePomXml + "</artifactId>", "<artifactId>" + newRepositoryNamePomXml + "</artifactId>");
                replacements.put("<artifactId>" + oldRepositoryNamePomXml + "-Solution</artifactId>", "<artifactId>" + newRepositoryNamePomXml + "-Solution</artifactId>");
                replacements.put("<artifactId>" + oldRepositoryNamePomXml + "-Tests</artifactId>", "<artifactId>" + newRepositoryNamePomXml + "-Tests</artifactId>");

                replacements.put("<name>" + oldRepositoryNamePomXml + "</name>", "<name>" + newRepositoryNamePomXml + "</name>");
                replacements.put("<name>" + oldRepositoryNamePomXml + " Solution</name>", "<name>" + newRepositoryNamePomXml + " Solution</name>");
                replacements.put("<name>" + oldRepositoryNamePomXml + " Tests</name>", "<name>" + newRepositoryNamePomXml + " Tests</name>");
                replacements.put("<name>" + oldRepositoryName + " Tests</name>", "<name>" + newRepositoryName + " Tests</name>");

                // Gradle specific
                replacements.put("rootProject.name = '" + oldRepositoryNamePomXml + "'", "rootProject.name = '" + newRepositoryNamePomXml + "'");
                replacements.put("rootProject.name = '" + oldRepositoryNamePomXml + "-Solution'", "rootProject.name = '" + newRepositoryNamePomXml + "-Solution'");
                replacements.put("rootProject.name = '" + oldRepositoryNamePomXml + "-Tests'", "rootProject.name = '" + newRepositoryNamePomXml + "-Tests'");

                replacements.put("\"buildName\":\"" + oldRepositoryNamePomXml + "\"", "\"buildName\":\"" + newRepositoryNamePomXml + "\"");
                replacements.put("\"buildName\":\"" + oldRepositoryNamePomXml + "-Solution\"", "\"buildName\":\"" + newRepositoryNamePomXml + "-Solution\"");
                replacements.put("\"buildName\":\"" + oldRepositoryNamePomXml + "-Tests\"", "\"buildName\":\"" + newRepositoryNamePomXml + "-Tests\"");

                replacements.put("testImplementation(':" + oldRepositoryNamePomXml, "testImplementation(':" + newRepositoryNamePomXml);
                replacements.put("testImplementation(':" + oldRepositoryNamePomXml + "-Solution", "testImplementation(':" + newRepositoryNamePomXml + "-Solution");
                replacements.put("testImplementation(':" + oldRepositoryNamePomXml + "-Tests", "testImplementation(':" + newRepositoryNamePomXml + "-Tests");
            }
        }
        return replacements;
    }

    /**
     * Adjust project names in imported exercise for TEST, BASE and SOLUTION repositories.
     * Replace values inserted in {@link ProgrammingExerciseRepositoryService#replacePlaceholders(ProgrammingExercise, Repository)}.
     *
     * @param oldExerciseTitle the title of the old exercise
     * @param newExercise      the exercise from which the values that should be inserted are extracted
     * @throws GitAPIException If the checkout/push of one repository fails
     * @throws IOException     If the values in the files could not be replaced
     */
    void adjustProjectNames(String oldExerciseTitle, ProgrammingExercise newExercise) throws GitAPIException, IOException {
        // If exercise names are the same, then there is no need for adjustment
        if (!oldExerciseTitle.equals(newExercise.getTitle())) {
            final var projectKey = newExercise.getProjectKey();
            Map<String, String> replacements = replacementMapping(oldExerciseTitle, newExercise.getTitle(), newExercise.getProgrammingLanguage());

            User user = userRepository.getUser();

            adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.TEMPLATE), user);
            adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.TESTS), user);
            adjustProjectName(replacements, projectKey, newExercise.generateRepositoryName(RepositoryType.SOLUTION), user);
        }
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
        Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, true);
        FileUtil.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath(), replacements, List.of("gradle-wrapper.jar"));
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, "Template adjusted by Artemis", true, user);
    }

}
