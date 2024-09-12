package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a bonus calculation example with the relevant parameters from bonusFrom and bonusTo grading scales.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BonusExampleDTO(Double studentPointsOfBonusTo, Double studentPointsOfBonusSource, String examGrade, Double bonusGrade, Double finalPoints, String finalGrade,
        boolean exceedsMax) {
}
