package de.tum.cit.aet.artemis.proof.util;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.util.ExerciseFactory;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.dto.ProofExerciseDTO;
import de.tum.cit.aet.artemis.proof.dto.ProofSubmissionDTO;

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

    public static ProofExerciseDTO generateProofExerciseDTO(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        return new ProofExerciseDTO(null, "Proof Exercise", null, "Prove that 1 + 1 = 2.", "Prove that 1 + 1 = 2", true, "By the Peano axioms, successor(1) = 2.", null, null, 10.0,
                0.0, IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, null, releaseDate, null, dueDate, assessmentDueDate, null, course.getId(), null);
    }

    public static ProofSubmissionDTO generateProofSubmissionDTO(boolean submitted) {
        return new ProofSubmissionDTO(null, "My proof text", true, submitted, null, null, null);
    }
}
