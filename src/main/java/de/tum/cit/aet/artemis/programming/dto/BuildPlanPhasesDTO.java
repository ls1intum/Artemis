package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.ArtemisJacksonDefaults;

@JsonInclude()
public record BuildPlanPhasesDTO(List<@Valid BuildPhaseDTO> phases, String dockerImage) {

    /**
     * Parsing bounds for the user-provided build plan configuration JSON. A build plan configuration is a small list of
     * phases, so these limits stay well above any real configuration while rejecting abnormally large, wide or deeply
     * nested input during parsing. This keeps the parsing effort bounded and avoids blocking a request thread on an
     * oversized payload.
     */
    public static final StreamReadConstraints BUILD_PLAN_CONFIGURATION_CONSTRAINTS = StreamReadConstraints.builder().maxNestingDepth(32).maxDocumentLength(1024L * 1024)
            .maxTokenCount(10_000).build();

    private static final JsonMapper mapper = createMapper();

    private static JsonMapper createMapper() {
        // Jackson 3 mappers are immutable, so the bounds go on the factory the mapper is built from rather than
        // being pushed into a copy afterwards
        return ArtemisJacksonDefaults.apply(JsonMapper.builder(JsonFactory.builder().streamReadConstraints(BUILD_PLAN_CONFIGURATION_CONSTRAINTS).build()))
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    /**
     * Deserializes a JSON string representation to a {@link BuildPlanPhasesDTO} object
     *
     * @param buildPlanConfiguration the JSON String representation
     * @return the new {@link BuildPlanPhasesDTO} object
     */
    public static BuildPlanPhasesDTO fromBuildPlanConfiguration(String buildPlanConfiguration) {
        if (buildPlanConfiguration == null || buildPlanConfiguration.isBlank()) {
            return new BuildPlanPhasesDTO(null, null);
        }
        return mapper.readValue(buildPlanConfiguration, BuildPlanPhasesDTO.class);
    }

    /**
     * Serializes this to a JSON string representation
     *
     * @return the JSON string
     */
    public String toBuildPlanConfiguration() {
        return mapper.writeValueAsString(this);
    }
}
