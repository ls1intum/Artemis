package de.tum.in.www1.artemis.service.connectors.ci;

import java.net.URL;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

public abstract class AbstractBuildPlanCreator {

    private final BuildPlanRepository buildPlanRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    @Value("${server.url}")
    private URL artemisServerUrl;

    protected AbstractBuildPlanCreator(final BuildPlanRepository buildPlanRepository, final ProgrammingExerciseRepository programmingExerciseRepository) {
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
    public String generateBuildPlanURL(final ProgrammingExercise exercise) {
        programmingExerciseRepository.generateBuildPlanAccessSecretIfNotExists(exercise);
        return String.format("%s/api/public/programming-exercises/%d/build-plan?secret=%s", artemisServerUrl, exercise.getId(), exercise.getBuildPlanAccessSecret());
    }

    /**
     * Creates a build plan for the given exercise. In case a build plan for this exercise already exists, it is
     * disconnected from the exercise and replaced with a new one. Saving it afterwards ensures that the foreign-key
     * relation to the changed set of connected exercises is properly updated after disconnecting the existing one.
     *
     * @param exercise The exercise for which a new build plan is created.
     */
    public void createBuildPlanForExercise(final ProgrammingExercise exercise) {
        final var defaultBuildPlan = generateDefaultBuildPlan(exercise);
        buildPlanRepository.setBuildPlanForExercise(defaultBuildPlan, exercise);
    }

    /**
     * Replaces placeholders in the build plan template by their actual values.
     *
     * @param replacements      Mapping from placeholder variables to their replacement values.
     * @param buildPlanTemplate In which the variables should be replaced.
     * @return A build plan with filled-in values.
     */
    protected String replaceVariablesInBuildPlanTemplate(final Map<String, String> replacements, final String buildPlanTemplate) {
        if (replacements == null) {
            return buildPlanTemplate;
        }

        String buildPlan = buildPlanTemplate;
        for (final var replacement : replacements.entrySet()) {
            buildPlan = buildPlan.replace(replacement.getKey(), replacement.getValue());
        }

        return buildPlan;
    }
}
