package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        List<List<String>> parsedList = parseDockerFlags(buildConfig);
        if (parsedList == null) {
            return null;
        }
        return getDockerRunConfigFromParsedList(parsedList);
    }

    /**
     * Converts a list of key-value pairs representing Docker flags into a {@link DockerRunConfig} instance. @see {@link #getDockerRunConfig(ProgrammingExerciseBuildConfig)}
     *
     * @param list the list of key-value pairs
     * @return a {@link DockerRunConfig} object initialized with the parsed flags, or {@code null} if an error occurs
     */
    @Nullable
    DockerRunConfig getDockerRunConfigFromParsedList(List<List<String>> list) {
        try {
            boolean networkDisabled = false;
            List<String> env = null;
            for (List<String> entry : list) {
                if (entry.size() != 2 || StringUtils.isBlank(entry.get(1)) || StringUtils.isBlank(entry.get(0)) || !DockerRunConfig.AllowedDockerFlags.isAllowed(entry.get(0))) {
                    log.warn("Invalid Docker flag entry: {}. Skipping.", entry);
                    continue;
                }
                switch (entry.get(0)) {
                    case "network":
                        networkDisabled = entry.get(1).equalsIgnoreCase("none");
                        break;
                    case "env":
                        env = parseEnvVariableString(entry.get(1));
                        break;
                    default:
                        log.error("Invalid Docker flag entry: {}. Skipping.", entry);
                        break;
                }

            }
            return new DockerRunConfig(networkDisabled, env);
        }
        catch (Exception e) {
            log.error("Failed to parse DockerRunConfig from JSON string: {}. Using default settings.", list, e);
        }

        return null;
    }

    /**
     * Parses the JSON string representing Docker flags into a list of key-value pairs.
     *
     * @return a list of key-value pairs, or {@code null} if an error occurs
     */
    @Nullable
    List<List<String>> parseDockerFlags(ProgrammingExerciseBuildConfig buildConfig) {
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

    private List<String> parseEnvVariableString(String envVariableString) {
        Pattern pattern = Pattern.compile(
                // match key-value pairs, where the key can be a single word or a string in single or double quotes
                // key-value pairs are separated by commas
                "(?:'([^']+)'|\"([^\"]+)\"|(\\w+))=(?:'([^']*)'|\"([^\"]*)\"|([^,]+))");

        Matcher matcher = pattern.matcher(envVariableString);

        List<String> envVars = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);

            String value = matcher.group(4) != null ? matcher.group(4) : matcher.group(5) != null ? matcher.group(5) : matcher.group(6);

            envVars.add(key + "=" + value);
        }

        return envVars;
    }
}
