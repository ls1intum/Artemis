package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The rubric of a presentation assessment with the counts the editor needs: any instance locks the structure, and a weight change recalculates the
 * assessed ones.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationRubricDTO(List<PresentationRubricCriterionDTO> criteria, long instanceCount, long assessedInstanceCount) {
}
