package de.tum.cit.aet.artemis.proof.util;

import java.time.ZonedDateTime;
import java.util.List;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.util.ExerciseFactory;
import de.tum.cit.aet.artemis.proof.domain.DerivationStep;
import de.tum.cit.aet.artemis.proof.domain.MathNode;
import de.tum.cit.aet.artemis.proof.domain.MathNodes;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.dto.ProofExerciseDTO;
import de.tum.cit.aet.artemis.proof.dto.ProofSubmissionDTO;

public class ProofExerciseFactory {

    /** Source expression: {@code 0 + x} */
    public static MathNode sampleSource() {
        return MathNodes.add(MathNodes.num("0"), MathNodes.var("x"));
    }

    /** Target expression: {@code x} */
    public static MathNode sampleTarget() {
        return MathNodes.var("x");
    }

    /** A valid derivation step applying {@code add_zero_left} to the root of {@link #sampleSource()}. */
    public static DerivationStep validStep() {
        DerivationStep step = new DerivationStep();
        step.setStepIndex(0);
        step.setAppliedRuleId("add_zero_left");
        step.setTargetNodePath(List.of()); // root
        step.setResultExpression(MathNodes.var("x"));
        return step;
    }

    public static ProofExercise generateProofExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var exercise = (ProofExercise) ExerciseFactory.populateExercise(new ProofExercise(), releaseDate, dueDate, assessmentDueDate, course);
        exercise.setDescription("Prove that 0 + x = x");
        exercise.setPredefinedCheckboxState(true);
        exercise.setExampleSolution("Apply add_zero_left at the root.");
        exercise.setSourceExpression(sampleSource());
        exercise.setTargetExpression(sampleTarget());
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

    public static ProofSubmission generateProofSubmissionWithValidStep(boolean submitted) {
        var submission = generateProofSubmission(submitted);
        DerivationStep step = validStep();
        step.setSubmission(submission);
        submission.setSteps(new java.util.ArrayList<>(List.of(step)));
        return submission;
    }

    public static ProofExerciseDTO generateProofExerciseDTO(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        return new ProofExerciseDTO(null, "Proof Exercise", null, "Prove that 0 + x = x.", "Prove that 0 + x = x", true, "Apply add_zero_left.", null, null, 10.0, 0.0,
                IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, null, releaseDate, null, dueDate, assessmentDueDate, null, course.getId(), null,
                sampleSource(), sampleTarget());
    }

    public static ProofSubmissionDTO generateProofSubmissionDTO(boolean submitted) {
        return new ProofSubmissionDTO(null, "My proof text", true, submitted, null, null, null, null);
    }

    public static ProofSubmissionDTO generateProofSubmissionDTOWithStep(boolean submitted) {
        var stepDTO = new ProofSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_left", List.of(), MathNodes.var("x"));
        return new ProofSubmissionDTO(null, null, null, submitted, null, null, null, List.of(stepDTO));
    }
}
