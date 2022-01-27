package de.tum.in.www1.artemis.repository.hestia;

import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.hestia.TextHint;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the TextHint entity.
 */
public interface TextHintRepository extends JpaRepository<TextHint, Long> {

    Set<TextHint> findByExerciseId(Long exerciseId);

    @NotNull
    default TextHint findByIdElseThrow(long exerciseHintId) throws EntityNotFoundException {
        return findById(exerciseHintId).orElseThrow(() -> new EntityNotFoundException("Text Hint", exerciseHintId));
    }

    /**
     * Returns the title of the text hint with the given id
     *
     * @param hintId the id of the hint
     * @return the name/title of the hint or null if the hint does not exist
     */
    @Query("""
            SELECT h.title
            FROM TextHint h
            WHERE h.id = :hintId
            """)
    String getHintTitle(@Param("hintId") Long hintId);
}
