package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;

@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIBuildConfigurationService {

    private final BuildScriptProviderService buildScriptProviderService;

    public LocalCIBuildConfigurationService(BuildScriptProviderService buildScriptProviderService) {
        this.buildScriptProviderService = buildScriptProviderService;
    }

    /**
     * Creates a build script for a given programming exercise, using pre-evaluated active phases.
     *
     * @param buildConfig  the programming exercise build config for which the build script should be created
     * @param activePhases the pre-evaluated active build phases, or null to fall back to existing paths
     * @return the build script
     */
    public String createBuildScriptFromActivePhases(ProgrammingExerciseBuildConfig buildConfig, List<BuildPhaseDTO> activePhases) {
        String buildScript = computeAeolusStyleScript(activePhases);
        return buildScriptProviderService.replacePlaceholders(buildScript, buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(),
                buildConfig.getTestCheckoutPath());
    }

    private static String computeAeolusStyleScript(List<BuildPhaseDTO> activePhases) {
        List<BuildPhaseDTO> nonForceRunPhases = new ArrayList<>();
        List<BuildPhaseDTO> forceRunPhases = new ArrayList<>();

        for (BuildPhaseDTO phase : activePhases) {
            if (phase.forceRun()) {
                forceRunPhases.add(phase);
            }
            else {
                nonForceRunPhases.add(phase);
            }
        }

        StringBuilder scriptBuilder = new StringBuilder();
        scriptBuilder.append("#!/usr/bin/env bash\n");
        scriptBuilder.append("set -e\n");
        scriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");
        scriptBuilder.append("export INITIAL_WORKING_DIRECTORY=${PWD}\n");

        for (BuildPhaseDTO phase : activePhases) {
            appendPhaseFunction(scriptBuilder, phase);
        }

        final boolean hasRunAlwaysPhases = !forceRunPhases.isEmpty();
        if (hasRunAlwaysPhases) {
            appendForceRunPostPhase(scriptBuilder, forceRunPhases);
        }

        appendMainFunction(scriptBuilder, nonForceRunPhases, hasRunAlwaysPhases);

        scriptBuilder.append("main \"${@}\"\n");
        return scriptBuilder.toString();
    }

    private static void appendPhaseFunction(StringBuilder scriptBuilder, BuildPhaseDTO phase) {
        scriptBuilder.append(phase.name()).append(" () {\n");
        scriptBuilder.append("  echo '⚙️ executing ").append(phase.name()).append("'\n");
        if (phase.script() != null && !phase.script().isBlank()) {
            List<String> scriptLines = phase.script().lines().toList();
            for (String line : scriptLines) {
                if (!line.isEmpty()) {
                    scriptBuilder.append("  ");
                }
                scriptBuilder.append(line).append("\n");
            }
        }
        scriptBuilder.append("}\n\n");
    }

    private static void appendForceRunPostPhase(StringBuilder scriptBuilder, List<BuildPhaseDTO> forceRunPhase) {
        scriptBuilder.append("final_force_run_post_action () {\n");
        scriptBuilder.append("  set +e # from now on, we don't exit on errors\n");
        scriptBuilder.append("  echo '⚙️ executing final_force_run_post_action'\n");
        for (BuildPhaseDTO phase : forceRunPhase) {
            scriptBuilder.append("  cd \"${INITIAL_WORKING_DIRECTORY}\"\n");
            scriptBuilder.append("  ").append(phase.name()).append("\n");
        }
        scriptBuilder.append("}\n\n");
    }

    private static void appendMainFunction(StringBuilder scriptBuilder, List<BuildPhaseDTO> nonForceRunPhase, boolean hasRunAlwaysPhases) {
        scriptBuilder.append("main () {\n");
        scriptBuilder.append("  if [[ \"${1}\" == \"aeolus_sourcing\" ]]; then\n");
        scriptBuilder.append("    return 0 # just source to use the methods in the subshell, no execution\n");
        scriptBuilder.append("  fi\n");
        scriptBuilder.append("  local _script_name\n");
        scriptBuilder.append("  _script_name=${BASH_SOURCE[0]:-$0}\n");
        if (hasRunAlwaysPhases) {
            scriptBuilder.append("  trap final_force_run_post_action EXIT\n\n");
        }

        for (BuildPhaseDTO phase : nonForceRunPhase) {
            scriptBuilder.append("  cd \"${INITIAL_WORKING_DIRECTORY}\"\n");
            scriptBuilder.append("  bash -c \"source ${_script_name} aeolus_sourcing; ").append(phase.name()).append("\"\n");
        }

        scriptBuilder.append("}\n\n");
    }
}
