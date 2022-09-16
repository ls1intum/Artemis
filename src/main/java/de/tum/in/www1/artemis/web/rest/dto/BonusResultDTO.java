package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.BonusStrategy;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

/**
 * Represents a bonus grade result with the relevant parameters from bonusFrom and bonusTo grading scales.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BonusResultDTO(BonusStrategy bonusStrategy, String bonusFromTitle, Double studentPointsOfBonusSource, Double bonusGrade, Double finalPoints, String finalGrade,
        PlagiarismVerdict mostSeverePlagiarismVerdict) {
}
