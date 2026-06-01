package de.tum.cit.aet.artemis.math.util;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.util.ExerciseFactory;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathExerciseDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;

public class MathExerciseFactory {

    public static MathExercise generateMathExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var exercise = (MathExercise) ExerciseFactory.populateExercise(new MathExercise(), releaseDate, dueDate, assessmentDueDate, course);
        exercise.setDescription("Prove that 0 + x = x");
        exercise.setExampleSolution("Apply add_zero_left at the root.");
        return exercise;
    }

    public static MathSubmission generateMathSubmission(boolean submitted) {
        var submission = new MathSubmission();
        submission.setSubmitted(submitted);
        submission.setSubmissionDate(ZonedDateTime.now());
        return submission;
    }

    public static MathExerciseDTO generateMathExerciseDTO(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        return new MathExerciseDTO(null, "Math Exercise", null, "Prove that 0 + x = x.", "Prove that 0 + x = x", "Apply add_zero_left.", null, null, 10.0, 0.0,
                IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, null, releaseDate, null, dueDate, assessmentDueDate, null, course.getId(), false);
    }

    public static MathSubmissionDTO generateMathSubmissionDTO(boolean submitted) {
        return new MathSubmissionDTO(null, submitted, null, null, null, null);
    }

}
