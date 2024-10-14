package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamDeletionSummaryDTO(long numberOfBuilds, long numberOfCommunicationPosts, long numberOfAnswerPosts, long numberRegisteredStudents, long numberNotStartedExams,
        long numberStartedExams, long numberSubmittedExams) {
}
