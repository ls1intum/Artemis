package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ConflictingResult;

@Repository
public interface ConflictingResultRepository extends JpaRepository<ConflictingResult, Long> {

    /**
     * Get all conflicting results that reference the result with the given ID.
     *
     * @param resultId the ID of the result for which all referencing conflicting results should be returned
     * @return all conflicting results for the given result ID
     */
    List<ConflictingResult> getAllByResultId(Long resultId);
}
