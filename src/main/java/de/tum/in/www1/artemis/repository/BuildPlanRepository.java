package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

public interface BuildPlanRepository extends JpaRepository<BuildPlan, Long> {

    @Query("""
            SELECT b
            FROM BuildPlan b
                INNER join b.programmingExercises programmingExercises
            WHERE programmingExercises.id = :exerciseId
            """)
    Optional<BuildPlan> findByProgrammingExercises_Id(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "programmingExercises" })
    Optional<BuildPlan> findByBuildPlan(String buildPlan);

    default void updateBuildPlan(ProgrammingExercise exercise, String buildPlan) {
        BuildPlan buildPlanWrapper = findByBuildPlan(buildPlan).orElse(new BuildPlan());
        buildPlanWrapper.setBuildPlan(buildPlan);
        buildPlanWrapper.addProgrammingExercise(exercise);
        save(buildPlanWrapper);
    }
}
