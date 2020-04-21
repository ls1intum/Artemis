package de.tum.in.www1.artemis.repository;

import java.util.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;

@Repository
public interface ModelAssessmentConflictRepository extends JpaRepository<ModelAssessmentConflict, Long> {

    @Query("SELECT c FROM ModelAssessmentConflict c WHERE c.causingConflictingResult.result.submission.id = :#{#submissionId}")
    List<ModelAssessmentConflict> findAllConflictsByCausingSubmission(@Param("submissionId") Long submissionId);

    @Query("SELECT c FROM ModelAssessmentConflict c WHERE c.causingConflictingResult.result.id = :#{#result.id}")
    List<ModelAssessmentConflict> findAllConflictsByCausingResult(@Param("result") Result result);

    @Query("SELECT  c FROM ModelAssessmentConflict c WHERE c.causingConflictingResult.result.participation.exercise.id = :#{#exerciseId}")
    List<ModelAssessmentConflict> findAllConflictsOfExercise(@Param("exerciseId") Long exerciseId);

    Optional<ModelAssessmentConflict> findById(Long id);

    /**
     * Return all modeling assessment conflicts for which the given conflicting result ID is referenced as the causing conflicting result ID.
     *
     * @param causingConflictingResultId the ID of the conflicting result that the returned conflicts should reference
     * @return all modeling assessment conflicts that reference the given conflicting result ID
     */
    List<ModelAssessmentConflict> findAllConflictsByCausingConflictingResultId(Long causingConflictingResultId);
}
