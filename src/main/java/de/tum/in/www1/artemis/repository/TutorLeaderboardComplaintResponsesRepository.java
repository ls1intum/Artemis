package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponses;

public interface TutorLeaderboardComplaintResponsesRepository extends JpaRepository<TutorLeaderboardComplaintResponses, Long> {

    /*
     * SELECT count(0) AS complaint_responses, sum(e.max_points) AS points, u.id AS user_id, u.first_name AS first_name, c.id AS course_id, e.id AS exercise_id FROM
     * (((((((artemis.jhi_user u JOIN artemis.user_groups g) JOIN artemis.course c) JOIN artemis.exercise e) JOIN artemis.participation p) JOIN artemis.result r) JOIN
     * artemis.complaint cp) JOIN artemis.complaint_response cr) WHERE ((cp.complaint_type = 'COMPLAINT') and(g.user_id = u.id) and(g.groups = c.teaching_assistant_group_name)
     * and(c.id = e.course_id) and(p.exercise_id = e.id) and(e.discriminator in('M', 'T', 'F', 'P')) and(r.participation_id = p.id) and(cr.reviewer_id = u.id) and(cp.result_id =
     * r.id) and(cp.id = cr.complaint_id) and(cp.accepted IS NOT NULL) and(r.completion_date IS NOT NULL)) GROUP BY u.id, e.id
     */
    List<TutorLeaderboardComplaintResponses> findTutorLeaderboardComplaintResponsesByCourseId(@Param("courseId") long courseId);

    List<TutorLeaderboardComplaintResponses> findTutorLeaderboardComplaintResponsesByExerciseId(@Param("groupName") String groupName, @Param("exerciseId") long exerciseId);

}
