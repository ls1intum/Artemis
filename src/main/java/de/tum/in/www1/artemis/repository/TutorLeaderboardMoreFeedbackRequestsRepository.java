package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequests;

public interface TutorLeaderboardMoreFeedbackRequestsRepository extends JpaRepository<TutorLeaderboardMoreFeedbackRequests, Long> {

    /*
     * @Query(""" SELECT new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequests( null, result.assessor, sum(exercise.max_points), course.id ) FROM
     * Complaint complaint left join complaint.result result WHERE (complaint.complaint_type = 'MORE_FEEDBACK') and result.participation.exercise.course.teachingAssistantGroupName
     * member of result.assessor.groups and result.completionDate is not null and result.participation.exercise.course.id = :#{#courseId} and
     * result.participation.exercise.discriminator in ('M', 'T', 'F', 'P') and (g.user_id = u.id) and (g.groups = c.teaching_assistant_group_name) and (c.id = e.course_id) and
     * (p.exercise_id = e.id) and (e.discriminator in('M', 'T', 'F', 'P')) and (r.participation_id = p.id) and (r.assessor_id = u.id) and (cp.result_id = r.id) and
     * (r.completion_date IS NOT NULL) GROUP BY result.assessor.id, course.id """) List<TutorLeaderboardMoreFeedbackRequests>
     * findTutorLeaderboardMoreFeedbackRequestsByCourseId(@Param("courseId") long courseId);;
     */
    /*
     * SELECT count(0) AS all_requests, sum(( CASE WHEN (cp.accepted IS NULL) THEN 1 ELSE 0 END)) AS not_answered_requests, sum(( CASE WHEN (cp.accepted IS NULL) THEN e.max_points
     * ELSE 0 END)) AS points, u.id AS user_id, u.first_name AS first_name, c.id AS course_id, e.id AS exercise_id FROM ((((((jhi_user u JOIN user_groups g) JOIN course c) JOIN
     * exercise e) JOIN participation p) JOIN result r) JOIN complaint cp) WHERE ((cp.complaint_type = 'MORE_FEEDBACK') and(g.user_id = u.id) and(g.groups =
     * c.teaching_assistant_group_name) and(c.id = e.course_id) and(p.exercise_id = e.id) and(e.discriminator in('M', 'T', 'F', 'P')) and(r.participation_id = p.id)
     * and(r.assessor_id = u.id) and(cp.result_id = r.id) and(r.completion_date IS NOT NULL)) GROUP BY u.id, e.id
     */
    List<TutorLeaderboardMoreFeedbackRequests> findTutorLeaderboardMoreFeedbackRequestsByCourseId(@Param("courseId") long courseId);

    List<TutorLeaderboardMoreFeedbackRequests> findTutorLeaderboardMoreFeedbackRequestsByExerciseId(@Param("groupName") String groupName, @Param("exerciseId") long exerciseId);

}
