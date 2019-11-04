package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;

@Service
@Transactional
public class FeedbackService {

    private final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final FeedbackRepository feedbackRepository;

    private final ResultRepository resultRepository;

    // need bamboo service and resultrepository to create and store from old feedbacks
    public FeedbackService(ResultRepository resultService, Optional<ContinuousIntegrationService> continuousIntegrationService, FeedbackRepository feedbackRepository) {
        this.resultRepository = resultService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Checking if the result already has feedbacks if not try retrieving them from bamboo and create them. Having feedbacks create a JSONObject which will be converted into the
     * bamboo format to fit the already used frontend system
     *
     * @deprecated This method is deprecated and is planned to be removed September 2019. The reason is that we store the build results in our database now and don't have to query
     *             the integration service for them.
     * @param result for which the feedback is supposed to be retrieved
     * @return a set of feedback objects including test case names and error messages
     */
    @Transactional
    @Deprecated
    public List<Feedback> getFeedbackForBuildResult(Result result) {
        boolean isAutomaticResult = result.getAssessmentType() != null && result.getAssessmentType().equals(AssessmentType.AUTOMATIC);
        // Please note: this is a migration for the old case when we did not store feedback in the database
        // Provide access to results with no feedback in the database
        // If the build failed (no feedback, but build logs) and the build plan does not exist any more (because it was cleaned up before),
        // we cannot send feedback to the student, this case is handled in the continuous integration service
        if (!result.isSuccessful() && isAutomaticResult && (result.getFeedbacks() == null || result.getFeedbacks().size() == 0)) {
            // if the result does not contain any feedback, try to retrieve them from Bamboo and store them in the result and return these.
            return continuousIntegrationService.get().getLatestBuildResultDetails(result);
        }
        return result.getFeedbacks();
    }

    /**
     * Save a feedback.
     *
     * @param feedback the entity to save
     * @return the persisted entity
     */
    @Transactional
    public Feedback save(Feedback feedback) {
        log.debug("Request to save Feedback : {}", feedback);
        return feedbackRepository.save(feedback);
    }

    /**
     * Find all existing Feedback Elements referencing a text block part of a TextCluster.
     *
     * @param cluster TextCluster requesting existinging Feedbacks for.
     * @return Map<TextBlockId, Feedback>
     */
    @Transactional(readOnly = true)
    public Map<String, Feedback> getFeedbackForTextExerciseInCluster(TextCluster cluster) {
        final List<String> references = cluster.getBlocks().stream().map(TextBlock::getId).collect(toList());
        final TextExercise exercise = cluster.getExercise();
        return feedbackRepository.findByReferenceInAndResult_Submission_Participation_Exercise(references, exercise).parallelStream()
                .collect(toMap(Feedback::getReference, f -> f));
    }
}
