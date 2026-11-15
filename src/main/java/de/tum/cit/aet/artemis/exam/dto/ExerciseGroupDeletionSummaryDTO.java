package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What deleting an exercise group removes besides the group itself, shown to the instructor before they confirm.
 *
 * @param numberOfProgrammingExercises  count of the programming exercises in the group
 * @param numberOfQuizExercises         count of the quiz exercises in the group
 * @param numberOfTextExercises         count of the text exercises in the group
 * @param numberOfModelingExercises     count of the modeling exercises in the group
 * @param numberOfFileUploadExercises   count of the file upload exercises in the group
 * @param numberOfStudentParticipations count of all participations in these exercises, including the instructors' test runs
 * @param numberOfSubmissions           count of all submissions
 * @param numberOfResults               count of all results, which includes automatic results and assessments
 * @param numberOfAssessments           count of the submissions with a finished manual assessment
 * @param numberOfComplaints            count of the complaints and more feedback requests
 * @param numberOfPlagiarismComparisons count of the compared pairs of submissions of the plagiarism checks
 * @param numberOfBuilds                count of the build jobs of the programming exercises
 * @param numberOfStudentExams          count of the student exams that contain one of the exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupDeletionSummaryDTO(long numberOfProgrammingExercises, long numberOfQuizExercises, long numberOfTextExercises, long numberOfModelingExercises,
        long numberOfFileUploadExercises, long numberOfStudentParticipations, long numberOfSubmissions, long numberOfResults, long numberOfAssessments, long numberOfComplaints,
        long numberOfPlagiarismComparisons, long numberOfBuilds, long numberOfStudentExams) {
}
