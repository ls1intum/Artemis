package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.ArrayList;
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

        StringBuilder buildScriptBuilder = new StringBuilder();
        buildScriptBuilder.append("#!/bin/bash\n");
        buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");

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
        return buildScriptProviderService.replacePlaceholders(buildScriptBuilder.toString(), programmingExercise.getBuildConfig().getAssignmentCheckoutPath(),
                programmingExercise.getBuildConfig().getSolutionCheckoutPath(), programmingExercise.getBuildConfig().getTestCheckoutPath());
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
     * @param buildConfig  the programming exercise build config for which the build script should be created
     * @param activePhases the pre-evaluated active build phases, or null to fall back to existing paths
     * @return the build script
     */
    public String createBuildScriptFromActivePhases(ProgrammingExerciseBuildConfig buildConfig, List<BuildPhaseDTO> activePhases) {
        String buildScript = computeAeolusStyleScript(activePhases);
        return buildScriptProviderService.replacePlaceholders(buildScript, buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(),
                buildConfig.getTestCheckoutPath());
    }

    protected static String computeAeolusStyleScript(List<BuildPhaseDTO> activePhases) {
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
        scriptBuilder.append("export AEOLUS_INITIAL_DIRECTORY=${PWD}\n");

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
        scriptBuilder.append("final_aeolus_post_action () {\n");
        scriptBuilder.append("  set +e # from now on, we don't exit on errors\n");
        scriptBuilder.append("  echo '⚙️ executing final_aeolus_post_action'\n");
        for (BuildPhaseDTO phase : forceRunPhase) {
            scriptBuilder.append("  cd \"${AEOLUS_INITIAL_DIRECTORY}\"\n");
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
            scriptBuilder.append("  trap final_aeolus_post_action EXIT\n\n");
        }

        for (BuildPhaseDTO phase : nonForceRunPhase) {
            scriptBuilder.append("  cd \"${AEOLUS_INITIAL_DIRECTORY}\"\n");
            scriptBuilder.append("  bash -c \"source ${_script_name} aeolus_sourcing; ").append(phase.name()).append("\"\n");
        }

        scriptBuilder.append("}\n\n");
    }
}
