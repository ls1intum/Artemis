package de.tum.in.www1.artemis.repository;

import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@SuppressWarnings("unused")
public interface ProgrammingExerciseSolutionEntryRepository extends JpaRepository<ProgrammingExerciseSolutionEntry, Long> {

    @NotNull
    default ProgrammingExerciseSolutionEntry findByIdElseThrow(long entryId) throws EntityNotFoundException {
        return findById(entryId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Solution Entry", entryId));
    }

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
}
