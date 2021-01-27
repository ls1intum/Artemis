package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;

@Repository
public interface ParticipantScoreRepository extends JpaRepository<ParticipantScore, Long> {

    @Query("""
                DELETE
                FROM ParticipantScore ps
                WHERE ps.exercise.id= :#{#exerciseId}
            """)
    @Modifying
    void removeAssociatedWithExercise(@Param("exerciseId") Long exerciseId);

    void removeAllByExerciseId(Long exerciseId);

    @Query("""
            SELECT ps
            FROM ParticipantScore ps
            WHERE ps.lastResult.id= :#{#resultId} OR ps.lastRatedResult.id = :#{#resultId}
            """)
    Optional<ParticipantScore> findParticipantScoreAssociatedWithResult(@Param("resultId") Long resultId);

    Optional<ParticipantScore> findParticipantScoreByLastRatedResult(Result result);

    Optional<ParticipantScore> findParticipantScoresByLastResult(Result result);
}
