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
    List<QuizTrainingLeaderboard> findByLeagueAndCourseIdOrderByScoreDescUserAscId(int leagueId, long courseId);

    @EntityGraph(type = LOAD, attributePaths = "user")
    Optional<QuizTrainingLeaderboard> findByUserIdAndCourseId(long userId, long courseId);

    @Transactional
    @Modifying
    @Query("""
                UPDATE QuizTrainingLeaderboard qtl
                SET qtl.score = :score,
                    qtl.answeredCorrectly = qtl.answeredCorrectly + :correctAnswers,
                    qtl.answeredWrong = qtl.answeredWrong + :wrongAnswers,
                    qtl.league = :league,
                    qtl.dueDate = :dueDate
                WHERE qtl.user.id = :userId AND qtl.course.id = :courseId
            """)
    void updateLeaderboardEntry(@Param("userId") long userId, @Param("courseId") long courseId, @Param("score") int score, @Param("correctAnswers") int correctAnswers,
            @Param("wrongAnswers") int wrongAnswers, @Param("league") int league, @Param("dueDate") ZonedDateTime dueDate);

    @Transactional
    @Modifying
    @Query("""
            UPDATE QuizTrainingLeaderboard qtl
            SET qtl.showInLeaderboard = :showInLeaderboard
            WHERE qtl.user.id = :userId
            """)
    void updateShownInLeaderboard(@Param("userId") long userId, @Param("showInLeaderboard") boolean showInLeaderboard);
}
