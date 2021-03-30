package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradeStep;

@Repository
public interface GradeStepRepository extends JpaRepository<GradeStep, Long> {

    List<GradeStep> findByGradingScale_Id(Long gradingScaleId);

    Optional<GradeStep> findByIdAndGradingScale_Id(Long gradeStepId, Long gradingScaleId);

    @Query("delete from GradeStep gs where gs.gradingScale.id=:gradingScaleId")
    void deleteAllGradeStepsForGradingScaleById(@Param("gradingScaleId") Long gradingScaleId);
}
