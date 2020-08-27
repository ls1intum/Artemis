package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.TutorScore;

@Repository
public interface TutorScoreRepository extends JpaRepository<TutorScore, Long> {

    List<TutorScore> findAllByExercise(Exercise exercise);

    @Query("SELECT t FROM TutorScore t WHERE t.exercise IN :#{#exercises}")
    List<TutorScore> findAllByExerciseIn(@Param("exercises") Set<Exercise> exercises);

    Optional<TutorScore> findByTutorAndExercise(@Param("tutor") User tutor, @Param("exercise") Exercise exercise);
}
