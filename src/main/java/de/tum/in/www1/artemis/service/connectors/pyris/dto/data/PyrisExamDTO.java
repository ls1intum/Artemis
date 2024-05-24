package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import static de.tum.in.www1.artemis.service.util.ZonedDateTimeUtil.toInstant;

import java.time.Instant;

import de.tum.in.www1.artemis.domain.exam.Exam;

public record PyrisExamDTO(long id, String title, boolean isTextExam, Instant startDate, Instant endDate, Instant publishResultsDate, Instant examStudentReviewStart,
        Instant examStudentReviewEnd

) {

    public static PyrisExamDTO of(Exam exam) {
        return new PyrisExamDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), toInstant(exam.getStartDate()), toInstant(exam.getEndDate()),
                toInstant(exam.getPublishResultsDate()), toInstant(exam.getExamStudentReviewStart()), toInstant(exam.getExamStudentReviewEnd()));
    }
}
