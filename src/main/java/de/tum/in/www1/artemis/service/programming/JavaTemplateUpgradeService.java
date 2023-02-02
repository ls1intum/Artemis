package de.tum.in.www1.artemis.service.programming;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Predicate;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.GitService;

/**
 * Service for upgrading of Java template files
 */
@Service
public class JavaTemplateUpgradeService implements TemplateUpgradeService {

    private static final String POM_FILE = "pom.xml";

    private static final String SCA_CONFIG_FOLDER = "staticCodeAnalysisConfig";

    private static final List<String> FILES_TO_DELETE = List.of("testUtils", "StructuralTest.java");

    private static final List<String> FILES_TO_OVERWRITE = List.of("AttributeTest.java", "ClassTest.java", "ConstructorTest.java", "MethodTest.java");

    private final Logger log = LoggerFactory.getLogger(JavaTemplateUpgradeService.class);

    private final ProgrammingExerciseService programmingExerciseService;

    private final GitService gitService;

    private final UserRepository userRepository;

    private final ResourceLoaderService resourceLoaderService;

    private final RepositoryService repositoryService;

    private final FileService fileService;

    public JavaTemplateUpgradeService(ProgrammingExerciseService programmingExerciseService, GitService gitService, ResourceLoaderService resourceLoaderService,
            UserRepository userRepository, RepositoryService repositoryService, FileService fileService) {
        this.programmingExerciseService = programmingExerciseService;
        this.gitService = gitService;
        this.userRepository = userRepository;
        this.resourceLoaderService = resourceLoaderService;
        this.repositoryService = repositoryService;
        this.fileService = fileService;
    }

    @Override
    public void upgradeTemplate(ProgrammingExercise exercise) {
        // TODO: Support sequential test runs
        if (exercise.hasSequentialTestRuns()) {
            return;
        }
        // Template and solution repository can also contain a project object model for some project types
        for (RepositoryType type : RepositoryType.values()) {
            upgradeTemplateFiles(exercise, type);
        }
    }

    /**
     * Upgrades the template files of a repository. Prefers project type specific templates as the
     * reference. The method updates the project object models (pom) in the target repository with the pom of the latest
     * Artemis template.
     *
     * @param exercise The exercise for the template files should be updated
     * @param repositoryType The type of repository to be updated
     */
    private void upgradeTemplateFiles(ProgrammingExercise exercise, RepositoryType repositoryType) {
        if (repositoryType == RepositoryType.AUXILIARY) {
            return;
        }
        try {
            String templatePomDir = repositoryType == RepositoryType.TESTS ? "test/projectTemplate" : repositoryType.getName();
            Resource[] templatePoms = getTemplateResources(exercise, templatePomDir + "/**/" + POM_FILE);
            Repository repository = gitService.getOrCheckoutRepository(exercise.getRepositoryURL(repositoryType), true);
            List<File> repositoryPoms = gitService.listFiles(repository).stream().filter(file -> Objects.equals(file.getName(), POM_FILE)).toList();

            // Validate that template and repository have the same number of pom.xml files, otherwise no upgrade will take place
            if (templatePoms.length == 1 && repositoryPoms.size() == 1) {
                Model updatedRepoModel = upgradeProjectObjectModel(templatePoms[0], repositoryPoms.get(0), Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()));
                writeProjectObjectModel(updatedRepoModel, repositoryPoms.get(0));
            }

            if (repositoryType == RepositoryType.TESTS) {
                // Remove legacy test files
                deleteLegacyTestClassesIfPresent(repository);

                // Overwrite old test classes with new templates if they are present in the repository
                Resource[] testFileTemplates = getTemplateResources(exercise, "test/testFiles/**/*");
                overwriteFilesIfPresent(repository, testFileTemplates);
                programmingExerciseService.replacePlaceholders(exercise, repository);

                // Add the latest static code analysis tool configurations or remove configurations
                if (Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled())) {
                    Resource[] staticCodeAnalysisResources = getTemplateResources(exercise, "test/" + SCA_CONFIG_FOLDER + "/**/*.*");
                    fileService.copyResources(staticCodeAnalysisResources, "java/test", repository.getLocalPath().toAbsolutePath().toString(), true);
                }
                else {
                    deleteFileIfPresent(repository, SCA_CONFIG_FOLDER);
                }
            }
            // TODO: double check that this still works correctly
            programmingExerciseService.commitAndPushRepository(repository, "Template upgraded by Artemis", false, userRepository.getUser());
        }
        catch (IOException | GitAPIException | XmlPullParserException exception) {
            log.error("Updating of template files of repository {} for exercise {} failed with error: {}", repositoryType.name(), exercise.getId(), exception.getMessage());
            // Rollback by deleting the local repository
            gitService.deleteLocalRepository(exercise.getRepositoryURL(repositoryType));
        }
    }

    private Resource[] getTemplateResources(ProgrammingExercise exercise, String filePattern) {
        // Get general template resources
        String programmingLanguageTemplate = programmingExerciseService.getProgrammingLanguageTemplatePath(exercise.getProgrammingLanguage());
        String templatePomPath = programmingLanguageTemplate + "/" + filePattern;

        Resource[] templatePoms = resourceLoaderService.getResources(templatePomPath);

        // Get project type specific template resources
        if (exercise.getProjectType() != null) {
            String projectTypeTemplate = programmingExerciseService.getProgrammingLanguageProjectTypePath(exercise.getProgrammingLanguage(), exercise.getProjectType());
            String projectTypePomPath = projectTypeTemplate + "/" + filePattern;

            Resource[] projectTypePoms = resourceLoaderService.getResources(projectTypePomPath);

            // Prefer project type specific resources
            templatePoms = projectTypePoms.length > 0 ? projectTypePoms : templatePoms;
        }
        return templatePoms;
    }

    private void writeProjectObjectModel(Model repositoryModel, File repositoryPom) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(repositoryPom)) {
            var pomWriter = new MavenXpp3Writer();
            pomWriter.write(outputStream, repositoryModel);
        }
    }

    private Model upgradeProjectObjectModel(Resource templatePom, File repositoryPom, boolean scaEnabled) throws IOException, XmlPullParserException {
        try (InputStream templateInput = templatePom.getInputStream(); InputStream repoInput = new FileInputStream(repositoryPom)) {

            var pomReader = new MavenXpp3Reader();
            Model templateModel = pomReader.read(templateInput);
            Model repoModel = pomReader.read(repoInput);

            // Update all dependencies found in the repository pom with version found in the template
            updateDependencies(repoModel, templateModel);

            // Update basic plugins for compilation and test report generation
            upgradePlugin(repoModel, templateModel, "org.apache.maven.plugins", "maven-compiler-plugin");
            upgradePlugin(repoModel, templateModel, "org.apache.maven.plugins", "maven-surefire-plugin");
            upgradePlugin(repoModel, templateModel, "org.apache.maven.plugins", "maven-failsafe-plugin");

            // Update SCA Plugins and properties
            if (scaEnabled) {
                upgradePlugin(repoModel, templateModel, "com.github.spotbugs", "spotbugs-maven-plugin");
                upgradePlugin(repoModel, templateModel, "org.apache.maven.plugins", "maven-checkstyle-plugin");
                upgradePlugin(repoModel, templateModel, "org.apache.maven.plugins", "maven-pmd-plugin");
                upgradeProperty(repoModel, "scaConfigDirectory", "${project.basedir}/staticCodeAnalysisConfig");
                upgradeProperty(repoModel, "analyzeTests", "false");
            }
            else {
                removePlugin(repoModel, "com.github.spotbugs", "spotbugs-maven-plugin");
                removePlugin(repoModel, "org.apache.maven.plugins", "maven-checkstyle-plugin");
                removePlugin(repoModel, "org.apache.maven.plugins", "maven-pmd-plugin");
                repoModel.getProperties().remove("scaConfigDirectory");
                repoModel.getProperties().remove("analyzeTests");
            }

            // Replace JUnit4 with Ares
            removeDependency(repoModel, "junit", "junit");
            addDependency(repoModel, templateModel, "de.tum.in.ase", "artemis-java-test-sandbox");

            // Remove legacy dependencies which are no longer needed or were moved to Ares
            removeDependency(repoModel, "org.json", "json");
            removeDependency(repoModel, "me.xdrop", "fuzzywuzzy");

            return repoModel;
        }
    }

    /**
     * Updates dependencies in the target model with dependency versions found in the templateModel. This operation
     * does not introduce new dependencies to the target model.
     *
     * @param targetModel Project object model which dependencies should be updated
     * @param templateModel Template project object model which might contain the latest dependency versions
     */
    private void updateDependencies(Model targetModel, Model templateModel) {
        for (var templateDependency : templateModel.getDependencies()) {
            var targetDependency = findDependency(targetModel, templateDependency.getGroupId(), templateDependency.getArtifactId());
            targetDependency.ifPresent(dependency -> dependency.setVersion(templateDependency.getVersion()));
        }
    }

    private void removeDependency(Model model, String groupId, String artifactId) {
        var dependency = findDependency(model, groupId, artifactId);
        dependency.ifPresent(model::removeDependency);
    }

    private void addDependency(Model targetModel, Model templateModel, String groupId, String artifactId) {
        var oldDependency = findDependency(targetModel, groupId, artifactId);
        var newDependency = findDependency(templateModel, groupId, artifactId);
        if (oldDependency.isEmpty() && newDependency.isPresent()) {
            targetModel.addDependency(newDependency.get());
        }
    }

    private void upgradeProperty(Model targetModel, String key, String value) {
        if (!targetModel.getProperties().containsKey(key)) {
            targetModel.addProperty(key, value);
        }
    }

    private void upgradePlugin(Model targetModel, Model sourceModel, String groupId, String artifactId) {
        removePlugin(targetModel, groupId, artifactId);
        addPlugin(targetModel, sourceModel, groupId, artifactId);
    }

    private void addPlugin(Model targetModel, Model sourceModel, String groupId, String artifactId) {
        var newPlugin = findPlugin(sourceModel, groupId, artifactId);
        newPlugin.ifPresent(plugin -> targetModel.getBuild().addPlugin(plugin));
    }

    private void removePlugin(Model targetModel, String groupId, String artifactId) {
        var oldPlugin = findPlugin(targetModel, groupId, artifactId);
        oldPlugin.ifPresent(plugin -> targetModel.getBuild().removePlugin(plugin));
    }

    private Optional<Dependency> findDependency(Model model, String artifactId, String groupId) {
        return model.getDependencies().stream().filter(isDependency(artifactId, groupId)).findFirst();
    }

    private Optional<Plugin> findPlugin(Model model, String artifactId, String groupId) {
        return model.getBuild().getPlugins().stream().filter(isPlugin(artifactId, groupId)).findFirst();
    }

    private Predicate<Dependency> isDependency(String groupId, String artifactId) {
        return dependency -> dependency.getGroupId().equals(groupId) && dependency.getArtifactId().equals(artifactId);
    }

    private Predicate<Plugin> isPlugin(String groupId, String artifactId) {
        return plugin -> plugin.getGroupId().equals(groupId) && plugin.getArtifactId().equals(artifactId);
    }

    private Optional<File> getFileByName(Repository repository, String filename) {
        return gitService.listFilesAndFolders(repository).keySet().stream().filter(file -> Objects.equals(filename, file.getName())).findFirst();
    }

    private Optional<Resource> getFileByName(Resource[] resources, String filename) {
        return Arrays.stream(resources).filter(resource -> Objects.equals(resource.getFilename(), filename)).findFirst();
    }

    private void deleteLegacyTestClassesIfPresent(Repository repository) throws IOException {
        for (String filename : FILES_TO_DELETE) {
            deleteFileIfPresent(repository, filename);
        }
    }

    private void deleteFileIfPresent(Repository repository, String filename) throws IOException {
        Optional<File> optionalFile = getFileByName(repository, filename);
        if (optionalFile.isPresent()) {
            repositoryService.deleteFile(repository, optionalFile.get().toString());
        }
    }

    private void overwriteFilesIfPresent(Repository repository, Resource[] templateResources) throws IOException {
        for (String filename : FILES_TO_OVERWRITE) {
            Optional<File> repoFile = getFileByName(repository, filename);
            Optional<Resource> templateResource = getFileByName(templateResources, filename);
            if (repoFile.isPresent() && templateResource.isPresent()) {
                try (InputStream inputStream = templateResource.get().getInputStream()) {
                    Files.copy(inputStream, repoFile.get().toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
