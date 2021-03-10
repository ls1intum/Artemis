package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequests;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponses;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaints;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequests;

/**
 * Spring Data JPA repository for the Complaint entity.
 */
@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByResult_Id(Long resultId);

    @Query("SELECT c FROM Complaint c LEFT JOIN FETCH c.result r LEFT JOIN FETCH r.assessor WHERE c.id = :#{#complaintId}")
    Optional<Complaint> findByIdWithEagerAssessor(@Param("complaintId") Long complaintId);

    /**
     * This magic method counts the number of complaints associated to a course id and to the results assessed by a specific user, identified by a tutor id
     *
     * @param courseId - the id of the course we want to filter by
     * @param tutorId  - the id of the tutor we are interested in
     * @return number of complaints associated to course courseId and tutor tutorId
     */
    long countByResult_Participation_Exercise_Course_IdAndResult_Assessor_Id(Long courseId, Long tutorId);

    /**
     * This magic method counts the number of complaints by complaint type associated to a course id
     *
     * @param courseId      - the id of the course we want to filter by
     * @param complaintType - type of complaint we want to filter by
     * @return number of more feedback requests associated to course courseId
     */
    long countByResult_Participation_Exercise_Course_IdAndComplaintType(Long courseId, ComplaintType complaintType);

    /**
     * This magic method counts the number of complaints by complaint type associated to a exam id
     *
     * @param examId      - the id of the exam we want to filter by
     * @param complaintType - type of complaint we want to filter by
     * @return number of complaints  associated to course examId
     */
    long countByResult_Participation_Exercise_ExerciseGroup_Exam_IdAndComplaintType(Long examId, ComplaintType complaintType);

    @Query("SELECT c FROM Complaint c LEFT JOIN FETCH c.result r LEFT JOIN FETCH r.assessor LEFT JOIN FETCH r.participation p LEFT JOIN FETCH p.exercise e LEFT JOIN FETCH r.submission WHERE e.id = :#{#exerciseId} AND c.complaintType = :#{#complaintType}")
    List<Complaint> findByResult_Participation_Exercise_Id_ComplaintTypeWithEagerSubmissionAndEagerAssessor(@Param("exerciseId") Long exerciseId,
            @Param("complaintType") ComplaintType complaintType);

    /**
     * Count the number of unaccepted complaints of a student in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the number
     * of complaints for a student in a course. Requests for more feedback are not counted here.
     *
     * @param studentId the id of the student
     * @param courseId  the id of the course
     * @return the number of unaccepted complaints
     */
    @Query("SELECT count(c) FROM Complaint c WHERE c.complaintType = 'COMPLAINT' AND c.student.id = :#{#studentId} AND c.result.participation.exercise.course.id = :#{#courseId} AND (c.accepted = false OR c.accepted is null)")
    long countUnacceptedComplaintsByComplaintTypeStudentIdAndCourseId(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /**
     * Count the number of unaccepted complaints of a team in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the number
     * of complaints for a team in a course. Requests for more feedback are not counted here.
     *
     * @param teamShortName the short name of the team
     * @param courseId  the id of the course
     * @return the number of unaccepted complaints
     */
    @Query("SELECT count(c) FROM Complaint c WHERE c.complaintType = 'COMPLAINT' AND c.team.shortName = :#{#teamShortName} AND c.result.participation.exercise.course.id = :#{#courseId} AND (c.accepted = false OR c.accepted is null)")
    long countUnacceptedComplaintsByComplaintTypeTeamShortNameAndCourseId(@Param("teamShortName") String teamShortName, @Param("courseId") Long courseId);

    /**
     * This magic method counts the number of complaints by complaint type associated to an exercise id
     *
     * @param exerciseId    - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints associated to exercise exerciseId
     */
    long countByResult_Participation_Exercise_IdAndComplaintType(Long exerciseId, ComplaintType complaintType);

    /**
     * Similar to {@link ComplaintRepository#countByResult_Participation_Exercise_IdAndComplaintType}
     * but ignores test run submissions
     * @param exerciseId - the id of the exercise we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return  number of complaints associated to exercise exerciseId without test runs
     */
    @Query("""
            SELECT count(c) FROM Complaint c
            WHERE c.complaintType = :#{#complaintType}
            AND c.result.participation.testRun = false
            AND c.result.participation.exercise.id = :#{#exerciseId}

            """)
    long countByResultParticipationExerciseIdAndComplaintTypeIgnoreTestRuns(@Param("exerciseId") Long exerciseId, @Param("complaintType") ComplaintType complaintType);

    /**
     * Delete all complaints that belong to results of a given participation
     * @param participationId the Id of the participation where the complaints should be deleted
     */
    void deleteByResult_Participation_Id(Long participationId);

    /**
     * Delete all complaints that belong to the given result
     * @param resultId the Id of the result where the complaints should be deleted
     */
    void deleteByResult_Id(long resultId);

    /**
     * Given a user id, retrieve all complaints related to assessments made by that assessor
     *
     * @param assessorId - the id of the assessor
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Assessor_Id(Long assessorId);

    /**
     * Given a exercise id, retrieve all complaints related to that exercise
     *
     * @param exerciseId - the id of the exercise
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Participation_Exercise_Id(Long exerciseId);

    /**
     * Given a course id, retrieve all complaints related to assessments related to that course
     *
     * @param courseId - the id of the course
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Participation_Exercise_Course_Id(Long courseId);

    /**
     * Given a examId id, retrieve all complaints related to assessments related to that course
     *
     * @param examId - the id of the course
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Participation_Exercise_ExerciseGroup_Exam_Id(Long examId);

    /**
     * Given a user id and an exercise id retrieve all complaints related to assessments made by that assessor in that exercise.
     *
     * @param assessorId - the id of the assessor
     * @param exerciseId - the id of the exercise
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Assessor_IdAndResult_Participation_Exercise_Id(Long assessorId, Long exerciseId);

    /**
     * Given a user id and a course id retrieve all complaints related to assessments made by that assessor in that course.
     *
     * @param assessorId - the id of the assessor
     * @param courseId   - the id of the course
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Assessor_IdAndResult_Participation_Exercise_Course_Id(Long assessorId, Long courseId);

    /*
     * SELECT count(0) AS all_complaints, sum(( CASE WHEN (cp.accepted = 1) THEN 1 ELSE 0 END)) AS accepted_complaints, sum(( CASE WHEN (cp.accepted = 1) THEN e.max_points ELSE 0
     * END)) AS points, u.id AS user_id, u.first_name AS first_name, c.id AS course_id, e.id AS exercise_id FROM ((((((jhi_user u JOIN user_groups g) JOIN course c) JOIN exercise
     * e) JOIN participation p) JOIN result r) JOIN complaint cp) WHERE ((cp.complaint_type = 'COMPLAINT') and(g.user_id = u.id) and(g.groups = c.teaching_assistant_group_name)
     * and(c.id = e.course_id) and(p.exercise_id = e.id) and(e.discriminator in('M', 'T', 'F', 'P')) and(r.participation_id = p.id) and(r.assessor_id = u.id) and(cp.result_id =
     * r.id) and(r.completion_date IS NOT NULL)) GROUP BY u.id, e.id I don'T really know how to accepted_complaints and points
     */
    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaints(
                -1L,
                complaint.result.assessor.id,
                count(complaint),
                sum( CASE WHEN (complaint.accepted = true) THEN 1 ELSE 0 END),
                sum( CASE WHEN (complaint.accepted = true) THEN complaint.result.participation.exercise.maxPoints ELSE 0 END),
                complaint.result.participation.exercise.course.id
            )
            FROM
                Complaint complaint
            WHERE
                 :#{#groupName} member of complaint.result.assessor.groups
                and complaint.complaintType = 'COMPLAINT'
                and complaint.result.participation.exercise.course.id = :courseId
                and TYPE(complaint.result.participation.exercise) in (ModelingExercise, TextExercise, FileUploadExercise, ProgrammingExercise)
                and complaint.result.completionDate IS NOT NULL
            GROUP BY complaint.result.assessor.id, complaint.result.participation.exercise.course.id
            """)
    List<TutorLeaderboardComplaints> findTutorLeaderboardComplaintsByCourseId(@Param("groupName") String groupName, @Param("courseId") long courseId);

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaints(
                :exerciseId,
                complaint.result.assessor.id,
                count(complaint),
                sum( CASE WHEN (complaint.accepted = true ) THEN 1 ELSE 0 END),
                sum( CASE WHEN (complaint.accepted = true) THEN complaint.result.participation.exercise.maxPoints ELSE 0 END),
                complaint.result.participation.exercise.course.id
            )
            FROM
                Complaint complaint
            WHERE
                 :#{#groupName} member of complaint.result.assessor.groups
                and complaint.complaintType = 'COMPLAINT'
                and complaint.result.participation.exercise.id = :#{#exerciseId}
                and TYPE(complaint.result.participation.exercise) in (ModelingExercise, TextExercise, FileUploadExercise, ProgrammingExercise)
                and complaint.result.completionDate IS NOT NULL
            GROUP BY complaint.result.assessor.id, complaint.result.participation.exercise.course.id
            """)
    List<TutorLeaderboardComplaints> findTutorLeaderboardComplaintsByExerciseId(@Param("groupName") String groupName, @Param("exerciseId") long exerciseId);

    /*
     * SELECT count(0) AS complaint_responses, sum(e.max_points) AS points, u.id AS user_id, u.first_name AS first_name, c.id AS course_id, e.id AS exercise_id FROM
     * (((((((artemis.jhi_user u JOIN artemis.user_groups g) JOIN artemis.course c) JOIN artemis.exercise e) JOIN artemis.participation p) JOIN artemis.result r) JOIN
     * artemis.complaint cp) JOIN artemis.complaint_response cr) WHERE ((cp.complaint_type = 'COMPLAINT') and(g.user_id = u.id) and(g.groups = c.teaching_assistant_group_name)
     * and(c.id = e.course_id) and(p.exercise_id = e.id) and(e.discriminator in('M', 'T', 'F', 'P')) and(r.participation_id = p.id) -- and(cr.reviewer_id = u.id) --
     * and(cp.result_id = r.id) and(cp.id = cr.complaint_id) and(cp.accepted IS NOT NULL) and(r.completion_date IS NOT NULL)) GROUP BY u.id, e.id
     */
    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponses(
                -1L,
                complaint.result.assessor.id,
                count(complaint),
                sum(complaint.result.participation.exercise.maxPoints),
                :#{#courseId}
            )
            FROM
                Complaint complaint
            WHERE
                complaint.complaintType = 'COMPLAINT'
                and :#{#groupName} member of complaint.result.assessor.groups
                and complaint.result.participation.exercise.course.id = :courseId
                and TYPE(complaint.result.participation.exercise) in (ModelingExercise, TextExercise, FileUploadExercise, ProgrammingExercise)
                and complaint.result.completionDate IS NOT NULL
                and complaint.accepted IS NOT NULL
            GROUP BY complaint.complaintResponse.reviewer.id, complaint.result.participation.exercise.course.id
            """)
    List<TutorLeaderboardComplaintResponses> findTutorLeaderboardComplaintResponsesByCourseId(@Param("groupName") String groupName, @Param("courseId") long courseId);

    @Query("""
            SELECT
             new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponses(
                 :exerciseId,
                 complaint.result.assessor.id,
                 count(complaint),
                 sum(complaint.result.participation.exercise.maxPoints),
                 complaint.result.participation.exercise.course.id
             )
             FROM
                 Complaint complaint
             WHERE
                 complaint.complaintType = 'COMPLAINT'
                 and :#{#groupName} member of complaint.result.assessor.groups
                 and complaint.result.participation.exercise.id = :exerciseId
                 and TYPE(complaint.result.participation.exercise) in (ModelingExercise, TextExercise, FileUploadExercise, ProgrammingExercise)
                 and complaint.result.completionDate IS NOT NULL
                 and complaint.accepted IS NOT NULL
             GROUP BY complaint.complaintResponse.reviewer.id, complaint.result.participation.exercise.course.id
             """)
    List<TutorLeaderboardComplaintResponses> findTutorLeaderboardComplaintResponsesByExerciseId(@Param("groupName") String groupName, @Param("exerciseId") long exerciseId);

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
    @Query("""
            SELECT
             new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequests(
                 -1L,
                 complaint.result.assessor.id,
                 count(complaint),
                 sum( CASE WHEN (complaint.accepted IS NULL) THEN 1 ELSE 0 END),
                 sum( CASE WHEN (complaint.accepted IS NULL) THEN complaint.result.participation.exercise.maxPoints ELSE 0 END),
                 :courseId
             )
             FROM
                 Complaint complaint
             WHERE
                 complaint.complaintType = 'FEEDBACK_REQUEST'
                 and :#{#groupName} member of complaint.result.assessor.groups
                 and complaint.result.participation.exercise.course.id = :courseId
                 and TYPE(complaint.result.participation.exercise) in (ModelingExercise, TextExercise, FileUploadExercise, ProgrammingExercise)
                 and complaint.result.completionDate IS NOT NULL
             GROUP BY complaint.complaintResponse.reviewer.id, complaint.result.participation.exercise.course.id
             """)
    List<TutorLeaderboardMoreFeedbackRequests> findTutorLeaderboardMoreFeedbackRequestsByCourseId(@Param("groupName") String groupName, @Param("courseId") long courseId);

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequests(
                :exerciseId,
                complaint.result.assessor.id,
                count(complaint),
                sum( CASE WHEN (complaint.accepted IS NULL) THEN 1 ELSE 0 END),
                sum( CASE WHEN (complaint.accepted IS NULL) THEN complaint.result.participation.exercise.maxPoints ELSE 0 END),
                complaint.result.participation.exercise.course.id
            )
            FROM
                Complaint complaint
            WHERE
                complaint.complaintType = 'FEEDBACK_REQUEST'
                and :#{#groupName} member of complaint.result.assessor.groups
                and complaint.result.participation.exercise.course.id = :courseId
                and TYPE(complaint.result.participation.exercise) in (ModelingExercise, TextExercise, FileUploadExercise, ProgrammingExercise)
                and complaint.result.completionDate IS NOT NULL
            GROUP BY complaint.complaintResponse.reviewer.id, complaint.result.participation.exercise.course.id
            """)
    List<TutorLeaderboardMoreFeedbackRequests> findTutorLeaderboardMoreFeedbackRequestsByExerciseId(@Param("groupName") String groupName, @Param("exerciseId") long exerciseId);

    /*
     * SELECT count(0) AS answered_requests, sum(e.max_points) AS points, u.id AS user_id, u.first_name AS first_name, c.id AS course_id, e.id AS exercise_id FROM (((((((jhi_user u
     * JOIN user_groups g) JOIN course c) JOIN exercise e) JOIN participation p) JOIN result r) JOIN complaint cp) JOIN complaint_response cr) WHERE ((cp.complaint_type =
     * 'MORE_FEEDBACK') and(g.user_id = u.id) and(g.groups = c.teaching_assistant_group_name) and(c.id = e.course_id) and(p.exercise_id = e.id) and(e.discriminator in('M', 'T',
     * 'F', 'P')) and(r.participation_id = p.id) and(cr.reviewer_id = u.id) and(cp.result_id = r.id) and(cp.id = cr.complaint_id) and(cp.accepted = 1) and(r.completion_date IS NOT
     * NULL)) GROUP BY u.id, e.id
     */

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequests(
                -1L,
                complaint.result.assessor.id,
                count(complaint),
                0L,
                :courseId
            )
            FROM
                Complaint complaint
            WHERE
                complaint.complaintType = 'FEEDBACK_REQUEST'
                and :#{#groupName} member of complaint.result.assessor.groups
                and complaint.result.participation.exercise.course.id = :courseId
                and TYPE(complaint.result.participation.exercise) in (ModelingExercise, TextExercise, FileUploadExercise, ProgrammingExercise)
                and complaint.result.completionDate IS NOT NULL
                and complaint.accepted = true
            GROUP BY complaint.complaintResponse.reviewer.id, complaint.result.participation.exercise.course.id
            """)
    List<TutorLeaderboardAnsweredMoreFeedbackRequests> findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId(@Param("groupName") String groupName,
            @Param("courseId") long courseId);

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequests(
                :exerciseId,
                complaint.result.assessor.id,
                count(complaint),
                0L,
                complaint.result.participation.exercise.course.id
            )
            FROM
                Complaint complaint
            WHERE
                complaint.complaintType = 'FEEDBACK_REQUEST'
                and :#{#groupName} member of complaint.result.assessor.groups
                and complaint.result.participation.exercise.course.id = :courseId
                and TYPE(complaint.result.participation.exercise) in (ModelingExercise, TextExercise, FileUploadExercise, ProgrammingExercise)
                and complaint.result.completionDate IS NOT NULL
                and complaint.accepted = true
            GROUP BY complaint.complaintResponse.reviewer.id, complaint.result.participation.exercise.course.id
            """)
    List<TutorLeaderboardAnsweredMoreFeedbackRequests> findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId(@Param("groupName") String groupName,
            @Param("exerciseId") long exerciseId);
}
