package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApollonModelDTO(String model) implements Serializable {
}
