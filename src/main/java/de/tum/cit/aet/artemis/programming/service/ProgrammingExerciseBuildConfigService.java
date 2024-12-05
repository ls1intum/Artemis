package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.buildagent.dto.DockerFlagsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseBuildConfigService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProgrammingExerciseBuildConfigService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts a JSON string representing Docker flags (in JSON format)
     * into a {@link DockerRunConfig} instance.
     *
     * <p>
     * The JSON string is expected to represent a {@link DockerFlagsDTO} object.
     * Example JSON input:
     *
     * <pre>
     * {"network":"none","env":{"key1":"value1","key2":"value2"}}
     * </pre>
     *
     * @param buildConfig the build config containing the Docker flags
     * @return a {@link DockerRunConfig} object initialized with the parsed flags, or {@code null} if the JSON string is empty
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
                env.add(key + "=" + value);
            }
        }

        return new DockerRunConfig(isNetworkDisabled, env);
    }

    /**
     * Parses the JSON string representing Docker flags into DockerFlagsDTO. (see {@link DockerFlagsDTO})
     *
     * @return a list of key-value pairs, or {@code null} if the JSON string is empty
     * @throws IllegalArgumentException if the JSON string is invalid
     */
    @Nullable
    DockerFlagsDTO parseDockerFlags(ProgrammingExerciseBuildConfig buildConfig) {
        if (StringUtils.isBlank(buildConfig.getDockerFlags())) {
            return null;
        }

        try {
            return objectMapper.readValue(buildConfig.getDockerFlags(), DockerFlagsDTO.class);
        }
        catch (Exception e) {
            log.error("Failed to parse DockerRunConfig from JSON string: {}. Using default settings.", buildConfig.getDockerFlags());
            throw new IllegalArgumentException("Failed to parse DockerRunConfig from JSON string: " + buildConfig.getDockerFlags(), e);
        }
    }
}
