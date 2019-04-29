package de.tum.in.www1.artemis.repository;

import java.util.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;

@Repository
public interface ModelAssessmentConflictRepository extends JpaRepository<ModelAssessmentConflict, Long> {

    @Query("select c from ModelAssessmentConflict c left join fetch c.causingConflictingResult cR left join fetch cR.result cRR left join fetch cRR.feedbacks left join fetch cRR.submission where c.causingConflictingResult.result.id = :#{#result.id}")
    List<ModelAssessmentConflict> findAllConflictsByCausingResult(@Param("result") Result result);

    @Query("select  c from ModelAssessmentConflict c where c.causingConflictingResult.result.participation.exercise.id = :#{#exerciseId}")
    List<ModelAssessmentConflict> findAllConflictsOfExercise(@Param("exerciseId") Long exerciseId);

    Optional<ModelAssessmentConflict> findById(Long id);
}
