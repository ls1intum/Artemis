package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param testIds Currently always empty. Diagram test references (testsColor) use test names, not numeric IDs.
 *                    Resolving names to IDs requires exercise-context DB lookup (planned for phase 2).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DiagramRenderInfoDTO(String diagramId, String renderMode, String svgUrl, String inlineSvg, String sourceHash, List<Long> testIds) implements Serializable {
}
