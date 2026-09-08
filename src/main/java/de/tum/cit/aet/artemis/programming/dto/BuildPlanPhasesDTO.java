package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

@JsonInclude()
public record BuildPlanPhasesDTO(List<@Valid @NotNull BuildPhaseDTO> phases, String dockerImage) {

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
