package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;

/**
 * Spring Data JPA repository for the Exercise Unit entity.
 */
@Profile(PROFILE_CORE)
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
                LEFT JOIN FETCH exerciseUnit.competencies c
                LEFT JOIN FETCH c.lectureUnits
            WHERE exerciseUnit.exercise.id = :exerciseId
            """)
    List<ExerciseUnit> findByIdWithCompetenciesBidirectional(@Param("exerciseId") Long exerciseId);
}
