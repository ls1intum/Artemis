package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Returns the number of test cases marked as {@link de.tum.in.www1.artemis.domain.enumeration.Visibility#AFTER_DUE_DATE} for the given exercise.
     * @param exerciseId the exercise which test cases should be considered.
     * @return the number of test cases marked as {@link de.tum.in.www1.artemis.domain.enumeration.Visibility#AFTER_DUE_DATE}.
     */
    @Query("""
                SELECT COUNT (DISTINCT testCase) FROM ProgrammingExerciseTestCase testCase
                WHERE testCase.exercise.id = :#{#exerciseId}
                    AND testCase.visibility = 'AFTER_DUE_DATE'
            """)
    long countAfterDueDateByExerciseId(@Param("exerciseId") Long exerciseId);
}
