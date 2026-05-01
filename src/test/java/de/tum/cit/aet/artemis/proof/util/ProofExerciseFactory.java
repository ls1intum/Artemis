package de.tum.cit.aet.artemis.proof.util;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseFactory;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;

public class ProofExerciseFactory {

    public static ProofExercise generateProofExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var exercise = (ProofExercise) ExerciseFactory.populateExercise(new ProofExercise(), releaseDate, dueDate, assessmentDueDate, course);
        exercise.setDescription("Prove that 1 + 1 = 2");
        exercise.setPredefinedCheckboxState(true);
        exercise.setExampleSolution("By the Peano axioms, successor(1) = 2.");
        return exercise;
    }

    public static ProofSubmission generateProofSubmission(boolean submitted) {
        var submission = new ProofSubmission();
        submission.setText("My proof text");
        submission.setStudentCheckboxState(true);
        submission.setSubmitted(submitted);
        submission.setSubmissionDate(ZonedDateTime.now());
        return submission;
    }
}
