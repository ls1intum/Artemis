package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExerciseDeletionSummaryDTO(long numberOfStudentParticipations, Long numberOfBuilds, Long numberOfSubmissions, Long numberOfAssessments,
        long numberOfCommunicationPosts, long numberOfAnswerPosts) {
}
