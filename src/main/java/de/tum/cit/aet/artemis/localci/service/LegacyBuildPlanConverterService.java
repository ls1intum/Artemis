package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
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

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    /**
     * If successful it returns a present {@link BuildPlanPhasesDTO} containing the legacy build script wrapped into one build phase.
     *
     * @param programmingExercise the exercise that is assumed to be legacy
     * @return the converted build plan phases
     */
    public Optional<BuildPlanPhasesDTO> convertLegacyBuildPlanConfiguration(ProgrammingExercise programmingExercise) {
        return convertLegacyBuildPlanConfiguration(programmingExercise.getBuildConfig());
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
        JsonNode node = null;

        if (buildPlanConfiguration != null && !buildPlanConfiguration.isBlank()) {
            try {
                node = objectMapper.readTree(buildPlanConfiguration);
                if (!node.isObject()) {
                    node = null;
                }
            }
            catch (JsonProcessingException e) {
                if (buildScript == null) {
                    return Optional.empty();
                }
            }
        }

        JsonNode actionsNode = node == null ? null : node.path("actions");
        if (buildScript == null && !isLegacyWindfile(actionsNode)) {
            return Optional.empty();
        }

        if (buildScript == null) {
            buildScript = createLegacyBuildScriptFromActions(actionsNode);
        }

        String dockerImage = node == null ? null : parseDockerImage(node);
        List<String> resultPaths = parseResultPaths(actionsNode);

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

    private static String parseDockerImage(JsonNode node) {
        JsonNode imageNode = node.path("metadata").path("docker").path("image");
        if (imageNode.isMissingNode() || imageNode.isNull()) {
            return null;
        }
        if (!imageNode.isTextual()) {
            return null;
        }
        return imageNode.asText().trim();
    }

    private static boolean isLegacyWindfile(JsonNode actionsNode) {
        return actionsNode != null && actionsNode.isArray();
    }

    private static String createLegacyBuildScriptFromActions(JsonNode actionsNode) {
        StringBuilder buildScriptBuilder = new StringBuilder();
        buildScriptBuilder.append("#!/bin/bash\n");
        buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");

        if (!isLegacyWindfile(actionsNode)) {
            return buildScriptBuilder.toString();
        }

        for (JsonNode actionNode : actionsNode) {
            if (!actionNode.isObject() || !actionNode.path("script").isTextual()) {
                continue;
            }

            JsonNode workdirNode = actionNode.path("workdir");
            String workdir = workdirNode.isTextual() ? workdirNode.asText().trim() : null;
            if (workdir != null && !workdir.isBlank()) {
                buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir/").append(workdir).append("\n");
            }
            buildScriptBuilder.append(actionNode.path("script").asText()).append("\n");
            if (workdir != null && !workdir.isBlank()) {
                buildScriptBuilder.append("cd ").append(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).append("/testing-dir\n");
            }
        }

        return buildScriptBuilder.toString();
    }

    private static List<String> parseResultPaths(JsonNode actionsNode) {
        if (!isLegacyWindfile(actionsNode)) {
            return List.of();
        }

        List<String> resultPaths = new ArrayList<>();
        for (JsonNode actionNode : actionsNode) {
            if (!actionNode.isObject()) {
                continue;
            }

            JsonNode resultsNode = actionNode.path("results");
            if (resultsNode.isMissingNode() || resultsNode.isNull()) {
                continue;
            }
            if (!resultsNode.isArray()) {
                continue;
            }

            for (JsonNode resultNode : resultsNode) {
                if (!resultNode.isObject()) {
                    continue;
                }
                JsonNode pathNode = resultNode.path("path");
                if (!pathNode.isTextual()) {
                    continue;
                }
                resultPaths.add(pathNode.asText().trim());
            }
        }

        return resultPaths;
    }
}
