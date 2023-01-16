package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;

/**
 * Spring Data repository for the StaticCodeAnalysisCategory entity.
 */
@Repository
public interface StaticCodeAnalysisCategoryRepository extends JpaRepository<StaticCodeAnalysisCategory, Long> {

    Set<StaticCodeAnalysisCategory> findByExerciseId(Long exerciseId);

    @Query("""
             SELECT s
             FROM StaticCodeAnalysisCategory s
                 LEFT JOIN FETCH s.exercise
             WHERE s.exercise.id = :exerciseId
            """)
    Set<StaticCodeAnalysisCategory> findWithExerciseByExerciseId(@Param("exerciseId") Long exerciseId);
}
