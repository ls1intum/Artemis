package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALCI;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseBuildConfig;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.ScriptAction;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;

@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIBuildConfigurationService {

    private final AeolusTemplateService aeolusTemplateService;

    public LocalCIBuildConfigurationService(AeolusTemplateService aeolusTemplateService) {
        this.aeolusTemplateService = aeolusTemplateService;
    }

    /**
     * Creates a build script for a given programming exercise.
     * The build script is used to build the programming exercise in a Docker container.
     *
     * @param programmingExercise the programming exercise for which the build script should be created
     * @return the build script
     */
    public String createBuildScript(ProgrammingExercise programmingExercise) {

        StringBuilder buildScriptBuilder = new StringBuilder();
        buildScriptBuilder.append("#!/bin/bash\n");
        buildScriptBuilder.append("cd ").append(LOCALCI_WORKING_DIRECTORY).append("/testing-dir\n");

        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
        String customScript = buildConfig.getBuildScript();
        // Todo: get default script if custom script is null before trying to get actions from windfile
        if (customScript != null) {
            buildScriptBuilder.append(customScript);
        }
        else {
            List<ScriptAction> actions;

            Windfile windfile = buildConfig.getWindfile();

            if (windfile == null) {
                windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
            }
            if (windfile != null) {
                actions = windfile.getScriptActions();
            }
            else {
                throw new LocalCIException("No windfile found for programming exercise " + programmingExercise.getId());
            }

            actions.forEach(action -> {
                String workdir = action.getWorkdir();
                if (workdir != null) {
                    buildScriptBuilder.append("cd ").append(LOCALCI_WORKING_DIRECTORY).append("/testing-dir/").append(workdir).append("\n");
                }
                buildScriptBuilder.append(action.getScript()).append("\n");
                if (workdir != null) {
                    buildScriptBuilder.append("cd ").append(LOCALCI_WORKING_DIRECTORY).append("/testing-dir\n");
                }
            });

        }
        return replacePlaceholders(buildScriptBuilder.toString(), programmingExercise.getBuildConfig());
    }

    public List<String> replaceResultPathsPlaceholders(List<String> resultPaths, ProgrammingExerciseBuildConfig buildConfig) {
        List<String> replacedResultPaths = new ArrayList<>();
        for (String resultPath : resultPaths) {
            String replacedResultPath = replacePlaceholders(resultPath, buildConfig);
            replacedResultPaths.add(replacedResultPath);
        }
        return replacedResultPaths;
    }

    private String replacePlaceholders(String originalString, ProgrammingExerciseBuildConfig buildConfig) {
        String assignmentRepo = buildConfig.getAssignmentCheckoutPath();
        assignmentRepo = assignmentRepo != null && !assignmentRepo.isBlank() ? assignmentRepo : Constants.ASSIGNMENT_REPO_NAME;
        String solutionRepo = buildConfig.getSolutionCheckoutPath();
        solutionRepo = solutionRepo != null && !solutionRepo.isBlank() ? solutionRepo : Constants.SOLUTION_REPO_NAME;
        String testRepo = buildConfig.getTestCheckoutPath();
        testRepo = testRepo != null && !testRepo.isBlank() ? testRepo : Constants.TEST_REPO_NAME;

        String replacedResultPath = originalString.replace(Constants.ASSIGNMENT_REPO_PARENT_PLACEHOLDER, assignmentRepo);
        replacedResultPath = replacedResultPath.replace(Constants.ASSIGNMENT_REPO_PLACEHOLDER, "/" + assignmentRepo + "/src");
        replacedResultPath = replacedResultPath.replace(Constants.SOLUTION_REPO_PLACEHOLDER, solutionRepo);
        replacedResultPath = replacedResultPath.replace(Constants.TEST_REPO_PLACEHOLDER, testRepo);
        return replacedResultPath;
    }

}
