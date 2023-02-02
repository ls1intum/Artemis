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

    protected abstract String generateDefaultBuildPlan(ProgrammingExercise programmingExercise);

    public void addBuildPlanToProgrammingExerciseIfUnset(final ProgrammingExercise programmingExercise) {
        Optional<BuildPlan> optionalBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId());
        if (optionalBuildPlan.isEmpty()) {
            var defaultBuildPlan = generateDefaultBuildPlan(programmingExercise);
            buildPlanRepository.setBuildPlanForExercise(defaultBuildPlan, programmingExercise);
        }
    }

    public String generateBuildPlanURL(ProgrammingExercise exercise) {
        programmingExerciseRepository.generateBuildPlanAccessSecretIfNotExists(exercise);
        return String.format("%s/api/programming-exercises/%s/build-plan?secret=%s", artemisServerUrl, exercise.getId(), exercise.getBuildPlanAccessSecret());
    }

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
