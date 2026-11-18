package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.ArtemisJacksonDefaults;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;

/**
 * The purpose of this service is to transform the legacy build plan into the new phases format.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LegacyBuildPlanConverterService {

    private static final JsonMapper objectMapper = createMapper();

    private static JsonMapper createMapper() {
        // Parse the user-provided build plan configuration with the same bounds as BuildPlanPhasesDTO, so that an oversized
        // or excessively wide payload is rejected during parsing instead of building a huge JSON tree on a request thread.
        // Jackson 3 mappers are immutable, so the bounds go on the factory the mapper is built from rather than
        // being pushed into a copy afterwards
        return ArtemisJacksonDefaults.apply(JsonMapper.builder(JsonFactory.builder().streamReadConstraints(BuildPlanPhasesDTO.BUILD_PLAN_CONFIGURATION_CONSTRAINTS).build()))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    /**
     * If successful it returns a present {@link BuildPlanPhasesDTO} containing the legacy build script wrapped into one build phase. If the build script is missing,
     * the old Windfile script actions are converted into the legacy script first.
     *
     * @param buildConfig the build config that is assumed to be legacy
     * @return the converted build plan phases
     */
    public Optional<BuildPlanPhasesDTO> convertLegacyBuildPlanConfiguration(ProgrammingExerciseBuildConfig buildConfig) {
        if (buildConfig == null) {
            return Optional.empty();
        }

        String buildPlanConfiguration = buildConfig.getBuildPlanConfiguration();
        String buildScript = buildConfig.getBuildScript();
        LegacyBuildPlanConfiguration configuration = null;

        if (buildPlanConfiguration != null && !buildPlanConfiguration.isBlank()) {
            try {
                // A configuration that is not a JSON object cannot be mapped and is handled like unparseable text, which leaves it without any usable part
                configuration = objectMapper.readValue(buildPlanConfiguration, LegacyBuildPlanConfiguration.class);
            }
            catch (JacksonException e) {
                if (buildScript == null) {
                    return Optional.empty();
                }
            }
        }

        List<LegacyBuildPlanConfiguration.Action> actions = configuration == null ? null : configuration.actions();
        if (buildScript == null && !isLegacyWindfile(actions)) {
            return Optional.empty();
        }

        if (buildScript == null) {
            buildScript = createLegacyBuildScriptFromActions(actions);
        }

        String dockerImage = configuration == null ? null : parseDockerImage(configuration);
        List<String> resultPaths = parseResultPaths(actions);

        return Optional.of(new BuildPlanPhasesDTO(wrapLegacyBuildScript(buildScript, resultPaths), dockerImage));
    }

    private static List<BuildPhaseDTO> wrapLegacyBuildScript(String script, List<String> resultPaths) {
        String wrappedScript = """
                # feel free to remove the code surrounding your script and split your script into multiple phases
                cd %s/testing-dir
                local tmp_file=$(mktemp)
                cat << '  __LEGACY_INNER_SCRIPT_END__' > "${tmp_file}"  # two leading spaces are intentional as the final script will be indented be for a phase
                %s
                __LEGACY_INNER_SCRIPT_END__
                chmod +x "${tmp_file}"
                "${tmp_file}" "$@"
                """.formatted(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY, script);
        return List.of(new BuildPhaseDTO("script", wrappedScript, BuildPhaseCondition.ALWAYS, false, resultPaths));
    }

    private static String parseDockerImage(LegacyBuildPlanConfiguration configuration) {
        LegacyBuildPlanConfiguration.Metadata metadata = configuration.metadata();
        if (metadata == null || metadata.docker() == null || metadata.docker().image() == null) {
            return null;
        }
        return metadata.docker().image().trim();
    }

    private static boolean isLegacyWindfile(List<LegacyBuildPlanConfiguration.Action> actions) {
        return actions != null;
    }

    private static String createLegacyBuildScriptFromActions(List<LegacyBuildPlanConfiguration.Action> actions) {
        StringBuilder buildScriptBuilder = new StringBuilder();
        buildScriptBuilder.append("#!/bin/bash\n");
        buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");

        if (!isLegacyWindfile(actions)) {
            return buildScriptBuilder.toString();
        }

        for (LegacyBuildPlanConfiguration.Action action : actions) {
            if (action.script() == null) {
                continue;
            }

            String workdir = action.workdir() == null ? null : action.workdir().trim();
            if (workdir != null && !workdir.isBlank()) {
                buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir/").append(workdir).append("\n");
            }
            buildScriptBuilder.append(action.script()).append("\n");
            if (workdir != null && !workdir.isBlank()) {
                buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");
            }
        }

        return buildScriptBuilder.toString();
    }

    private static List<String> parseResultPaths(List<LegacyBuildPlanConfiguration.Action> actions) {
        if (!isLegacyWindfile(actions)) {
            return List.of();
        }

        List<String> resultPaths = new ArrayList<>();
        for (LegacyBuildPlanConfiguration.Action action : actions) {
            if (action.results() == null) {
                continue;
            }

            for (LegacyBuildPlanConfiguration.Result result : action.results()) {
                if (result.path() == null) {
                    continue;
                }
                resultPaths.add(result.path().trim());
            }
        }

        return resultPaths;
    }
}
