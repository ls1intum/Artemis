package de.tum.in.www1.artemis.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class ExampleSubmissionService {

    private static final String ENTITY_NAME = "exampleSubmission";

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final TextExerciseImportService textExerciseImportService;

    private final ModelingExerciseImportService modelingExerciseImportService;

    private final TextSubmissionRepository textSubmissionRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ExerciseRepository exerciseRepository,
            TextExerciseImportService textExerciseImportService, ModelingExerciseImportService modelingExerciseImportService, TextSubmissionRepository textSubmissionRepository,
            GradingCriterionRepository gradingCriterionRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.textExerciseImportService = textExerciseImportService;
        this.textSubmissionRepository = textSubmissionRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
    }

    /**
     * First saves the corresponding submission with the exampleSubmission flag. Then the example submission itself is saved.
     *
     * @param exampleSubmission the example submission to save
     * @return the exampleSubmission entity
     */
    public ExampleSubmission save(ExampleSubmission exampleSubmission) {
        Submission submission = exampleSubmission.getSubmission();
        if (submission != null) {
            submission.setExampleSubmission(true);
            // Rebuild connection between result and submission, if it has been lost, because hibernate needs it
            if (submission.getLatestResult() != null && submission.getLatestResult().getSubmission() == null) {
                submission.getLatestResult().setSubmission(submission);
            }
            submissionRepository.save(submission);
        }
        return exampleSubmissionRepository.save(exampleSubmission);
    }

    /**
     * Deletes a ExampleSubmission with the given ID, cleans up the tutor participations, removes the result and the submission
     * @param exampleSubmissionId the ID of the ExampleSubmission which should be deleted
     */
    public void deleteById(long exampleSubmissionId) {
        Optional<ExampleSubmission> optionalExampleSubmission = exampleSubmissionRepository.findByIdWithResultsAndTutorParticipations(exampleSubmissionId);

        if (optionalExampleSubmission.isPresent()) {
            ExampleSubmission exampleSubmission = optionalExampleSubmission.get();

            for (TutorParticipation tutorParticipation : exampleSubmission.getTutorParticipations()) {
                tutorParticipation.getTrainedExampleSubmissions().remove(exampleSubmission);
            }

            Long exerciseId = exampleSubmission.getExercise().getId();
            Optional<Exercise> optionalExercise = exerciseRepository.findByIdWithEagerExampleSubmissions(exerciseId);

            // Remove the reference to the exercise when the example submission is deleted
            optionalExercise.ifPresent(exercise -> {
                exercise.removeExampleSubmission(exampleSubmission);
                exerciseRepository.save(exercise);
            });

            // due to Cascade.Remove this will also remove the submission and the result(s) in case they exist
            exampleSubmissionRepository.delete(exampleSubmission);
        }
    }

    /**
     * Creates new example submission by copying the student submission with its assessments
     * calls copySubmission of required service depending on type of exercise
     *
     * @param submissionId The original student submission id to be copied
     * @param exercise   The exercise to which the example submission belongs
     * @return the exampleSubmission entity
     */
    public ExampleSubmission importStudentSubmissionAsExampleSubmission(Long submissionId, Exercise exercise) {
        ExampleSubmission newExampleSubmission = new ExampleSubmission();
        newExampleSubmission.setExercise(exercise);

        var gradingCriteria = this.gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream()).forEach(gradingInstruction -> {
            gradingInstructionCopyTracker.put(gradingInstruction.getId(), gradingInstruction);
        });

        if (exercise instanceof ModelingExercise) {
            ModelingSubmission modelingSubmission = (ModelingSubmission) submissionRepository.findOneWithEagerResultAndFeedback(submissionId);
            checkGivenExerciseIdSameForSubmissionParticipation(exercise.getId(), modelingSubmission.getParticipation().getExercise().getId());
            // example submission does not need participation
            modelingSubmission.setParticipation(null);

            newExampleSubmission.setSubmission(modelingExerciseImportService.copySubmission(modelingSubmission, gradingInstructionCopyTracker));
        }
        if (exercise instanceof TextExercise) {
            TextSubmission textSubmission = textSubmissionRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(submissionId);
            checkGivenExerciseIdSameForSubmissionParticipation(exercise.getId(), textSubmission.getParticipation().getExercise().getId());
            // example submission does not need participation
            textSubmission.setParticipation(null);
            newExampleSubmission.setSubmission(textExerciseImportService.copySubmission(textSubmission, gradingInstructionCopyTracker));
        }
        return exampleSubmissionRepository.save(newExampleSubmission);
    }

    /**
     * Checks the original exercise id is matched with the exercise id in the submission participation
     *
     * @param originalExerciseId        given exercise id in the request
     * @param exerciseIdInSubmission    exercise id in submission participation
     */
    public void checkGivenExerciseIdSameForSubmissionParticipation(long originalExerciseId, long exerciseIdInSubmission) {
        if (!Objects.equals(originalExerciseId, exerciseIdInSubmission)) {
            throw new BadRequestAlertException("ExerciseId does not match with the exerciseId in submission participation", ENTITY_NAME, "idNotMatched");
        }
    }

}
