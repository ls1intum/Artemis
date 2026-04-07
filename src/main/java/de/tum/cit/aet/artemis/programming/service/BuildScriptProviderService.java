package de.tum.cit.aet.artemis.programming.service;

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
 * The scripts are loaded from the resources/templates/aeolus directory
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

        if (ProjectType.MAVEN_BLACKBOX.equals(projectType.orElse(null))) {
            fileNameComponents.add("plain_" + projectType.get().name().toLowerCase());
        }
        else {
            fileNameComponents.add(projectType.map(Enum::name).orElse("default").toLowerCase());
        }

        if (staticAnalysis) {
            fileNameComponents.add("static");
        }
        if (sequentialRuns) {
            fileNameComponents.add("sequential");
        }
        return String.join("_", fileNameComponents) + "." + fileExtension;
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
