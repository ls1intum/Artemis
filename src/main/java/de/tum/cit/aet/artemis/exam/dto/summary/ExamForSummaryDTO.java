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
 * single field the summary client additionally reads: {@code publishResultsDate}. The exam-taking client uses it to
 * decide whether results and the example solution are already visible (see {@code exam-result-summary} /
 * {@code quiz-exam-summary}). The conduction payload never carried it, so it is added here as a summary-only variant
 * rather than widening the shared {@link ExamForConductionDTO}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForSummaryDTO(@JsonUnwrapped ExamForConductionDTO exam, ZonedDateTime publishResultsDate) {

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
        return new ExamForSummaryDTO(ExamForConductionDTO.of(exam), exam.getPublishResultsDate());
    }
}
