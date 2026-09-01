package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * The build plan configuration stored on a programming exercise.
 * <p>
 * A build plan consists of one or more named {@link BuildContainerDTO containers}. Configurations written before
 * multi-container support carry a single flat list of phases and one Docker image instead; such configurations are
 * normalized into a single container by {@link #effectiveContainers()}, so that callers can always work with
 * containers regardless of when the configuration was written.
 *
 * @param phases      the phases of a legacy single-container configuration, or null if this configuration uses containers
 * @param dockerImage the Docker image of a legacy single-container configuration, or null if this configuration uses containers
 * @param containers  the containers of the build plan, or null if this is a legacy single-container configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildPlanPhasesDTO(List<@Valid @NotNull BuildPhaseDTO> phases, String dockerImage, List<@Valid BuildContainerDTO> containers) {

    /**
     * Parsing bounds for the user-provided build plan configuration JSON. A build plan configuration is a small list of
     * phases, so these limits stay well above any real configuration while rejecting abnormally large, wide or deeply
     * nested input during parsing. This keeps the parsing effort bounded and avoids blocking a request thread on an
     * oversized payload.
     */
    public static final StreamReadConstraints BUILD_PLAN_CONFIGURATION_CONSTRAINTS = StreamReadConstraints.builder().maxNestingDepth(32).maxDocumentLength(1024L * 1024)
            .maxTokenCount(10_000).build();

    private static final ObjectMapper mapper = createMapper();

    private static ObjectMapper createMapper() {
        ObjectMapper configuredMapper = JsonObjectMapper.get().copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        configuredMapper.getFactory().setStreamReadConstraints(BUILD_PLAN_CONFIGURATION_CONSTRAINTS);
        return configuredMapper;
    }

    /**
     * Creates a legacy single-container configuration that carries a flat list of phases and one Docker image.
     *
     * @param phases      the build phases
     * @param dockerImage the Docker image the phases run in
     */
    public BuildPlanPhasesDTO(List<BuildPhaseDTO> phases, String dockerImage) {
        this(phases, dockerImage, null);
    }

    /**
     * Returns the containers of this build plan. A legacy configuration that only carries phases and a Docker image is
     * normalized into a single container named {@link BuildContainerDTO#DEFAULT_CONTAINER_NAME}, so that callers do not
     * have to distinguish between the two configuration formats. That container scopes no repositories, i.e. it checks
     * out the repositories configured on the exercise, which preserves the behaviour of a build plan without containers.
     *
     * @return the containers of this build plan, or an empty list if the configuration carries no phases at all
     */
    public List<BuildContainerDTO> effectiveContainers() {
        if (containers != null && !containers.isEmpty()) {
            return containers;
        }
        if (phases == null || phases.isEmpty()) {
            return List.of();
        }
        return List.of(new BuildContainerDTO(BuildContainerDTO.DEFAULT_CONTAINER_NAME, dockerImage, phases));
    }

    /**
     * Returns the build phases of all containers of this build plan. Callers that ask a question about the build plan as
     * a whole, such as whether it contains a phase that runs after the due date, do not need to know which container a
     * phase belongs to.
     *
     * @return the build phases of every container, in container order
     */
    public List<BuildPhaseDTO> allPhases() {
        return effectiveContainers().stream().flatMap(container -> Objects.requireNonNullElse(container.phases(), List.<BuildPhaseDTO>of()).stream()).toList();
    }

    /**
     * Deserializes a JSON string representation to a {@link BuildPlanPhasesDTO} object
     *
     * @param buildPlanConfiguration the JSON String representation
     * @return the new {@link BuildPlanPhasesDTO} object
     * @throws JsonProcessingException if the JSON is invalid
     */
    public static BuildPlanPhasesDTO fromBuildPlanConfiguration(String buildPlanConfiguration) throws JsonProcessingException {
        if (buildPlanConfiguration == null || buildPlanConfiguration.isBlank()) {
            return new BuildPlanPhasesDTO(null, null);
        }
        return mapper.readValue(buildPlanConfiguration, BuildPlanPhasesDTO.class);
    }

    /**
     * Serializes this to a JSON string representation
     *
     * @return the JSON string
     * @throws JsonProcessingException if there was an issue with serialization
     */
    public String toBuildPlanConfiguration() throws JsonProcessingException {
        return mapper.writeValueAsString(this);
    }
}
