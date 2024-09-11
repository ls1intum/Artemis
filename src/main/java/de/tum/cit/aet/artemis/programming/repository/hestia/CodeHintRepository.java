package de.tum.cit.aet.artemis.programming.repository.hestia;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.hestia.CodeHint;

/**
 * Spring Data repository for the CodeHint entity.
 */
public interface CodeHintRepository extends ArtemisJpaRepository<CodeHint, Long> {

    Set<CodeHint> findByExerciseId(Long exerciseId);

    @NotNull
    default CodeHint findByIdWithSolutionEntriesElseThrow(long exerciseHintId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithSolutionEntries(exerciseHintId), exerciseHintId);
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
