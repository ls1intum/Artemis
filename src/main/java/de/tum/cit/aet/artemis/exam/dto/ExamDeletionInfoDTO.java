package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A minimal DTO containing only the information needed for exam deletion/reset progress tracking.
 * This avoids loading full Exam entities with all their associations.
 *
 * @param examId                   the exam ID
 * @param studentExamCount         the number of student exams
 * @param programmingExerciseCount the number of programming exercises in the exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamDeletionInfoDTO(long examId, long studentExamCount, long programmingExerciseCount) {
}
