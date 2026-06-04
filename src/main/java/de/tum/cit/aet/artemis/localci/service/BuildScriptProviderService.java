package de.tum.cit.aet.artemis.localci.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Service for providing build scripts for programming exercises
 * The scripts are loaded from the resources/templates/phases directory
 */
@Lazy
@Service
@Profile("localci")
public class BuildScriptProviderService {

    /**
     * Returns the file content of the template file for the given language and project type with the different options
     *
     * @param projectType    The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis whether the static analysis template should be used
     * @param sequentialRuns whether the sequential runs template should be used
     * @param fileExtension  the file extension of the template file
     * @return The filename of the requested configuration
     */
    public String buildTemplateName(Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns, String fileExtension) {
        List<String> fileNameComponents = new ArrayList<>();

        fileNameComponents.add(templateFamilyFor(projectType.orElse(null)));

        if (staticAnalysis) {
            fileNameComponents.add("static");
        }
        if (sequentialRuns) {
            fileNameComponents.add("sequential");
        }
        return String.join("_", fileNameComponents) + "." + fileExtension;
    }

    /**
     * Maps a project type to the build-template family whose script applies to it. Several project types differ only in their <em>repository layout</em>, not in the build command
     * the CI runs: a Maven test repository is always built with {@code mvn clean test} and a Gradle one with {@code ./gradlew clean test}, regardless of how the assignment is laid
     * out inside it. So {@link ProjectType#MAVEN_MAVEN} reuses the {@code plain_maven} family and {@link ProjectType#GRADLE_GRADLE} reuses the {@code plain_gradle} family, rather
     * than each requiring its own (identical) phases template. Without this mapping those project types resolve to a non-existent {@code maven_maven.yaml} /
     * {@code gradle_gradle.yaml}, leaving the exercise with an empty build script that runs no tests and synchronises no test cases. {@link ProjectType#MAVEN_BLACKBOX} keeps its
     * own
     * distinct {@code plain_maven_blackbox} family (a genuinely different build), and every other project type maps to its own name.
     *
     * @param projectType the project type, or {@code null} for a legacy project without one
     * @return the lower-case template-family prefix (e.g. {@code "plain_maven"}, {@code "plain_gradle"}, {@code "plain_maven_blackbox"}, {@code "default"})
     */
    private static String templateFamilyFor(ProjectType projectType) {
        if (projectType == null) {
            return "default";
        }
        return switch (projectType) {
            // MAVEN_MAVEN differs from PLAIN_MAVEN only in repository layout; the Maven build command is identical, so it reuses the plain_maven phases.
            case MAVEN_MAVEN -> "plain_maven";
            // GRADLE_GRADLE differs from PLAIN_GRADLE only in repository layout; the Gradle build command is identical, so it reuses the plain_gradle phases.
            case GRADLE_GRADLE -> "plain_gradle";
            // MAVEN_BLACKBOX is a genuinely distinct build with its own phases template; preserve its historical "plain_<name>" prefix.
            case MAVEN_BLACKBOX -> "plain_" + projectType.name().toLowerCase();
            default -> projectType.name().toLowerCase();
        };
    }

    /**
     * Replaces placeholders in the given result paths with the actual paths.
     *
     * @param resultPaths the result paths to replace the placeholders in
     * @param buildConfig the build configuration containing the actual paths
     * @return the result paths with the placeholders replaced
     */
    public List<String> replaceResultPathsPlaceholders(List<String> resultPaths, ProgrammingExerciseBuildConfig buildConfig) {
        List<String> replacedResultPaths = new ArrayList<>();
        for (String resultPath : resultPaths) {
            String replacedResultPath = replacePlaceholders(resultPath, buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(),
                    buildConfig.getTestCheckoutPath());
            replacedResultPaths.add(replacedResultPath);
        }
        return replacedResultPaths;
    }

    /**
     * Replaces placeholders in the given original string with the actual paths.
     *
     * @param originalString the original string to replace the placeholders in
     * @param assignmentRepo the assignment repository name
     * @param solutionRepo   the solution repository name
     * @param testRepo       the test repository name
     * @return the original string with the placeholders replaced
     */
    public String replacePlaceholders(String originalString, String assignmentRepo, String solutionRepo, String testRepo) {
        assignmentRepo = !StringUtils.isBlank(assignmentRepo) ? assignmentRepo : Constants.ASSIGNMENT_REPO_NAME;
        solutionRepo = solutionRepo != null && !solutionRepo.isBlank() ? solutionRepo : Constants.SOLUTION_REPO_NAME;
        testRepo = testRepo != null && !testRepo.isBlank() ? testRepo : Constants.TEST_REPO_NAME;

        String replacedResultPath = originalString.replace(Constants.ASSIGNMENT_REPO_PARENT_PLACEHOLDER, assignmentRepo);
        replacedResultPath = replacedResultPath.replace(Constants.ASSIGNMENT_REPO_PLACEHOLDER, "/" + assignmentRepo + "/src");
        replacedResultPath = replacedResultPath.replace(Constants.SOLUTION_REPO_PLACEHOLDER, solutionRepo);
        replacedResultPath = replacedResultPath.replace(Constants.TEST_REPO_PLACEHOLDER, testRepo);
        return replacedResultPath;
    }
}
