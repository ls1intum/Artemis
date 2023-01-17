package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.BuildPlan;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildPlanRepository extends JpaRepository<BuildPlan, Long> {

    Optional<BuildPlan> findByBuildPlan(String buildPlan);

    @Query("""
           SELECT buildPlan
           FROM BuildPlan buildPlan, ProgrammingExercise pE
           WHERE pE.id = :programmingExerciseId AND pE.buildPlan.id = buildPlan.id
           """)
    Optional<BuildPlan> findByProgrammingExerciseId(@Param("programmingExerciseId") Long programmingExerciseId);
}
