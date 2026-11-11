package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The rubric of a presentation assessment with the number of assessed instances, which lock the structure and whose results a weight change recalculates.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationRubricDTO(List<PresentationRubricCriterionDTO> criteria, long assessedInstanceCount) {
}
