package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(requiredProperties = { "orphanStudentScore", "orphanTeamScore", "orphanFeedbackForOrphanResults", "orphanLongFeedbackTextForOrphanResults",
        "orphanTextBlockForOrphanResults", "orphanRating", "orphanResultsWithoutParticipation", "orphanFeedbackMessage" })
public record OrphanCleanupCountDTO(int orphanStudentScore, int orphanTeamScore, int orphanFeedbackForOrphanResults, int orphanLongFeedbackTextForOrphanResults,
        int orphanTextBlockForOrphanResults, int orphanRating, int orphanResultsWithoutParticipation, int orphanFeedbackMessage) {
}
