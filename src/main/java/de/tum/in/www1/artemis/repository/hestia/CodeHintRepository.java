package de.tum.in.www1.artemis.repository.hestia;

import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the CodeHint entity.
 */
public interface CodeHintRepository extends JpaRepository<CodeHint, Long> {

    Set<CodeHint> findByExerciseId(Long exerciseId);

    Set<CodeHint> findByTaskId(Long taskId);

    @NotNull
    default CodeHint findByIdElseThrow(long exerciseHintId) throws EntityNotFoundException {
        return findById(exerciseHintId).orElseThrow(() -> new EntityNotFoundException("Code Hint", exerciseHintId));
    }

    /**
     * Returns the title of the code hint with the given id
     *
     * @param hintId the id of the hint
     * @return the name/title of the hint or null if the hint does not exist
     */
    @Query("""
            SELECT h.title
            FROM CodeHint h
            WHERE h.id = :#{#hintId}
            """)
    String getHintTitle(@Param("hintId") Long hintId);
}
