package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the ProgrammingExerciseTestCase entity.
 */
@Repository
public interface ProgrammingExerciseTestCaseRepository extends JpaRepository<ProgrammingExerciseTestCase, Long> {

    Set<ProgrammingExerciseTestCase> findByExerciseId(long exerciseId);

    Optional<ProgrammingExerciseTestCase> findByExerciseIdAndTestName(long exerciseId, String testName);

    default ProgrammingExerciseTestCase findByIdWithExerciseElseThrow(long testCaseId) {
        return findByIdWithExercise(testCaseId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Test Case", testCaseId));
    }

    /**
     * Returns the test case with the programming exercise
     *
     * @param testCaseId of the test case
     * @return the test case with the programming exercise
     */
    @Query("""
            SELECT tc
            FROM ProgrammingExerciseTestCase tc
                LEFT JOIN FETCH tc.exercise ex
            WHERE tc.id = :testCaseId
            """)
    Optional<ProgrammingExerciseTestCase> findByIdWithExercise(@Param("testCaseId") long testCaseId);

    /**
     * Returns all test cases with the associated solution entries for a programming exercise
     *
     * @param exerciseId of the exercise
     * @return all test cases with the associated solution entries
     */
    @Query("""
            SELECT DISTINCT tc
            FROM ProgrammingExerciseTestCase tc
                LEFT JOIN FETCH tc.solutionEntries se
            WHERE tc.exercise.id = :exerciseId
            """)
    Set<ProgrammingExerciseTestCase> findByExerciseIdWithSolutionEntries(@Param("exerciseId") long exerciseId);

    /**
     * Returns all test cases with the associated solution entries for a programming exercise
     *
     * @param exerciseId of the exercise
     * @param active     status of the test case
     * @return all test cases with the associated solution entries
     */
    @Query("""
            SELECT DISTINCT tc
            FROM ProgrammingExerciseTestCase tc
                LEFT JOIN FETCH tc.solutionEntries se
            WHERE tc.exercise.id = :exerciseId
                AND tc.active = :active
            """)
    Set<ProgrammingExerciseTestCase> findByExerciseIdWithSolutionEntriesAndActive(@Param("exerciseId") long exerciseId, @Param("active") Boolean active);

    Set<ProgrammingExerciseTestCase> findByExerciseIdAndActive(long exerciseId, Boolean active);

    /**
     * Returns the number of test cases marked as {@link de.tum.in.www1.artemis.domain.enumeration.Visibility#AFTER_DUE_DATE} for the given exercise.
     *
     * @param exerciseId the exercise which test cases should be considered.
     * @return the number of test cases marked as {@link de.tum.in.www1.artemis.domain.enumeration.Visibility#AFTER_DUE_DATE}.
     */
    @Query("""
            SELECT COUNT(DISTINCT testCase)
            FROM ProgrammingExerciseTestCase testCase
            WHERE testCase.exercise.id = :exerciseId
                AND testCase.visibility = de.tum.in.www1.artemis.domain.enumeration.Visibility.AFTER_DUE_DATE
            """)
    long countAfterDueDateByExerciseId(@Param("exerciseId") long exerciseId);
}
