package de.tum.in.www1.artemis.repository;

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
@Profile("core")
@Repository
public interface GradingCriterionRepository extends JpaRepository<GradingCriterion, Long> {

    Set<GradingCriterion> findByExerciseId(long exerciseId);

    @Query("select distinct criterion from GradingCriterion criterion left join fetch criterion.structuredGradingInstructions where criterion.exercise.id = :#{#exerciseId}")
    Set<GradingCriterion> findByExerciseIdWithEagerGradingCriteria(@Param("exerciseId") long exerciseId);
}
