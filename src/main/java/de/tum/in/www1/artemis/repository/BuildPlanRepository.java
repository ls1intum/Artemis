package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

public interface BuildPlanRepository extends JpaRepository<BuildPlan, Long> {

    Optional<BuildPlan> findByProgrammingExercises_Id(long programmingExerciseId);

    Optional<BuildPlan> findByBuildPlan(String buildPlan);

    default void updateBuildPlan(ProgrammingExercise exercise, String buildPlan, BuildPlanRepository buildPlanRepository) {
        BuildPlan buildPlanWrapper = findByBuildPlan(buildPlan).orElse(new BuildPlan());
        buildPlanWrapper.addProgrammingExercise(exercise);
        save(buildPlanWrapper);
    }
}
