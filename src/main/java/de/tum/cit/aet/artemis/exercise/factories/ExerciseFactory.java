package de.tum.cit.aet.artemis.exercise.factories;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;

/**
 * Factory for the fields every course exercise shares, used by integration test fixtures and by the demo course seeded by the {@code demo} profile.
 * <p>
 * This factory only <b>populates</b> the entity, it never persists it and it never derives values on its own: every identifying value is passed in by the caller. Tests pass
 * randomized values to keep fixtures independent of each other, while the demo seeding uses fixed values so that re-running it stays idempotent.
 */
public final class ExerciseFactory {

    private ExerciseFactory() {
        // static factory, do not instantiate
    }

    /**
     * Populates the fields that all course exercises share. Type specific fields are the responsibility of the caller.
     *
     * @param exercise         The exercise to populate.
     * @param title            The title of the exercise.
     * @param shortName        The short name of the exercise. Must start with a letter, see {@code Constants.SHORT_NAME_PATTERN}.
     * @param problemStatement The markdown problem statement shown to students.
     * @param maxPoints        The achievable points. Must be greater than zero unless the exercise is not included in the overall score.
     * @param bonusPoints      The achievable bonus points. Must be zero unless the exercise is included completely.
     * @param dates            The release, start, due and assessment due dates.
     * @param course           The course the exercise belongs to.
     * @param <T>              The concrete exercise type.
     * @return The populated exercise.
     */
    public static <T extends Exercise> T populateExercise(T exercise, String title, @Nullable String shortName, @Nullable String problemStatement, double maxPoints,
            double bonusPoints, ExerciseDates dates, Course course) {
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setProblemStatement(problemStatement);
        exercise.setMaxPoints(maxPoints);
        exercise.setBonusPoints(bonusPoints);
        exercise.setReleaseDate(dates.releaseDate());
        exercise.setStartDate(dates.startDate());
        exercise.setDueDate(dates.dueDate());
        exercise.setAssessmentDueDate(dates.assessmentDueDate());
        exercise.setDifficulty(DifficultyLevel.MEDIUM);
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        Integer presentationScore = course.getPresentationScore();
        exercise.setPresentationScoreEnabled(presentationScore != null && presentationScore != 0);
        exercise.setCourse(course);
        exercise.setExerciseGroup(null);
        return exercise;
    }
}
