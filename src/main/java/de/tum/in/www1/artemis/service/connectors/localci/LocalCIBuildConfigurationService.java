package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.ScriptAction;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;

@Service
@Profile("localci")
public class LocalCIBuildConfigurationService {

    @Value("${artemis.continuous-integration.local-cis-build-scripts-path}")
    private String localCIBuildScriptBasePath;

    private final AeolusTemplateService aeolusTemplateService;

    public LocalCIBuildConfigurationService(AeolusTemplateService aeolusTemplateService) {
        this.aeolusTemplateService = aeolusTemplateService;
    }

    /**
     * Creates a build script for a given programming exercise.
     * The build script is stored in a file in the local-ci-scripts directory.
     * The build script is used to build the programming exercise in a Docker container.
     *
     * @param participation the participation for which to create the build script
     * @return the build script
     */
    public String createBuildScript(ProgrammingExerciseParticipation participation) {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();

        Path scriptsPath = Path.of(localCIBuildScriptBasePath);

        if (!Files.exists(scriptsPath)) {
            try {
                Files.createDirectory(scriptsPath);
            }
            catch (IOException e) {
                throw new LocalCIException("Failed to create directory for local CI scripts", e);
            }
        }

        StringBuilder buildScript = new StringBuilder();
        buildScript.append("#!/bin/bash\n");
        buildScript.append("cd ").append(LOCALCI_WORKING_DIRECTORY).append("/testing-dir\n");

        String customScript = programmingExercise.getBuildScript();
        // Todo: get default script if custom script is null before trying to get actions from windfile
        if (customScript != null) {
            buildScript.append(customScript);
        }
        else {
            List<ScriptAction> actions;

            Windfile windfile = programmingExercise.getWindfile();

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

    /**
     * Deletes the build script for a given programming exercise.
     * The build script is stored in a file in the local-ci-scripts directory.
     *
     * @param buildJobId the id of the build job for which to delete the build script
     */
    public void deleteScriptFile(String buildJobId) {
        Path scriptsPath = Path.of(localCIBuildScriptBasePath);
        Path buildScriptPath = scriptsPath.resolve(buildJobId + "-build.sh").toAbsolutePath();
        try {
            Files.deleteIfExists(buildScriptPath);
        }
        catch (IOException e) {
            throw new LocalCIException("Failed to delete build script file", e);
        }
    }

}
