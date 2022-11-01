package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.SETUP_COMMIT_MESSAGE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;

@Service
public class ProgrammingExerciseRepositoryService {

    private static final Path ALL_FILES_GLOB = Path.of("**", "*.*");

    private static final String TEST_FILES_PATH = "testFiles";

    private static final String PACKAGE_NAME_FOLDER_PLACEHOLDER = "${packageNameFolder}";

    private static final String PACKAGE_NAME_FILE_PLACEHOLDER = "${packageNameFile}";

    private static final String PACKAGE_NAME_PLACEHOLDER = "${packageName}";

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
    void commitAndPushRepository(final Repository repository, final String message, boolean emptyCommit, final User user) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, message, emptyCommit, user);

        // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
        repository.setFiles(null);
    }

    /**
     * Set up the exercise template by determining the files needed for the template and copying them. Commit and push the changes to all repositories for this programming exercise.
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
     * @param repositoryType The type of the repository which is set up.
     * @return All required information about the required resources to set up the repository.
     * @throws GitAPIException Thrown in case cloning the initial empty repository fails.
     */
    private RepositoryResources getRepositoryResources(final ProgrammingExercise programmingExercise, final RepositoryType repositoryType) throws GitAPIException {
        final String programmingLanguage = programmingExercise.getProgrammingLanguage().toString().toLowerCase(Locale.ROOT);
        final ProjectType projectType = programmingExercise.getProjectType();

        final VcsRepositoryUrl repoUrl = programmingExercise.getRepositoryURL(repositoryType);
        final Repository repo = gitService.getOrCheckoutRepository(repoUrl, true);
        // Get path, files and prefix for the programming-language dependent files. They are copied first.
        final Path templatePath = ProgrammingExerciseService.getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage()).resolve(repositoryType.getName())
                .resolve(ALL_FILES_GLOB);

        Resource[] resources = resourceLoaderService.getResources(templatePath);
        Path prefix = Path.of(programmingLanguage, repositoryType.getName());

        Resource[] projectTypeResources = null;
        Path projectTypePrefix = null;

        if (projectType != null && !ProjectType.PLAIN.equals(projectType)) {
            // Get path, files and prefix for the project-type dependent files. They are copied last and can overwrite the resources from the programming language.
            final Path programmingLanguageProjectTypePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(),
                    projectType);
            final String projectTypePath = projectType.name().toLowerCase();
            final Path generalProjectTypePrefix = Path.of(programmingLanguage, projectTypePath);
            final Path projectTypeTemplatePath = programmingLanguageProjectTypePath.resolve(repositoryType.getName()).resolve(ALL_FILES_GLOB);

            final Path projectTypeSpecificPrefix = generalProjectTypePrefix.resolve(repositoryType.getName());
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

    /**
     * Sets up the three initial repositories for a new exercise.
     *
     * @param programmingExercise The exercise that should be set up.
     * @param exerciseCreator The user that wants to create the exercise
     * @param exerciseResources The resources for the template repository.
     * @param solutionResources The resources for the solution repository.
     * @param testResources The resources for the repository containing the tests.
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
        for (AuxiliaryRepository repo : programmingExercise.getAuxiliaryRepositories()) {
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
     * @param templateName         The name of the template
     * @param programmingExercise  the programming exercise
     * @param user                 The user that triggered the action (used as Git commit author)
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

        fileService.copyResources(repositoryResources.resources, repositoryResources.prefix.toString(), repoLocalPath.toString(), true);
        // Also copy project type specific files AFTERWARDS (so that they might overwrite the default files)
        if (repositoryResources.projectTypeResources != null) {
            fileService.copyResources(repositoryResources.projectTypeResources, repositoryResources.projectTypePrefix.toString(), repoLocalPath.toString(), true);
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
     * @param resources The resources the repository should be filled with.
     * @param programmingExercise The programming exercise the new repository belongs to.
     * @param user The user that is creating the exercise.
     * @throws IOException Thrown in case copying files fails.
     * @throws GitAPIException Thrown in case pushing the updates to the version control system fails.
     */
    private void setupJVMTestTemplateAndPush(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final User user)
            throws IOException, GitAPIException {
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path repoLocalPath = getRepoAbsoluteLocalPath(resources.repository);

        // First get files that are not dependent on the project type
        final Path templatePath = ProgrammingExerciseService.getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage()).resolve("test");

        // Java both supports Gradle and Maven as a test template
        Path projectTemplatePath = templatePath;
        if (projectType != null && projectType.isGradle()) {
            projectTemplatePath = projectTemplatePath.resolve("gradle");
        }
        else {
            projectTemplatePath = projectTemplatePath.resolve("maven");
        }
        projectTemplatePath = projectTemplatePath.resolve("projectTemplate").resolve(ALL_FILES_GLOB);

        final Resource[] projectTemplate = resourceLoaderService.getResources(projectTemplatePath);
        // keep the folder structure
        fileService.copyResources(projectTemplate, "projectTemplate", repoLocalPath.toString(), true);

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
     * @param resources The resources for the test repository.
     * @param programmingExercise The programming exercise the repository belongs to.
     * @param repoLocalPath The local path where the repository can be found.
     * @throws IOException Thrown in case copying the resources to the repository fails.
     */
    private void setupJVMTestTemplateProjectTypeResources(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final Path repoLocalPath)
            throws IOException {
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path projectTypeTemplatePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType)
                .resolve("test");
        final Path projectTypeProjectTemplatePath = projectTypeTemplatePath.resolve("projectTemplate").resolve(ALL_FILES_GLOB);

        try {
            final Resource[] projectTypeProjectTemplate = resourceLoaderService.getResources(projectTypeProjectTemplatePath);
            fileService.copyResources(projectTypeProjectTemplate, resources.projectTypePrefix.toString(), repoLocalPath.toString(), false);
        }
        catch (FileNotFoundException fileNotFoundException) {
            log.debug("Could not copy resource to template", fileNotFoundException);
        }
    }

    /**
     * Sets up the test repository for an exercise using regular (= non-sequential) test runs.
     *
     * @param resources The resources for the test repository.
     * @param programmingExercise The programming exercise the repository belongs to.
     * @param templatePath The local path in which the templates files can be found.
     * @param sectionsMap Defines which parts of the template files should be copied based on the chosen exercise features.
     * @throws IOException Thrown in case copying some resource to the local repo fails.
     */
    private void setupTestTemplateRegularTestRuns(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final Path templatePath,
            final Map<String, Boolean> sectionsMap) throws IOException {
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path repoLocalPath = getRepoAbsoluteLocalPath(resources.repository);
        final Path testFilePath = templatePath.resolve(TEST_FILES_PATH).resolve(ALL_FILES_GLOB);
        final Resource[] testFileResources = resourceLoaderService.getResources(testFilePath);
        final String packagePath = repoLocalPath.resolve("test").resolve(PACKAGE_NAME_FOLDER_PLACEHOLDER).toAbsolutePath().toString();

        sectionsMap.put("non-sequential", true);
        sectionsMap.put("sequential", false);

        // replace placeholder settings in project file
        String projectFileFileName;
        if (projectType != null && projectType.isGradle()) {
            projectFileFileName = "build.gradle";
        }
        else {
            projectFileFileName = "pom.xml";
        }
        fileService.replacePlaceholderSections(repoLocalPath.resolve(projectFileFileName).toAbsolutePath().toString(), sectionsMap);

        fileService.copyResources(testFileResources, resources.prefix.toString(), packagePath, false);

        if (projectType != null) {
            overwriteProjectTypeSpecificFiles(resources, programmingExercise, packagePath);
        }

        // Copy static code analysis config files
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
            final Path staticCodeAnalysisConfigPath = templatePath.resolve("staticCodeAnalysisConfig").resolve(ALL_FILES_GLOB);
            final Resource[] staticCodeAnalysisResources = resourceLoaderService.getResources(staticCodeAnalysisConfigPath);
            fileService.copyResources(staticCodeAnalysisResources, resources.prefix.toString(), repoLocalPath.toString(), true);
        }
    }

    private void overwriteProjectTypeSpecificFiles(final RepositoryResources resources, final ProgrammingExercise programmingExercise, final String packagePath)
            throws IOException {
        final ProjectType projectType = programmingExercise.getProjectType();
        final Path projectTypeTemplatePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType)
                .resolve("test");

        try {
            final Resource[] projectTypeTestFileResources = resourceLoaderService.getResources(projectTypeTemplatePath);
            // filter non-existing resources to avoid exceptions
            final List<Resource> existingProjectTypeTestFileResources = new ArrayList<>();
            for (Resource resource : projectTypeTestFileResources) {
                if (resource.exists()) {
                    existingProjectTypeTestFileResources.add(resource);
                }
            }

            if (!existingProjectTypeTestFileResources.isEmpty()) {
                fileService.copyResources(existingProjectTypeTestFileResources.toArray(new Resource[] {}), resources.projectTypePrefix.toString(), packagePath, false);
            }
        }
        catch (FileNotFoundException fileNotFoundException) {
            log.debug("Could not copy resource to template", fileNotFoundException);
        }
    }

    /**
     * Sets up the test repository for an exercise using sequential test runs.
     *
     * @param resources The resources for the test repository.
     * @param templatePath The local path in which the templates files can be found.
     * @param projectTemplatePath The local path in which the project type specific templates files can be found.
     * @param projectType The project type of the exercise.
     * @param sectionsMap Defines which parts of the template files should be copied based on the chosen exercise features.
     * @throws IOException Thrown in case copying some resource to the local repo fails.
     */
    private void setupTestTemplateSequentialTestRuns(final RepositoryResources resources, final Path templatePath, final Path projectTemplatePath, final ProjectType projectType,
            final Map<String, Boolean> sectionsMap) throws IOException {
        final Path repoLocalPath = getRepoAbsoluteLocalPath(resources.repository);
        final Path stagePomXmlName = Path.of("stagePom.xml");

        // maven configuration should be set for kotlin and older exercises where no project type has been introduced where no project type is defined
        final boolean isMaven = ProjectType.isMavenProject(projectType);

        sectionsMap.put("non-sequential", false);
        sectionsMap.put("sequential", true);

        String projectFileName;
        if (isMaven) {
            projectFileName = "pom.xml";
        }
        else {
            projectFileName = "build.gradle";
        }
        fileService.replacePlaceholderSections(repoLocalPath.resolve(projectFileName).toAbsolutePath().toString(), sectionsMap);

        // staging project files are only required for maven
        Resource stagePomXml = null;
        if (isMaven) {
            Path stagePomXmlPath = templatePath.resolve(stagePomXmlName);
            if (projectTemplatePath.resolve(stagePomXmlName).toFile().exists()) {
                stagePomXmlPath = projectTemplatePath.resolve(stagePomXmlName);
            }
            stagePomXml = resourceLoaderService.getResource(stagePomXmlPath);
        }

        // This is done to prepare for a feature where instructors/tas can add multiple build stages.
        final List<String> sequentialTestTasks = new ArrayList<>();
        sequentialTestTasks.add("structural");
        sequentialTestTasks.add("behavior");

        for (String buildStage : sequentialTestTasks) {
            final Path buildStagePath = repoLocalPath.resolve(buildStage);
            Files.createDirectory(buildStagePath);

            Path buildStageResourcesPath = templatePath.resolve(TEST_FILES_PATH).resolve(buildStage).resolve(ALL_FILES_GLOB);
            Resource[] buildStageResources = resourceLoaderService.getResources(buildStageResourcesPath);

            Files.createDirectory(buildStagePath.toAbsolutePath().resolve("test"));
            Files.createDirectory(buildStagePath.toAbsolutePath().resolve("test").resolve(PACKAGE_NAME_FOLDER_PLACEHOLDER));

            String packagePath = buildStagePath.toAbsolutePath().resolve("test").resolve(PACKAGE_NAME_FOLDER_PLACEHOLDER).toAbsolutePath().toString();

            // staging project files are only required for maven
            if (isMaven && stagePomXml != null) {
                Files.copy(stagePomXml.getInputStream(), buildStagePath.resolve("pom.xml"));
            }

            fileService.copyResources(buildStageResources, resources.prefix.toString(), packagePath, false);

            // Possibly overwrite files if the project type is defined
            if (projectType != null) {
                buildStageResourcesPath = projectTemplatePath.resolve(TEST_FILES_PATH).resolve(buildStage).resolve(ALL_FILES_GLOB);
                try {
                    buildStageResources = resourceLoaderService.getResources(buildStageResourcesPath);
                    fileService.copyResources(buildStageResources, resources.prefix.toString(), packagePath, false);
                }
                catch (FileNotFoundException fileNotFoundException) {
                    log.debug("Could not copy resource to template", fileNotFoundException);
                }
            }
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
                fileService.replaceVariablesInDirectoryName(repository.getLocalPath().toAbsolutePath().toString(), PACKAGE_NAME_FOLDER_PLACEHOLDER,
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
     * @param replacements A set of known replacements that can be reused in other files.
     * @param programmingExercise The exercise for which the replacements are performed.
     * @param repository The repository in which the replacements are performed.
     * @throws IOException Thrown if accessing repository files fails.
     */
    private void replaceSwiftPlaceholders(final Map<String, String> replacements, final ProgrammingExercise programmingExercise, final Repository repository) throws IOException {
        final String appNamePlaceholder = "${appName}";
        final String repositoryLocalPath = getRepoAbsoluteLocalPath(repository).toString();
        final String packageName = programmingExercise.getPackageName();

        if (ProjectType.PLAIN.equals(programmingExercise.getProjectType())) {
            fileService.replaceVariablesInDirectoryName(repositoryLocalPath, PACKAGE_NAME_FOLDER_PLACEHOLDER, packageName);
            fileService.replaceVariablesInFileName(repositoryLocalPath, PACKAGE_NAME_FILE_PLACEHOLDER, packageName);

            replacements.put(PACKAGE_NAME_PLACEHOLDER, packageName);
        }
        else if (ProjectType.XCODE.equals(programmingExercise.getProjectType())) {
            fileService.replaceVariablesInDirectoryName(repositoryLocalPath, appNamePlaceholder, packageName);
            fileService.replaceVariablesInFileName(repositoryLocalPath, appNamePlaceholder, packageName);

            replacements.put(appNamePlaceholder, packageName);
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
