package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ExampleSubmissionService {

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final ExerciseRepository exerciseRepository;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            ExerciseRepository exerciseRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.exerciseRepository = exerciseRepository;
    }

    public Optional<ExampleSubmission> findById(Long exampleSubmissionId) {
        return exampleSubmissionRepository.findById(exampleSubmissionId);
    }

    public Optional<ExampleSubmission> findByIdWithEagerTutorParticipations(Long exampleSubmissionId) {
        return exampleSubmissionRepository.findByIdWithEagerTutorParticipations(exampleSubmissionId);
    }

    /**
     * First saves the corresponding modeling submission with the exampleSubmission flag. Then the example submission itself is saved. Rolls back if inserting fails - occurs for
     * concurrent createExampleSubmission() calls.
     *
     * @param exampleSubmission the example submission to save
     * @return the exampleSubmission entity
     */
    public ExampleSubmission save(ExampleSubmission exampleSubmission) {
        Submission submission = exampleSubmission.getSubmission();
        if (submission != null) {
            submission.setExampleSubmission(true);
            // Rebuild connection between result and submission, if it has been lost, because hibernate needs it
            if (submission.getResult() != null && submission.getResult().getSubmission() == null) {
                submission.getResult().setSubmission(submission);
            }
            submissionRepository.save(submission);
        }
        return exampleSubmissionRepository.save(exampleSubmission);
    }

    /**
     * Given the id of an example submission, it returns the results of the linked submission, if any
     *
     * @param exampleSubmissionId the id of the example submission we want to retrieve
     * @return list of feedback for an example submission
     */
    public List<Feedback> getFeedbackForExampleSubmission(Long exampleSubmissionId) {
        Optional<ExampleSubmission> exampleSubmission = this.exampleSubmissionRepository.findByIdWithEagerResultAndFeedback(exampleSubmissionId);
        Submission submission = exampleSubmission.get().getSubmission();

        if (submission == null) {
            return null;
        }

        Result result = submission.getResult();

        // result.isExampleResult() can have 3 values: null, false, true. We return if it is not true
        if (result == null || result.isExampleResult() != Boolean.TRUE) {
            return null;
        }

        return result.getFeedbacks();
    }

    public ExampleSubmission findOneWithEagerResult(Long exampleSubmissionId) {
        return exampleSubmissionRepository.findByIdWithEagerResultAndFeedback(exampleSubmissionId)
                .orElseThrow(() -> new EntityNotFoundException("Example submission with id \"" + exampleSubmissionId + "\" does not exist"));
    }

    public ExampleSubmission findOneBySubmissionId(Long submissionId) {
        return exampleSubmissionRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Example submission for submission with id \"" + submissionId + "\" does not exist"));
    }

    public Optional<ExampleSubmission> getWithEagerExercise(Long exampleSubmissionId) {
        return exampleSubmissionRepository.findByIdWithEagerExercise(exampleSubmissionId);
    }

    /**
     * Deletes a ExampleSubmission with the given ID, cleans up the tutor participations, removes the result and the submission
     * @param exampleSubmissionId the ID of the ExampleSubmission which should be deleted
     */
    @Transactional
    public void deleteById(long exampleSubmissionId) {
        Optional<ExampleSubmission> optionalExampleSubmission = exampleSubmissionRepository.findByIdWithEagerTutorParticipations(exampleSubmissionId);

        if (optionalExampleSubmission.isPresent()) {
            ExampleSubmission exampleSubmission = optionalExampleSubmission.get();

            for (TutorParticipation tutorParticipation : exampleSubmission.getTutorParticipations()) {
                tutorParticipation.getTrainedExampleSubmissions().remove(exampleSubmission);
            }

            Long exerciseId = exampleSubmission.getExercise().getId();
            Optional<Exercise> exerciseWithExampleSubmission = exerciseRepository.findByIdWithEagerExampleSubmissions(exerciseId);

            // Remove the reference to the exercise when the example submission is deleted
            exerciseWithExampleSubmission.ifPresent(exercise -> {
                exercise.removeExampleSubmission(exampleSubmission);
            });

            // due to Cascase.Remove this will also remove the submission and the result in case they exist
            exampleSubmissionRepository.delete(exampleSubmission);
        }
    }
}
