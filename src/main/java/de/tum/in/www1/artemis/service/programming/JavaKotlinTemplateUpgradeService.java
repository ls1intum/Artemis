package de.tum.in.www1.artemis.service.programming;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

import de.tum.in.www1.artemis.ResourceLoaderService;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.GitService;

@Service
public class JavaKotlinTemplateUpgradeService extends TemplateUpgradeService {

    private final Logger log = LoggerFactory.getLogger(JavaKotlinTemplateUpgradeService.class);

    private final ProgrammingExerciseService programmingExerciseService;

    private final GitService gitService;

    private final UserService userService;

    private final ResourceLoaderService resourceLoaderService;

    public JavaKotlinTemplateUpgradeService(ProgrammingExerciseService programmingExerciseService, GitService gitService, ResourceLoaderService resourceLoaderService,
            UserService userService) {
        this.programmingExerciseService = programmingExerciseService;
        this.gitService = gitService;
        this.userService = userService;
        this.resourceLoaderService = resourceLoaderService;
    }

    public void upgradeTemplate(ProgrammingExercise exercise) {
        // TODO: Support sequential test runs
        if (exercise.hasSequentialTestRuns()) {
            return;
        }

        try {
            updateRepository(exercise, RepositoryType.TEMPLATE.getName(), RepositoryType.TEMPLATE);
            updateRepository(exercise, RepositoryType.SOLUTION.getName(), RepositoryType.SOLUTION);
            updateRepository(exercise, "test/projectTemplate", RepositoryType.TESTS);
        }
        catch (IOException | GitAPIException | InterruptedException | XmlPullParserException exception) {
            log.error("Updating of template files for exercise " + exercise.getId() + " failed with error" + exception.getMessage());
        }
    }

    private void updateRepository(ProgrammingExercise exercise, String templateFolder, RepositoryType repositoryType)
            throws IOException, GitAPIException, InterruptedException, XmlPullParserException {
        // Get general template poms
        String programmingLanguageTemplate = programmingExerciseService.getProgrammingLanguageTemplatePath(exercise.getProgrammingLanguage());
        String templatePomPath = programmingLanguageTemplate + "/" + templateFolder + "/**/pom.xml";

        Resource[] templatePoms = resourceLoaderService.getResources(templatePomPath);

        // Get project type specific template poms
        if (exercise.getProjectType() != null) {
            String projectTypeTemplate = programmingExerciseService.getProgrammingLanguageProjectTypePath(exercise.getProgrammingLanguage(), exercise.getProjectType());
            String projectTypePomPath = projectTypeTemplate + "/" + templateFolder + "/**/pom.xml";

            Resource[] projectTypePoms = resourceLoaderService.getResources(projectTypePomPath);

            // Prefer project type specific poms
            templatePoms = projectTypePoms.length > 0 ? projectTypePoms : templatePoms;
        }

        // Checkout repository
        Repository repository = gitService.getOrCheckoutRepository(getRepositoryURL(exercise, repositoryType), true);
        List<File> repositoryPoms = gitService.listFiles(repository).stream().filter(file -> file.getName().equals("pom.xml")).collect(Collectors.toList());

        // Validate that template and repository have the same number of pom.xml files, otherwise no upgrade will take place
        // TODO: Improve matching of repository and template poms, support sequential test runs
        if (templatePoms.length == 1 && repositoryPoms.size() == 1) {
            Model updatedRepoModel = upgradeProjectObjectModel(templatePoms[0].getFile(), repositoryPoms.get(0));
            writeProjectObjectModel(updatedRepoModel, repositoryPoms.get(0));
            programmingExerciseService.commitAndPushRepository(repository, "Template updated by Artemis", userService.getUser());
        }
    }

    private URL getRepositoryURL(ProgrammingExercise exercise, RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> exercise.getTemplateRepositoryUrlAsUrl();
            case SOLUTION -> exercise.getSolutionRepositoryUrlAsUrl();
            case TESTS -> exercise.getTestRepositoryUrlAsUrl();
        };
    }

    private void writeProjectObjectModel(Model repositoryModel, File repositoryPom) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(repositoryPom)) {
            var pomWriter = new MavenXpp3Writer();
            pomWriter.write(outputStream, repositoryModel);
        }
    }

    private Model upgradeProjectObjectModel(File templatePom, File repositoryPom) throws IOException, XmlPullParserException {
        try (InputStream templateInput = new FileInputStream(templatePom); InputStream repoInput = new FileInputStream(repositoryPom)) {

            var pomReader = new MavenXpp3Reader();
            Model templateModel = pomReader.read(templateInput);
            Model repoModel = pomReader.read(repoInput);

            // Update all dependencies found in the repository pom with version found in the template
            updateDependencies(repoModel, templateModel);

            // Update Maven Compiler Plugin with JDK version, Maven Surefire Plugin and Maven Failsafe Plugin by replacing them with template plugins
            upgradePlugin(repoModel, templateModel, "org.apache.maven.plugins", "maven-compiler-plugin");
            upgradePlugin(repoModel, templateModel, "org.apache.maven.plugins", "maven-surefire-plugin");
            upgradePlugin(repoModel, templateModel, "org.apache.maven.plugins", "maven-failsafe-plugin");

            // Replace JUnit4 with AJTS
            removeDependency(repoModel, "junit", "junit");
            addDependency(repoModel, templateModel, "de.tum.in.ase", "artemis-java-test-sandbox");

            // Remove legacy dependencies which are no longer needed or were moved to AJTS
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
            // TODO: Only update dependency if new version is 'higher' than old version?
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

    private void upgradePlugin(Model targetModel, Model templateModel, String groupId, String artifactId) {
        replacePlugin(targetModel, groupId, artifactId, templateModel, groupId, artifactId);
    }

    /**
     * Replaces a plugin in the old model with a plugin of the template model. The replacement only takes place if both
     * the plugin to be replaced and the replacement plugin exist in their respective models.
     *
     * @param oldModel Project object model to be updated
     * @param oldGroupId Group id of the plugin to be replaced
     * @param oldArtifactId Artifact id of the plugin to be replaced
     * @param templateModel Project object model containing the replacement plugin
     * @param templateGroupId Group id of the replacement plugin
     * @param templateArtifactId Artifact id of the replacement plugin
     */
    private void replacePlugin(Model oldModel, String oldGroupId, String oldArtifactId, Model templateModel, String templateGroupId, String templateArtifactId) {
        var oldPlugin = findPlugin(oldModel, oldGroupId, oldArtifactId);
        var templatePlugin = findPlugin(templateModel, templateGroupId, templateArtifactId);
        if (oldPlugin.isPresent() && templatePlugin.isPresent()) {
            oldModel.getBuild().removePlugin(oldPlugin.get());
            oldModel.getBuild().addPlugin(templatePlugin.get());
        }
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
}
