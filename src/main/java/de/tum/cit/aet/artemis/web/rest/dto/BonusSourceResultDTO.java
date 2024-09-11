package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismVerdict;

/**
 * Represents a grade result from a bonus source course or exam with plagiarism verdict.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BonusSourceResultDTO(Double achievedPoints, PlagiarismVerdict mostSeverePlagiarismVerdict, Double achievedPresentationScore, Integer presentationScoreThreshold,
        boolean hasParticipated) {
}
