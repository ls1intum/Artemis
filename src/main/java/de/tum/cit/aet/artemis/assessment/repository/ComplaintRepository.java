package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.Complaint;
import de.tum.cit.aet.artemis.domain.assessment.dashboard.ExerciseMapEntry;
import de.tum.cit.aet.artemis.domain.enumeration.ComplaintType;
import de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequests;
import de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponses;
import de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaints;
import de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequests;

/**
 * Spring Data JPA repository for the Complaint entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ComplaintRepository extends ArtemisJpaRepository<Complaint, Long> {

    @Query("""
            SELECT c
            FROM Complaint c
                LEFT JOIN c.result r
                LEFT JOIN r.submission s
            WHERE s.id = :submissionId
            """)
    Optional<Complaint> findByResultSubmissionId(@Param("submissionId") Long submissionId);

    @Query("""
            SELECT c
            FROM Complaint c
                LEFT JOIN c.result r
                LEFT JOIN r.submission s
                LEFT JOIN FETCH c.complaintResponse
            WHERE s.id = :submissionId
            """)
    Optional<Complaint> findWithEagerComplaintResponseByResultSubmissionId(@Param("submissionId") long submissionId);

    Optional<Complaint> findByResultId(Long resultId);

    @Query("""
            SELECT c
            FROM Complaint c
                LEFT JOIN FETCH c.result r
                LEFT JOIN FETCH r.assessor
            WHERE c.id = :complaintId
            """)
    Optional<Complaint> findByIdWithEagerAssessor(@Param("complaintId") Long complaintId);

    /**
     * This magic method counts the number of complaints by complaint type associated to a course id
     *
     * @param courseId      - the id of the course we want to filter by
     * @param complaintType - type of complaint we want to filter by
     * @return number of more feedback requests associated to course courseId
     */
    long countByResult_Participation_Exercise_Course_IdAndComplaintType(Long courseId, ComplaintType complaintType);

    /**
     * This magic method counts the number of complaints by complaint type associated to an exam id
     *
     * @param examId        - the id of the exam we want to filter by
     * @param complaintType - type of complaint we want to filter by
     * @return number of complaints associated to course examId
     */
    long countByResult_Participation_Exercise_ExerciseGroup_Exam_IdAndComplaintType(Long examId, ComplaintType complaintType);

    @Query("""
            SELECT c
            FROM Complaint c
                LEFT JOIN FETCH c.result r
                LEFT JOIN FETCH r.assessor
                LEFT JOIN FETCH r.participation p
                LEFT JOIN FETCH p.exercise e
                LEFT JOIN FETCH r.submission
            WHERE e.id = :exerciseId
                AND c.complaintType = :complaintType
            """)
    List<Complaint> getAllComplaintsByExerciseIdAndComplaintType(@Param("exerciseId") Long exerciseId, @Param("complaintType") ComplaintType complaintType);

    /**
     * Count the number of unaccepted complaints of a student in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the number
     * of complaints for a student in a course. Requests for more feedback are not counted here.
     *
     * @param studentId the id of the student
     * @param courseId  the id of the course
     * @return the number of unaccepted complaints
     */
    @Query("""
            SELECT COUNT(c)
            FROM Complaint c
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.COMPLAINT
                AND c.student.id = :studentId
                AND c.result.participation.exercise.course.id = :courseId
                AND (c.accepted = FALSE OR c.accepted IS NULL)
            """)
    long countUnacceptedComplaintsByStudentIdAndCourseId(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /**
     * Count the number of unaccepted complaints of a team in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the number
     * of complaints for a team in a course. Requests for more feedback are not counted here.
     *
     * @param teamShortName the short name of the team
     * @param courseId      the id of the course
     * @return the number of unaccepted complaints
     */
    @Query("""
            SELECT COUNT(c)
            FROM Complaint c
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.COMPLAINT
                AND c.team.shortName = :teamShortName
                AND c.result.participation.exercise.course.id = :courseId
                AND (c.accepted = FALSE OR c.accepted IS NULL)
            """)
    long countUnacceptedComplaintsByComplaintTypeTeamShortNameAndCourseId(@Param("teamShortName") String teamShortName, @Param("courseId") Long courseId);

    /**
     * This method counts the number of complaints by complaint type associated to an exercise id
     *
     * @param exerciseId    - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints associated to exercise exerciseId
     */
    @Query("""
            SELECT COUNT(c)
            FROM Complaint c
            WHERE c.result.participation.exercise.id = :exerciseId
                AND c.complaintType = :complaintType
            """)
    long countComplaintsByExerciseIdAndComplaintType(@Param("exerciseId") Long exerciseId, @Param("complaintType") ComplaintType complaintType);

    /**
     * This method counts the number of complaints by complaint type associated to an exercise id
     *
     * @param exerciseIds   - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return list of exercise ids with the number of complaints based on the complaint type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.assessment.dashboard.ExerciseMapEntry(
                c.result.participation.exercise.id,
                COUNT(DISTINCT c)
            )
            FROM Complaint c
            WHERE c.result.participation.exercise.id IN :exerciseIds
                AND c.complaintType = :complaintType
            GROUP BY c.result.participation.exercise.id
            """)
    List<ExerciseMapEntry> countComplaintsByExerciseIdsAndComplaintType(@Param("exerciseIds") Set<Long> exerciseIds, @Param("complaintType") ComplaintType complaintType);

    /**
     * This method counts the number of complaints by complaint type associated to an exercise id ignoring test runs
     *
     * @param exerciseIds   - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return list of exercise ids with the number of complaints based on the complaint type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.assessment.dashboard.ExerciseMapEntry(
                c.result.participation.exercise.id,
                COUNT(DISTINCT c)
            )
            FROM Complaint c
            WHERE c.result.participation.exercise.id IN :exerciseIds
                AND c.complaintType = :complaintType
                AND c.result.participation.testRun = FALSE
            GROUP BY c.result.participation.exercise.id
            """)
    List<ExerciseMapEntry> countComplaintsByExerciseIdsAndComplaintTypeIgnoreTestRuns(@Param("exerciseIds") Set<Long> exerciseIds,
            @Param("complaintType") ComplaintType complaintType);

    /**
     * Similar to {@link ComplaintRepository#countComplaintsByExerciseIdAndComplaintType}
     * but ignores test run submissions
     *
     * @param exerciseId    - the id of the exercise we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints associated to exercise exerciseId without test runs
     */
    @Query("""
            SELECT COUNT(c)
            FROM Complaint c
            WHERE c.complaintType = :complaintType
                AND c.result.participation.testRun = FALSE
                AND c.result.participation.exercise.id = :exerciseId
            """)
    long countByResultParticipationExerciseIdAndComplaintTypeIgnoreTestRuns(@Param("exerciseId") Long exerciseId, @Param("complaintType") ComplaintType complaintType);

    /**
     * Delete all complaints that belong to the given result
     *
     * @param resultId the id of the result where the complaints should be deleted
     */
    @Transactional // ok because of delete
    @Modifying
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
     * Given an exercise id, retrieve all complaints related to that exercise
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

    // Valid JPQL syntax. Only SCA fails to properly detect the types.
    /**
     * Get the number of Complaints for all tutors of a course
     *
     * @param courseId - id of the course
     * @return list of TutorLeaderboardComplaints
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaints(
                r.assessor.id,
                COUNT(c),
                SUM( CASE WHEN (c.accepted = TRUE ) THEN 1L ELSE 0L END),
                SUM( CASE WHEN (c.accepted = TRUE) THEN e.maxPoints ELSE 0.0 END)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.COMPLAINT
                AND e.course.id = :courseId
                AND r.completionDate IS NOT NULL
                AND r.assessor.id IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardComplaints> findTutorLeaderboardComplaintsByCourseId(@Param("courseId") long courseId);

    // Valid JPQL syntax. Only SCA fails to properly detect the types.
    /**
     * Get the number of Complaints for all tutors of an exercise
     *
     * @param exerciseId - id of the exercise
     * @return list of TutorLeaderboardComplaints
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaints(
                r.assessor.id,
                COUNT(c),
                SUM( CASE WHEN (c.accepted = TRUE ) THEN 1L ELSE 0L END),
                SUM( CASE WHEN (c.accepted = TRUE) THEN e.maxPoints ELSE 0.0 END)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.COMPLAINT
                AND e.id = :exerciseId
                AND r.completionDate IS NOT NULL
                AND r.assessor.id IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardComplaints> findTutorLeaderboardComplaintsByExerciseId(@Param("exerciseId") long exerciseId);

    // Valid JPQL syntax. Only SCA fails to properly detect the types.
    /**
     * Get the number of Complaints for all tutors of an exam
     *
     * @param examId - id of the exercise
     * @return list of TutorLeaderboardComplaints
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaints(
                r.assessor.id,
                COUNT(c),
                SUM( CASE WHEN (c.accepted = TRUE ) THEN 1L ELSE 0L END),
                SUM( CASE WHEN (c.accepted = TRUE) THEN e.maxPoints ELSE 0.0 END)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
                JOIN e.exerciseGroup eg
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.COMPLAINT
                AND eg.exam.id = :examId
                AND r.completionDate IS NOT NULL
                AND r.assessor.id IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardComplaints> findTutorLeaderboardComplaintsByExamId(@Param("examId") long examId);

    /**
     * Get the number of complaintResponses for all tutors assessments of a course
     *
     * @param courseId - id of the exercise
     * @return list of TutorLeaderboardComplaintResponses
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponses(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.COMPLAINT
                AND e.course.id = :courseId
                AND r.completionDate IS NOT NULL
                AND c.accepted IS NOT NULL
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardComplaintResponses> findTutorLeaderboardComplaintResponsesByCourseId(@Param("courseId") long courseId);

    /**
     * Get the number of complaintResponses for all tutors assessments of an exercise
     *
     * @param exerciseId - id of the exercise
     * @return list of TutorLeaderboardComplaintResponses
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponses(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.COMPLAINT
                AND e.id = :exerciseId
                AND r.completionDate IS NOT NULL
                AND c.accepted IS NOT NULL
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardComplaintResponses> findTutorLeaderboardComplaintResponsesByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Get the number of complaintResponses for all tutors assessments of an exam
     *
     * @param examId - id of the exam
     * @return list of TutorLeaderboardComplaintResponses
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardComplaintResponses(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
                JOIN e.exerciseGroup eg
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.COMPLAINT
                AND eg.exam.id = :examId
                AND r.completionDate IS NOT NULL
                AND c.accepted IS NOT NULL
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardComplaintResponses> findTutorLeaderboardComplaintResponsesByExamId(@Param("examId") long examId);

    // Valid JPQL syntax. Only SCA fails to properly detect the types.
    /**
     * Get the number of Feedback Requests for all tutors assessments of a course
     *
     * @param courseId - id of the exercise
     * @return list of TutorLeaderboardMoreFeedbackRequests
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequests(
                r.assessor.id,
                COUNT(c),
                SUM( CASE WHEN (c.accepted IS NULL) THEN 1L ELSE 0L END),
                SUM( CASE WHEN (c.accepted IS NULL) THEN e.maxPoints ELSE 0.0 END)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.MORE_FEEDBACK
                AND e.course.id = :courseId
                AND r.completionDate IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardMoreFeedbackRequests> findTutorLeaderboardMoreFeedbackRequestsByCourseId(@Param("courseId") long courseId);

    // Valid JPQL syntax. Only SCA fails to properly detect the types.
    /**
     * Get the number of Feedback Requests for all tutors assessments of an exercise
     *
     * @param exerciseId - id of the exercise
     * @return list of TutorLeaderboardMoreFeedbackRequests
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardMoreFeedbackRequests(
                r.assessor.id,
                COUNT(c),
                SUM( CASE WHEN (c.accepted IS NULL) THEN 1L ELSE 0L END),
                SUM( CASE WHEN (c.accepted IS NULL) THEN e.maxPoints ELSE 0.0 END)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
            WHERE
                c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.MORE_FEEDBACK
                AND e.id = :exerciseId
                AND r.completionDate IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardMoreFeedbackRequests> findTutorLeaderboardMoreFeedbackRequestsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Get the number of Feedback Request Responses for all tutors assessments of a course
     *
     * @param courseId - id of the course
     * @return list of TutorLeaderboardAnsweredMoreFeedbackRequests
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequests(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.MORE_FEEDBACK
                AND e.course.id = :courseId
                AND r.completionDate IS NOT NULL
                AND c.accepted = TRUE
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardAnsweredMoreFeedbackRequests> findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId(@Param("courseId") long courseId);

    /**
     * Get the number of Feedback Request Responses for all tutors assessments of an exercise
     *
     * @param exerciseId - id of the exercise
     * @return list of TutorLeaderboardAnsweredMoreFeedbackRequests
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.domain.leaderboard.tutor.TutorLeaderboardAnsweredMoreFeedbackRequests(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN r.participation p
                JOIN p.exercise e
            WHERE c.complaintType = de.tum.cit.aet.artemis.domain.enumeration.ComplaintType.MORE_FEEDBACK
                AND e.id = :exerciseId
                AND r.completionDate IS NOT NULL
                AND c.accepted = TRUE
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardAnsweredMoreFeedbackRequests> findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId(@Param("exerciseId") long exerciseId);

    default Complaint findWithEagerAssessorByIdElseThrow(Long complaintId) {
        return getValueElseThrow(findByIdWithEagerAssessor(complaintId), complaintId);
    }
}
