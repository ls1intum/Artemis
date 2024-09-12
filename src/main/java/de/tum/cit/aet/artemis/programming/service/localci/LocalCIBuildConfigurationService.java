package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALCI;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.aeolus.ScriptAction;
import de.tum.cit.aet.artemis.programming.service.aeolus.Windfile;

@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIBuildConfigurationService {

    private final AeolusTemplateService aeolusTemplateService;

    private final BuildScriptProviderService buildScriptProviderService;

    public LocalCIBuildConfigurationService(AeolusTemplateService aeolusTemplateService, BuildScriptProviderService buildScriptProviderService) {
        this.aeolusTemplateService = aeolusTemplateService;
        this.buildScriptProviderService = buildScriptProviderService;
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
        return buildScriptProviderService.replacePlaceholders(buildScriptBuilder.toString(), programmingExercise.getBuildConfig().getAssignmentCheckoutPath(),
                programmingExercise.getBuildConfig().getSolutionCheckoutPath(), programmingExercise.getBuildConfig().getTestCheckoutPath());
    }
}
