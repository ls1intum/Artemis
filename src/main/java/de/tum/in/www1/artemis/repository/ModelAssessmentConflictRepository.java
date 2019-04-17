package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;

@Repository
public interface ModelAssessmentConflictRepository extends JpaRepository<ModelAssessmentConflict, Long> {

    @Query("select c from ModelAssessmentConflict c where c.causingConflictingResult.result.id = :#{#result.id}")
    List<ModelAssessmentConflict> findAllConflictsByCausingResult(@Param("result") Result result);
}
