package de.tum.cit.aet.artemis.modeling.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfSubmissions, long numberOfAssessments, long numberOfCommunicationPosts,
        long numberOfAnswerPosts) {
}
