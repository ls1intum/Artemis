package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALCI;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
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
     * @param participation the participation for which to create the build script
     * @return the build script
     */
    public String createBuildScript(ProgrammingExerciseParticipation participation) {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();

        StringBuilder buildScript = new StringBuilder();
        buildScript.append("#!/bin/bash\n");
        buildScript.append("cd ").append(LOCALCI_WORKING_DIRECTORY).append("/testing-dir\n");

        String customScript = programmingExercise.getBuildConfig().getBuildScript();
        // Todo: get default script if custom script is null before trying to get actions from windfile
        if (customScript != null) {
            buildScript.append(customScript);
        }
        else {
            List<ScriptAction> actions;

            Windfile windfile = programmingExercise.getBuildConfig().getWindfile();

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
                    buildScript.append("cd ").append(LOCALCI_WORKING_DIRECTORY).append("/testing-dir/").append(workdir).append("\n");
                }
                buildScript.append(action.getScript()).append("\n");
                if (workdir != null) {
                    buildScript.append("cd ").append(LOCALCI_WORKING_DIRECTORY).append("/testing-dir\n");
                }
            });

        }
        return buildScript.toString();
    }

}
