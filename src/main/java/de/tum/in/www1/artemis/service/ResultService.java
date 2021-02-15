package de.tum.in.www1.artemis.service;

import static java.util.Arrays.asList;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ResultService {

    private final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final UserRepository userRepository;

    private final ResultRepository resultRepository;

    private final LtiService ltiService;

    private final ObjectMapper objectMapper;

    private final FeedbackRepository feedbackRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final RatingRepository ratingRepository;

    private final SubmissionRepository submissionRepository;

    private final ComplaintRepository complaintRepository;

    public ResultService(UserRepository userRepository, ResultRepository resultRepository, LtiService ltiService, ObjectMapper objectMapper, FeedbackRepository feedbackRepository,
            WebsocketMessagingService websocketMessagingService, ComplaintResponseRepository complaintResponseRepository, SubmissionRepository submissionRepository,
            ComplaintRepository complaintRepository, RatingRepository ratingRepository) {
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.ltiService = ltiService;
        this.objectMapper = objectMapper;
        this.feedbackRepository = feedbackRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.complaintResponseRepository = complaintResponseRepository;
        this.submissionRepository = submissionRepository;
        this.complaintRepository = complaintRepository;
        this.ratingRepository = ratingRepository;
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
     * @param withSubmission determines whether the submission should also be fetched
     * @return an optional result (might exist or not).
     */
    public Optional<Result> findLatestResultWithFeedbacksForParticipation(Long participationId, boolean withSubmission) {
        if (withSubmission) {
            return resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participationId);
        }
        else {
            return resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(participationId);
        }
    }

    /**
     * Get the latest results for each participation in an exercise from the database together with the list of feedback items.
     *
     * @param exerciseId the id of the exercise to load from the database
     * @return an list of results.
     */
    public List<Result> findLatestAutomaticResultsWithFeedbacksForExercise(Long exerciseId) {
        return resultRepository.findLatestAutomaticResultsWithEagerFeedbacksForExercise(exerciseId);
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
        User currentUser = userRepository.getUser();
        result.setAssessor(currentUser);
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

        User user = userRepository.getUserWithGroupsAndAuthorities();

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
        savedResult = findOneWithEagerSubmissionAndFeedback(result.getId());

        // if it is an example result we do not have any participation (isExampleResult can be also null)
        if (Boolean.FALSE.equals(savedResult.isExampleResult()) || savedResult.isExampleResult() == null) {

            if (savedResult.getParticipation() instanceof ProgrammingExerciseStudentParticipation) {
                ltiService.onNewResult((StudentParticipation) savedResult.getParticipation());
            }

            websocketMessagingService.broadcastNewResult(savedResult.getParticipation(), savedResult);
        }
        return savedResult;
    }

    public Result createNewRatedManualResult(Result result, boolean isProgrammingExerciseWithFeedback) {
        return createNewManualResult(result, isProgrammingExerciseWithFeedback, true);
    }

    /**
     * NOTE: As we use delete methods with underscores, we need a transactional context here!
     * Deletes result with corresponding complaint and complaint response
     * @param resultId the id of the result
     */
    @Transactional // ok
    public void deleteResultWithComplaint(long resultId) {
        complaintResponseRepository.deleteByComplaint_Result_Id(resultId);
        complaintRepository.deleteByResult_Id(resultId);
        ratingRepository.deleteByResult_Id(resultId);
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
    public DueDateStat countNumberOfAssessments(Long courseId) {
        return new DueDateStat(resultRepository.countByAssessorIsNotNullAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(courseId, true),
                resultRepository.countByAssessorIsNotNullAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(courseId, false));
    }

    /**
     * Calculates the number of assessments done for each correction round.
     *
     * @param exercise the exercise for which we want to calculate the # of assessments for each correction round
     * @param examMode states whether or not the the function is called in the exam mode
     * @param totalNumberOfAssessments so total number of assessments sum up over all correction rounds
     * @return the number of assessments for each correction rounds
     */
    public DueDateStat[] calculateNrOfAssessmentsOfCorrectionRoundsForDashboard(Exercise exercise, boolean examMode, DueDateStat totalNumberOfAssessments) {
        DueDateStat[] numberOfAssessmentsOfCorrectionRounds;
        if (examMode) {
            // set number of corrections specific to each correction round
            int numberOfCorrectionRounds = exercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam();
            numberOfAssessmentsOfCorrectionRounds = countNumberOfFinishedAssessmentsForExerciseForCorrectionRound(exercise, numberOfCorrectionRounds);
        }
        else {
            // no examMode here, so correction rounds defaults to 1 and is the same as totalNumberOfAssessments
            numberOfAssessmentsOfCorrectionRounds = new DueDateStat[] { totalNumberOfAssessments };
        }
        return numberOfAssessmentsOfCorrectionRounds;
    }

    /**
     * Given an exerciseId, return the number of assessments for that exerciseId that have been completed (e.g. no draft!)
     *
     * @param exerciseId - the exercise we are interested in
     * @param examMode should be used for exam exercises to ignore test run submissions
     * @return a number of assessments for the exercise
     */
    public DueDateStat countNumberOfFinishedAssessmentsForExercise(Long exerciseId, boolean examMode) {
        if (examMode) {
            return new DueDateStat(resultRepository.countNumberOfFinishedAssessmentsForExerciseIgnoreTestRuns(exerciseId), 0L);
        }
        return new DueDateStat(resultRepository.countNumberOfFinishedAssessmentsForExercise(exerciseId), 0L);
    }

    /**
     * Given an exerciseId and a correctionRound, return the number of assessments for that exerciseId and correctionRound that have been finished
     *
     * @param exercise  - the exercise we are interested in
     * @param correctionRounds - the correction round we want finished assessments for
     * @return an array of the number of assessments for the exercise for a given correction round
     */
    public DueDateStat[] countNumberOfFinishedAssessmentsForExerciseForCorrectionRound(Exercise exercise, int correctionRounds) {
        DueDateStat[] correctionRoundsDataStats = new DueDateStat[correctionRounds];

        for (int i = 0; i < correctionRounds; i++) {
            correctionRoundsDataStats[i] = new DueDateStat(resultRepository.countNumberOfFinishedAssessmentsByCorrectionRoundsAndExerciseIdIgnoreTestRuns(exercise.getId(), i), 0L);
        }

        return correctionRoundsDataStats;
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
    public DueDateStat countNumberOfAutomaticAssistedAssessmentsForExercise(Long exerciseId) {
        return new DueDateStat(resultRepository.countNumberOfAssessmentsByTypeForExerciseBeforeDueDate(exerciseId, asList(AssessmentType.AUTOMATIC, AssessmentType.SEMI_AUTOMATIC)),
                resultRepository.countNumberOfAssessmentsByTypeForExerciseAfterDueDate(exerciseId, asList(AssessmentType.AUTOMATIC, AssessmentType.SEMI_AUTOMATIC)));
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

    public void notifyUserAboutNewResult(Result result, Participation participation) {
        notifyNewResult(result, participation);
    }

    private void notifyNewResult(Result result, Participation participation) {
        websocketMessagingService.broadcastNewResult(participation, result);
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
