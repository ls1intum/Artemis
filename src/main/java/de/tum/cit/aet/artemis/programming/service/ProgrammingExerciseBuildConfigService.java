package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH;
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
        final int keyIndex = 0;
        final int valueIndex = 1;
        try {
            boolean networkDisabled = false;
            List<String> env = null;
            for (List<String> entry : list) {
                if (entry.size() != 2 || StringUtils.isBlank(entry.get(valueIndex)) || StringUtils.isBlank(entry.get(keyIndex))
                        || !DockerRunConfig.AllowedDockerFlags.isAllowed(entry.get(keyIndex))) {
                    log.warn("Invalid Docker flag entry: {}. Skipping.", entry);
                    continue;
                }
                switch (entry.get(keyIndex)) {
                    case "network":
                        networkDisabled = entry.get(valueIndex).equalsIgnoreCase("none");
                        break;
                    case "env":
                        env = parseEnvVariableString(entry.get(valueIndex));
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

        if (envVariableString.length() > MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH) {
            log.warn("The environment variables string is too long. It will be truncated to {} characters.", MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH);
            envVariableString = envVariableString.substring(0, MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH);
        }

        Matcher matcher = pattern.matcher(envVariableString);

        return extractEnvVariablesKeyValues(matcher);
    }

    /**
     * Extracts the key-value pairs from the matcher and returns them as a list of strings
     * The key/value can be a single word, a string in single quotes, or a string in double quotes
     *
     * @param matcher the matcher that contains the key-value pairs
     * @return a list of strings containing the key-value pairs
     */
    private List<String> extractEnvVariablesKeyValues(Matcher matcher) {
        List<String> envVars = new ArrayList<>();
        while (matcher.find()) {
            // The key can be a single word, a string in single quotes, or a string in double quotes, if matched to group1, the key is in single quotes, if matched to group2, the
            // key is in double quotes, otherwise it is a single word
            // if all groups are null, the key is a single word
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);

            // The value can be a single word, a string in single quotes, or a string in double quotes, if matched to group4, the value is in single quotes, if matched to group5,
            // the value is in double quotes, otherwise it is a single word
            // if all groups are null, the value is a single word
            String value = matcher.group(4) != null ? matcher.group(4) : matcher.group(5) != null ? matcher.group(5) : matcher.group(6);

            // Add the key-value pair to the list
            envVars.add(key + "=" + value);
        }
        return envVars;
    }
}
