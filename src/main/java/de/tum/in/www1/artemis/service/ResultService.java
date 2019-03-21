package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Created by Josias Montag on 06.10.16.
 */
@Service
public class ResultService {

    private final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final UserService userService;
    private final ParticipationService participationService;
    private final ResultRepository resultRepository;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final LtiService ltiService;
    private final SimpMessageSendingOperations messagingTemplate;


    public ResultService(UserService userService, ParticipationService participationService, ResultRepository resultRepository, Optional<ContinuousIntegrationService> continuousIntegrationService, LtiService ltiService, SimpMessageSendingOperations messagingTemplate) {
        this.userService = userService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.ltiService = ltiService;
        this.messagingTemplate = messagingTemplate;
    }


    public Result findOne(long id) {
        log.debug("Request to get Result: {}", id);
        return resultRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Result with id: \"" + id + "\" does not exist"));
    }

    public Result findOneWithSubmission(long id) {
        log.debug("Request to get Result: {}", id);
        return resultRepository.findByIdWithSubmission(id)
            .orElseThrow(() -> new EntityNotFoundException("Result with id: \"" + id + "\" does not exist"));
    }


    /**
     * Sets the assessor field of the given result with the current user and stores these changes to the database.
     * The User object set as assessor gets Groups and Authorities eagerly loaded.
     * @param result
     */
    public void setAssessor(Result result){
        User currentUser = userService.getUserWithGroupsAndAuthorities();
        result.setAssessor(currentUser);
        resultRepository.save(result);
        log.debug("Assessment locked with result id: " + result.getId() + " for assessor: " + result.getAssessor().getFirstName());
    }

    /**
     * Perform async operations after we were notified about new results.
     *
     * @param participation Participation for which a new build is available
     */
    @Async
    @Deprecated
    public void onResultNotifiedOld(Participation participation) {
        log.debug("Received new build result for participation " + participation.getId());
        // fetches the new build result
        Result result = continuousIntegrationService.get().onBuildCompletedOld(participation);
        notifyUser(participation, result);
    }

    /**
     * Use the given requestBody to extract the relevant information from it
     * @param participation Participation for which the build was finished
     * @param requestBody RequestBody containing all relevant information
     */
    public void onResultNotifiedNew(Participation participation, Object requestBody) throws Exception {
        log.info("Received new build result (NEW) for participation " + participation.getId());

        Result result = continuousIntegrationService.get().onBuildCompletedNew(participation, requestBody);
        notifyUser(participation, result);
    }

    private void notifyUser(Participation participation, Result result) {
        if (result != null) {
            // notify user via websocket
            messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", result);

            //TODO: can we avoid to invoke this code for non LTI students? (to improve performance)
//            if (participation.isLti()) {
//            }
            // handles new results and sends them to LTI consumers
            ltiService.onNewBuildResult(participation);
        }
    }


    /**
     * Handle the manual creation of a new result potentially including feedback
     *
     * @param result
     */
    public void createNewResult(Result result, boolean isProgrammingExerciseWithFeedback) {
        if (!result.getFeedbacks().isEmpty()) {
            result.setHasFeedback(isProgrammingExerciseWithFeedback);
        }

        //TODO: in this case we do not have a submission. However, it would be good to create one, even if it might be "empty"
        User user = userService.getUserWithGroupsAndAuthorities();

        result.setAssessmentType(AssessmentType.MANUAL);
        result.setAssessor(user);

        //manual feedback is always rated
        result.setRated(true);

        result.getFeedbacks().forEach(feedback -> {
            feedback.setResult(result);
        });

        // this call should cascade all feedback relevant changed and save them accordingly
        Result savedResult = resultRepository.save(result);

        // if it is an example result we do not have any participation (isExampleResult can be also null)
        if (result.isExampleResult() == Boolean.FALSE) {
            try {
                result.getParticipation().addResult(savedResult);
                participationService.save(result.getParticipation());
            } catch (NullPointerException ex) {
                log.warn("Unable to load result list for participation", ex);
            }

            messagingTemplate.convertAndSend("/topic/participation/" + result.getParticipation().getId() + "/newResults", result);
            ltiService.onNewBuildResult(savedResult.getParticipation());
        }
    }

    public List<Result> findByCourseId(Long courseId) {
        return resultRepository.findAllByParticipation_Exercise_CourseId(courseId);
    }
}
