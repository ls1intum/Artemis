package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.localci.LocalCIBuildPlan;

public interface LocalCIBuildPlanRepository extends JpaRepository<LocalCIBuildPlan, Long> {

    @Query("""
            SELECT buildPlan
            FROM LocalCIBuildPlan buildPlan
                INNER JOIN FETCH buildPlan.programmingExercises programmingExercises
            WHERE programmingExercises.id = :exerciseId
            """)
    Optional<LocalCIBuildPlan> findByProgrammingExercises_IdWithProgrammingExercises(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT buildPlan
            FROM LocalCIBuildPlan buildPlan
            WHERE buildPlan.name = :name
            """)
    Optional<LocalCIBuildPlan> findByName(@Param("name") String name);

    default void setBuildPlanForExercise(String name, ProgrammingExercise exercise) {
        LocalCIBuildPlan buildPlanWrapper = findByName(name).orElseThrow();
        buildPlanWrapper.addProgrammingExercise(exercise);
        save(buildPlanWrapper);
    }
}
