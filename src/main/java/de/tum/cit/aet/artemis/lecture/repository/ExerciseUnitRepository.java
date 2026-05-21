package de.tum.cit.aet.artemis.lecture.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;

/**
 * Spring Data JPA repository for the Exercise Unit entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface ExerciseUnitRepository extends ArtemisJpaRepository<ExerciseUnit, Long> {

    @Query("""
            SELECT eu
            FROM ExerciseUnit eu
            WHERE eu.lecture.id = :lectureId
            """)
    List<ExerciseUnit> findByLectureId(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT exerciseUnit
            FROM ExerciseUnit exerciseUnit
                LEFT JOIN FETCH exerciseUnit.competencyLinks cl
                LEFT JOIN FETCH cl.competency c
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit
            WHERE exerciseUnit.exercise.id = :exerciseId
            """)
    List<ExerciseUnit> findByIdWithCompetenciesBidirectional(@Param("exerciseId") Long exerciseId);
}
