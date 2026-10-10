package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.buildagent.dto.DockerFlagsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDockerFlagsDTO;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseBuildConfigService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProgrammingExerciseBuildConfigService.class);

    private final JsonMapper objectMapper = JsonObjectMapper.get();

    private final LicenseService licenseService;

    @Value("${artemis.continuous-integration.container-flags-limit.allowed-custom-networks:none}")
    private List<String> allowedNetworks;

    public ProgrammingExerciseBuildConfigService(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

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
     * @param buildConfig         the build config containing the Docker flags
     * @param programmingExercise the exercise the configuration belongs to, passed in rather than reached through the
     *                                configuration: it owns the exercise key, so that reference is lazy and does not
     *                                resolve once the session is closed
     * @return a {@link DockerRunConfig} object initialized with the parsed flags, or {@code null} if the JSON string is empty
     */
    @Nullable
    public DockerRunConfig getDockerRunConfig(ProgrammingExerciseBuildConfig buildConfig, ProgrammingExercise programmingExercise) {
        return getDockerRunConfig(buildConfig, programmingExercise, null);
    }

    /**
     * Converts the Docker flags of the exercise, overridden by the flags of the container the job runs, into a
     * {@link DockerRunConfig}. A field the container sets replaces the exercise's, its environment variables are merged
     * on top of the exercise's by name; see {@link BuildContainerDockerFlagsDTO}.
     *
     * @param buildConfig         the build config containing the exercise's Docker flags
     * @param programmingExercise the exercise the configuration belongs to
     * @param containerFlags      the flags of the container the job runs, or null for a job that uses the exercise's flags alone
     * @return the run config of the job, or {@code null} if neither the exercise nor the container sets any flag
     * @throws ResponseStatusException if the container's network is not allowed on this instance
     */
    @Nullable
    public DockerRunConfig getDockerRunConfig(ProgrammingExerciseBuildConfig buildConfig, ProgrammingExercise programmingExercise,
            @Nullable BuildContainerDockerFlagsDTO containerFlags) {
        DockerFlagsDTO dockerFlagsDTO = parseDockerFlags(buildConfig);

        String network = null;
        Map<String, String> exerciseEnvironment = null;
        int cpuCount = 0;
        int memory = 0;
        int memorySwap = 0;
        if (dockerFlagsDTO != null) {
            network = StringUtils.trimToNull(dockerFlagsDTO.network());
            exerciseEnvironment = dockerFlagsDTO.env();
            cpuCount = dockerFlagsDTO.cpuCount();
            memory = dockerFlagsDTO.memory();
            memorySwap = dockerFlagsDTO.memorySwap();
        }
        if (containerFlags != null) {
            String containerNetwork = StringUtils.trimToNull(containerFlags.network());
            if (containerNetwork != null) {
                requireAllowedNetwork(containerNetwork);
                network = containerNetwork;
            }
            exerciseEnvironment = mergeEnvironment(exerciseEnvironment, containerFlags.env());
            if (containerFlags.cpuCount() != null) {
                cpuCount = containerFlags.cpuCount();
            }
            if (containerFlags.memory() != null) {
                memory = containerFlags.memory();
            }
            if (containerFlags.memorySwap() != null) {
                memorySwap = containerFlags.memorySwap();
            }
        }

        ProgrammingExercise exercise = programmingExercise;
        if (exercise == null) {
            return createDockerRunConfig(network, exerciseEnvironment, cpuCount, memory, memorySwap);
        }

        ProgrammingLanguage programmingLanguage = exercise.getProgrammingLanguage();
        ProjectType projectType = exercise.getProjectType();
        Map<String, String> environment = addLanguageSpecificEnvironment(exerciseEnvironment, programmingLanguage, projectType);

        return createDockerRunConfig(network, environment, cpuCount, memory, memorySwap);
    }

    /**
     * Merges the environment variables of a container on top of the exercise's.
     *
     * @param exerciseEnvironment  the exercise's environment variables, or null for none
     * @param containerEnvironment the container's environment variables, or null for none
     * @return both by name, the container's value winning, or null if neither sets any
     */
    @Nullable
    private static Map<String, String> mergeEnvironment(@Nullable Map<String, String> exerciseEnvironment, @Nullable Map<String, String> containerEnvironment) {
        if (containerEnvironment == null || containerEnvironment.isEmpty()) {
            return exerciseEnvironment;
        }
        Map<String, String> environment = new HashMap<>(exerciseEnvironment == null ? Map.of() : exerciseEnvironment);
        environment.putAll(containerEnvironment);
        return environment;
    }

    /**
     * Checks whether a Docker network is allowed on this instance.
     *
     * @param network a Docker network, or null or blank for the default network
     * @return whether a container may join it on this instance
     */
    public boolean isAllowedNetwork(@Nullable String network) {
        return StringUtils.isBlank(network) || allowedNetworks.contains(network);
    }

    private void requireAllowedNetwork(String network) {
        if (!isAllowedNetwork(network)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid network: " + network);
        }
    }

    @Nullable
    private Map<String, String> addLanguageSpecificEnvironment(@Nullable Map<String, String> exerciseEnvironment, ProgrammingLanguage language, ProjectType projectType) {
        Map<String, String> licenseEnvironment = licenseService.getEnvironment(language, projectType);
        if (licenseEnvironment.isEmpty()) {
            return exerciseEnvironment;
        }

        Map<String, String> env = new HashMap<>(licenseEnvironment);
        if (exerciseEnvironment != null) {
            env.putAll(exerciseEnvironment);
        }

        return env;
    }

    DockerRunConfig createDockerRunConfig(String network, Map<String, String> environmentMap, int cpuCount, int memory, int memorySwap) {
        if (network == null && environmentMap == null && cpuCount == 0 && memory == 0 && memorySwap == 0) {
            return null;
        }
        List<String> environmentStrings = new ArrayList<>();

        if (environmentMap != null) {
            for (Map.Entry<String, String> entry : environmentMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                environmentStrings.add(key + "=" + value);
            }
        }

        return new DockerRunConfig(environmentStrings, network, cpuCount, memory, memorySwap);
    }

    /**
     * Parses the JSON string representing Docker flags into DockerFlagsDTO. (see {@link DockerFlagsDTO})
     *
     * @return the parsed docker flags object
     * @throws IllegalArgumentException if the JSON string is invalid
     * @throws ResponseStatusException  if the network is not allowed
     */
    @Nullable
    DockerFlagsDTO parseDockerFlags(ProgrammingExerciseBuildConfig buildConfig) {
        if (StringUtils.isBlank(buildConfig.getDockerFlags())) {
            return null;
        }

        DockerFlagsDTO dockerFlagsDTO;
        try {
            dockerFlagsDTO = objectMapper.readValue(buildConfig.getDockerFlags(), DockerFlagsDTO.class);
        }
        catch (Exception e) {
            log.error("Failed to parse DockerRunConfig from JSON string: {}. Using default settings.", buildConfig.getDockerFlags());
            throw new IllegalArgumentException("Failed to parse DockerRunConfig from JSON string: " + buildConfig.getDockerFlags(), e);
        }

        requireAllowedNetwork(dockerFlagsDTO.network());

        return dockerFlagsDTO;
    }
}
