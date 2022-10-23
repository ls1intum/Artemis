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
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.User;
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
        repository.setFiles(null); // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
    }

    /**
     * Set up the exercise template by determining the files needed for the template and copying them. Commit and push the changes to all repositories for this programming exercise.
     *
     * @param programmingExercise the programming exercise that should be set up
     * @param exerciseCreator     the User that performed the action (used as Git commit author)
     */
    void setupExerciseTemplate(final ProgrammingExercise programmingExercise, final User exerciseCreator) throws GitAPIException {
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
        String programmingLanguageTemplate = ProgrammingExerciseService.getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage());
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
            String programmingLanguageProjectTypePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(),
                    programmingExercise.getProjectType());
            String projectType = programmingExercise.getProjectType().name().toLowerCase();
            String projectTypePrefix = programmingLanguage + "/" + projectType;

            exercisePath = programmingLanguageProjectTypePath + "/exercise/**/*.*";
            solutionPath = programmingLanguageProjectTypePath + "/solution/**/*.*";
            testPath = programmingLanguageProjectTypePath + "/test/**/*.*";

            if (ProjectType.XCODE.equals(programmingExercise.getProjectType())) {
                // For Xcode, we don't share source code, so we only copy files once
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

        final RepoResources exerciseRes = new RepoResources(exerciseRepo, exerciseResources, exercisePrefix, projectTypeExerciseResources, projectTypeExercisePrefix);
        final RepoResources solutionRes = new RepoResources(solutionRepo, solutionResources, solutionPrefix, projectTypeSolutionResources, projectTypeSolutionPrefix);
        final RepoResources testRes = new RepoResources(testRepo, testResources, testPrefix, projectTypeTestResources, projectTypeTestPrefix);

        setupRepositories(programmingExercise, exerciseCreator, exerciseRes, solutionRes, testRes);
    }

    private record RepoResources(Repository repository, Resource[] resources, String prefix, Resource[] projectTypeResources, String projectTypePrefix) {
    }

    private void setupRepositories(final ProgrammingExercise programmingExercise, final User exerciseCreator, final RepoResources exerciseResources,
            final RepoResources solutionResources, final RepoResources testResources) throws GitAPIException {
        try {
            setupTemplateAndPush(exerciseResources, "Exercise", programmingExercise, exerciseCreator);
            // The template repo can be re-written, so we can unprotect the default branch.
            var templateVcsRepositoryUrl = programmingExercise.getVcsTemplateRepositoryUrl();
            String templateBranch = versionControlService.get().getOrRetrieveBranchOfExercise(programmingExercise);
            versionControlService.get().unprotectBranch(templateVcsRepositoryUrl, templateBranch);

            setupTemplateAndPush(solutionResources, "Solution", programmingExercise, exerciseCreator);
            setupTestTemplateAndPush(testResources, "Test", programmingExercise, exerciseCreator);
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

    void createRepositoriesForNewExercise(final ProgrammingExercise programmingExercise) throws GitAPIException {
        final String projectKey = programmingExercise.getProjectKey();
        versionControlService.get().createProjectForExercise(programmingExercise); // Create project
        versionControlService.get().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE), null); // Create template repository
        versionControlService.get().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.TESTS), null); // Create tests repository
        versionControlService.get().createRepository(projectKey, programmingExercise.generateRepositoryName(RepositoryType.SOLUTION), null); // Create solution repository

        // Create auxiliary repositories
        createAndInitializeAuxiliaryRepositories(projectKey, programmingExercise);
    }

    private void createAndInitializeAuxiliaryRepositories(final String projectKey, final ProgrammingExercise programmingExercise) throws GitAPIException {
        for (AuxiliaryRepository repo : programmingExercise.getAuxiliaryRepositories()) {
            String repositoryName = programmingExercise.generateRepositoryName(repo.getName());
            versionControlService.get().createRepository(projectKey, repositoryName, null);
            repo.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(programmingExercise.getProjectKey(), repositoryName).toString());
            Repository vcsRepository = gitService.getOrCheckoutRepository(repo.getVcsRepositoryUrl(), true);
            gitService.commitAndPush(vcsRepository, SETUP_COMMIT_MESSAGE, true, null);
        }
    }

    // Copy template and push, if no file is in the directory

    /**
     * Copy template and push, if no file is currently in the repository.
     *
     * @param templateName         The name of the template
     * @param programmingExercise  the programming exercise
     * @param user                 The user that triggered the action (used as Git commit author)
     * @throws Exception An exception in case something went wrong
     */
    private void setupTemplateAndPush(final RepoResources repoResources, final String templateName, final ProgrammingExercise programmingExercise, final User user)
            throws Exception {
        // Only copy template if repo is empty
        if (!gitService.listFiles(repoResources.repository).isEmpty()) {
            return;
        }

        final String repoLocalPath = getRepoLocalPath(repoResources.repository);

        fileService.copyResources(repoResources.resources, repoResources.prefix, repoLocalPath, true);
        // Also copy project type specific files AFTERWARDS (so that they might overwrite the default files)
        if (repoResources.projectTypeResources != null) {
            fileService.copyResources(repoResources.projectTypeResources, repoResources.projectTypePrefix, repoLocalPath, true);
        }

        replacePlaceholders(programmingExercise, repoResources.repository);
        commitAndPushRepository(repoResources.repository, templateName + "-Template pushed by Artemis", true, user);
    }

    private static String getRepoLocalPath(final Repository repository) {
        return repository.getLocalPath().toAbsolutePath().toString();
    }

    /**
     * Set up the test repository. This method differentiates non-sequential and sequential test repositories (more than 1 test job).
     *
     * @param resources           The resources which should get added to the template
     * @param templateName        The name of the template
     * @param programmingExercise The related programming exercise for which the template should get created
     * @param user                the user who has initiated the generation of the programming exercise
     * @throws Exception If anything goes wrong
     */
    private void setupTestTemplateAndPush(final RepoResources resources, final String templateName, final ProgrammingExercise programmingExercise, final User user)
            throws Exception {
        // Only copy template if repo is empty
        if (gitService.listFiles(resources.repository).isEmpty()
                && (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA || programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.KOTLIN)) {
            setupJVMTestTemplateAndPush(resources, templateName, programmingExercise, user);
        }
        else {
            // If there is no special test structure for a programming language, just copy all the test files.
            setupTemplateAndPush(resources, templateName, programmingExercise, user);
        }
    }

    private void setupJVMTestTemplateAndPush(final RepoResources resources, final String templateName, final ProgrammingExercise programmingExercise, final User user)
            throws IOException, GitAPIException {
        final String repoLocalPath = getRepoLocalPath(resources.repository);

        // First get files that are not dependent on the project type
        String templatePath = ProgrammingExerciseService.getProgrammingLanguageTemplatePath(programmingExercise.getProgrammingLanguage()) + "/test";

        // Java both supports Gradle and Maven as a test template
        String projectTemplatePath = templatePath;
        ProjectType projectType = programmingExercise.getProjectType();
        if (projectType != null && projectType.isGradle()) {
            projectTemplatePath += "/gradle";
        }
        else {
            projectTemplatePath += "/maven";
        }
        projectTemplatePath += "/projectTemplate/**/*.*";
        Resource[] projectTemplate = resourceLoaderService.getResources(projectTemplatePath);
        // keep the folder structure
        fileService.copyResources(projectTemplate, "projectTemplate", repoLocalPath, true);

        // These resources might override the programming language dependent resources as they are project type dependent.
        if (projectType != null) {
            setupJVMTestTemplateProjectTypeResources(resources, programmingExercise, repoLocalPath, projectType);
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
            setupTestTemplateRegularTestRuns(resources, programmingExercise, templatePath, projectType, sectionsMap);
        }

        replacePlaceholders(programmingExercise, resources.repository);
        commitAndPushRepository(resources.repository, templateName + "-Template pushed by Artemis", true, user);
    }

    private void setupJVMTestTemplateProjectTypeResources(final RepoResources resources, final ProgrammingExercise programmingExercise, final String repoLocalPath,
            final ProjectType projectType) throws IOException {
        String projectTypeTemplatePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType) + "/test";
        String projectTypeProjectTemplatePath = projectTypeTemplatePath + "/projectTemplate/**/*.*";

        try {
            Resource[] projectTypeProjectTemplate = resourceLoaderService.getResources(projectTypeProjectTemplatePath);
            fileService.copyResources(projectTypeProjectTemplate, resources.projectTypePrefix, repoLocalPath, false);
        }
        catch (FileNotFoundException ignored) {
        }
    }

    private void setupTestTemplateRegularTestRuns(final RepoResources resources, final ProgrammingExercise programmingExercise, final String templatePath,
            final ProjectType projectType, final Map<String, Boolean> sectionsMap) throws IOException {
        final String repoLocalPath = getRepoLocalPath(resources.repository);
        String testFilePath = templatePath + "/testFiles/**/*.*";
        Resource[] testFileResources = resourceLoaderService.getResources(testFilePath);
        String packagePath = Path.of(repoLocalPath, "test", "${packageNameFolder}").toAbsolutePath().toString();

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
        fileService.replacePlaceholderSections(Path.of(repoLocalPath, projectFileFileName).toAbsolutePath().toString(), sectionsMap);

        fileService.copyResources(testFileResources, resources.prefix, packagePath, false);

        // Possibly overwrite files if the project type is defined
        if (projectType != null) {
            String projectTypeTemplatePath = ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(programmingExercise.getProgrammingLanguage(), projectType) + "/test";

            try {
                Resource[] projectTypeTestFileResources = resourceLoaderService.getResources(projectTypeTemplatePath);
                // filter non-existing resources to avoid exceptions
                List<Resource> existingProjectTypeTestFileResources = new ArrayList<>();
                for (Resource resource : projectTypeTestFileResources) {
                    if (resource.exists()) {
                        existingProjectTypeTestFileResources.add(resource);
                    }
                }
                if (!existingProjectTypeTestFileResources.isEmpty()) {
                    fileService.copyResources(existingProjectTypeTestFileResources.toArray(new Resource[] {}), resources.projectTypePrefix, packagePath, false);
                }
            }
            catch (FileNotFoundException ignored) {
            }
        }

        // Copy static code analysis config files
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
            String staticCodeAnalysisConfigPath = templatePath + "/staticCodeAnalysisConfig/**/*.*";
            Resource[] staticCodeAnalysisResources = resourceLoaderService.getResources(staticCodeAnalysisConfigPath);
            fileService.copyResources(staticCodeAnalysisResources, resources.prefix, repoLocalPath, true);
        }
    }

    private void setupTestTemplateSequentialTestRuns(RepoResources resources, String templatePath, String projectTemplatePath, ProjectType projectType,
            Map<String, Boolean> sectionsMap) throws IOException {
        final String repoLocalPath = getRepoLocalPath(resources.repository);
        final String stagePomXmlName = "/stagePom.xml";

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
        fileService.replacePlaceholderSections(Path.of(repoLocalPath, projectFileName).toAbsolutePath().toString(), sectionsMap);

        // staging project files are only required for maven
        Resource stagePomXml = null;
        if (isMaven) {
            String stagePomXmlPath = templatePath + stagePomXmlName;
            if (new java.io.File(projectTemplatePath + stagePomXmlName).exists()) {
                stagePomXmlPath = projectTemplatePath + stagePomXmlName;
            }
            stagePomXml = resourceLoaderService.getResource(stagePomXmlPath);
        }

        // This is done to prepare for a feature where instructors/tas can add multiple build stages.
        final List<String> sequentialTestTasks = new ArrayList<>();
        sequentialTestTasks.add("structural");
        sequentialTestTasks.add("behavior");

        for (String buildStage : sequentialTestTasks) {
            Path buildStagePath = Path.of(repoLocalPath, buildStage);
            Files.createDirectory(buildStagePath);

            String buildStageResourcesPath = templatePath + "/testFiles/" + buildStage + "/**/*.*";
            Resource[] buildStageResources = resourceLoaderService.getResources(buildStageResourcesPath);

            Files.createDirectory(Path.of(buildStagePath.toAbsolutePath().toString(), "test"));
            Files.createDirectory(Path.of(buildStagePath.toAbsolutePath().toString(), "test", "${packageNameFolder}"));

            String packagePath = Path.of(buildStagePath.toAbsolutePath().toString(), "test", "${packageNameFolder}").toAbsolutePath().toString();

            // staging project files are only required for maven
            if (isMaven && stagePomXml != null) {
                Files.copy(stagePomXml.getInputStream(), buildStagePath.resolve("pom.xml"));
            }

            fileService.copyResources(buildStageResources, resources.prefix, packagePath, false);

            // Possibly overwrite files if the project type is defined
            if (projectType != null) {
                buildStageResourcesPath = projectTemplatePath + "/testFiles/" + buildStage + "/**/*.*";
                try {
                    buildStageResources = resourceLoaderService.getResources(buildStageResourcesPath);
                    fileService.copyResources(buildStageResources, resources.prefix, packagePath, false);
                }
                catch (FileNotFoundException ignored) {
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
        final ProjectType projectType = programmingExercise.getProjectType();

        switch (programmingLanguage) {
            case JAVA, KOTLIN -> {
                fileService.replaceVariablesInDirectoryName(repository.getLocalPath().toAbsolutePath().toString(), "${packageNameFolder}",
                        programmingExercise.getPackageFolderName());
                replacements.put("${packageName}", programmingExercise.getPackageName());
            }
            case SWIFT -> replaceSwiftPlaceholders(replacements, programmingExercise, repository, projectType);
        }

        // there is no need in python to replace package names

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
     * @param projectType The type of the Swift project.
     * @throws IOException Thrown if accessing repository files fails.
     */
    private void replaceSwiftPlaceholders(final Map<String, String> replacements, final ProgrammingExercise programmingExercise, final Repository repository,
            final ProjectType projectType) throws IOException {
        final String appNamePlaceholder = "${appName}";
        final String repositoryLocalPath = repository.getLocalPath().toAbsolutePath().toString();
        final String packageName = programmingExercise.getPackageName();

        switch (projectType) {
            case PLAIN -> {
                fileService.replaceVariablesInDirectoryName(repositoryLocalPath, "${packageNameFolder}", packageName);
                fileService.replaceVariablesInFileName(repositoryLocalPath, "${packageNameFile}", packageName);

                replacements.put("${packageName}", packageName);
            }
            case XCODE -> {
                fileService.replaceVariablesInDirectoryName(repositoryLocalPath, appNamePlaceholder, packageName);
                fileService.replaceVariablesInFileName(repositoryLocalPath, appNamePlaceholder, packageName);

                replacements.put(appNamePlaceholder, packageName);
            }
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
