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
import de.tum.cit.aet.artemis.programming.domain.hestia.CodeHint;

/**
 * Spring Data repository for the CodeHint entity.
 */
@Profile(PROFILE_CORE)
@Repository
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
