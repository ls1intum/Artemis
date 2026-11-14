package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What deleting an exam removes besides the exam itself, shown to the instructor before they confirm.
 *
 * @param numberOfBuilds                count of the build jobs of all programming exercises
 * @param numberOfCommunicationPosts    count of the posts in the channel of the exam
 * @param numberOfAnswerPosts           count of the answers to those posts
 * @param numberRegisteredStudents      count of the students that are registered for the exam
 * @param numberNotStartedExams         count of the student exams that no student started
 * @param numberStartedExams            count of the student exams that a student started
 * @param numberSubmittedExams          count of the student exams that a student handed in
 * @param numberOfExerciseGroups        count of the exercise groups
 * @param numberOfProgrammingExercises  count of the programming exercises
 * @param numberOfQuizExercises         count of the quiz exercises
 * @param numberOfTextExercises         count of the text exercises
 * @param numberOfModelingExercises     count of the modeling exercises
 * @param numberOfFileUploadExercises   count of the file upload exercises
 * @param numberOfStudentParticipations count of all participations in the exercises, including the instructors' test runs
 * @param numberOfSubmissions           count of all submissions
 * @param numberOfResults               count of all results, which includes automatic results and assessments
 * @param numberOfAssessments           count of the submissions with a finished manual assessment
 * @param numberOfComplaints            count of the complaints and more feedback requests
 * @param numberOfPlagiarismComparisons count of the compared pairs of submissions of the plagiarism checks
 * @param numberOfTestRuns              count of the test runs of the instructors
 * @param numberOfExamSessions          count of the sessions that were recorded while students worked on the exam
 * @param numberOfExamLiveEvents        count of the live events such as announcements and working time updates
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamDeletionSummaryDTO(long numberOfBuilds, long numberOfCommunicationPosts, long numberOfAnswerPosts, long numberRegisteredStudents, long numberNotStartedExams,
        long numberStartedExams, long numberSubmittedExams, long numberOfExerciseGroups, long numberOfProgrammingExercises, long numberOfQuizExercises, long numberOfTextExercises,
        long numberOfModelingExercises, long numberOfFileUploadExercises, long numberOfStudentParticipations, long numberOfSubmissions, long numberOfResults,
        long numberOfAssessments, long numberOfComplaints, long numberOfPlagiarismComparisons, long numberOfTestRuns, long numberOfExamSessions, long numberOfExamLiveEvents) {
}
