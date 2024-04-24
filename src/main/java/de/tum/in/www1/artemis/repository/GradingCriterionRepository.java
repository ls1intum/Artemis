package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingCriterion;

/**
 * Spring Data JPA repository for the GradingCriteria entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface GradingCriterionRepository extends JpaRepository<GradingCriterion, Long> {

    @Query("""
            SELECT DISTINCT criterion
            FROM GradingCriterion criterion
                LEFT JOIN FETCH criterion.structuredGradingInstructions
            WHERE criterion.exercise.id = :exerciseId
            """)
    Set<GradingCriterion> findByExerciseIdWithEagerGradingCriteria(@Param("exerciseId") long exerciseId);
}
