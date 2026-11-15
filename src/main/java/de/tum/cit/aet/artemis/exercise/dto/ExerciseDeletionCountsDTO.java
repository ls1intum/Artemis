package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The part of the deletion summary of an exercise that is counted together in one query. The teams, submissions and results are counted separately, see
 * {@link ExerciseDeletionSummaryDTO}, which is assembled from both.
 *
 * @param numberOfStudentParticipations count of the participations of students and teams
 * @param numberOfAssessments           count of the participations that have a rated manual assessment
 * @param numberOfComplaints            count of the complaints and more feedback requests
 * @param numberOfPlagiarismComparisons count of the compared pairs of submissions of the plagiarism checks
 * @param numberOfExampleSubmissions    count of the example submissions
 * @param numberOfBuilds                count of the build jobs, which only exist for programming exercises
 * @param numberOfCommunicationPosts    count of the posts in the channel of the exercise
 * @param numberOfAnswerPosts           count of the answers to those posts
 * @param numberOfCompetencyLinks       count of the links to competencies
 * @param numberOfLectureUnitLinks      count of the lecture units that link to the exercise
 * @param numberOfStudentExams          count of the student exams that contain the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY) // Note: Primitive long will be included even if 0
public record ExerciseDeletionCountsDTO(long numberOfStudentParticipations, long numberOfAssessments, long numberOfComplaints, long numberOfPlagiarismComparisons,
        long numberOfExampleSubmissions, long numberOfBuilds, long numberOfCommunicationPosts, long numberOfAnswerPosts, long numberOfCompetencyLinks,
        long numberOfLectureUnitLinks, long numberOfStudentExams) {
}
