package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.scores.StudentScore;

@Repository
public interface StudentScoresRepository extends JpaRepository<StudentScore, Long> {

    List<StudentScore> findAllByExerciseId(long exerciseId);

    @Query("SELECT s FROM StudentScore s WHERE s.exerciseId IN :#{#exercises}")
    List<StudentScore> findAllByExerciseIdIn(@Param("exercises") Set<Long> exercises);
}
