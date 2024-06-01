package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.Visibility;

/**
 * This is a DTO for updating a programming exercise test case.
 * It is only allowed to alter the weight, bonus multiplier, bonus points and visibility flag of a test case from an
 * endpoint, the other attributes are generated automatically.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseTestCaseDTO(Long id, Double weight, Double bonusMultiplier, Double bonusPoints, Visibility visibility) {
}
