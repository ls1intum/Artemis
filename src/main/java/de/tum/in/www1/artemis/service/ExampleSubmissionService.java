package de.tum.in.www1.artemis.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

@Service
public class ExampleSubmissionService {

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final TextExerciseImportService textExerciseImportService;

    private final ModelingExerciseImportService modelingExerciseImportService;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ExerciseRepository exerciseRepository,
            TextExerciseImportService textExerciseImportService, ModelingExerciseImportService modelingExerciseImportService) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.textExerciseImportService = textExerciseImportService;
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
    @Transactional // ok
    public void deleteById(long exampleSubmissionId) {
        Optional<ExampleSubmission> optionalExampleSubmission = exampleSubmissionRepository.findByIdWithResultsAndTutorParticipations(exampleSubmissionId);

        if (optionalExampleSubmission.isPresent()) {
            ExampleSubmission exampleSubmission = optionalExampleSubmission.get();

            for (TutorParticipation tutorParticipation : exampleSubmission.getTutorParticipations()) {
                tutorParticipation.getTrainedExampleSubmissions().remove(exampleSubmission);
            }

            Long exerciseId = exampleSubmission.getExercise().getId();
            Optional<Exercise> exerciseWithExampleSubmission = exerciseRepository.findByIdWithEagerExampleSubmissions(exerciseId);

            // Remove the reference to the exercise when the example submission is deleted
            exerciseWithExampleSubmission.ifPresent(exercise -> exercise.removeExampleSubmission(exampleSubmission));

            // due to Cascade.Remove this will also remove the submission and the result in case they exist
            exampleSubmissionRepository.delete(exampleSubmission);
        }
    }

    /**
     * Creates new example submission by copying the student submission with its assessments
     * calls copySubmission of required service depending on type of exercise
     *
     * @param submission The original student submission to be copied
     * @param exercise   The exercise to which the example submission belongs
     * @return the exampleSubmission entity
     */
    public ExampleSubmission importStudentSubmissionAsExampleSubmission(Submission submission, Exercise exercise) {
        ExampleSubmission newExampleSubmission = new ExampleSubmission();
        newExampleSubmission.setExercise(exercise);
        // example submission does not need participation
        submission.setParticipation(null);

        if (exercise instanceof ModelingExercise) {
            newExampleSubmission.setSubmission(modelingExerciseImportService.copySubmission(submission));
        }
        else if (exercise instanceof TextExercise) {
            newExampleSubmission.setSubmission(textExerciseImportService.copySubmission(submission));
        }

        return exampleSubmissionRepository.save(newExampleSubmission);
    }
}
