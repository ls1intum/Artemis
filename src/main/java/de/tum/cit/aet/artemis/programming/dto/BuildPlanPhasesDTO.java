package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

@JsonInclude()
public record BuildPlanPhasesDTO(List<@Valid BuildPhaseDTO> phases, String dockerImage) {

    private static final ObjectMapper mapper = JsonObjectMapper.get().copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /**
     * Deserializes a JSON string representation to a {@link BuildPlanPhasesDTO} object
     *
     * @param buildPlanConfiguration the JSON String representation
     * @return the new {@link BuildPlanPhasesDTO} object
     * @throws JsonProcessingException if the JSON is invalid or has unknown keys
     */
    public static BuildPlanPhasesDTO fromBuildPlanConfiguration(String buildPlanConfiguration) throws JsonProcessingException {
        return mapper.readValue(buildPlanConfiguration, BuildPlanPhasesDTO.class);
    }

    /**
     * Checks whether a JSON string is a {@link BuildPlanPhasesDTO}
     *
     * @param buildPlanConfiguration the JSON string to check
     * @return true if valid else false
     */
    public static boolean isInPhasesFormat(String buildPlanConfiguration) {
        try {
            fromBuildPlanConfiguration(buildPlanConfiguration);
        }
        catch (JsonProcessingException e) {
            return false;
        }
        return true;
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
