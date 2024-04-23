package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;

/**
 * Spring Data repository for the StaticCodeAnalysisCategory entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface StaticCodeAnalysisCategoryRepository extends JpaRepository<StaticCodeAnalysisCategory, Long> {

    Logger log = LoggerFactory.getLogger(StaticCodeAnalysisCategoryRepository.class);

    Set<StaticCodeAnalysisCategory> findByExerciseId(long exerciseId);

    @Query("""
            SELECT s
            FROM StaticCodeAnalysisCategory s
                LEFT JOIN FETCH s.exercise
            WHERE s.exercise.id = :exerciseId
            """)
    Set<StaticCodeAnalysisCategory> findWithExerciseByExerciseId(@Param("exerciseId") long exerciseId);
}
