package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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

    private final ObjectMapper objectMapper;

    private final ProgrammingExerciseTestCaseService testCaseService;

    public ResultService(UserService userService, ParticipationService participationService, ResultRepository resultRepository,
            Optional<ContinuousIntegrationService> continuousIntegrationService, LtiService ltiService, SimpMessageSendingOperations messagingTemplate, ObjectMapper objectMapper,
            ProgrammingExerciseTestCaseService testCaseService) {
        this.userService = userService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.ltiService = ltiService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.testCaseService = testCaseService;
    }

    public Result findOne(long id) {
        log.debug("Request to get Result: {}", id);
        return resultRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Result with id: \"" + id + "\" does not exist"));
    }

    public Result findOneWithEagerFeedbacks(long id) {
        log.debug("Request to get Result: {}", id);
        return resultRepository.findByIdWithEagerFeedbacks(id).orElseThrow(() -> new EntityNotFoundException("Result with id: \"" + id + "\" does not exist"));
    }

    public Result findOneWithSubmission(long id) {
        log.debug("Request to get Result: {}", id);
        return resultRepository.findByIdWithSubmission(id).orElseThrow(() -> new EntityNotFoundException("Result with id: \"" + id + "\" does not exist"));
    }

    /**
     * Sets the assessor field of the given result with the current user and stores these changes to the database. The User object set as assessor gets Groups and Authorities
     * eagerly loaded.
     * 
     * @param result
     */
    public void setAssessor(Result result) {
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
    public void onResultNotifiedOld(ProgrammingExerciseParticipation participation) {
        log.debug("Received new build result for participation " + participation.getId());
        // fetches the new build result
        Result result = continuousIntegrationService.get().onBuildCompletedOld(participation);
        notifyUser(participation, result);
    }

    /**
     * Use the given requestBody to extract the relevant information from it. Fetch and attach the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation Participation for which the build was finished
     * @param requestBody   RequestBody containing the build result and its feedback items
     */
    public void onResultNotifiedNew(ProgrammingExerciseParticipation participation, Object requestBody) throws Exception {
        log.info("Received new build result (NEW) for participation " + participation.getId());

        Result result = continuousIntegrationService.get().onBuildCompletedNew(participation, requestBody);

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        // When the result is from a solution participation , extract the feedback items (= test cases) and store them in our database.
        if (result != null && programmingExercise.isParticipationSolutionParticipationOfThisExercise(participation)) {
            extractTestCasesFromResult(participation, result);
        }
        // Find out which test cases were executed and calculate the score according to their status and weight.
        // This needs to be done as some test cases might not have been executed.
        result = testCaseService.updateResultFromTestCases(result, programmingExercise);

        notifyUser(participation, result);
    }

    /**
     * Generates test cases from the given result's feedbacks & notifies the subscribing users about the test cases if they have changed. Has the side effect of sending a message
     * through the websocket!
     *
     * @param participation of the given result.
     * @param result        from which to extract the test cases.
     */
    private void extractTestCasesFromResult(ProgrammingExerciseParticipation participation, Result result) {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        boolean haveTestCasesChanged = testCaseService.generateTestCasesFromFeedbacks(result.getFeedbacks(), programmingExercise);
        if (haveTestCasesChanged) {
            // Notify the client about the updated testCases
            Set<ProgrammingExerciseTestCase> testCases = testCaseService.findByExerciseId(participation.getProgrammingExercise().getId());
            messagingTemplate.convertAndSend("/topic/programming-exercise/" + participation.getProgrammingExercise().getId() + "/test-cases", testCases);
        }
    }

    private void notifyUser(ProgrammingExerciseParticipation participation, Result result) {
        if (result != null) {
            // notify user via websocket
            messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", result);

            // TODO: can we avoid to invoke this code for non LTI students? (to improve performance)
            // if (participation.isLti()) {
            // }
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

        // TODO: in this case we do not have a submission. However, it would be good to create one, even if it might be "empty"
        User user = userService.getUserWithGroupsAndAuthorities();

        result.setAssessmentType(AssessmentType.MANUAL);
        result.setAssessor(user);

        // manual feedback is always rated
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
            }
            catch (NullPointerException ex) {
                log.warn("Unable to load result list for participation", ex);
            }

            messagingTemplate.convertAndSend("/topic/participation/" + result.getParticipation().getId() + "/newResults", result);
            ltiService.onNewBuildResult((ProgrammingExerciseParticipation) savedResult.getParticipation());
        }
    }

    public List<Result> findByCourseId(Long courseId) {
        return resultRepository.findAllByParticipation_Exercise_CourseId(courseId);
    }

    /**
     * Given a courseId, return the number of assessments for that course that have been completed (e.g. no draft!)
     *
     * @param courseId - the course we are interested in
     * @return a number of assessments for the course
     */
    public long countNumberOfAssessments(Long courseId) {
        return resultRepository.countByAssessorIsNotNullAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(courseId, true);
    }

    /**
     * Given a courseId and a tutorId, return the number of assessments for that course written by that tutor that have been completed (e.g. no draft!)
     *
     * @param courseId - the course we are interested in
     * @param tutorId  - the tutor we are interested in
     * @return a number of assessments for the course
     */
    @Transactional(readOnly = true)
    public long countNumberOfAssessmentsForTutor(Long courseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(tutorId, courseId, true);
    }

    /**
     * Given an exerciseId, return the number of assessments for that exerciseId that have been completed (e.g. no draft!)
     *
     * @param exerciseId - the exercise we are interested in
     * @return a number of assessments for the exercise
     */
    @Transactional(readOnly = true)
    public long countNumberOfAssessmentsForExercise(Long exerciseId) {
        return resultRepository.countByAssessorIsNotNullAndParticipation_ExerciseIdAndRatedAndCompletionDateIsNotNull(exerciseId, true);
    }

    /**
     * Given a exerciseId and a tutorId, return the number of assessments for that exercise written by that tutor that have been completed (e.g. no draft!)
     *
     * @param exerciseId - the exercise we are interested in
     * @param tutorId    - the tutor we are interested in
     * @return a number of assessments for the exercise
     */
    @Transactional(readOnly = true)
    public long countNumberOfAssessmentsForTutorInExercise(Long exerciseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_ExerciseIdAndRatedAndCompletionDateIsNotNull(tutorId, exerciseId, true);
    }

    /**
     * Creates a copy of the given original result with all properties except for the participation and submission and converts it to a JSON string. This method is used for storing
     * the original result of a submission before updating the result due to a complaint.
     *
     * @param originalResult the original result that was complained about
     * @return the reduced result as a JSON string
     * @throws JsonProcessingException when the conversion to JSON string fails
     */
    public String getOriginalResultAsString(Result originalResult) throws JsonProcessingException {
        Result resultCopy = new Result();
        resultCopy.setId(originalResult.getId());
        resultCopy.setResultString(originalResult.getResultString());
        resultCopy.setCompletionDate(originalResult.getCompletionDate());
        resultCopy.setSuccessful(originalResult.isSuccessful());
        resultCopy.setScore(originalResult.getScore());
        resultCopy.setRated(originalResult.isRated());
        resultCopy.hasFeedback(originalResult.getHasFeedback());
        resultCopy.setFeedbacks(originalResult.getFeedbacks());
        resultCopy.setAssessor(originalResult.getAssessor());
        resultCopy.setAssessmentType(originalResult.getAssessmentType());

        Optional<Boolean> hasComplaint = originalResult.getHasComplaint();
        if (hasComplaint.isPresent()) {
            resultCopy.setHasComplaint(originalResult.getHasComplaint().get());
        }
        else {
            resultCopy.setHasComplaint(false);
        }

        return objectMapper.writeValueAsString(resultCopy);
    }
}
