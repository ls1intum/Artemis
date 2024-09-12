package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisHealthStatusDTO(String model, ModelStatus status) {

    public enum ModelStatus {
        UP, DOWN, NOT_AVAILABLE
    }
}
