package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

/**
 * Spring Data repository for the ProgrammingExerciseTestCase entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingExerciseTestCaseRepository extends JpaRepository<ProgrammingExerciseTestCase, Long> {

    Set<ProgrammingExerciseTestCase> findByExerciseId(Long exerciseId);

    Set<ProgrammingExerciseTestCase> findByExerciseIdAndActive(Long exerciseId, Boolean active);
}
