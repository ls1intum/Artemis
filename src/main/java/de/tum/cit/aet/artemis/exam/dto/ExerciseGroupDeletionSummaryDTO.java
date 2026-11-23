package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * What deleting an exercise group removes besides the group itself, shown to the instructor before they confirm.
 *
 * @param exerciseCounts       the counts of the exercises in the group and of what hangs on them, flat in the JSON
 * @param numberOfStudentExams count of the student exams that contain one of the exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupDeletionSummaryDTO(@JsonUnwrapped ExamExerciseDeletionCountsDTO exerciseCounts, long numberOfStudentExams) {
}
