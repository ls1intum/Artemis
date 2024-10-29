package de.tum.cit.aet.artemis.exam.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for providing statistics for the exam instructor dashboard, some values are nullable because they are not always needed
 * Instructors typically see all values, while tutors only see some values
 *
 * @param numberOfTotalExamAssessmentsFinishedByCorrectionRound all exercises summed up
 * @param numberOfTotalParticipationsForAssessment              all exercises summed up
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamChecklistDTO(@Nullable Long numberOfGeneratedStudentExams, @Nullable Long numberOfTestRuns,
        @Nullable Long[] numberOfTotalExamAssessmentsFinishedByCorrectionRound, Long numberOfTotalParticipationsForAssessment, @Nullable Long numberOfExamsSubmitted,
        @Nullable Long numberOfExamsStarted, Long numberOfAllComplaints, Long numberOfAllComplaintsDone, @Nullable Boolean allExamExercisesAllStudentsPrepared,
        Boolean existsUnassessedQuizzes, Boolean existsUnsubmittedExercises) {

    public ExamChecklistDTO(@Nullable Long[] numberOfTotalExamAssessmentsFinishedByCorrectionRound, Long numberOfTotalParticipationsForAssessment, Boolean existsUnassessedQuizzes,
            Boolean existsUnsubmittedExercises) {
        this(null, null, numberOfTotalExamAssessmentsFinishedByCorrectionRound, numberOfTotalParticipationsForAssessment, null, null, null, null, null, existsUnassessedQuizzes,
                existsUnsubmittedExercises);
    }

    /**
     * Checks if the Boolean values are null. If they are, it assigns them a default value of false.
     */
    public ExamChecklistDTO {
        if (allExamExercisesAllStudentsPrepared == null) {
            allExamExercisesAllStudentsPrepared = false;
        }
        if (existsUnassessedQuizzes == null) {
            existsUnassessedQuizzes = false;
        }
        if (existsUnsubmittedExercises == null) {
            existsUnsubmittedExercises = false;
        }
    }

}
