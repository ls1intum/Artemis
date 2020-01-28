package de.tum.in.www1.artemis.service;

import static java.util.Arrays.asList;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.*;
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

    private final ResultRepository resultRepository;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final LtiService ltiService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final ObjectMapper objectMapper;

    private final ProgrammingExerciseTestCaseService testCaseService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final FeedbackRepository feedbackRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final SubmissionRepository submissionRepository;

    private final ComplaintRepository complaintRepository;

    public ResultService(UserService userService, ResultRepository resultRepository, Optional<ContinuousIntegrationService> continuousIntegrationService, LtiService ltiService,
            SimpMessageSendingOperations messagingTemplate, ObjectMapper objectMapper, ProgrammingExerciseTestCaseService testCaseService,
            ProgrammingSubmissionService programmingSubmissionService, FeedbackRepository feedbackRepository, WebsocketMessagingService websocketMessagingService,
            ComplaintResponseRepository complaintResponseRepository, SubmissionRepository submissionRepository, ComplaintRepository complaintRepository) {
        this.userService = userService;
        this.resultRepository = resultRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.ltiService = ltiService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.testCaseService = testCaseService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.feedbackRepository = feedbackRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.complaintResponseRepository = complaintResponseRepository;
        this.submissionRepository = submissionRepository;
        this.complaintRepository = complaintRepository;
    }

    /**
     * Get a result from the database by its id,
     *
     * @param id the id of the result to load from the database
     * @return the result
     */
    public Result findOne(long id) {
        log.debug("Request to get Result: {}", id);
        return resultRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Result with id: \"" + id + "\" does not exist"));
    }

    /**
     * Get a result from the database by its id together with the associated submission and the list of feedback items.
     *
     * @param resultId the id of the result to load from the database
     * @return the result with submission and feedback list
     */
    public Result findOneWithEagerSubmissionAndFeedback(long resultId) {
        log.debug("Request to get Result: {}", resultId);
        return resultRepository.findWithEagerSubmissionAndFeedbackById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("Result with id: \"" + resultId + "\" does not exist"));
    }

    /**
     * Get the latest result from the database by participation id together with the list of feedback items.
     *
     * @param participationId the id of the participation to load from the database
     * @return an optional result (might exist or not).
     */
    public Optional<Result> findLatestResultWithFeedbacksForParticipation(Long participationId) {
        return resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(participationId);
    }

    /**
     * Check if there is a result for the given participation.
     *
     * @param participationId the id of the participation for which to check if there is a result.
     * @return true if there is a result for the given participation, otherwise not.
     */
    public Boolean existsByParticipationId(Long participationId) {
        return resultRepository.existsByParticipationId(participationId);
    }

    /**
     * Sets the assessor field of the given result with the current user and stores these changes to the database. The User object set as assessor gets Groups and Authorities
     * eagerly loaded.
     * 
     * @param result Result for which current user is set as an assessor
     */
    public void setAssessor(Result result) {
        User currentUser = userService.getUser();
        result.setAssessor(currentUser);
    }

    // TODO: We should think about moving this method to a separate ProgrammingResultService as it can be confusing that this functionality is exclusive for programming exercises.
    /**
     * Use the given requestBody to extract the relevant information from it. Fetch and attach the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation the participation for which the build was finished
     * @param requestBody   RequestBody containing the build result and its feedback items
     * @return result after compilation
     */
    public Optional<Result> processNewProgrammingExerciseResult(@NotNull Participation participation, @NotNull Object requestBody) {
        log.debug("Received new build result (NEW) for participation " + participation.getId());

        if (!(participation instanceof ProgrammingExerciseParticipation))
            throw new EntityNotFoundException("Participation with id " + participation.getId() + " is not a programming exercise participation!");

        Result result;
        try {
            result = continuousIntegrationService.get().onBuildCompletedNew((ProgrammingExerciseParticipation) participation, requestBody);
        }
        catch (Exception ex) {
            log.error("Result for participation " + participation.getId() + " could not be created due to the following exception: " + ex);
            return Optional.empty();
        }

        if (result != null) {
            ProgrammingExercise programmingExercise = (ProgrammingExercise) participation.getExercise();
            boolean isSolutionParticipation = participation instanceof SolutionProgrammingExerciseParticipation;
            boolean isTemplateParticipation = participation instanceof TemplateProgrammingExerciseParticipation;
            // Find out which test cases were executed and calculate the score according to their status and weight.
            // This needs to be done as some test cases might not have been executed.
            // When the result is from a solution participation , extract the feedback items (= test cases) and store them in our database.
            if (isSolutionParticipation) {
                extractTestCasesFromResult(programmingExercise, result);
            }
            result = testCaseService.updateResultFromTestCases(result, programmingExercise, !isSolutionParticipation && !isTemplateParticipation);
            result = resultRepository.save(result);
            // workaround to prevent that result.submission suddenly turns into a proxy and cannot be used any more later after returning this method

            // If the solution participation was updated, also trigger the template participation build.
            if (isSolutionParticipation) {
                // This method will return without triggering the build if the submission is not of type TEST.
                triggerTemplateBuildIfTestCasesChanged(programmingExercise.getId(), result.getId());
            }
        }
        return Optional.ofNullable(result);
    }

    /**
     * Trigger the build of the template repository, if the submission of the provided result is of type TEST.
     * Will use the commitHash of the submission for triggering the template build.
     *
     * If the submission of the provided result is not of type TEST, the method will return without triggering the build.
     *
     * @param programmingExerciseId ProgrammingExercise id that belongs to the result.
     * @param resultId              Result id.
     */
    private void triggerTemplateBuildIfTestCasesChanged(long programmingExerciseId, long resultId) {
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.findByResultId(resultId);
        }
        catch (EntityNotFoundException ex) {
            // This is an unlikely error that would mean that no submission could be created for the result. In this case we can only log and abort.
            log.error("Could not trigger the build of the template repository for the programming exercise id " + programmingExerciseId
                    + " because no submission could be found for the provided result id " + resultId);
            return;
        }
        // We only trigger the template build when the test repository was changed.
        if (!submission.getType().equals(SubmissionType.TEST)) {
            return;
        }
        // We use the last commitHash of the test repository.
        ObjectId testCommitHash = ObjectId.fromString(submission.getCommitHash());
        try {
            programmingSubmissionService.triggerTemplateBuildAndNotifyUser(programmingExerciseId, testCommitHash, SubmissionType.TEST);
        }
        catch (EntityNotFoundException ex) {
            // If for some reason the programming exercise does not have a template participation, we can only log and abort.
            log.error("Could not trigger the build of the template repository for the programming exercise id " + programmingExerciseId
                    + " because no template participation could be found for the given exercise");
        }
    }

    /**
     * Generates test cases from the given result's feedbacks & notifies the subscribing users about the test cases if they have changed. Has the side effect of sending a message
     * through the websocket!
     *
     * @param exercise the programming exercise for which the test cases should be extracted from the new result
     * @param result   from which to extract the test cases.
     */
    private void extractTestCasesFromResult(ProgrammingExercise exercise, Result result) {
        boolean haveTestCasesChanged = testCaseService.generateTestCasesFromFeedbacks(result.getFeedbacks(), exercise);
        if (haveTestCasesChanged) {
            // Notify the client about the updated testCases
            Set<ProgrammingExerciseTestCase> testCases = testCaseService.findByExerciseId(exercise.getId());
            messagingTemplate.convertAndSend("/topic/programming-exercise/" + exercise.getId() + "/test-cases", testCases);
        }
    }

    /**
     * Update a manual result of a programming exercise.
     * Makes sure that the feedback items are persisted correctly, taking care of the OrderingColumn attribute of result.feedbacks.
     * See https://stackoverflow.com/questions/6763329/ordercolumn-onetomany-null-index-column-for-collection and inline doc for reference.
     *
     * Also informs the client using a websocket about the updated result.
     *
     * @param result Result.
     * @return updated result with eagerly loaded Submission and Feedback items.
     */
    public Result updateManualProgrammingExerciseResult(Result result) {
        // This is a workaround for saving a result with new feedbacks.
        // The issue seems to be that result.feedbacks is both a OneToMany relationship + has a the OrderColumn annotation.
        // Without this a 'null index column for collection' error is triggered when trying to save the result.
        if (result.getId() != null) {
            // This creates a null value in the feedbacks_order column, when the result is saved below, it is filled with the next available number (e.g. last item was 2, next is
            // 3).
            List<Feedback> savedFeedbackItems = feedbackRepository.saveAll(result.getFeedbacks());
            result.setFeedbacks(savedFeedbackItems);
        }
        return createNewRatedManualResult(result, true);
    }

    /**
     * Handle the manual creation of a new result potentially including feedback
     *
     * @param result newly created Result
     * @param isProgrammingExerciseWithFeedback defines if the programming exercise contains feedback
     * @param ratedResult override value for rated property of result
     *
     * @return updated result with eagerly loaded Submission and Feedback items.
     */
    public Result createNewManualResult(Result result, boolean isProgrammingExerciseWithFeedback, boolean ratedResult) {
        if (!result.getFeedbacks().isEmpty()) {
            result.setHasFeedback(isProgrammingExerciseWithFeedback);
        }

        User user = userService.getUserWithGroupsAndAuthorities();

        result.setAssessmentType(AssessmentType.MANUAL);
        result.setAssessor(user);
        result.setCompletionDate(ZonedDateTime.now());

        // manual feedback is always rated, can be overwritten though in the case of a result for an external submission
        result.setRated(ratedResult);

        result.getFeedbacks().forEach(feedback -> {
            feedback.setResult(result);
        });

        // this call should cascade all feedback relevant changed and save them accordingly
        var savedResult = resultRepository.save(result);
        // The websocket client expects the submission and feedbacks, so we retrieve the result again instead of using the save result.
        Optional<Result> savedResultOpt = resultRepository.findWithEagerSubmissionAndFeedbackById(result.getId());
        if (savedResultOpt.isEmpty()) {
            throw new EntityNotFoundException("Could not retrieve result with id " + result.getId() + " after save.");
        }
        savedResult = savedResultOpt.get();

        // if it is an example result we do not have any participation (isExampleResult can be also null)
        if (savedResult.isExampleResult() == Boolean.FALSE || savedResult.isExampleResult() == null) {

            if (savedResult.getParticipation() instanceof ProgrammingExerciseStudentParticipation) {
                ltiService.onNewBuildResult((ProgrammingExerciseStudentParticipation) savedResult.getParticipation());
            }

            websocketMessagingService.broadcastNewResult(savedResult.getParticipation(), savedResult);
        }
        return savedResult;
    }

    public Result createNewRatedManualResult(Result result, boolean isProgrammingExerciseWithFeedback) {
        return createNewManualResult(result, isProgrammingExerciseWithFeedback, true);
    }

    /**
     * NOTE: As we use delete methods with underscores, we need a transacational context here!
     * Deletes result with corresponding complaint and complaint response
     * @param resultId the id of the result
     */
    @Transactional // ok
    public void deleteResultWithComplaint(long resultId) {
        complaintResponseRepository.deleteByComplaint_Result_Id(resultId);
        complaintRepository.deleteByResult_Id(resultId);
        resultRepository.deleteById(resultId);
    }

    /**
     * Get a course from the database by its id.
     *
     * @param courseId the id of the course to load from the database
     * @return the course
     */
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
    public long countNumberOfAssessmentsForTutor(Long courseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(tutorId, courseId, true);
    }

    /**
     * Given an exerciseId, return the number of assessments for that exerciseId that have been completed (e.g. no draft!)
     *
     * @param exerciseId - the exercise we are interested in
     * @return a number of assessments for the exercise
     */
    public long countNumberOfFinishedAssessmentsForExercise(Long exerciseId) {
        return resultRepository.countNumberOfFinishedAssessmentsForExercise(exerciseId);
    }

    /**
     * Given a exerciseId and a tutorId, return the number of assessments for that exercise written by that tutor that have been completed (e.g. no draft!)
     *
     * @param exerciseId - the exercise we are interested in
     * @param tutorId    - the tutor we are interested in
     * @return a number of assessments for the exercise
     */
    public long countNumberOfAssessmentsForTutorInExercise(Long exerciseId, Long tutorId) {
        return resultRepository.countByAssessor_IdAndParticipation_ExerciseIdAndRatedAndCompletionDateIsNotNull(tutorId, exerciseId, true);
    }

    /**
     * Calculate the number of assessments which are either AUTOMATIC or SEMI_AUTOMATIC for a given exercise
     *
     * @param exerciseId the exercise we are interested in
     * @return number of assessments for the exercise
     */
    public Long countNumberOfAutomaticAssistedAssessmentsForExercise(Long exerciseId) {
        return resultRepository.countByAssessorIsNotNullAndParticipation_ExerciseIdAndRatedAndAssessmentTypeInAndCompletionDateIsNotNull(exerciseId, true,
                asList(AssessmentType.AUTOMATIC, AssessmentType.SEMI_AUTOMATIC));
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

    public void notifyUserAboutNewResult(Result result, Long participationId) {
        notifyNewResult(result, participationId);
    }

    private void notifyNewResult(Result result, Long participationId) {
        websocketMessagingService.sendMessage("/topic/participation/" + participationId + "/newResults", result);
    }

    /**
     * Create a new example result for the provided submission ID.
     *
     * @param submissionId The ID of the submission (that is connected to an example submission) for which a result should get created
     * @param isProgrammingExerciseWithFeedback defines if the programming exercise contains feedback
     * @return The newly created (and empty) example result
     */
    public Result createNewExampleResultForSubmissionWithExampleSubmission(long submissionId, boolean isProgrammingExerciseWithFeedback) {
        final var submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("No example submission with ID " + submissionId + " found!"));
        if (!submission.isExampleSubmission()) {
            throw new IllegalArgumentException("Submission is no example submission! Example results are not allowed!");
        }

        final var newResult = new Result();
        newResult.setSubmission(submission);
        newResult.setExampleResult(true);
        return createNewRatedManualResult(newResult, isProgrammingExerciseWithFeedback);
    }
}
