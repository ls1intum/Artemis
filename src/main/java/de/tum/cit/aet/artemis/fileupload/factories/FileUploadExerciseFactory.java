package de.tum.cit.aet.artemis.fileupload.factories;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

/**
 * Factory for constructing {@link FileUploadExercise} objects that are not backed by user input, i.e. integration test fixtures and the demo course seeded by the {@code demo}
 * profile.
 * <p>
 * This factory only <b>constructs</b> the entity, it never persists it.
 */
public final class FileUploadExerciseFactory {

    private FileUploadExerciseFactory() {
        // static factory, do not instantiate
    }

    /**
     * Generates a file upload exercise for the given course.
     *
     * @param title            The title of the exercise.
     * @param shortName        The short name of the exercise.
     * @param problemStatement The markdown problem statement shown to students.
     * @param maxPoints        The achievable points.
     * @param bonusPoints      The achievable bonus points.
     * @param dates            The release, start, due and assessment due dates.
     * @param filePattern      The allowed file endings as a comma separated list without dots, for example {@code "png,pdf"}.
     * @param exampleSolution  The example solution shown after the example solution publication date.
     * @param course           The course the exercise belongs to.
     * @return The generated file upload exercise.
     */
    public static FileUploadExercise generateFileUploadExercise(String title, @Nullable String shortName, @Nullable String problemStatement, double maxPoints, double bonusPoints,
            ExerciseDates dates, String filePattern, @Nullable String exampleSolution, Course course) {
        FileUploadExercise fileUploadExercise = ExerciseFactory.populateExercise(new FileUploadExercise(), title, shortName, problemStatement, maxPoints, bonusPoints, dates,
                course);
        fileUploadExercise.setFilePattern(filePattern);
        fileUploadExercise.setExampleSolution(exampleSolution);
        return fileUploadExercise;
    }
}
