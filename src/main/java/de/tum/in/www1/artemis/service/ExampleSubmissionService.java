package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class ExampleSubmissionService {

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
    }

    public Optional<ExampleSubmission> get(long id) {
        return exampleSubmissionRepository.findById(id);
    }

    /**
     * First saves the corresponding modeling submission with the exampleSubmission flag. Then the example submission itself is saved. Rolls back if inserting fails - occurs for
     * concurrent createExampleSubmission() calls.
     *
     * @param exampleSubmission the example submission to save
     * @return the exampleSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional
    public List<Feedback> getFeedbackForExampleSubmission(Long exampleSubmissionId) {
        ExampleSubmission exampleSubmission = this.exampleSubmissionRepository.getOne(exampleSubmissionId);
        Submission submission = exampleSubmission.getSubmission();

        if (submission == null) {
            return null;
        }

        Result result = submission.getResult();

        // result.isExampleResult() can have 3 values: null, false, true. We return if it is not true
        if (result == null || result.isExampleResult() != Boolean.TRUE) {
            return null;
        }

        // TODO: create different return for different type of exercises, this is for text exercises
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
    public void deleteById(long exampleSubmissionId) {
        // Note: as we access all lazy elements (i.e. proxies) of the example submission (tutor participations, submission and result) in the same transaction in which we
        // retrieved the example submission, Hibernate will take care to lazy load the proxies and we don't need to fetch them here.
        // While it might be faster, it makes the code more complex in this case and deletion is a rare case which does not need to fully performance optimized
        Optional<ExampleSubmission> optionalExampleSubmission = exampleSubmissionRepository.findById(exampleSubmissionId);

        if (optionalExampleSubmission.isPresent()) {
            ExampleSubmission exampleSubmission = optionalExampleSubmission.get();

            for (TutorParticipation tutorParticipation : exampleSubmission.getTutorParticipations()) {
                tutorParticipation.getTrainedExampleSubmissions().remove(exampleSubmission);
            }

            resultRepository.delete(exampleSubmission.getSubmission().getResult());
            submissionRepository.delete(exampleSubmission.getSubmission());
            exampleSubmissionRepository.delete(exampleSubmission);
        }
    }
}
