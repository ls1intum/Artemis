package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ExampleSubmissionService {

    private final ExampleSubmissionRepository exampleSubmissionRepository;
    private final FeedbackRepository feedbackRepository;

    public ExampleSubmissionService(ExampleSubmissionRepository exampleSubmissionRepository,
                                    FeedbackRepository feedbackRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Saves the given example submission and the corresponding model and creates the result if necessary.
     * Rolls back if inserting fails - occurs for concurrent createExampleSubmission() calls.
     *
     * @param exampleSubmission the submission to save
     * @return the exampleSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public ExampleSubmission save(ExampleSubmission exampleSubmission) {
        exampleSubmission = exampleSubmissionRepository.save(exampleSubmission);

        return exampleSubmission;
    }

    @Transactional
    public List<Feedback> getFeedbacksForExampleSubmission(Long id) {
        ExampleSubmission exampleSubmission = this.exampleSubmissionRepository.getOne(id);
        Result result = exampleSubmission.getSubmission().getResult();

        if (!result.isExampleResult()) {
            return null;
        }

        // TODO find out why this doesn't work
//        List<Feedback> feedbacks = result.getFeedbacks();

        return this.feedbackRepository.findByResult(result);
    }
}
