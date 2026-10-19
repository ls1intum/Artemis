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
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;

/**
 * Removes the learning progress of a user that is being deleted permanently: competencies, lecture units and the quiz
 * training state.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface LearningDataCleanupRepository extends ArtemisJpaRepository<QuizQuestionProgress, Long> {

    @Query("""
            SELECT progress.userId AS userId, COUNT(progress) AS count
            FROM QuizQuestionProgress progress
            WHERE progress.userId IN :userIds
            GROUP BY progress.userId
            """)
    List<UserReferenceCount> countQuizQuestionProgress(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM QuizQuestionProgress progress
            WHERE progress.userId = :userId
            """)
    int deleteQuizQuestionProgress(@Param("userId") long userId);

    @Query("""
            SELECT entry.user.id AS userId, COUNT(entry) AS count
            FROM QuizTrainingLeaderboard entry
            WHERE entry.user.id IN :userIds
            GROUP BY entry.user.id
            """)
    List<UserReferenceCount> countQuizLeaderboardEntries(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM QuizTrainingLeaderboard entry
            WHERE entry.user.id = :userId
            """)
    int deleteQuizLeaderboardEntries(@Param("userId") long userId);

    @Query("""
            SELECT progress.user.id AS userId, COUNT(progress) AS count
            FROM CompetencyProgress progress
            WHERE progress.user.id IN :userIds
            GROUP BY progress.user.id
            """)
    List<UserReferenceCount> countCompetencyProgress(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM CompetencyProgress progress
            WHERE progress.user.id = :userId
            """)
    int deleteCompetencyProgress(@Param("userId") long userId);

    @Query("""
            SELECT completion.user.id AS userId, COUNT(completion) AS count
            FROM LectureUnitCompletion completion
            WHERE completion.user.id IN :userIds
            GROUP BY completion.user.id
            """)
    List<UserReferenceCount> countLectureUnitCompletions(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LectureUnitCompletion completion
            WHERE completion.user.id = :userId
            """)
    int deleteLectureUnitCompletions(@Param("userId") long userId);

    @Query("""
            SELECT path.user.id AS userId, COUNT(path) AS count
            FROM LearningPath path
            WHERE path.user.id IN :userIds
            GROUP BY path.user.id
            """)
    List<UserReferenceCount> countLearningPaths(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LearningPath path
            WHERE path.user.id = :userId
            """)
    int deleteLearningPaths(@Param("userId") long userId);

    @Query("""
            SELECT profile.user.id AS userId, COUNT(profile) AS count
            FROM LearnerProfile profile
            WHERE profile.user.id IN :userIds
            GROUP BY profile.user.id
            """)
    List<UserReferenceCount> countLearnerProfiles(@Param("userIds") Collection<Long> userIds);

    /**
     * Deletes the learner profile of an account, its per-course parts first so that nothing still points at it.
     *
     * @param userId the account being deleted
     * @return how many profiles were deleted
     */
    default int deleteLearnerProfile(long userId) {
        deleteCourseLearnerProfiles(userId);
        return deleteLearnerProfileRow(userId);
    }

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM CourseLearnerProfile profile
            WHERE profile.learnerProfile IN (
                SELECT learnerProfile
                FROM LearnerProfile learnerProfile
                WHERE learnerProfile.user.id = :userId
            )
            """)
    int deleteCourseLearnerProfiles(@Param("userId") long userId);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LearnerProfile profile
            WHERE profile.user.id = :userId
            """)
    int deleteLearnerProfileRow(@Param("userId") long userId);

    /**
     * Renames the account behind the research events it produced. Those events record a login rather than a foreign
     * key, so removing the account row leaves them pointing at a name that could later be handed to somebody else.
     *
     * @param oldIdentity the login the account had
     * @param newIdentity the placeholder to record instead
     * @return how many events were renamed
     */
    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE ScienceEvent event
            SET event.identity = :newIdentity
            WHERE event.identity = :oldIdentity
            """)
    int renameScienceEventIdentity(@Param("oldIdentity") String oldIdentity, @Param("newIdentity") String newIdentity);
}
