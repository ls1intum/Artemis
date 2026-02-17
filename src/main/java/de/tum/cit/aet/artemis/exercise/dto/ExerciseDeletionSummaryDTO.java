package de.tum.cit.aet.artemis.exercise.dto;

public record ExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfBuilds, long numberOfAssessments, long numberOfCommunicationPosts,
        long numberOfAnswerPosts) {
}
