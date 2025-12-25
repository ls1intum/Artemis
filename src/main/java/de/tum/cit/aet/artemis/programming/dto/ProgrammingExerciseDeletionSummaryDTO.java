package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfBuilds, long numberOfCommunicationPosts, long numberOfAnswerPosts) {
}
