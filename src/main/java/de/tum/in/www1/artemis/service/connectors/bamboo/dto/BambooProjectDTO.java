package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BambooProjectDTO(String key, String name, String description, de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooProjectDTO.BambooBuildPlansDTO plans) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooBuildPlansDTO(List<BambooBuildPlanDTO> plan) {
    }
}
