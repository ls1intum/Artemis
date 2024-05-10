package de.tum.in.www1.artemis.web.rest.dto;

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
        @Nullable Long numberOfExamsStarted, Long numberOfAllComplaints, Long numberOfAllComplaintsDone, @Nullable Boolean allExamExercisesAllStudentsPrepared) {

}
