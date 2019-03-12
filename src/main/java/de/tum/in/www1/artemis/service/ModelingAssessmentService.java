package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class ModelingAssessmentService extends AssessmentService {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final UserService userService;
    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final FeedbackRepository feedbackRepository;

    public ModelingAssessmentService(ResultRepository resultRepository,
                                     UserService userService,
                                     ModelingSubmissionRepository modelingSubmissionRepository,
                                     FeedbackRepository feedbackRepository) {
        super(resultRepository);
        this.userService = userService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
    }


    /**
     * This function is used for submitting a manual assessment/result. It updates the completion date, sets the
     * assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the database.
     *
     * @param result   the result the assessment belongs to
     * @param exercise the exercise the assessment belongs to
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitManualAssessment(Result result, ModelingExercise exercise) {
        // TODO CZ: use AssessmentService#submitResult() function instead
        result.setRatedIfNotExceeded(exercise.getDueDate(), result.getSubmission().getSubmissionDate());
        result.setCompletionDate(ZonedDateTime.now());
        result.evaluateFeedback(exercise.getMaxScore()); // TODO CZ: move to AssessmentService class, as it's the same for modeling and text exercises (i.e. total score is sum of feedback credits)
        resultRepository.save(result);
        return result;
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL
     * and sets the assessor attribute. Furthermore, it saves the result in the database.
     *
     * @param modelingSubmission the modeling submission to which the feedback belongs to
     * @param modelingAssessment the assessment as a feedback list that should be added to the result of the
     *                           corresponding submission
     */
    @Transactional
    public Result saveManualAssessment(ModelingSubmission modelingSubmission, List<Feedback> modelingAssessment) {
        Result result = modelingSubmission.getResult();
        if (result == null) {
            result = new Result();
        }

        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);

        // Note: If there is old feedback that gets removed here and not added again in the for-loop, it will also be
        //       deleted in the database because of the 'orphanRemoval = true' flag.
        result.getFeedbacks().clear();
        for (Feedback feedback : modelingAssessment) {
            feedback.setType(FeedbackType.MANUAL);
            result.addFeedback(feedback);
        }
        result.setHasFeedback(true);

        if (result.getSubmission() == null) {
            result.setSubmission(modelingSubmission);
            // TODO CZ: is setting the result and saving the submission really necessary here? setting the submission of the result should be enough as the relationship is owned by the result
            modelingSubmission.setResult(result);
            modelingSubmissionRepository.save(modelingSubmission);
        }
        // Note: This also saves the feedback objects in the database because of the 'cascade = CascadeType.ALL' option.
        return resultRepository.save(result);
    }
}
