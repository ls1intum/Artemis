package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Feedback;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.FeedbackRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class FeedbackService {

    private final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final FeedbackRepository feedbackRepository;
    private final ResultRepository resultRepository;

    //need bamboo service and resultrepository to create and store from old feedbacks
    public FeedbackService (ResultRepository resultService,  Optional<ContinuousIntegrationService> continuousIntegrationService, FeedbackRepository feedbackRepository){
        this.resultRepository = resultService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.feedbackRepository = feedbackRepository;
    }


    /*
    *if the feedbackResource couldn't find any feedbacks try to retreive them from Bamboo and store them in feedbacks and return these.
    *
    * @param resultId: the id of the result to whom the feedback belongs to
    * @return the newly saved result with the feedbacks
    */
    public Result retreiveBuildDetailsFromBambooAndStoreThem(Long resultId){
        Result result = resultRepository.findOne(resultId);
        Participation participation = result.getParticipation();

        Map buildDetails = continuousIntegrationService.get().getLatestBuildResultDetails(participation);
        HashSet<Feedback> feedbacks = continuousIntegrationService.get().createFeedbacksForResult(buildDetails);

        result.setFeedbacks(feedbacks);
        resultRepository.save(result);

        return result;
    }

}
