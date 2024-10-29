package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

/**
 * Represents a bonus grade result with the relevant parameters from bonusFrom and bonusTo grading scales.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BonusResultDTO(BonusStrategy bonusStrategy, String bonusFromTitle, Double studentPointsOfBonusSource, String bonusGrade, Double finalPoints, String finalGrade,
        PlagiarismVerdict mostSeverePlagiarismVerdict, Double achievedPresentationScore, Integer presentationScoreThreshold) {
}
