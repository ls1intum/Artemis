package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FeedbackService {

    private final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final FeedbackRepository feedbackRepository;
    private final ResultRepository resultRepository;

    //need bamboo service and resultrepository to create and store from old feedbacks
    public FeedbackService (ResultRepository resultService, Optional<ContinuousIntegrationService> continuousIntegrationService, FeedbackRepository feedbackRepository){
        this.resultRepository = resultService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     *Checking if the result already has feedbacks if not try retrieving them from bamboo and create them. Having feedbacks create
     * a JSONObject which will be converted into the bamboo format to fit the already used frontend system
     *
     * @param result for which the feedback is supposed to be retrieved
     * @return a set of feedback objects including test case names and error messages
     */
    @Transactional
    public List<Feedback> getFeedbackForBuildResult(Result result) {

        // Please note: this is a migration for the old case when we did not store feedback in the database
        // Provide access to results with no feedback in the database
        // TODO: what happens if the build failed?
        if(!result.isSuccessful() && result.getFeedbacks() == null || result.getFeedbacks().size() == 0) {
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Feedback save(Feedback feedback) {
        log.debug("Request to save Feedback : {}", feedback);
        return feedbackRepository.saveAndFlush(feedback);
    }
}
