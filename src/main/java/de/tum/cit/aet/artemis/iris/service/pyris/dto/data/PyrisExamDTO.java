package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisExamDTO(
        long id,
        String title,
        boolean isTextExam,
        Instant startDate,
        Instant endDate,
        Instant publishResultsDate,
        Instant examStudentReviewStart,
        Instant examStudentReviewEnd
// @formatter:on
) {

    public static PyrisExamDTO from(Exam exam) {
        // @formatter:off
        return new PyrisExamDTO(
                exam.getId(),
                exam.getTitle(),
                exam.isTestExam(),
                toInstant(exam.getStartDate()),
                toInstant(exam.getEndDate()),
                toInstant(exam.getPublishResultsDate()),
                toInstant(exam.getExamStudentReviewStart()),
                toInstant(exam.getExamStudentReviewEnd())
        );
        // @formatter:on
    }
}
