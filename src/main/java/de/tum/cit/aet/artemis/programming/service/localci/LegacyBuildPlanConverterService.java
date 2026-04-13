package de.tum.cit.aet.artemis.programming.service.localci;

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
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;

/**
 * In legacy LOCALCI exercises the buildPlanConfiguration was used only for the docker image and the result paths.
 * A single phase can be constructed from the legacy buildScript.
 * <p>
 * dockerImage + result paths from buildPlanConfiguration + buildScript
 * --> buildPlanConfiguration in new format
 */
@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LegacyBuildPlanConverterService {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    public Optional<BuildPlanPhasesDTO> convertLegacyBuildPlanConfiguration(ProgrammingExercise programmingExercise) {
        var buildConfig = programmingExercise.getBuildConfig();
        if (buildConfig == null || buildConfig.getBuildScript() == null) {
            return Optional.empty();
        }

        String buildPlanConfiguration = programmingExercise.getBuildConfig().getBuildPlanConfiguration();
        if (buildPlanConfiguration == null || buildPlanConfiguration.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode node = objectMapper.readTree(buildPlanConfiguration);
            if (!node.isObject()) {
                return Optional.empty();
            }

            String dockerImage = parseDockerImage(node);
            if (dockerImage == null) {
                return Optional.empty();
            }

            List<String> resultPaths = parseResultPaths(node);
            if (resultPaths == null) {
                return Optional.empty();
            }

            String wrappedScript = wrapLegacyBuildScript(buildConfig.getBuildScript());
            List<BuildPhaseDTO> phases = List.of(new BuildPhaseDTO("script", wrappedScript, BuildPhaseCondition.ALWAYS, false, resultPaths));
            return Optional.of(new BuildPlanPhasesDTO(phases, dockerImage));
        }
        catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static String wrapLegacyBuildScript(String legacyScript) {
        return "cd " + LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir\n" + "local tmp_file=$(mktemp)\n" + "cat << '  __LEGACY_INNER_SCRIPT_END__' > \"${tmp_file}\"\n"
                + legacyScript + "\n" + "__LEGACY_INNER_SCRIPT_END__\n" + "chmod +x \"${tmp_file}\"\n" + "\"${tmp_file}\" \"$@\"\n";
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

    private static List<String> parseResultPaths(JsonNode node) {
        JsonNode actionsNode = node.path("actions");
        if (!actionsNode.isArray()) {
            return null;
        }

        List<String> resultPaths = new ArrayList<>();
        for (JsonNode actionNode : actionsNode) {
            if (!actionNode.isObject()) {
                return null;
            }

            JsonNode resultsNode = actionNode.path("results");
            if (resultsNode.isMissingNode() || resultsNode.isNull()) {
                continue;
            }
            if (!resultsNode.isArray()) {
                return null;
            }

            for (JsonNode resultNode : resultsNode) {
                if (!resultNode.isObject()) {
                    return null;
                }
                JsonNode pathNode = resultNode.path("path");
                if (pathNode.isMissingNode() || pathNode.isNull()) {
                    return null;
                }
                if (!pathNode.isTextual()) {
                    return null;
                }
                resultPaths.add(pathNode.asText().trim());
            }
        }

        return resultPaths;
    }
}
