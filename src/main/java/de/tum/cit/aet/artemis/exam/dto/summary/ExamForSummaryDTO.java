package de.tum.cit.aet.artemis.exam.dto.summary;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.dto.conduction.ExamForConductionDTO;

/**
 * Projection of an {@link Exam} as nested inside the post-submission summary student-exam payload.
 * <p>
 * The summary reuses the conduction exam projection verbatim (same cover / date / confirmation fields) and adds the
 * fields the summary client additionally reads, all of them gating post-publish UI:
 * <ul>
 * <li>{@code publishResultsDate} - whether results and the example solution are already visible (see
 * {@code exam-result-summary} / {@code quiz-exam-summary})</li>
 * <li>{@code exampleSolutionPublicationDate} - whether the example solution itself may be rendered yet
 * ({@code exam-result-summary.component.ts})</li>
 * <li>{@code examStudentReviewStart} / {@code examStudentReviewEnd} - the window during which the complaint /
 * review UI is enabled ({@code exam-general-information.component}, {@code exam-result-summary.component.ts})</li>
 * </ul>
 * The conduction payload never carries any of these, so they are added here as a summary-only variant rather than
 * widening the shared {@link ExamForConductionDTO}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForSummaryDTO(@JsonUnwrapped ExamForConductionDTO exam, ZonedDateTime publishResultsDate, ZonedDateTime exampleSolutionPublicationDate,
        ZonedDateTime examStudentReviewStart, ZonedDateTime examStudentReviewEnd) {

    /**
     * Converts an Exam into an ExamForSummaryDTO.
     *
     * @param exam the exam to convert
     * @return the converted DTO, or null if the exam is null
     */
    public static ExamForSummaryDTO of(Exam exam) {
        if (exam == null) {
            return null;
        }
        return new ExamForSummaryDTO(ExamForConductionDTO.of(exam), exam.getPublishResultsDate(), exam.getExampleSolutionPublicationDate(), exam.getExamStudentReviewStart(),
                exam.getExamStudentReviewEnd());
    }
}
