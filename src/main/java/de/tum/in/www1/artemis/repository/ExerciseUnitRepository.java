package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;

/**
 * Spring Data JPA repository for the Exercise Unit entity.
 */
@Repository
public interface ExerciseUnitRepository extends JpaRepository<ExerciseUnit, Long> {

    @Query("""
            SELECT eu
            FROM ExerciseUnit eu
            WHERE
            eu.lecture.id = :#{#lectureId}""")
    List<ExerciseUnit> findByLectureId(@Param("lectureId") Long lectureId);

    List<ExerciseUnit> removeAllByExerciseId(Long exerciseId);
}
