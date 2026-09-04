package de.tum.cit.aet.artemis.account.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Removes the assessment rows of a user that is being deleted permanently.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 *
 * <p>
 * What the account produced as a student goes with it; what it did as an assessor is only detached, because the
 * assessment belongs to the submission it was given on and has to survive the assessor leaving.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AssessmentDataCleanupRepository extends ArtemisJpaRepository<Complaint, Long> {

    @Query("""
            SELECT complaint.student.id AS userId, COUNT(complaint) AS count
            FROM Complaint complaint
            WHERE complaint.student.id IN :userIds
            GROUP BY complaint.student.id
            """)
    List<UserReferenceCount> countComplaints(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Complaint complaint
            WHERE complaint.student.id = :userId
            """)
    int deleteComplaints(@Param("userId") long userId);

    @Query("""
            SELECT response.reviewer.id AS userId, COUNT(response) AS count
            FROM ComplaintResponse response
            WHERE response.reviewer.id IN :userIds
            GROUP BY response.reviewer.id
            """)
    List<UserReferenceCount> countReviewedComplaintResponses(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE ComplaintResponse response
            SET response.reviewer = NULL
            WHERE response.reviewer.id = :userId
            """)
    int detachReviewedComplaintResponses(@Param("userId") long userId);

    @Query("""
            SELECT note.creator.id AS userId, COUNT(note) AS count
            FROM AssessmentNote note
            WHERE note.creator.id IN :userIds
            GROUP BY note.creator.id
            """)
    List<UserReferenceCount> countCreatedAssessmentNotes(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE AssessmentNote note
            SET note.creator = NULL
            WHERE note.creator.id = :userId
            """)
    int detachCreatedAssessmentNotes(@Param("userId") long userId);

    @Query("""
            SELECT result.assessor.id AS userId, COUNT(result) AS count
            FROM Result result
            WHERE result.assessor.id IN :userIds
            GROUP BY result.assessor.id
            """)
    List<UserReferenceCount> countAssessedResults(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE Result result
            SET result.assessor = NULL
            WHERE result.assessor.id = :userId
            """)
    int detachAssessedResults(@Param("userId") long userId);

    @Query("""
            SELECT score.user.id AS userId, COUNT(score) AS count
            FROM StudentScore score
            WHERE score.user.id IN :userIds
            GROUP BY score.user.id
            """)
    List<UserReferenceCount> countStudentScores(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM StudentScore score
            WHERE score.user.id = :userId
            """)
    int deleteStudentScores(@Param("userId") long userId);

    @Query("""
            SELECT tutorParticipation.tutor.id AS userId, COUNT(tutorParticipation) AS count
            FROM TutorParticipation tutorParticipation
            WHERE tutorParticipation.tutor.id IN :userIds
            GROUP BY tutorParticipation.tutor.id
            """)
    List<UserReferenceCount> countTutorParticipations(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TutorParticipation tutorParticipation
            WHERE tutorParticipation.tutor.id = :userId
            """)
    int deleteTutorParticipations(@Param("userId") long userId);

    /**
     * Deletes the responses to the complaints the account raised, so that the complaints themselves can be removed.
     * A response written by the account on somebody else's complaint is detached instead, by
     * {@link #detachReviewedComplaintResponses}.
     *
     * @param userId the account being deleted
     * @return how many responses were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ComplaintResponse response
            WHERE response.complaint.id IN (SELECT complaint.id FROM Complaint complaint WHERE complaint.student.id = :userId)
            """)
    int deleteResponsesToComplaintsOf(@Param("userId") long userId);

    /**
     * Drops the links from the account's tutor participations to the example submissions it trained on, so that the
     * participations themselves can be removed. The example submissions stay: they belong to the exercise.
     *
     * <p>
     * Addressed natively because {@code tutor_participation_trained_example_submissions} is a join table and has no
     * entity of its own.
     *
     * @param userId the account being deleted
     * @return how many links were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query(nativeQuery = true, value = """
            DELETE FROM tutor_participation_trained_example_submissions
            WHERE tutor_participation_id IN (SELECT id FROM tutor_participation WHERE tutor_id = :userId)
            """)
    int deleteTrainedExampleSubmissionLinks(@Param("userId") long userId);

    /**
     * Deletes the responses to the complaints a team raised, so that the complaints themselves can be removed.
     *
     * @param teamId the team being deleted
     * @return how many responses were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ComplaintResponse response
            WHERE response.complaint.id IN (SELECT complaint.id FROM Complaint complaint WHERE complaint.team.id = :teamId)
            """)
    int deleteResponsesToComplaintsOfTeam(@Param("teamId") long teamId);

    /**
     * Deletes the complaints a team raised. A team that is removed with its only member takes these with it, and the
     * foreign key from a complaint to its team refuses the deletion while any is left.
     *
     * @param teamId the team being deleted
     * @return how many complaints were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Complaint complaint
            WHERE complaint.team.id = :teamId
            """)
    int deleteComplaintsOfTeam(@Param("teamId") long teamId);
}
