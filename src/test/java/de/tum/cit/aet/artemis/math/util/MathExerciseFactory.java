package de.tum.cit.aet.artemis.math.util;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.util.ExerciseFactory;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathExerciseDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;

public class MathExerciseFactory {

    /** Source expression: {@code 0 + x} */
    public static MathNode sampleSource() {
        return MathNodes.add(MathNodes.num("0"), MathNodes.var("x"));
    }

    /** Target expression: {@code x} */
    public static MathNode sampleTarget() {
        return MathNodes.var("x");
    }

    public static MathExercise generateMathExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var exercise = (MathExercise) ExerciseFactory.populateExercise(new MathExercise(), releaseDate, dueDate, assessmentDueDate, course);
        exercise.setDescription("Prove that 0 + x = x");
        exercise.setExampleSolution("Apply add_zero_left at the root.");
        exercise.setSourceExpression(sampleSource());
        exercise.setTargetExpression(sampleTarget());
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
                IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, null, releaseDate, null, dueDate, assessmentDueDate, null, course.getId(),
                sampleSource(), sampleTarget(), false, true, false, false, null, null, null, false, null);
    }

    public static MathSubmissionDTO generateMathSubmissionDTO(boolean submitted) {
        return new MathSubmissionDTO(null, submitted, null, null, null, null);
    }

}
