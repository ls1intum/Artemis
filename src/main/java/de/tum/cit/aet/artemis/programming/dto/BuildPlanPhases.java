package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildPlanPhases(List<BuildPhase> phases, String dockerImage) {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static BuildPlanPhases deserialize(String json) throws JsonProcessingException {
        return mapper.readValue(json, BuildPlanPhases.class);
    }

    public String serialize() throws JsonProcessingException {
        return mapper.writeValueAsString(this);
    }
}
