package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY) // Note: Primitive long will be included even if 0
public record ExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfBuilds, long numberOfAssessments, long numberOfCommunicationPosts,
        long numberOfAnswerPosts) {
}
