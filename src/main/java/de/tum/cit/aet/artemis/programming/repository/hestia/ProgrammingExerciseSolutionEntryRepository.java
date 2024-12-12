package de.tum.cit.aet.artemis.programming.repository.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseSolutionEntry;

/**
 * Spring Data repository for the ProgrammingExerciseSolutionEntry entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseSolutionEntryRepository extends ArtemisJpaRepository<ProgrammingExerciseSolutionEntry, Long> {

    /**
     * Gets a solution entry with its test cases and programming exercise
     *
     * @param entryId The id of the solution entry
     * @return The solution entry with the given ID if found
     * @throws EntityNotFoundException If no solution entry with the given ID was found
     */
    @NotNull
    default ProgrammingExerciseSolutionEntry findByIdWithTestCaseAndProgrammingExerciseElseThrow(long entryId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithTestCaseAndProgrammingExercise(entryId), entryId);
    }

    @Query("""
            SELECT se
            FROM ProgrammingExerciseSolutionEntry se
                LEFT JOIN FETCH se.testCase tc
                LEFT JOIN FETCH tc.exercise pe
            WHERE pe.id = :exerciseId
            """)
    Set<ProgrammingExerciseSolutionEntry> findByExerciseIdWithTestCases(@Param("exerciseId") long exerciseId);

    /**
     * Gets a solution entry with its test cases and programming exercise
     *
     * @param entryId The id of the solution entry
     * @return The solution entry with the given ID
     */
    @Query("""
            SELECT se
            FROM ProgrammingExerciseSolutionEntry se
                LEFT JOIN FETCH se.testCase tc
                LEFT JOIN FETCH tc.exercise pe
            WHERE se.id = :entryId
            """)
    Optional<ProgrammingExerciseSolutionEntry> findByIdWithTestCaseAndProgrammingExercise(@Param("entryId") long entryId);

    @Query("""
            SELECT h.solutionEntries
            FROM CodeHint h
            WHERE h.id = :codeHintId
            """)
    Set<ProgrammingExerciseSolutionEntry> findByCodeHintId(@Param("codeHintId") Long codeHintId);

    @Query("""
            SELECT t.solutionEntries
            FROM ProgrammingExerciseTestCase t
            WHERE t.id = :testCaseId
            """)
    Set<ProgrammingExerciseSolutionEntry> findByTestCaseId(@Param("testCaseId") Long testCaseId);

    @Query("""
            SELECT se
            FROM ProgrammingExerciseSolutionEntry se
                LEFT JOIN FETCH se.codeHint
            WHERE se.testCase.id = :testCaseId
            """)
    Set<ProgrammingExerciseSolutionEntry> findByTestCaseIdWithCodeHint(@Param("testCaseId") Long testCaseId);
}
