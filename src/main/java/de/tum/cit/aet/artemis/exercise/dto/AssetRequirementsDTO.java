package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssetRequirementsDTO(String katex, String highlighting, String diagramMode, List<String> requiredCss) implements Serializable {
}
