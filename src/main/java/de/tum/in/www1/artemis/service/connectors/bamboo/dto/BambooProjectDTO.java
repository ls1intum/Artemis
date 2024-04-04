package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Deprecated(forRemoval = true) // will be removed in 7.0.0
public record BambooProjectDTO(String key, String name, String description, BambooBuildPlansDTO plans) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BambooBuildPlansDTO(@JsonSetter(nulls = Nulls.AS_EMPTY) List<BambooBuildPlanDTO> plan) {
    }
}
