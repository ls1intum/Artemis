package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizTrainingLeaderboardRepository extends ArtemisJpaRepository<QuizTrainingLeaderboard, Long> {

    @EntityGraph(type = LOAD, attributePaths = "user")
    List<QuizTrainingLeaderboard> findByLeagueIdAndCourseIdOrderByScoreDescTotalScoreDescUserAscId(long leagueId, long courseId);

    @EntityGraph(type = LOAD, attributePaths = "user")
    Optional<QuizTrainingLeaderboard> findByUserIdAndCourseId(Long userId, Long courseId);
}
