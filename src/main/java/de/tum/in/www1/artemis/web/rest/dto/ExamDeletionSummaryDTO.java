package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamDeletionSummaryDTO(long numberOfBuilds, long numberOfCommunicationPosts, long numberOfAnswerPosts, long numberRegisteredStudents, long numberNotStartedExams,
        long numberStartedExams, long numberSubmittedExams) {
}
