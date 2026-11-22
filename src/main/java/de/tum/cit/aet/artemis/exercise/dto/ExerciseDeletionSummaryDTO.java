package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What deleting an exercise removes besides the exercise itself, shown to the instructor before they confirm.
 *
 * @param numberOfStudentParticipations count of the participations of students and teams
 * @param numberOfTeams                 count of the teams that were created for the exercise
 * @param numberOfSubmissions           count of all submissions
 * @param numberOfResults               count of all results, which includes automatic results and assessments
 * @param numberOfAssessments           count of the participations that have a rated manual assessment
 * @param numberOfComplaints            count of the complaints and more feedback requests
 * @param numberOfPlagiarismComparisons count of the compared pairs of submissions of the plagiarism checks
 * @param numberOfExampleSubmissions    count of the example submissions
 * @param numberOfBuilds                count of the build jobs, which only exist for programming exercises
 * @param numberOfCommunicationPosts    count of the posts in the channel of the exercise
 * @param numberOfAnswerPosts           count of the answers to those posts
 * @param numberOfCompetencyLinks       count of the links to competencies
 * @param numberOfLectureUnitLinks      count of the lecture units that link to the exercise
 * @param numberOfStudentExams          count of the student exams that contain the exercise, which is only more than zero for exam exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY) // Note: Primitive long will be included even if 0
public record ExerciseDeletionSummaryDTO(long numberOfStudentParticipations, long numberOfTeams, long numberOfSubmissions, long numberOfResults, long numberOfAssessments,
        long numberOfComplaints, long numberOfPlagiarismComparisons, long numberOfExampleSubmissions, long numberOfBuilds, long numberOfCommunicationPosts,
        long numberOfAnswerPosts, long numberOfCompetencyLinks, long numberOfLectureUnitLinks, long numberOfStudentExams) {
}
