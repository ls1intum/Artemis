package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfBuilds, long numberOfAssessments, long numberOfCommunicationPosts,
        long numberOfAnswerPosts) {
}
