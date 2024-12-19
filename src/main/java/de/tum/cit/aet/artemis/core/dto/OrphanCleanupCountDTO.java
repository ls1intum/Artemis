package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrphanCleanupCountDTO(int orphanFeedback, int orphanLongFeedbackText, int orphanTextBlock, int orphanStudentScore, int orphanTeamScore,
        int orphanFeedbackForOrphanResults, int orphanLongFeedbackTextForOrphanResults, int orphanTextBlockForOrphanResults, int orphanRating,
        int orphanResultsWithoutParticipation) {
}
