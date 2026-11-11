package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The rubric of a presentation assessment with the number of assessed instances, which lock the structure and whose results a weight change recalculates.
 * The version of the presentation assessment lets a client save the rubric only as long as nobody changed the presentation in the meantime.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationRubricDTO(List<PresentationRubricCriterionDTO> criteria, long assessedInstanceCount, long version) {
}
