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

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;

/**
 * Removes the exercise rows of a user that is being deleted permanently.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 *
 * <p>
 * Team membership is addressed natively because {@code team_student} is a join table of {@code Team} and has no entity
 * of its own; there is nothing for JPQL to name.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExerciseDataCleanupRepository extends ArtemisJpaRepository<SubmissionVersion, Long> {

    @Query("""
            SELECT version.author.id AS userId, COUNT(version) AS count
            FROM SubmissionVersion version
            WHERE version.author.id IN :userIds
            GROUP BY version.author.id
            """)
    List<UserReferenceCount> countSubmissionVersions(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM SubmissionVersion version
            WHERE version.author.id = :userId
            """)
    int deleteSubmissionVersions(@Param("userId") long userId);

    @Query("""
            SELECT version.authorId AS userId, COUNT(version) AS count
            FROM ExerciseVersion version
            WHERE version.authorId IN :userIds
            GROUP BY version.authorId
            """)
    List<UserReferenceCount> countExerciseVersions(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ExerciseVersion version
            WHERE version.authorId = :userId
            """)
    int deleteExerciseVersions(@Param("userId") long userId);

    @Query("""
            SELECT comment.author.id AS userId, COUNT(comment) AS count
            FROM Comment comment
            WHERE comment.author.id IN :userIds
            GROUP BY comment.author.id
            """)
    List<UserReferenceCount> countReviewComments(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE Comment comment
            SET comment.author = NULL
            WHERE comment.author.id = :userId
            """)
    int detachReviewComments(@Param("userId") long userId);

    @Query("""
            SELECT team.owner.id AS userId, COUNT(team) AS count
            FROM Team team
            WHERE team.owner.id IN :userIds
            GROUP BY team.owner.id
            """)
    List<UserReferenceCount> countOwnedTeams(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE Team team
            SET team.owner = NULL
            WHERE team.owner.id = :userId
            """)
    int detachOwnedTeams(@Param("userId") long userId);

    @Query("""
            SELECT participation.student.id AS userId, COUNT(participation) AS count
            FROM StudentParticipation participation
            WHERE participation.student.id IN :userIds
            GROUP BY participation.student.id
            """)
    List<UserReferenceCount> countStudentParticipations(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM StudentParticipation participation
            WHERE participation.student.id = :userId
            """)
    int deleteStudentParticipations(@Param("userId") long userId);

    @Query(nativeQuery = true, value = """
            SELECT student_id AS userId, COUNT(*) AS count
            FROM team_student
            WHERE student_id IN :userIds
            GROUP BY student_id
            """)
    List<UserReferenceCount> countTeamMemberships(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query(nativeQuery = true, value = """
            DELETE FROM team_student
            WHERE student_id = :userId
            """)
    int deleteTeamMemberships(@Param("userId") long userId);

    /**
     * The teams the account owns and is the only member of. Nobody else can carry them on, so they are removed with
     * the account rather than handed over.
     *
     * @param userId the account being deleted
     * @return the ids of those teams
     */
    @Query("""
            SELECT team.id
            FROM Team team
                JOIN team.students student
            WHERE student.id = :userId
                AND team.owner.id = :userId
                AND SIZE(team.students) = 1
            """)
    List<Long> findExclusivelyOwnedTeamIds(@Param("userId") long userId);

    /**
     * The teams the account owns, whether or not it is a member of them.
     *
     * @param userId the account being deleted
     * @return the ids of those teams
     */
    @Query("""
            SELECT team.id
            FROM Team team
            WHERE team.owner.id = :userId
            """)
    List<Long> findOwnedTeamIds(@Param("userId") long userId);

    /**
     * The other members of a team, in a stable order so that ownership is handed to the same person however often the
     * deletion is retried.
     *
     * @param teamId         the team to look at
     * @param excludedUserId the account being deleted
     * @return the ids of the remaining members, lowest first
     */
    @Query("""
            SELECT student.id
            FROM Team team
                JOIN team.students student
            WHERE team.id = :teamId
                AND student.id <> :excludedUserId
            ORDER BY student.id
            """)
    List<Long> findRemainingTeamStudentIds(@Param("teamId") long teamId, @Param("excludedUserId") long excludedUserId);

    /**
     * Hands a team over to one of its remaining members.
     *
     * @param teamId  the team to hand over
     * @param ownerId the member taking it over
     * @return how many teams were changed
     */
    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE Team team
            SET team.owner = (SELECT owner FROM User owner WHERE owner.id = :ownerId)
            WHERE team.id = :teamId
            """)
    int replaceTeamOwner(@Param("teamId") long teamId, @Param("ownerId") long ownerId);

    /**
     * Leaves a team without an owner, for when nobody is left to hand it to.
     *
     * @param teamId the team to detach
     * @return how many teams were changed
     */
    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE Team team
            SET team.owner = NULL
            WHERE team.id = :teamId
            """)
    int detachTeamOwner(@Param("teamId") long teamId);

    /**
     * Deletes a team whose only member was the account being deleted.
     *
     * @param teamId the team to delete
     * @return how many teams were deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Team team
            WHERE team.id = :teamId
            """)
    int deleteTeam(@Param("teamId") long teamId);

    /**
     * The account's participations, in a stable order. Each is taken down individually because a participation owns
     * submissions, results and, for programming exercises, a repository and build plan outside the database.
     *
     * @param userId the account being deleted
     * @return the ids of its participations, lowest first
     */
    @Query("""
            SELECT participation.id
            FROM StudentParticipation participation
            WHERE participation.student.id = :userId
            ORDER BY participation.id
            """)
    List<Long> findStudentParticipationIds(@Param("userId") long userId);
}
