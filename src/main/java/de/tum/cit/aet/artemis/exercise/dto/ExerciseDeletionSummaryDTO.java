package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude
public record ExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfBuilds, long numberOfAssessments, long numberOfCommunicationPosts,
        long numberOfAnswerPosts) {
}
