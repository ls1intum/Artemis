package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Renames the project of an imported exercise inside its repositories.
 * <p>
 * An exercise is set up from templates that carry its title into the build files, so an import that changes the title
 * leaves the source exercise's name in the copied repositories. This service replaces it in the template, test and
 * solution repositories of the new exercise, and pushes the result.
 * <p>
 * It is the counterpart of the placeholder replacement
 * {@link ProgrammingExerciseRepositoryService#replacePlaceholders(ProgrammingExercise, de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig, Repository)}
 * performs when the exercise is first created: that one writes the values in, this one rewrites them after a copy.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseProjectNameService {

    /** A space in a repository name, which the Maven artifact id spells as a hyphen. */
    private static final Pattern SPACE = Pattern.compile(" ");

    private final GitService gitService;

    private final UserRepository userRepository;

    private final Optional<VersionControlService> versionControlService;

    public ProgrammingExerciseProjectNameService(GitService gitService, UserRepository userRepository, Optional<VersionControlService> versionControlService) {
        this.gitService = gitService;
        this.userRepository = userRepository;
        this.versionControlService = versionControlService;
    }

    /**
     * Adjust project names in imported exercise for TEST, BASE and SOLUTION repositories.
     *
     * @param oldExerciseTitle the title of the old exercise
     * @param newExercise      the exercise from which the values that should be inserted are extracted
     * @throws GitAPIException If the checkout/push of one repository fails
     * @throws IOException     If the values in the files could not be replaced
     */
    public void adjustProjectNames(String oldExerciseTitle, ProgrammingExercise newExercise) throws GitAPIException, IOException {
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
     * Creates a map of replacements that should be applied to the repository files when exercise name is changed.
     *
     * @param oldRepositoryName   the name of the repository that should be replaced
     * @param newRepositoryName   the name of the repository that should be used for the replacement
     * @param programmingLanguage the programming language of the exercise
     * @return a map of replacements that should be applied
     */
    private static Map<String, String> replacementMapping(String oldRepositoryName, String newRepositoryName, ProgrammingLanguage programmingLanguage) {
        String oldRepositoryNamePomXml = SPACE.matcher(oldRepositoryName).replaceAll("-");
        String newRepositoryNamePomXml = SPACE.matcher(newRepositoryName).replaceAll("-");

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
     * Adjust project names in imported exercise for specific repository.
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
