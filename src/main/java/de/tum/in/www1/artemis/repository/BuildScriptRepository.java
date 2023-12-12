package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.BuildScript;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

public interface BuildScriptRepository extends JpaRepository<BuildScript, Long> {

    @Query("""
            SELECT buildScript
            FROM BuildScript buildScript
                INNER JOIN FETCH buildScript.programmingExercise programmingExercise
            WHERE programmingExercise.id = :exerciseId
            """)
    Optional<BuildScript> findByProgrammingExercisesId(@Param("exerciseId") long exerciseId);

    /**
     * Adds the given build script to the exercise.
     *
     * @param buildScript The build script.
     * @param exercise    The exercise the build script will be added to.
     * @return The new build script.
     */
    default BuildScript setBuildScriptForExercise(final String buildScript, final ProgrammingExercise exercise) {
        BuildScript buildScriptWrapper = findByProgrammingExercisesId(exercise.getId()).orElse(new BuildScript());
        buildScriptWrapper.setBuildScript(buildScript);
        buildScriptWrapper.setProgrammingExercise(exercise);
        return save(buildScriptWrapper);
    }
}
