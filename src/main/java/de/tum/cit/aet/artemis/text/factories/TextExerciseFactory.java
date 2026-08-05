package de.tum.cit.aet.artemis.text.factories;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Factory for constructing {@link TextExercise} objects that are not backed by user input, i.e. integration test fixtures and the demo course seeded by the {@code demo} profile.
 * <p>
 * This factory only <b>constructs</b> the entity, it never persists it.
 */
public final class TextExerciseFactory {

    private TextExerciseFactory() {
        // static factory, do not instantiate
    }

    /**
     * Generates a text exercise for the given course.
     *
     * @param title            The title of the exercise.
     * @param shortName        The short name of the exercise.
     * @param problemStatement The markdown problem statement shown to students.
     * @param maxPoints        The achievable points.
     * @param bonusPoints      The achievable bonus points.
     * @param dates            The release, start, due and assessment due dates.
     * @param exampleSolution  The example solution shown after the example solution publication date.
     * @param course           The course the exercise belongs to.
     * @return The generated text exercise.
     */
    public static TextExercise generateTextExercise(String title, @Nullable String shortName, @Nullable String problemStatement, double maxPoints, double bonusPoints,
            ExerciseDates dates, @Nullable String exampleSolution, Course course) {
        TextExercise textExercise = ExerciseFactory.populateExercise(new TextExercise(), title, shortName, problemStatement, maxPoints, bonusPoints, dates, course);
        textExercise.setExampleSolution(exampleSolution);
        textExercise.setPlagiarismDetectionConfig(new PlagiarismDetectionConfig());
        return textExercise;
    }
}
