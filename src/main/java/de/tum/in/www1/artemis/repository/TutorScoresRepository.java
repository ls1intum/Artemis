package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorScore;

@Repository
public interface TutorScoresRepository extends JpaRepository<TutorScore, Long> {

    List<TutorScore> findAllByExerciseId(long exerciseId);

    @Query("SELECT t FROM TutorScore t WHERE t.exerciseId IN :#{#exercises}")
    List<TutorScore> findAllByExerciseIdIn(@Param("exercises") Set<Long> exercises);
}
