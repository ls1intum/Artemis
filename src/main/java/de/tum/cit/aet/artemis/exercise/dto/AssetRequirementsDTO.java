package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssetRequirementsDTO(String katex, String highlighting, String diagramMode) implements Serializable {
}
