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

    public static BuildPlanPhasesDTO fromBuildPlanConfiguration(String buildPlanConfiguration) throws JsonProcessingException {
        return mapper.readValue(buildPlanConfiguration, BuildPlanPhasesDTO.class);
    }

    public static boolean isInPhasesFormat(String buildPlanConfiguration) {
        try {
            fromBuildPlanConfiguration(buildPlanConfiguration);
        }
        catch (JsonProcessingException e) {
            return false;
        }
        return true;
    }

    public String toBuildPlanConfiguration() throws JsonProcessingException {
        return mapper.writeValueAsString(this);
    }
}
