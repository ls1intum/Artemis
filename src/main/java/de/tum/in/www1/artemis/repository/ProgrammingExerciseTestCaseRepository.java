package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * Spring Data  repository for the ProgrammingExerciseTestCase entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseTestCaseRepository extends JpaRepository<ProgrammingExerciseTestCase, Long> {

    @Query("select programmingExerciseTestCase from ProgrammingExerciseTestCase programmingExerciseTestCase where programmingExerciseTestCase.exercise.id = :#{#exerciseId}")
    public List<ProgrammingExerciseTestCase> getByExerciseId(@Param("exerciseId") Long exerciseId);

}
