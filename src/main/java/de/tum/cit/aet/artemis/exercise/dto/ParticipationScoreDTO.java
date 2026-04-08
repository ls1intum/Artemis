package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;

/**
 * DTO used by the exercise scores view to display paginated participation results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipationScoreDTO(long participationId, ZonedDateTime initializationDate, int submissionCount, String participantName, String participantIdentifier,
        Long studentId, Long teamId, Long resultId, Double score, Boolean successful, ZonedDateTime completionDate, AssessmentType assessmentType, String assessmentNote,
        long durationInSeconds, Long submissionId, Boolean buildFailed, String buildPlanId, String repositoryUri, boolean testRun) {
}
