package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DiagramRenderInfoDTO(String diagramId, String renderMode, String svgUrl, @Nullable String inlineSvg, String sourceHash, List<Long> testIds) implements Serializable {
}
