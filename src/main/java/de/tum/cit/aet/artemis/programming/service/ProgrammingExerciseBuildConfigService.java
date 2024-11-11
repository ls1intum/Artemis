package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.buildagent.dto.DockerFlagsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseBuildConfigService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProgrammingExerciseBuildConfigService.class);

    /**
     * Converts a JSON string representing Docker flags (in the form of a list of key-value pairs)
     * into a {@link DockerRunConfig} instance.
     *
     * <p>
     * The JSON string is expected to represent a list of key-value pairs where each
     * entry is a list containing two strings: the first being the key and the second being the value.
     * Example JSON input:
     *
     * <pre>
     * [["network", "none"], ["env", "TEST"]]
     * </pre>
     *
     * @param buildConfig the build config containing the Docker flags
     * @return a {@link DockerRunConfig} object initialized with the parsed flags, or {@code null} if an error occurs
     */
    @Nullable
    public DockerRunConfig getDockerRunConfig(ProgrammingExerciseBuildConfig buildConfig) {
        DockerFlagsDTO dockerFlagsDTO = parseDockerFlags(buildConfig);

        return getDockerRunConfigFromParsedFlags(dockerFlagsDTO);
    }

    DockerRunConfig getDockerRunConfigFromParsedFlags(DockerFlagsDTO dockerFlagsDTO) {
        if (dockerFlagsDTO == null) {
            return null;
        }
        List<String> env = new ArrayList<>();
        boolean isNetworkDisabled = dockerFlagsDTO.network() != null && dockerFlagsDTO.network().equals("none");

        if (dockerFlagsDTO.env() != null) {
            for (Map.Entry<String, String> entry : dockerFlagsDTO.env().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.length() > MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH || value.length() > MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH) {
                    log.warn("Docker environment variable key or value is too long. Key: {}, Value: {}", key, value);
                    return null;
                }
                env.add(key + "=" + value);
            }
        }

        return new DockerRunConfig(isNetworkDisabled, env);
    }

    /**
     * Parses the JSON string representing Docker flags into a list of key-value pairs.
     *
     * @return a list of key-value pairs, or {@code null} if an error occurs
     */
    @Nullable
    DockerFlagsDTO parseDockerFlags(ProgrammingExerciseBuildConfig buildConfig) {
        if (StringUtils.isBlank(buildConfig.getDockerFlags())) {
            return null;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();

            return objectMapper.readValue(buildConfig.getDockerFlags(), new TypeReference<>() {
            });
        }
        catch (Exception e) {
            log.error("Failed to parse DockerRunConfig from JSON string: {}. Using default settings.", buildConfig.getDockerFlags(), e);
        }

        return null;
    }
}
