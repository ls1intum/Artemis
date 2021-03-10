package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.ldap.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequests;

public interface TutorLeaderboardAnsweredMoreFeedbackRequestsRepository extends JpaRepository<TutorLeaderboardAnsweredMoreFeedbackRequests, Long> {

    @Query("""
            """)
    /*
     * SELECT count(0) AS answered_requests, sum(e.max_points) AS points, u.id AS user_id, u.first_name AS first_name, c.id AS course_id, e.id AS exercise_id FROM (((((((jhi_user u
     * JOIN user_groups g) JOIN course c) JOIN exercise e) JOIN participation p) JOIN result r) JOIN complaint cp) JOIN complaint_response cr) WHERE ((cp.complaint_type =
     * 'MORE_FEEDBACK') and(g.user_id = u.id) and(g.groups = c.teaching_assistant_group_name) and(c.id = e.course_id) and(p.exercise_id = e.id) and(e.discriminator in('M', 'T',
     * 'F', 'P')) and(r.participation_id = p.id) and(cr.reviewer_id = u.id) and(cp.result_id = r.id) and(cp.id = cr.complaint_id) and(cp.accepted = 1) and(r.completion_date IS NOT
     * NULL)) GROUP BY u.id, e.id
     */
    List<TutorLeaderboardAnsweredMoreFeedbackRequests> findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId(@Param("courseId") long courseId);

    List<TutorLeaderboardAnsweredMoreFeedbackRequests> findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId(@Param("groupName") String groupName,
            @Param("exerciseId") long exerciseId);

}
