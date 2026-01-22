package de.tum.cit.aet.artemis.text.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfSubmissions, long numberOfAssessments, long numberOfCommunicationPosts,
        long numberOfAnswerPosts) {
}
