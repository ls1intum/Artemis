package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamMode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Minimal projection of an {@link Exam}, nested inside {@link StudentExamDTO} where the client needs exam-level
 * context (course access rights, the exam-level default working time, and the exam mode) alongside
 * the student exam itself.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForStudentExamDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExamMode examMode, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int workingTime,
        CourseForStudentExamDTO course) {

    /**
     * Converts an Exam into an ExamForStudentExamDTO.
     *
     * @param exam the exam to convert
     * @return the converted DTO, or null if the exam is null
     */
    public static ExamForStudentExamDTO of(Exam exam) {
        if (exam == null) {
            return null;
        }
        return new ExamForStudentExamDTO(exam.getId(), exam.getTitle(), exam.getExamMode(), exam.getWorkingTime(), CourseForStudentExamDTO.of(exam.getCourse()));
    }
}
