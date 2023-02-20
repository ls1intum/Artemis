package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface BuildPlanRepository extends JpaRepository<BuildPlan, Long> {

    @Query("""
            SELECT buildPlan
            FROM BuildPlan buildPlan
                INNER JOIN FETCH buildPlan.programmingExercises programmingExercises
            WHERE programmingExercises.id = :exerciseId
            """)
    Optional<BuildPlan> findByProgrammingExercises_IdWithProgrammingExercises(@Param("exerciseId") long exerciseId);

    default BuildPlan findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(long exerciseId) {
        return findByProgrammingExercises_IdWithProgrammingExercises(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Could not find a build plan for exercise " + exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "programmingExercises" })
    Optional<BuildPlan> findByBuildPlan(String buildPlan);

    default void setBuildPlanForExercise(String buildPlan, ProgrammingExercise exercise) {
        BuildPlan buildPlanWrapper = findByBuildPlan(buildPlan).orElse(new BuildPlan());
        buildPlanWrapper.setBuildPlan(buildPlan);
        buildPlanWrapper.addProgrammingExercise(exercise);
        save(buildPlanWrapper);
    }

    /**
     * Disconnects the build plan from this exercise in case it exists.
     *
     * @param exercise The programming exercise from which the build plan should be removed.
     */
    default void disconnectBuildPlanFromExercise(final ProgrammingExercise exercise) {
        findByProgrammingExercises_IdWithProgrammingExercises(exercise.getId()).ifPresent(buildPlan -> {
            buildPlan.disconnectFromExercise(exercise);
            save(buildPlan);
        });
    }
}
