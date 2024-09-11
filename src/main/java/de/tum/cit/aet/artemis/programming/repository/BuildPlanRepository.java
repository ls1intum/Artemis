package de.tum.cit.aet.artemis.programming.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlan;

public interface BuildPlanRepository extends ArtemisJpaRepository<BuildPlan, Long> {

    @Query("""
            SELECT buildPlan
            FROM BuildPlan buildPlan
                INNER JOIN FETCH buildPlan.programmingExercises programmingExercises
            WHERE programmingExercises.id = :exerciseId
            """)
    Optional<BuildPlan> findByProgrammingExercises_IdWithProgrammingExercises(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT buildPlan
            FROM BuildPlan buildPlan
                INNER JOIN FETCH buildPlan.programmingExercises programmingExercises
                LEFT JOIN FETCH programmingExercises.buildConfig buildConfig
            WHERE programmingExercises.id = :exerciseId
            """)
    Optional<BuildPlan> findByProgrammingExercises_IdWithProgrammingExercisesWithBuildConfig(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT buildPlan
            FROM BuildPlan buildPlan
                JOIN buildPlan.programmingExercises programmingExercises
            WHERE programmingExercises.id = :exerciseId
                """)
    Optional<BuildPlan> findByProgrammingExercises_Id(@Param("exerciseId") long exerciseId);

    default BuildPlan findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(final long exerciseId) {
        return getValueElseThrow(findByProgrammingExercises_IdWithProgrammingExercises(exerciseId));
    }

    default BuildPlan findByProgrammingExercises_IdWithProgrammingExercisesWithBuildConfigElseThrow(final long exerciseId) {
        return getValueElseThrow(findByProgrammingExercises_IdWithProgrammingExercisesWithBuildConfig(exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "programmingExercises" })
    Optional<BuildPlan> findByBuildPlan(String buildPlan);

    /**
     * Adds the given build plan to the exercise.
     *
     * @param buildPlan The build plan script.
     * @param exercise  The exercise the build plan will be added to.
     * @return The new build plan.
     */
    default BuildPlan setBuildPlanForExercise(final String buildPlan, final ProgrammingExercise exercise) {
        BuildPlan buildPlanWrapper = findByBuildPlan(buildPlan).orElse(new BuildPlan());
        buildPlanWrapper.setBuildPlan(buildPlan);
        buildPlanWrapper.addProgrammingExercise(exercise);
        return save(buildPlanWrapper);
    }

    /**
     * Copies the build plan from the source exercise to the target exercise.
     *
     * @param sourceExercise The exercise containing the build plan to be copied.
     * @param targetExercise The exercise into which the build plan is copied.
     */
    default void copyBetweenExercises(ProgrammingExercise sourceExercise, ProgrammingExercise targetExercise) {
        findByProgrammingExercises_IdWithProgrammingExercises(sourceExercise.getId()).ifPresent(buildPlan -> {
            buildPlan.addProgrammingExercise(targetExercise);
            save(buildPlan);
        });
    }
}
