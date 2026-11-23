package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * What deleting an exam removes besides the exam itself, shown to the instructor before they confirm.
 *
 * @param exerciseCounts             the counts of the exercises of the exam and of what hangs on them, flat in the JSON
 * @param numberOfCommunicationPosts count of the posts in the channel of the exam
 * @param numberOfAnswerPosts        count of the answers to those posts
 * @param numberRegisteredStudents   count of the students that are registered for the exam
 * @param numberNotStartedExams      count of the student exams that no student started
 * @param numberStartedExams         count of the student exams that a student started
 * @param numberSubmittedExams       count of the student exams that a student handed in
 * @param numberOfExerciseGroups     count of the exercise groups
 * @param numberOfTestRuns           count of the test runs of the instructors
 * @param numberOfExamSessions       count of the sessions that were recorded while students worked on the exam
 * @param numberOfExamLiveEvents     count of the live events such as announcements and working time updates
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamDeletionSummaryDTO(@JsonUnwrapped ExamExerciseDeletionCountsDTO exerciseCounts, long numberOfCommunicationPosts, long numberOfAnswerPosts,
        long numberRegisteredStudents, long numberNotStartedExams, long numberStartedExams, long numberSubmittedExams, long numberOfExerciseGroups, long numberOfTestRuns,
        long numberOfExamSessions, long numberOfExamLiveEvents) {
}
