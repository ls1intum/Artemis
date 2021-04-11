package de.tum.in.www1.artemis.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

@Service
public class ExampleSubmissionService {

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ExerciseRepository exerciseRepository;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ExerciseRepository exerciseRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseRepository = exerciseRepository;
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
}
