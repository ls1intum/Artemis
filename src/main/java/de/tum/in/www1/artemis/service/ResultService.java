package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.connectors.LtiNewResultService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ResultService {

    private final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final UserRepository userRepository;

    private final ResultRepository resultRepository;

    private final LtiNewResultService ltiNewResultService;

    private final WebsocketMessagingService websocketMessagingService;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final RatingRepository ratingRepository;

    private final FeedbackRepository feedbackRepository;

    private final SubmissionRepository submissionRepository;

    private final ComplaintRepository complaintRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseDateService exerciseDateService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public ResultService(UserRepository userRepository, ResultRepository resultRepository, LtiNewResultService ltiNewResultService, FeedbackRepository feedbackRepository,
            WebsocketMessagingService websocketMessagingService, ComplaintResponseRepository complaintResponseRepository, SubmissionRepository submissionRepository,
            ComplaintRepository complaintRepository, RatingRepository ratingRepository, ParticipantScoreRepository participantScoreRepository,
            AuthorizationCheckService authCheckService, ExerciseDateService exerciseDateService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.ltiNewResultService = ltiNewResultService;
        this.websocketMessagingService = websocketMessagingService;
        this.feedbackRepository = feedbackRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.submissionRepository = submissionRepository;
        this.complaintRepository = complaintRepository;
        this.ratingRepository = ratingRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.authCheckService = authCheckService;
        this.exerciseDateService = exerciseDateService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
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

        result.getFeedbacks().forEach(feedback -> feedback.setResult(result));

        // this call should cascade all feedback relevant changed and save them accordingly
        var savedResult = resultRepository.save(result);
        // The websocket client expects the submission and feedbacks, so we retrieve the result again instead of using the save result.
        savedResult = resultRepository.findByIdWithEagerSubmissionAndFeedbackElseThrow(result.getId());

        // if it is an example result we do not have any participation (isExampleResult can be also null)
        if (Boolean.FALSE.equals(savedResult.isExampleResult()) || savedResult.isExampleResult() == null) {

            if (savedResult.getParticipation() instanceof ProgrammingExerciseStudentParticipation) {
                ltiNewResultService.onNewResult((StudentParticipation) savedResult.getParticipation());
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
     *
     * @param result                      the result to delete
     * @param shouldClearParticipantScore determines whether the participant scores should be cleared. This should be true, if only one single result is deleted. If the whole participation or exercise is deleted, the participant scores have been deleted before and clearing is not necessary, then this value should be false
     */
    public void deleteResult(Result result, boolean shouldClearParticipantScore) {
        log.debug("Delete result {}", result.getId());
        complaintResponseRepository.deleteByComplaint_Result_Id(result.getId());
        complaintRepository.deleteByResult_Id(result.getId());
        ratingRepository.deleteByResult_Id(result.getId());
        if (shouldClearParticipantScore) {
            participantScoreRepository.clearAllByResultId(result.getId());
        }
        resultRepository.delete(result);
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

    /**
     * Store the given feedback to the passed result (by replacing all existing feedback) with a workaround for Hibernate exceptions.
     * <p>
     * With ordered collections (like result and feedback here), we have to be very careful with the way we persist the objects in the database.
     * We must first persist the child object without a relation to the parent object. Then, we recreate the association and persist the parent object.
     * <p>
     * If the result is not saved (shouldSave = false), the caller is responsible to save the result (which will persist the feedback changes as well)
     *
     * @param result           the result with should be saved with the given feedback
     * @param feedbackList     new feedback items which replace the existing feedback
     * @param shouldSave       whether the result should be saved or not
     * @return the updated (and potentially saved) result
     */
    public Result storeFeedbackInResult(@NotNull Result result, List<Feedback> feedbackList, boolean shouldSave) {
        var savedFeedbacks = saveFeedbackWithHibernateWorkaround(result, feedbackList);
        result.setFeedbacks(savedFeedbacks);
        return shouldSaveResult(result, shouldSave);
    }

    /**
     * Add the feedback to the passed result with a workaround for Hibernate exceptions.
     * <p>
     * With ordered collections (like result and feedback here), we have to be very careful with the way we persist the objects in the database.
     * We must first persist the child object without a relation to the parent object. Then, we recreate the association and persist the parent object.
     * <p>
     * If the result is not saved (shouldSave = false), the caller is responsible to save the result (which will persist the feedback changes as well)
     *
     * @param result           the result with should be saved with the given feedback
     * @param feedbackList     new feedback items which should be added to the feedback
     * @param shouldSave       whether the result should be saved or not
     * @return the updated (and potentially saved) result
     */
    @NotNull
    public Result addFeedbackToResult(@NotNull Result result, List<Feedback> feedbackList, boolean shouldSave) {
        List<Feedback> savedFeedbacks = saveFeedbackWithHibernateWorkaround(result, feedbackList);
        result.addFeedbacks(savedFeedbacks);
        return shouldSaveResult(result, shouldSave);
    }

    /**
     * Returns a list of feedbacks that is filtered for students depending on the settings and the time.
     *
     * @param result    the result for which the feedback elements should be returned
     * @return the list of filtered feedbacks
     */
    public List<Feedback> getFeedbacksForResult(Result result) {
        Exercise exercise = result.getParticipation().getExercise();
        boolean filterForStudent = !authCheckService.isAtLeastTeachingAssistantForExercise(exercise);

        List<Feedback> feedbacks = result.getFeedbacks();
        if (filterForStudent) {
            if (exercise.isExamExercise()) {
                Exam exam = exercise.getExerciseGroup().getExam();
                result.filterSensitiveFeedbacks(!exam.resultsPublished());
            }
            else {
                boolean applyFilter = exerciseDateService.isBeforeDueDate(result.getParticipation())
                        || (AssessmentType.AUTOMATIC.equals(result.getAssessmentType()) && exerciseDateService.isBeforeLatestDueDate(exercise));
                result.filterSensitiveFeedbacks(applyFilter);
            }
            feedbacks = result.getFeedbacks();

            boolean resultSetAndNonAutomatic = result.getAssessmentType() != null && result.getAssessmentType() != AssessmentType.AUTOMATIC;
            boolean dueDateNotSetOrNotOver = exercise.getAssessmentDueDate() != null && ZonedDateTime.now().isBefore(exercise.getAssessmentDueDate());

            // A tutor is allowed to access all feedback, but filter for a student the manual feedback if the assessment due date is not over yet
            if (!exercise.isExamExercise() && resultSetAndNonAutomatic && dueDateNotSetOrNotOver) {
                // filter all non-automatic feedbacks
                feedbacks = feedbacks.stream().filter(feedback -> feedback.getType() != null && feedback.getType() == FeedbackType.AUTOMATIC).toList();
            }
        }
        // remove unnecessary data to keep the json payload smaller
        for (Feedback feedback : feedbacks) {
            if (feedback.getResult() != null) {
                feedback.getResult().setSubmission(null);
                feedback.getResult().setParticipation(null);
            }
        }
        return feedbacks;
    }

    /**
     * Returns the matching template, solution or student participation for a given build plan key.
     * @param planKey the build plan key
     * @return the matching participation
     */
    @Nullable
    public ProgrammingExerciseParticipation getParticipationWithResults(String planKey) {
        // we have to support template, solution and student build plans here
        if (planKey.endsWith("-" + BuildPlanType.TEMPLATE.getName())) {
            return templateProgrammingExerciseParticipationRepository.findByBuildPlanIdWithResults(planKey).orElse(null);
        }
        else if (planKey.endsWith("-" + BuildPlanType.SOLUTION.getName())) {
            return solutionProgrammingExerciseParticipationRepository.findByBuildPlanIdWithResults(planKey).orElse(null);
        }
        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository.findByBuildPlanId(planKey);
        ProgrammingExerciseStudentParticipation participation = null;
        if (!participations.isEmpty()) {
            participation = participations.get(0);
            if (participations.size() > 1) {
                // in the rare case of multiple participations, take the latest one.
                for (ProgrammingExerciseStudentParticipation otherParticipation : participations) {
                    if (otherParticipation.getInitializationDate().isAfter(participation.getInitializationDate())) {
                        participation = otherParticipation;
                    }
                }
            }
        }
        return participation;
    }

    /**
     * Get the successful results for an exercise, ordered ascending by build completion date.
     *
     * @param exercise which the results belong to.
     * @param withSubmissions true, if each result should also contain the submissions.
     * @return a list of results as described above for the given exercise.
     */
    public List<Result> resultsForExercise(Exercise exercise, List<StudentParticipation> participations, boolean withSubmissions) {
        final List<Result> results = new ArrayList<>();

        for (StudentParticipation participation : participations) {
            // Filter out participations without students / teams
            if (participation.getParticipant() == null) {
                continue;
            }

            Submission relevantSubmissionWithResult = exercise.findLatestSubmissionWithRatedResultWithCompletionDate(participation, true);
            if (relevantSubmissionWithResult == null || relevantSubmissionWithResult.getLatestResult() == null) {
                continue;
            }

            participation.setSubmissionCount(participation.getSubmissions().size());
            if (withSubmissions) {
                relevantSubmissionWithResult.getLatestResult().setSubmission(relevantSubmissionWithResult);
            }
            results.add(relevantSubmissionWithResult.getLatestResult());
        }

        if (withSubmissions) {
            results.removeIf(result -> result.getSubmission() == null || !result.getSubmission().isSubmitted());
        }

        // remove unnecessary elements in the json response
        results.forEach(result -> {
            result.getParticipation().setResults(null);
            result.getParticipation().setSubmissions(null);
            result.getParticipation().setExercise(null);
        });

        return results;
    }

    /**
     * Returns the result for the given id with authorization checks.
     * @param participationId the id of the participation
     * @param resultId the id of the result
     * @param role the minimum role required to access the result
     * @return the result
     */
    public Result getResultForParticipationAndCheckAccess(Long participationId, Long resultId, Role role) {
        Result result = resultRepository.findByIdElseThrow(resultId);
        Participation participation = result.getParticipation();
        if (!participation.getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId of the path doesnt match the participationId of the participation corresponding to the result " + resultId + "!",
                    "Participation", "400");
        }
        Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, null);
        return result;
    }

    @NotNull
    private List<Feedback> saveFeedbackWithHibernateWorkaround(@NotNull Result result, List<Feedback> feedbackList) {
        // Avoid hibernate exception
        List<Feedback> savedFeedbacks = new ArrayList<>();
        feedbackList.forEach(feedback -> {
            // cut association to parent object
            feedback.setResult(null);
            // persist the child object without an association to the parent object.
            feedback = feedbackRepository.saveAndFlush(feedback);
            // restore the association to the parent object
            feedback.setResult(result);
            savedFeedbacks.add(feedback);
        });
        return savedFeedbacks;
    }

    @NotNull
    private Result shouldSaveResult(@NotNull Result result, boolean shouldSave) {
        if (shouldSave) {
            // Note: This also saves the feedback objects in the database because of the 'cascade = CascadeType.ALL' option.
            return resultRepository.save(result);
        }
        else {
            return result;
        }
    }
}
