package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A level of the scale of a presentation rubric criterion, from the lowest to the highest. Its position is its index in the scale.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationRubricLevelDTO(Long id, @Size(max = 1000) String description) {
}
