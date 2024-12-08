package de.tum.cit.aet.artemis.core.dto;

public record OrphanCleanupCountDTO(int orphanFeedback, int orphanLongFeedbackText, int orphanTextBlock, int orphanStudentScore, int orphanTeamScore,
        int orphanFeedbackForOrphanResults, int orphanLongFeedbackTextForOrphanResults, int orphanTextBlockForOrphanResults, int orphanRating,
        int orphanResultsWithoutParticipation) {
}
