package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

public abstract class AbstractBuildPlanCreator {

    private final BuildPlanRepository buildPlanRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    @Value("${server.url}")
    private URL artemisServerUrl;

    protected AbstractBuildPlanCreator(BuildPlanRepository buildPlanRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.buildPlanRepository = buildPlanRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Generates a default build plan for the given programming exercise.
     *
     * @param programmingExercise The programming exercise for which a build plan is generated.
     * @return The default build plan for the given exercise as a String.
     */
    protected abstract String generateDefaultBuildPlan(ProgrammingExercise programmingExercise);

    /**
     * Generates the URL required to access the build plan for the given exercise.
     *
     * @param exercise The exercise for which the URL of its build plan is generated.
     * @return The build plan URL.
     */
    public String generateBuildPlanURL(ProgrammingExercise exercise) {
        programmingExerciseRepository.generateBuildPlanAccessSecretIfNotExists(exercise);
        return String.format("%s/api/programming-exercises/%s/build-plan?secret=%s", artemisServerUrl, exercise.getId(), exercise.getBuildPlanAccessSecret());
    }

    /**
     * Creates a build plan for the given exercise. In case a build plan for this exercise already exists, it is
     * disconnected from the exercise and replaced with a new one. Saving it afterwards ensures that the foreign-key
     * relation to the changed set of connected exercises is properly updated after disconnecting the existing one.
     *
     * @param exercise The exercise for which a new build plan is created.
     */
    public void createBuildPlanForExercise(ProgrammingExercise exercise) {
        Optional<BuildPlan> optionalBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(exercise.getId());
        if (optionalBuildPlan.isPresent()) {
            BuildPlan oldBuildPlan = optionalBuildPlan.get();
            oldBuildPlan.disconnectFromExercise(exercise);
            buildPlanRepository.save(oldBuildPlan);
        }
        var defaultBuildPlan = generateDefaultBuildPlan(exercise);
        buildPlanRepository.setBuildPlanForExercise(defaultBuildPlan, exercise);
    }
}
