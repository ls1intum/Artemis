package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

@JsonInclude()
public record BuildPlanPhasesDTO(List<@Valid BuildPhaseDTO> phases, String dockerImage) {

    private static final ObjectMapper mapper = JsonObjectMapper.get();

    public static BuildPlanPhasesDTO fromBuildPlanConfiguration(String buildPlanConfiguration) throws JsonProcessingException {
        return mapper.readValue(buildPlanConfiguration, BuildPlanPhasesDTO.class);
    }

    public String toBuildPlanConfiguration() throws JsonProcessingException {
        return mapper.writeValueAsString(this);
    }
}
