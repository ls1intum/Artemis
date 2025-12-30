package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardAnsweredMoreFeedbackRequestsDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintResponsesDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintsDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardMoreFeedbackRequestsDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Complaint entity.
 */
@Profile(PROFILE_CORE)
@Lazy
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

    @Query("""
            SELECT COUNT(c)
            FROM Complaint c
            WHERE c.result.exerciseId IN :exerciseIds
                AND c.complaintType = :complaintType
            """)
    long countByExerciseIdsAndComplaintType(@Param("exerciseIds") Set<Long> exerciseIds, @Param("complaintType") ComplaintType complaintType);

    /**
     * Retrieve all complaints for an exercise by complaint type.
     * Uses the denormalized result.exerciseId to avoid expensive joins for filtering.
     *
     * @param exerciseId    the id of the exercise
     * @param complaintType the type of complaint
     * @return list of complaints with eagerly loaded result, assessor, submission, participation and exercise
     */
    @Query("""
            SELECT c
            FROM Complaint c
                LEFT JOIN FETCH c.result r
                LEFT JOIN FETCH r.assessor
                LEFT JOIN FETCH r.submission s
                LEFT JOIN FETCH s.participation p
                LEFT JOIN FETCH p.exercise e
            WHERE c.result.exerciseId = :exerciseId
                AND c.complaintType = :complaintType
            """)
    List<Complaint> getAllComplaintsByExerciseIdAndComplaintType(@Param("exerciseId") Long exerciseId, @Param("complaintType") ComplaintType complaintType);

    /**
     * Count the number of unaccepted complaints of a student in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the number
     * of complaints for a student in a course. Requests for more feedback are not counted here.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation -> exercise -> course.
     *
     * @param studentId   the id of the student
     * @param exerciseIds the ids of the exercises in the course
     * @return the number of unaccepted complaints
     */
    @Query("""
            SELECT COUNT(c)
            FROM Complaint c
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT
                AND c.student.id = :studentId
                AND c.result.exerciseId IN :exerciseIds
                AND (c.accepted = FALSE OR c.accepted IS NULL)
            """)
    long countUnacceptedComplaintsByStudentIdAndExerciseIds(@Param("studentId") Long studentId, @Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Count the number of unaccepted complaints of a team in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the number
     * of complaints for a team in a course. Requests for more feedback are not counted here.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation -> exercise -> course.
     *
     * @param teamShortName the short name of the team
     * @param exerciseIds   the ids of the exercises in the course
     * @return the number of unaccepted complaints
     */
    @Query("""
            SELECT COUNT(c)
            FROM Complaint c
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT
                AND c.team.shortName = :teamShortName
                AND c.result.exerciseId IN :exerciseIds
                AND (c.accepted = FALSE OR c.accepted IS NULL)
            """)
    long countUnacceptedComplaintsByTeamShortNameAndExerciseIds(@Param("teamShortName") String teamShortName, @Param("exerciseIds") Set<Long> exerciseIds);

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
            WHERE c.result.exerciseId = :exerciseId
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
            SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO(
                c.result.exerciseId,
                COUNT(DISTINCT c)
            )
            FROM Complaint c
            WHERE c.result.exerciseId IN :exerciseIds
                AND c.complaintType = :complaintType
            GROUP BY c.result.exerciseId
            """)
    List<ExerciseMapEntryDTO> countComplaintsByExerciseIdsAndComplaintType(@Param("exerciseIds") Set<Long> exerciseIds, @Param("complaintType") ComplaintType complaintType);

    /**
     * This method counts the number of complaints by complaint type associated to an exercise id ignoring test runs
     *
     * @param exerciseIds   - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return list of exercise ids with the number of complaints based on the complaint type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO(
                c.result.exerciseId,
                COUNT(DISTINCT c)
            )
            FROM Complaint c
            WHERE c.result.exerciseId IN :exerciseIds
                AND c.complaintType = :complaintType
                AND c.result.submission.participation.testRun = FALSE
            GROUP BY c.result.exerciseId
            """)
    List<ExerciseMapEntryDTO> countComplaintsByExerciseIdsAndComplaintTypeIgnoreTestRuns(@Param("exerciseIds") Set<Long> exerciseIds,
            @Param("complaintType") ComplaintType complaintType);

    /**
     * Similar to {@link ComplaintRepository#countComplaintsByExerciseIdAndComplaintType}
     * but ignores test run submissions.
     * Uses the denormalized result.exerciseId to avoid expensive joins for exercise filtering.
     * Note: Still requires join to participation for testRun check.
     *
     * @param exerciseId    - the id of the exercise we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints associated to exercise exerciseId without test runs
     */
    @Query("""
            SELECT COUNT(c)
            FROM Complaint c
            WHERE c.complaintType = :complaintType
                AND c.result.submission.participation.testRun = FALSE
                AND c.result.exerciseId = :exerciseId
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
     * Given a course id, retrieve all complaints related to assessments related to that course.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation -> exercise.
     *
     * @param exerciseIds - the ids of the exercises in the course
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.submission.participation", "result.submission", "result.assessor" })
    List<Complaint> findAllByResult_ExerciseIdIn(Set<Long> exerciseIds);

    /**
     * Given a user id and an exercise id retrieve all complaints related to assessments made by that assessor in that exercise.
     * Uses the denormalized result.exerciseId to avoid expensive joins.
     *
     * @param assessorId - the id of the assessor
     * @param exerciseId - the id of the exercise
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.submission.participation", "result.submission", "result.assessor" })
    List<Complaint> findAllByResult_Assessor_IdAndResult_ExerciseId(Long assessorId, Long exerciseId);

    /**
     * Given a user id and exercise ids retrieve all complaints related to assessments made by that assessor in those exercises.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation -> exercise -> course.
     *
     * @param assessorId  - the id of the assessor
     * @param exerciseIds - the ids of the exercises (e.g., from a course)
     * @return a list of complaints
     */
    @EntityGraph(type = LOAD, attributePaths = { "result.submission.participation", "result.submission", "result.assessor" })
    List<Complaint> findAllByResult_Assessor_IdAndResult_ExerciseIdIn(Long assessorId, Set<Long> exerciseIds);

    /**
     * Get the number of Complaints for all tutors of a course.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseIds - ids of the exercises in the course
     * @return list of TutorLeaderboardComplaints
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintsDTO(
                r.assessor.id,
                COUNT(c),
                SUM(CASE WHEN c.accepted = TRUE THEN 1L ELSE 0L END),
                CAST(SUM(CASE WHEN c.accepted = TRUE THEN e.maxPoints ELSE 0.0 END) AS double)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT
                AND r.exerciseId IN :exerciseIds
                AND r.completionDate IS NOT NULL
                AND r.assessor.id IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardComplaintsDTO> findTutorLeaderboardComplaintsByCourseId(@Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Get the number of Complaints for all tutors of an exercise.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseId - id of the exercise
     * @return list of TutorLeaderboardComplaints
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintsDTO(
                r.assessor.id,
                COUNT(c),
                SUM(CASE WHEN c.accepted = TRUE THEN 1L ELSE 0L END),
                CAST(SUM(CASE WHEN c.accepted = TRUE THEN e.maxPoints ELSE 0.0 END) AS double)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT
                AND r.exerciseId = :exerciseId
                AND r.completionDate IS NOT NULL
                AND r.assessor.id IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardComplaintsDTO> findTutorLeaderboardComplaintsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Get the number of Complaints for all tutors of an exam.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseIds - ids of the exercises to consider
     * @return list of TutorLeaderboardComplaints
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintsDTO(
                r.assessor.id,
                COUNT(c),
                SUM(CASE WHEN c.accepted = TRUE THEN 1L ELSE 0L END),
                CAST(SUM(CASE WHEN c.accepted = TRUE THEN e.maxPoints ELSE 0.0 END) AS double)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT
                AND r.exerciseId IN :exerciseIds
                AND r.completionDate IS NOT NULL
                AND r.assessor.id IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardComplaintsDTO> findTutorLeaderboardComplaintsByExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Get the number of complaintResponses for all tutors assessments of a course.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseIds - ids of the exercises in the course
     * @return list of TutorLeaderboardComplaintResponses
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintResponsesDTO(
                cr.reviewer.id,
                COUNT(c),
                SUM(CAST(e.maxPoints AS double))
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT
                AND r.exerciseId IN :exerciseIds
                AND r.completionDate IS NOT NULL
                AND c.accepted IS NOT NULL
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardComplaintResponsesDTO> findTutorLeaderboardComplaintResponsesByCourseId(@Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Get the number of complaintResponses for all tutors assessments of an exercise.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseId - id of the exercise
     * @return list of TutorLeaderboardComplaintResponses
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintResponsesDTO(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT
                AND r.exerciseId = :exerciseId
                AND r.completionDate IS NOT NULL
                AND c.accepted IS NOT NULL
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardComplaintResponsesDTO> findTutorLeaderboardComplaintResponsesByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Get the number of complaintResponses for all tutors assessments of an exam.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseIds - ids of the exercises to consider
     * @return list of TutorLeaderboardComplaintResponses
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintResponsesDTO(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT
                AND r.exerciseId IN :exerciseIds
                AND r.completionDate IS NOT NULL
                AND c.accepted IS NOT NULL
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardComplaintResponsesDTO> findTutorLeaderboardComplaintResponsesByExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);

    // Valid JPQL syntax. Only SCA fails to properly detect the types.
    /**
     * Get the number of Feedback Requests for all tutors assessments of a course.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseIds - ids of the exercises in the course (should be filtered to only include exercises with manual assessment)
     * @return list of TutorLeaderboardMoreFeedbackRequests
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardMoreFeedbackRequestsDTO(
                r.assessor.id,
                COUNT(c),
                SUM(CASE WHEN c.accepted IS NULL THEN 1L ELSE 0L END),
                CAST(SUM(CASE WHEN c.accepted IS NULL THEN e.maxPoints ELSE 0.0 END) AS double)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.MORE_FEEDBACK
                AND r.exerciseId IN :exerciseIds
                AND r.completionDate IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardMoreFeedbackRequestsDTO> findTutorLeaderboardMoreFeedbackRequestsByCourseId(@Param("exerciseIds") Collection<Long> exerciseIds);

    // Valid JPQL syntax. Only SCA fails to properly detect the types.
    /**
     * Get the number of Feedback Requests for all tutors assessments of an exercise.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseId - id of the exercise
     * @return list of TutorLeaderboardMoreFeedbackRequests
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardMoreFeedbackRequestsDTO(
                r.assessor.id,
                COUNT(c),
                SUM(CASE WHEN c.accepted IS NULL THEN 1L ELSE 0L END),
                CAST(SUM(CASE WHEN c.accepted IS NULL THEN e.maxPoints ELSE 0.0 END) AS double)
            )
            FROM Complaint c
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE
                c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.MORE_FEEDBACK
                AND r.exerciseId = :exerciseId
                AND r.completionDate IS NOT NULL
            GROUP BY r.assessor.id
            """)
    List<TutorLeaderboardMoreFeedbackRequestsDTO> findTutorLeaderboardMoreFeedbackRequestsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Get the number of Feedback Request Responses for all tutors assessments of a course.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseIds - ids of the exercises in the course (should be filtered to only include exercises with manual assessment)
     * @return list of TutorLeaderboardAnsweredMoreFeedbackRequests
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardAnsweredMoreFeedbackRequestsDTO(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.MORE_FEEDBACK
                AND r.exerciseId IN :exerciseIds
                AND r.completionDate IS NOT NULL
                AND c.accepted = TRUE
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardAnsweredMoreFeedbackRequestsDTO> findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId(@Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Get the number of Feedback Request Responses for all tutors assessments of an exercise.
     * Uses the denormalized result.exerciseId to avoid expensive joins through submission -> participation.
     *
     * @param exerciseId - id of the exercise
     * @return list of TutorLeaderboardAnsweredMoreFeedbackRequests
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardAnsweredMoreFeedbackRequestsDTO(
                cr.reviewer.id,
                COUNT(c),
                SUM(e.maxPoints)
            )
            FROM Complaint c
                JOIN c.complaintResponse cr
                JOIN c.result r
                JOIN Exercise e ON r.exerciseId = e.id
            WHERE c.complaintType = de.tum.cit.aet.artemis.assessment.domain.ComplaintType.MORE_FEEDBACK
                AND r.exerciseId = :exerciseId
                AND r.completionDate IS NOT NULL
                AND c.accepted = TRUE
            GROUP BY cr.reviewer.id
            """)
    List<TutorLeaderboardAnsweredMoreFeedbackRequestsDTO> findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId(@Param("exerciseId") long exerciseId);

    default Complaint findWithEagerAssessorByIdElseThrow(Long complaintId) {
        return getValueElseThrow(findByIdWithEagerAssessor(complaintId), complaintId);
    }
}
