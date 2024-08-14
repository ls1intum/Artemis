package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALCI;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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
     * @param workingDirectory    the working directory in which the build script should be executed
     * @return the build script
     */
    public String createBuildScript(ProgrammingExercise programmingExercise, String workingDirectory) {

        StringBuilder buildScript = new StringBuilder();
        buildScript.append("#!/bin/bash\n");
        buildScript.append("cd ").append(workingDirectory).append("/testing-dir\n");

        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
        String customScript = buildConfig.getBuildScript();
        // Todo: get default script if custom script is null before trying to get actions from windfile
        if (customScript != null) {
            buildScript.append(customScript);
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
                    buildScript.append("cd ").append(workingDirectory).append("/testing-dir/").append(workdir).append("\n");
                }
                buildScript.append(action.getScript()).append("\n");
                if (workdir != null) {
                    buildScript.append("cd ").append(workingDirectory).append("/testing-dir\n");
                }
            });

        }
        return buildScript.toString();
    }

}
