package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Feedback;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.FeedbackRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    @Transactional
    public Result retreiveBuildDetailsFromBambooAndStoreThem(Result result){
        Participation participation = result.getParticipation();

        Map buildDetails = continuousIntegrationService.get().getLatestBuildResultDetails(participation);
        HashSet<Feedback> feedbacks = continuousIntegrationService.get().createFeedbacksForResult(buildDetails);

        result.setFeedbacks(feedbacks);
        resultRepository.save(result);

        return result;
    }

    /**
     *converting a set of feedbacks into a Json object which transforms them into the original bamboo format
     *
     * @param feedbacks the set of feedbacks of a result
     * @return a JSON object containing only the key details, which hold all feedbacks in the original format of bamboo
     */
    private JSONObject convertFeedbacksToBambooServiceFormat(Set<Feedback> feedbacks){
        if(feedbacks == null){
            return null;
        }

        try {
            JSONArray details = new JSONArray();
            for (Feedback feedback : feedbacks) {

                //Creating all error messages
                JSONArray error = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("message", feedback.getDetailText());
                error.put(message);

                //put error messages in the error system
                JSONObject errors = new JSONObject();
                errors.put("size", 1);
                errors.put("error", error);
                errors.put("start-index", 0);
                errors.put("max-result", 1);

                //create the JsonObject for one feedback
                JSONObject detail = new JSONObject();
                detail.put("expand", "errors");
                detail.put("className", feedback.getText().split(" : ")[0]);
                detail.put("methodName", feedback.getText().split(" : ")[1]);
                detail.put("status", "failed");
                detail.put("duration", 0);
                detail.put("durationInSeconds", 0);
                detail.put("errors", errors);


                details.put(detail);
            }

            JSONObject detailsCombined = new JSONObject();
            detailsCombined.put("details", details);

            return detailsCombined;
        }catch(Exception e){

        }
        return null;
    }

    /**
     *Checking if the result already has feedbacks if not try retreiving them from bamboo and create them. Having feedbacks create
     * a JSONObject which will be converted into the bamboo format to fit the already used frontend system
     *
     * @param result for which the feedback is supposed to be retreived
     * @return a JSONObject which contains only the key details, which holds all feedbacks in the original bamboo format
     */
    @Transactional
    public JSONObject reteiveFeedbackForResultBuild(Result result){

        Set<Feedback> feedbacks = result.getFeedbacks();

        //Provide access to results with no feedback
        if(feedbacks == null || feedbacks.size() == 0){
            result = retreiveBuildDetailsFromBambooAndStoreThem(result);

            if(result.getFeedbacks() != null) {
                feedbacks = new HashSet<>(result.getFeedbacks());
            }else{
                feedbacks = null;
            }
        }

        return convertFeedbacksToBambooServiceFormat(feedbacks);
    }

}
