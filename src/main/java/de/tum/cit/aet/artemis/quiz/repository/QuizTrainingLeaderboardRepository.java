package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizTrainingLeaderboardRepository extends ArtemisJpaRepository<QuizTrainingLeaderboard, Long> {

    @EntityGraph(type = LOAD, attributePaths = "user")
    List<QuizTrainingLeaderboard> findByLeagueAndCourseIdAndShowInLeaderboardTrueOrderByScoreDescUserAscId(int leagueId, long courseId);

    @EntityGraph(type = LOAD, attributePaths = "user")
    Optional<QuizTrainingLeaderboard> findByUserIdAndCourseId(long userId, long courseId);

    /*
     * We use a custom query here to perform an atomic update of the leaderboard entry.
     * The leagues are determined based on the score after applying the scoreDelta.
     * Each league corresponds to a score range:
     * - Bronze (5): 0-49
     * - Silver (4): 50-149
     * - Gold (3): 150-299
     * - Diamond (2): 300-500
     * - Master (1): 500 and above
     */
    @Transactional
    @Modifying
    @Query("""
                UPDATE QuizTrainingLeaderboard qtl
                SET qtl.league =
                            CASE
                                WHEN (qtl.score + :scoreDelta) < 50 THEN 5
                                WHEN (qtl.score + :scoreDelta) >= 50 AND (qtl.score + :scoreDelta) < 150 THEN 4
                                WHEN (qtl.score + :scoreDelta) >= 150 AND (qtl.score + :scoreDelta) < 300 THEN 3
                                WHEN (qtl.score + :scoreDelta) >= 300 AND (qtl.score + :scoreDelta)< 500 THEN 2
                                ELSE 1
                            END,
                       qtl.score = qtl.score + :scoreDelta,
                       qtl.answeredCorrectly = qtl.answeredCorrectly + :correctAnswers,
                       qtl.answeredWrong = qtl.answeredWrong + :wrongAnswers,
                       qtl.dueDate = :dueDate
                WHERE qtl.user.id = :userId AND qtl.course.id = :courseId
            """)
    void updateLeaderboardEntry(@Param("userId") long userId, @Param("courseId") long courseId, @Param("scoreDelta") int scoreDelta, @Param("correctAnswers") int correctAnswers,
            @Param("wrongAnswers") int wrongAnswers, @Param("dueDate") ZonedDateTime dueDate);

    @Transactional
    @Modifying
    @Query("""
            UPDATE QuizTrainingLeaderboard qtl
            SET qtl.showInLeaderboard = :showInLeaderboard
            WHERE qtl.user.id = :userId
            """)
    void updateShowInLeaderboard(@Param("userId") long userId, @Param("showInLeaderboard") boolean showInLeaderboard);

    boolean existsQuizTrainingLeaderboardByUser_Id(long userId);

    Optional<QuizTrainingLeaderboard> findFirstByUser_Id(long userId);
}
