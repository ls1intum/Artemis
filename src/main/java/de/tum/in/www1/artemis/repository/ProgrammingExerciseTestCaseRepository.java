package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

/**
 * Spring Data repository for the ProgrammingExerciseTestCase entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseTestCaseRepository extends JpaRepository<ProgrammingExerciseTestCase, Long> {

    @Transactional
    @Modifying
    @Query("delete from ProgrammingExerciseTestCase tc where tc.exercise = :#{#exerciseId}")
    public void deleteByExerciseId(Long exerciseId);
}
