package de.tum.cit.aet.artemis.service.connectors.pyris.dto.data;

import static de.tum.cit.aet.artemis.service.util.TimeUtil.toInstant;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.exam.Exam;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisExamDTO(long id, String title, boolean isTextExam, Instant startDate, Instant endDate, Instant publishResultsDate, Instant examStudentReviewStart,
        Instant examStudentReviewEnd) {

    public static PyrisExamDTO of(Exam exam) {
        return new PyrisExamDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), toInstant(exam.getStartDate()), toInstant(exam.getEndDate()),
                toInstant(exam.getPublishResultsDate()), toInstant(exam.getExamStudentReviewStart()), toInstant(exam.getExamStudentReviewEnd()));
    }
}
