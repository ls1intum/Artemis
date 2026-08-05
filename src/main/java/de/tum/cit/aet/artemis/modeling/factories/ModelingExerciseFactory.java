package de.tum.cit.aet.artemis.modeling.factories;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

/**
 * Factory for constructing {@link ModelingExercise} objects that are not backed by user input, i.e. integration test fixtures and the demo course seeded by the {@code demo}
 * profile.
 * <p>
 * This factory only <b>constructs</b> the entity, it never persists it.
 */
public final class ModelingExerciseFactory {

    private ModelingExerciseFactory() {
        // static factory, do not instantiate
    }

    /**
     * Generates a modeling exercise for the given course.
     *
     * @param title                      The title of the exercise.
     * @param shortName                  The short name of the exercise.
     * @param problemStatement           The markdown problem statement shown to students.
     * @param maxPoints                  The achievable points.
     * @param bonusPoints                The achievable bonus points.
     * @param dates                      The release, start, due and assessment due dates.
     * @param diagramType                The type of diagram students are asked to model.
     * @param exampleSolutionModel       The Apollon JSON of the example solution, may be null.
     * @param exampleSolutionExplanation The explanation shown alongside the example solution, may be null.
     * @param course                     The course the exercise belongs to.
     * @return The generated modeling exercise.
     */
    public static ModelingExercise generateModelingExercise(String title, @Nullable String shortName, @Nullable String problemStatement, double maxPoints, double bonusPoints,
            ExerciseDates dates, DiagramType diagramType, @Nullable String exampleSolutionModel, @Nullable String exampleSolutionExplanation, Course course) {
        ModelingExercise modelingExercise = ExerciseFactory.populateExercise(new ModelingExercise(), title, shortName, problemStatement, maxPoints, bonusPoints, dates, course);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel(exampleSolutionModel);
        modelingExercise.setExampleSolutionExplanation(exampleSolutionExplanation);
        return modelingExercise;
    }
}
