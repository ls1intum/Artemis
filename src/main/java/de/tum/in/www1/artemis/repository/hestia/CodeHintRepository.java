package de.tum.in.www1.artemis.repository.hestia;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the CodeHint entity.
 */
public interface CodeHintRepository extends ArtemisJpaRepository<CodeHint, Long> {

    Set<CodeHint> findByExerciseId(Long exerciseId);

    @NotNull
    default CodeHint findByIdWithSolutionEntriesElseThrow(long exerciseHintId) throws EntityNotFoundException {
        return findByIdWithSolutionEntries(exerciseHintId).orElseThrow(() -> new EntityNotFoundException("Code Hint", exerciseHintId));
    }

    @Query("""
            SELECT h
            FROM CodeHint h
                LEFT JOIN FETCH h.task t
                LEFT JOIN FETCH h.solutionEntries tc
            WHERE h.id = :codeHintId
            """)
    Optional<CodeHint> findByIdWithSolutionEntries(@Param("codeHintId") Long codeHintId);

    @Query("""
            SELECT h
            FROM CodeHint h
                LEFT JOIN FETCH h.task t
                LEFT JOIN FETCH h.solutionEntries tc
            WHERE t.id = :taskId
            """)
    Set<CodeHint> findByTaskIdWithSolutionEntries(@Param("taskId") Long taskId);
}
