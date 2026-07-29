package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Minimal projection of an {@link Exam}, nested inside {@link StudentExamDTO} where the client needs exam-level
 * context (course access rights, the exam-level default working time, and whether it is a test exam) alongside
 * the student exam itself.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForStudentExamDTO(long id, String title, boolean testExam, int workingTime, CourseForStudentExamDTO course) {

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
        return new ExamForStudentExamDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.getWorkingTime(), CourseForStudentExamDTO.of(exam.getCourse()));
    }
}
