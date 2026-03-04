package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildPhaseDTO(String name, String script, BuildPhaseConditionDTO condition, List<String> resultPaths) {
}
