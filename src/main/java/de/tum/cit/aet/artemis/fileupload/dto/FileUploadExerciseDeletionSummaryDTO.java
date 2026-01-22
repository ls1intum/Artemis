package de.tum.cit.aet.artemis.fileupload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfSubmissions, long numberOfAssessments, long numberOfCommunicationPosts,
        long numberOfAnswerPosts) {
}
