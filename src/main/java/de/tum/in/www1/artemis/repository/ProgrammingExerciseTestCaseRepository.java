package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

/**
 * Spring Data repository for the ProgrammingExerciseTestCase entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseTestCaseRepository extends JpaRepository<ProgrammingExerciseTestCase, Long> {

    @Query("select programmingExerciseTestCase from ProgrammingExerciseTestCase programmingExerciseTestCase where programmingExerciseTestCase.exercise.id = :#{#exerciseId}")
    Set<ProgrammingExerciseTestCase> findByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("select tc from ProgrammingExerciseTestCase tc where tc.exercise.id = :#{#exerciseId} and tc.active = true")
    Set<ProgrammingExerciseTestCase> findActiveByExerciseId(@Param("exerciseId") Long exerciseId);
}
