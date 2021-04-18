package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradeStep;

/**
 * Spring Data JPA Repository for the GradeStep entity
 */
@Repository
public interface GradeStepRepository extends JpaRepository<GradeStep, Long> {

    /**
     * Find all grade steps for a grading scale by id
     *
     * @param gradingScaleId the id of the grading scale
     * @return a List of all grade steps for the grading scale
     */
    @Query("""
                SELECT gradeStep
                FROM GradeStep gradeStep
                WHERE gradeStep.gradingScale.id = :#{#gradingScaleId}
            """)
    List<GradeStep> findByGradingScaleId(@Param("gradingScaleId") Long gradingScaleId);

}
