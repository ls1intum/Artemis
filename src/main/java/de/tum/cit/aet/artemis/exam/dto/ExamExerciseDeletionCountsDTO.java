package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.DescribedWhenUnwrapped;

/**
 * The counts that the deletion summary of an exam and the deletion summary of an exercise group share. Both embed it with
 * {@code JsonUnwrapped}, so the counts are flat in their JSON.
 *
 * @param numberOfProgrammingExercises  count of the programming exercises
 * @param numberOfQuizExercises         count of the quiz exercises
 * @param numberOfTextExercises         count of the text exercises
 * @param numberOfModelingExercises     count of the modeling exercises
 * @param numberOfFileUploadExercises   count of the file upload exercises
 * @param numberOfStudentParticipations count of all participations in these exercises, including the instructors' test runs
 * @param numberOfSubmissions           count of all submissions
 * @param numberOfResults               count of all results, which includes automatic results and assessments
 * @param numberOfAssessments           count of the submissions with a finished manual assessment
 * @param numberOfComplaints            count of the complaints and more feedback requests
 * @param numberOfPlagiarismComparisons count of the compared pairs of submissions of the plagiarism checks
 * @param numberOfBuilds                count of the build jobs of the programming exercises
 */
@DescribedWhenUnwrapped
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamExerciseDeletionCountsDTO(long numberOfProgrammingExercises, long numberOfQuizExercises, long numberOfTextExercises, long numberOfModelingExercises,
        long numberOfFileUploadExercises, long numberOfStudentParticipations, long numberOfSubmissions, long numberOfResults, long numberOfAssessments, long numberOfComplaints,
        long numberOfPlagiarismComparisons, long numberOfBuilds) {
}
