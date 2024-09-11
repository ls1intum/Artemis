package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.GradingCriterion;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the GradingCriteria entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface GradingCriterionRepository extends ArtemisJpaRepository<GradingCriterion, Long> {

    @Query("""
            SELECT DISTINCT criterion
            FROM GradingCriterion criterion
                LEFT JOIN FETCH criterion.structuredGradingInstructions
            WHERE criterion.exercise.id = :exerciseId
            """)
    Set<GradingCriterion> findByExerciseIdWithEagerGradingCriteria(@Param("exerciseId") long exerciseId);
}
