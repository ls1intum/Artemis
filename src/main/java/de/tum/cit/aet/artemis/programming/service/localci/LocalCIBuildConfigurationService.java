package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.aeolus.ScriptAction;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;

@Lazy
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
        return createBuildScript(programmingExercise, null);
    }

    /**
     * Creates a build script for a given programming exercise, optionally using pre-evaluated active phases.
     * <p>
     * Decision tree:
     * <ol>
     * <li>If activePhases is provided (non-null, non-empty): assemble script from active phases with {@code set -e}</li>
     * <li>Else if buildScript is set: use verbatim custom script</li>
     * <li>Else: use windfile actions (custom or default template)</li>
     * </ol>
     *
     * @param programmingExercise the programming exercise for which the build script should be created
     * @param activePhases        the pre-evaluated active build phases, or null to fall back to existing paths
     * @return the build script
     */
    public String createBuildScript(ProgrammingExercise programmingExercise, List<BuildPhaseDTO> activePhases) {

        StringBuilder buildScriptBuilder = new StringBuilder();
        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();

        if (activePhases != null) {
            // Phases path: assemble script from active phases with set -e for fail-fast behavior
            buildScriptBuilder.append("#!/bin/bash\n");
            buildScriptBuilder.append("set -e\n");
            buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");

            for (BuildPhaseDTO phase : activePhases) {
                buildScriptBuilder.append(phase.script()).append("\n");
            }
        } else {
            // else keep the other logic the same
            buildScriptBuilder.append("#!/bin/bash\n");
            buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");

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
                    actions = windfile.scriptActions();
                }
                else {
                    throw new LocalCIException("No windfile found for programming exercise " + programmingExercise.getId());
                }

                actions.forEach(action -> {
                    String workdir = action.workdir();
                    if (workdir != null) {
                        buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir/").append(workdir).append("\n");
                    }
                    buildScriptBuilder.append(action.script()).append("\n");
                    if (workdir != null) {
                        buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");
                    }
                });
            }
        }

        return buildScriptProviderService.replacePlaceholders(buildScriptBuilder.toString(), buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(),
                buildConfig.getTestCheckoutPath());
    }
}
