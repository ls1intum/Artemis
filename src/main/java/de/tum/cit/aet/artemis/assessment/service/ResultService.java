package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackAffectedStudentDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackAnalysisResponseDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDetailDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackPageableDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.buildagent.dto.ResultBuildJob;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.NameSimilarity;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionFilterService;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseNamesDTO;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final UserRepository userRepository;

    private final ResultRepository resultRepository;

    private final Optional<LtiApi> ltiApi;

    private final ResultWebsocketService resultWebsocketService;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final RatingRepository ratingRepository;

    private final FeedbackRepository feedbackRepository;

    private final ComplaintRepository complaintRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseDateService exerciseDateService;

    private final Optional<StudentExamApi> studentExamApi;

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    private final BuildJobRepository buildJobRepository;

    private final BuildLogEntryService buildLogEntryService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private static final int MAX_FEEDBACK_IDS = 5;

    private static final double SIMILARITY_THRESHOLD = 0.7;

    private final SubmissionFilterService submissionFilterService;

    public ResultService(UserRepository userRepository, ResultRepository resultRepository, Optional<LtiApi> ltiApi, ResultWebsocketService resultWebsocketService,
            ComplaintResponseRepository complaintResponseRepository, RatingRepository ratingRepository, FeedbackRepository feedbackRepository,
            LongFeedbackTextRepository longFeedbackTextRepository, ComplaintRepository complaintRepository, ParticipantScoreRepository participantScoreRepository,
            AuthorizationCheckService authCheckService, ExerciseDateService exerciseDateService, Optional<StudentExamApi> studentExamApi, BuildJobRepository buildJobRepository,
            BuildLogEntryService buildLogEntryService, StudentParticipationRepository studentParticipationRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            ProgrammingExerciseRepository programmingExerciseRepository, SubmissionFilterService submissionFilterService) {
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.ltiApi = ltiApi;
        this.resultWebsocketService = resultWebsocketService;
        this.complaintResponseRepository = complaintResponseRepository;
        this.ratingRepository = ratingRepository;
        this.feedbackRepository = feedbackRepository;
        this.longFeedbackTextRepository = longFeedbackTextRepository;
        this.complaintRepository = complaintRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.authCheckService = authCheckService;
        this.exerciseDateService = exerciseDateService;
        this.studentExamApi = studentExamApi;
        this.buildJobRepository = buildJobRepository;
        this.buildLogEntryService = buildLogEntryService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionFilterService = submissionFilterService;
    }

    /**
     * Handle the manual creation of a new result potentially including feedback
     *
     * @param result      newly created Result
     * @param ratedResult override value for rated property of result
     * @return updated result with eagerly loaded Submission and Feedback items.
     */
    public Result createNewManualResult(Result result, boolean ratedResult) {
        User user = userRepository.getUserWithGroupsAndAuthorities();

        result.setAssessmentType(AssessmentType.MANUAL);
        result.setAssessor(user);
        result.setCompletionDate(ZonedDateTime.now());

        // manual feedback is always rated, can be overwritten though in the case of a result for an external submission
        result.setRated(ratedResult);

        result.getFeedbacks().forEach(feedback -> feedback.setResult(result));

        // this call should cascade all feedback relevant changed and save them accordingly
        resultRepository.save(result);
        // The websocket client expects the submission and feedbacks, so we retrieve the result again instead of using the save result.
        var savedResult = resultRepository.findWithSubmissionAndFeedbackAndTeamStudentsByIdElseThrow(result.getId());

        // if it is an example result we do not have any participation (isExampleResult can be also null)
        if (Boolean.FALSE.equals(savedResult.isExampleResult()) || savedResult.isExampleResult() == null) {

            if (savedResult.getSubmission().getParticipation() instanceof ProgrammingExerciseStudentParticipation && ltiApi.isPresent()) {
                ltiApi.get().onNewResult((StudentParticipation) savedResult.getSubmission().getParticipation());
            }

            resultWebsocketService.broadcastNewResult(savedResult.getSubmission().getParticipation(), savedResult);
        }
        return savedResult;
    }

    public void createNewRatedManualResult(Result result) {
        createNewManualResult(result, true);
    }

    /**
     * Deletes result with corresponding complaint and complaint response
     *
     * @param result                      the result to delete
     * @param shouldClearParticipantScore determines whether the participant scores should be cleared. This should be true, if only one single result is deleted. If the whole
     *                                        participation or exercise is deleted, the participant scores have been deleted before and clearing is not necessary, then this value
     *                                        should be false
     */
    public void deleteResult(Result result, boolean shouldClearParticipantScore) {
        log.debug("Delete result {}", result.getId());
        deleteResultReferences(result.getId(), shouldClearParticipantScore);
        resultRepository.delete(result);
    }

    /**
     * NOTE: this method DOES NOT delete the result itself (e.g. because this will be done automatically when the submission is deleted)
     * Deletes result with corresponding complaint and complaint response
     *
     * @param resultId                    the id of the result for which all references should be deleted
     * @param shouldClearParticipantScore determines whether the participant scores should be cleared. This should be true, if only one single result is deleted. If the whole
     *                                        participation or exercise is deleted, the participant scores have been deleted before and clearing is not necessary, then this value
     *                                        should be false
     */
    public void deleteResultReferences(Long resultId, boolean shouldClearParticipantScore) {
        log.debug("Delete result references {}", resultId);
        complaintResponseRepository.deleteByComplaint_Result_Id(resultId);
        complaintRepository.deleteByResult_Id(resultId);
        ratingRepository.deleteByResult_Id(resultId);
        if (shouldClearParticipantScore) {
            participantScoreRepository.clearAllByResultId(resultId);
        }
    }

    /**
     * Store the given feedback to the passed result (by replacing all existing feedback) with a workaround for Hibernate exceptions.
     * <p>
     * With ordered collections (like result and feedback here), we have to be very careful with the way we persist the objects in the database.
     * We must first persist the child object without a relation to the parent object. Then, we recreate the association and persist the parent object.
     * <p>
     * If the result is not saved (shouldSave = false), the caller is responsible to save the result (which will persist the feedback changes as well)
     *
     * @param result       the result with should be saved with the given feedback
     * @param feedbackList new feedback items which replace the existing feedback
     * @param shouldSave   whether the result should be saved or not
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
     * @param result       the result with should be saved with the given feedback
     * @param feedbackList new feedback items which should be added to the feedback
     * @param shouldSave   whether the result should be saved or not
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
     * @param result the result for which the feedback elements should be returned
     * @return the list of filtered feedbacks
     */
    public List<Feedback> filterFeedbackForClient(Result result) {
        this.filterSensitiveInformationIfNecessary(result.getSubmission().getParticipation(), result);

        return result.getFeedbacks().stream() //
                .map(feedback -> feedback.result(null)) // remove unnecessary data to keep the json payload smaller
                .sorted(Comparator.comparing(feedback -> Objects.requireNonNullElse(feedback.getType(), FeedbackType.AUTOMATIC))) // sort according to FeedbackType enum order.
                .toList();
    }

    /**
     * Removes sensitive information that students should not see (yet) from the given result.
     *
     * @param participation the result belongs to.
     * @param result        a result of this participation
     */
    public void filterSensitiveInformationIfNecessary(final Participation participation, final Result result) {
        this.filterSensitiveInformationIfNecessary(participation, List.of(result), Optional.empty());
    }

    /**
     * Removes sensitive information that students should not see (yet) from the given results.
     *
     * @param participation the results belong to.
     * @param results       collection of results of this participation
     * @param user          the user for which the information should be filtered if it is an empty optional, the currently logged-in user is used
     */
    public void filterSensitiveInformationIfNecessary(final Participation participation, final Collection<Result> results, Optional<User> user) {
        results.forEach(Result::filterSensitiveInformation);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user.orElse(null))) {
            filterInformation(participation, results);
        }
    }

    private void filterInformation(Participation participation, Collection<Result> results) {
        // The test cases marked as after_due_date should only be shown after all
        // students can no longer submit so that no unfair advantage is possible.
        //
        // For course exercises, this applies only to automatic results. For manual ones the instructors
        // are responsible to set an appropriate assessment due date.
        //
        // For exams, we filter sensitive results until the results are published.
        // For test exam exercises, this is the case when the student submitted the test exam.

        Exercise exercise = participation.getExercise();
        if (exercise.isExamExercise()) {
            filterSensitiveFeedbacksInExamExercise(participation, results, exercise);
        }
        else {
            filterSensitiveFeedbackInCourseExercise(participation, results, exercise);
        }
    }

    private void filterSensitiveFeedbackInCourseExercise(Participation participation, Collection<Result> results, Exercise exercise) {
        boolean beforeLatestDueDate = exerciseDateService.isBeforeLatestDueDate(exercise);
        boolean participationBeforeDueDate = exerciseDateService.isBeforeDueDate(participation);
        results.forEach(result -> {
            boolean isBeforeDueDateOrAutomaticAndBeforeLatestDueDate = participationBeforeDueDate
                    || (AssessmentType.AUTOMATIC.equals(result.getAssessmentType()) && beforeLatestDueDate);
            if (Hibernate.isInitialized(result.getFeedbacks())) {
                result.filterSensitiveFeedbacks(isBeforeDueDateOrAutomaticAndBeforeLatestDueDate);
            }

            boolean assessmentTypeSetAndNonAutomatic = result.getAssessmentType() != null && result.getAssessmentType() != AssessmentType.AUTOMATIC;
            boolean beforeAssessmentDueDate = !ExerciseDateService.isAfterAssessmentDueDate(exercise);

            // A tutor is allowed to access all feedback, but filter for a student the manual feedback if the assessment due date is not over yet
            if (assessmentTypeSetAndNonAutomatic && beforeAssessmentDueDate) {
                // filter all non-automatic feedbacks
                if (Hibernate.isInitialized(result.getFeedbacks())) {
                    result.getFeedbacks().removeIf(feedback -> feedback.getType() != FeedbackType.AUTOMATIC);
                }
            }
        });
    }

    private void filterSensitiveFeedbacksInExamExercise(Participation participation, Collection<Result> results, Exercise exercise) {
        StudentExamApi api = studentExamApi.orElseThrow(() -> new ExamApiNotPresentException(StudentExamApi.class));
        Exam exam = exercise.getExerciseGroup().getExam();
        boolean shouldResultsBePublished = exam.resultsPublished();
        if (!shouldResultsBePublished && exam.isTestExam() && participation instanceof StudentParticipation) {
            var studentExamOptional = api.findByExamIdAndParticipationId(exam.getId(), participation.getId());
            if (studentExamOptional.isPresent()) {
                shouldResultsBePublished = studentExamOptional.get().areResultsPublishedYet();
            }
        }
        for (Result result : results) {
            if (Hibernate.isInitialized(result.getFeedbacks())) {
                result.filterSensitiveFeedbacks(!shouldResultsBePublished);
            }
        }
    }

    /**
     * Get the successful results for an exercise, ordered ascending by build completion date.
     *
     * @param participations  the participations with references to the exercises for which the results should be returned
     * @param withSubmissions true, if each result should also contain the submissions.
     * @return a list of results as described above for the given exercise.
     */
    public List<Result> resultsForExercise(Set<StudentParticipation> participations, boolean withSubmissions) {
        final List<Result> results = new ArrayList<>();

        for (StudentParticipation participation : participations) {
            // Filter out participations without students / teams
            if (participation.getParticipant() == null) {
                continue;
            }

            Optional<Submission> optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(participation.getSubmissions(), true);
            if (optionalSubmission.isEmpty() || optionalSubmission.get().getLatestResult() == null) {
                continue;
            }
            var submission = optionalSubmission.get();
            participation.setSubmissionCount(participation.getSubmissions().size());
            if (withSubmissions) {
                submission.getLatestResult().setSubmission(submission);
            }
            results.add(submission.getLatestResult());
        }

        if (withSubmissions) {
            results.removeIf(result -> result.getSubmission() == null || !result.getSubmission().isSubmitted());
        }

        return results;
    }

    /**
     * Returns the result for the given id with authorization checks.
     *
     * @param participationId the id of the participation
     * @param resultId        the id of the result
     * @param role            the minimum role required to access the result
     * @return the result
     */
    public Result getResultForParticipationAndCheckAccess(Long participationId, Long resultId, Role role) {
        Result result = resultRepository.findByIdElseThrow(resultId);
        Participation participation = result.getSubmission().getParticipation();
        if (!participation.getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId of the path doesnt match the participationId of the participation corresponding to the result " + resultId + "!",
                    "Participation", "400");
        }
        Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, null);
        return result;
    }

    /**
     * Get a map of result ids to the respective build job ids if build log files for this build job exist.
     *
     * @param participationId the participation id for which the results and build logs should be checked
     * @return a map of result ids to respective build job ids if the build log files exist, null otherwise
     */
    public Map<Long, String> getLogsAvailabilityForResults(Long participationId) {
        Map<Long, String> logsAvailability = new HashMap<>();
        Set<ResultBuildJob> buildJobs = buildJobRepository.findBuildJobIdsWithResultForParticipationId(participationId);
        ProgrammingExerciseNamesDTO names = null;
        for (var buildJob : buildJobs) {
            // all build jobs belong to the same programming exercise, so we can fetch the names only once
            if (names == null) {
                names = programmingExerciseRepository.findNames(buildJob.programmingExerciseId());
            }
            if (buildLogEntryService.buildJobHasLogFile(buildJob.buildJobId(), names)) {
                logsAvailability.put(buildJob.resultId(), buildJob.buildJobId());
            }
            else {
                logsAvailability.put(buildJob.resultId(), null);
            }
        }
        return logsAvailability;
    }

    @NotNull
    private List<Feedback> saveFeedbackWithHibernateWorkaround(@NotNull Result result, List<Feedback> feedbackList) {
        List<Feedback> savedFeedbacks = new ArrayList<>();

        // Fetch long feedback texts associated with the provided feedback list
        Map<Long, LongFeedbackText> longFeedbackTextMap = longFeedbackTextRepository
                .findByFeedbackIds(feedbackList.stream().filter(feedback -> feedback.getId() != null && feedback.getHasLongFeedbackText()).map(Feedback::getId).toList()).stream()
                .collect(Collectors.toMap(longFeedbackText -> longFeedbackText.getFeedback().getId(), Function.identity()));

        feedbackList.forEach(feedback -> {
            handleFeedbackPersistence(feedback, result, longFeedbackTextMap);
            savedFeedbacks.add(feedback);
        });

        return savedFeedbacks;
    }

    private void handleFeedbackPersistence(Feedback feedback, Result result, Map<Long, LongFeedbackText> longFeedbackTextMap) {
        // Temporarily detach feedback from the parent result to avoid Hibernate issues
        feedback.setResult(null);

        // Connect old long feedback text to the feedback before saving, otherwise it would be deleted
        if (feedback.getId() != null && feedback.getHasLongFeedbackText()) {

            // If the long feedback is not empty, it means that changes have been made on the client, so we do not want
            // to override the new long feedback with its previous version
            if (feedback.getLongFeedback().isPresent()) {
                // Delete the old long feedback so we don't get a duplicate key error
                longFeedbackTextRepository.deleteByFeedbackId(feedback.getId());
            }
            else {
                LongFeedbackText longFeedback = longFeedbackTextMap.get(feedback.getId());
                feedback.setLongFeedbackText(Set.of(longFeedback));
            }
        }

        // Persist the feedback entity without the parent association
        feedback = feedbackRepository.saveAndFlush(feedback);

        // Restore associations to the result
        feedback.setResult(result);
    }

    @NotNull
    private Result shouldSaveResult(@NotNull Result result, boolean shouldSave) {
        if (shouldSave) {
            // long feedback text is deleted as it otherwise causes duplicate entries errors and will be saved again with {@link resultRepository.save}
            deleteLongFeedback(result.getFeedbacks(), result);

            // Set all long feedback IDs to null to make hibernate aware that the long feedback doesn't exist.
            result.getFeedbacks().forEach(feedback -> feedback.getLongFeedback().ifPresent(longFeedbackText -> longFeedbackText.setId(null)));
            // Note: This also saves the feedback objects in the database because of the 'cascade = CascadeType.ALL' option.
            return resultRepository.save(result);
        }
        else {
            return result;
        }
    }

    /**
     * Retrieves paginated and filtered aggregated feedback details for a given exercise, including the count of each unique feedback detail text,
     * test case name, task name, and error category.
     * <br>
     * For each feedback detail:
     * 1. The relative count is calculated as a percentage of the total distinct results for the exercise.
     * 2. Task names are assigned based on associated test case names, with a mapping created between test cases and tasks from the exercise database.
     * Feedback items not assigned to any task are labeled as "Not assigned to a task."
     * 3. Error categories are classified as one of "Student Error," "Ares Error," or "AST Error," based on feedback content.
     * <br>
     * It supports filtering by:
     * - Search term: Case-insensitive filtering on feedback detail text.
     * - Test case names: Filters feedback based on specific test case names. Only active test cases are included in the filtering options.
     * - Task names: Filters feedback based on specified task names and includes unassigned tasks if "Not assigned to a task" is selected.
     * - Occurrence range: Filters feedback where the number of occurrences (COUNT) is within the specified minimum and maximum range.
     * - Error categories: Filters feedback based on selected error categories, such as "Student Error," "Ares Error," and "AST Error."
     * <br>
     * Pagination and sorting:
     * - Sorting is applied based on the specified column and order (ascending or descending).
     * - The result is paginated according to the provided page number and page size.
     * Additionally one can group the feedback detail text.
     *
     * @param exerciseId    The ID of the exercise for which feedback details should be retrieved.
     * @param data          The {@link FeedbackPageableDTO} containing page number, page size, search term, sorting options, and filtering parameters
     *                          (task names, test cases, occurrence range, error categories).
     * @param groupFeedback The flag to enable grouping and aggregation of feedback details.
     * @return A {@link FeedbackAnalysisResponseDTO} object containing:
     *         - A {@link SearchResultPageDTO} of paginated feedback details.
     *         - The total number of distinct results for the exercise.
     *         - A set of task names, including "Not assigned to a task" if applicable.
     *         - A list of active test case names used in the feedback.
     *         - A list of predefined error categories ("Student Error," "Ares Error," "AST Error") available for filtering.
     */
    public FeedbackAnalysisResponseDTO getFeedbackDetailsOnPage(long exerciseId, FeedbackPageableDTO data, boolean groupFeedback) {

        // 1. Fetch programming exercise with associated test cases
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTestCasesByIdElseThrow(exerciseId);

        // 2. Get the distinct count of results for calculating relative feedback counts
        long distinctResultCount = studentParticipationRepository.countDistinctResultsByExerciseId(exerciseId);

        // 3. Extract only active test case names for use in filtering options
        List<String> activeTestCaseNames = programmingExercise.getTestCases().stream().filter(ProgrammingExerciseTestCase::isActive).map(ProgrammingExerciseTestCase::getTestName)
                .toList();

        // 4. Retrieve all tasks associated with the exercise and map their names
        List<ProgrammingExerciseTask> tasks = programmingExerciseTaskService.getTasksWithUnassignedTestCases(exerciseId);
        Set<String> taskNames = tasks.stream().map(ProgrammingExerciseTask::getTaskName).collect(Collectors.toSet());

        // 5. Include unassigned tasks if specified by the filter; otherwise, only include specified tasks
        List<String> includeNotAssignedToTask = new ArrayList<>(taskNames);
        if (!data.getFilterTasks().isEmpty()) {
            includeNotAssignedToTask.removeAll(data.getFilterTasks());
        }
        else {
            includeNotAssignedToTask.clear();
        }

        // 6. Define the occurrence range based on filter parameters
        long minOccurrence = data.getFilterOccurrence().length == 2 ? Long.parseLong(data.getFilterOccurrence()[0]) : 0;
        long maxOccurrence = data.getFilterOccurrence().length == 2 ? Long.parseLong(data.getFilterOccurrence()[1]) : Integer.MAX_VALUE;

        // 7. Define the error categories to filter based on user selection
        List<String> filterErrorCategories = data.getFilterErrorCategories();

        // 8. Set up pagination and sorting based on input data
        final Pageable pageable = groupFeedback ? Pageable.unpaged() : PageUtil.createDefaultPageRequest(data, PageUtil.ColumnMapping.FEEDBACK_ANALYSIS);

        // 9. Query the database based on groupFeedback attribute to retrieve paginated and filtered feedback
        final Page<FeedbackDetailDTO> feedbackDetailPage = studentParticipationRepository.findFilteredFeedbackByExerciseId(exerciseId,
                StringUtils.isBlank(data.getSearchTerm()) ? "" : data.getSearchTerm().toLowerCase(), data.getFilterTestCases(), includeNotAssignedToTask, minOccurrence,
                maxOccurrence, filterErrorCategories, pageable);

        List<FeedbackDetailDTO> processedDetails;
        int totalPages;
        long totalCount;
        long highestOccurrenceOfGroupedFeedback = 0;
        if (!groupFeedback) {
            // Process and map feedback details, calculating relative count and assigning task names
            processedDetails = feedbackDetailPage.getContent().stream()
                    .map(detail -> new FeedbackDetailDTO(detail.feedbackIds().subList(0, Math.min(detail.feedbackIds().size(), MAX_FEEDBACK_IDS)), detail.count(),
                            (detail.count() * 100.00) / distinctResultCount, detail.detailTexts(), detail.testCaseName(), detail.taskName(), detail.errorCategory(),
                            detail.hasLongFeedbackText()))
                    .toList();
            totalPages = feedbackDetailPage.getTotalPages();
            totalCount = feedbackDetailPage.getTotalElements();
        }
        else {
            // Fetch all feedback details
            List<FeedbackDetailDTO> allFeedbackDetails = feedbackDetailPage.getContent();

            // Apply grouping and aggregation with a similarity threshold of 90%
            List<FeedbackDetailDTO> aggregatedFeedbackDetails = aggregateFeedback(allFeedbackDetails, SIMILARITY_THRESHOLD);

            highestOccurrenceOfGroupedFeedback = aggregatedFeedbackDetails.stream().mapToLong(FeedbackDetailDTO::count).max().orElse(0);
            // Apply manual sorting
            Comparator<FeedbackDetailDTO> comparator = getComparatorForFeedbackDetails(data);
            List<FeedbackDetailDTO> processedDetailsPreSort = new ArrayList<>(aggregatedFeedbackDetails);
            processedDetailsPreSort.sort(comparator);
            // Apply manual pagination
            int page = data.getPage();
            int pageSize = data.getPageSize();
            int start = Math.max(0, (page - 1) * pageSize);
            int end = Math.min(start + pageSize, processedDetailsPreSort.size());
            processedDetails = processedDetailsPreSort.subList(start, end);
            processedDetails = processedDetails.stream()
                    .map(detail -> new FeedbackDetailDTO(detail.feedbackIds().subList(0, Math.min(detail.feedbackIds().size(), 5)), detail.count(),
                            (detail.count() * 100.00) / distinctResultCount, detail.detailTexts(), detail.testCaseName(), detail.taskName(), detail.errorCategory(),
                            detail.hasLongFeedbackText()))
                    .toList();
            totalPages = (int) Math.ceil((double) processedDetailsPreSort.size() / pageSize);
            totalCount = aggregatedFeedbackDetails.size();
        }

        // 10. Predefined error categories available for filtering on the client side
        final List<String> ERROR_CATEGORIES = List.of("Student Error", "Ares Error", "AST Error");

        // 11. Return response containing processed feedback details, task names, active test case names, and error categories
        return new FeedbackAnalysisResponseDTO(new SearchResultPageDTO<>(processedDetails, totalPages), totalCount, taskNames, activeTestCaseNames, ERROR_CATEGORIES,
                highestOccurrenceOfGroupedFeedback);
    }

    private Comparator<FeedbackDetailDTO> getComparatorForFeedbackDetails(FeedbackPageableDTO search) {
        Map<String, Comparator<FeedbackDetailDTO>> comparators = Map.of("count", Comparator.comparingLong(FeedbackDetailDTO::count), "detailTexts",
                Comparator.comparing(detail -> detail.detailTexts().isEmpty() ? "" : detail.detailTexts().getFirst(), // Sort by the first element of the list
                        String.CASE_INSENSITIVE_ORDER),
                "testCaseName", Comparator.comparing(FeedbackDetailDTO::testCaseName, String.CASE_INSENSITIVE_ORDER), "taskName",
                Comparator.comparing(FeedbackDetailDTO::taskName, String.CASE_INSENSITIVE_ORDER));

        Comparator<FeedbackDetailDTO> comparator = comparators.getOrDefault(search.getSortedColumn(), (a, b) -> 0);
        return search.getSortingOrder() == SortingOrder.ASCENDING ? comparator : comparator.reversed();
    }

    private List<FeedbackDetailDTO> aggregateFeedback(List<FeedbackDetailDTO> feedbackDetails, double similarityThreshold) {
        List<FeedbackDetailDTO> processedDetails = new ArrayList<>();

        for (FeedbackDetailDTO base : feedbackDetails) {
            boolean isMerged = false;

            for (FeedbackDetailDTO processed : processedDetails) {
                // Ensure feedbacks have the same testCaseName and taskName
                if (base.testCaseName().equals(processed.testCaseName()) && base.taskName().equals(processed.taskName())) {
                    double similarity = NameSimilarity.levenshteinSimilarity(base.detailTexts().getFirst(), processed.detailTexts().getFirst());

                    if (similarity > similarityThreshold) {
                        // Merge the current base feedback into the processed feedback
                        List<Long> mergedFeedbackIds = new ArrayList<>(processed.feedbackIds());
                        if (processed.feedbackIds().size() < MAX_FEEDBACK_IDS) {
                            mergedFeedbackIds.addAll(base.feedbackIds());
                        }

                        List<String> mergedTexts = new ArrayList<>(processed.detailTexts());
                        mergedTexts.add(base.detailTexts().getFirst());

                        long mergedCount = processed.count() + base.count();

                        // Replace the processed entry with the updated one
                        processedDetails.remove(processed);
                        FeedbackDetailDTO updatedProcessed = new FeedbackDetailDTO(mergedFeedbackIds, mergedCount, 0, mergedTexts, processed.testCaseName(), processed.taskName(),
                                processed.errorCategory(), processed.hasLongFeedbackText());
                        processedDetails.add(updatedProcessed); // Add the updated entry
                        isMerged = true;
                        break; // No need to check further
                    }
                }
            }

            if (!isMerged) {
                // If not merged, add it as a new entry in processedDetails
                FeedbackDetailDTO newEntry = new FeedbackDetailDTO(base.feedbackIds(), base.count(), 0, List.of(base.detailTexts().getFirst()), base.testCaseName(),
                        base.taskName(), base.errorCategory(), base.hasLongFeedbackText());
                processedDetails.add(newEntry);
            }
        }

        return processedDetails;
    }

    /**
     * Retrieves the maximum feedback count for a given exercise.
     * <br>
     * This method calls the repository to fetch the maximum number of feedback occurrences across all feedback items for a specific exercise.
     * This is used for filtering feedback based on the number of occurrences.
     *
     * @param exerciseId The ID of the exercise for which the maximum feedback count is to be retrieved.
     * @return The maximum count of feedback occurrences for the given exercise.
     */
    public long getMaxCountForExercise(long exerciseId) {
        return studentParticipationRepository.findMaxCountForExercise(exerciseId);
    }

    /**
     * Retrieves a paginated list of students affected by specific feedback entries for a given exercise.
     * <br>
     * This method filters students based on feedback IDs and returns participation details for each affected student.
     * <br>
     *
     * @param exerciseId  for which the affected student participation data is requested.
     * @param feedbackIds used to filter the participation to only those affected by specific feedback entries.
     * @return A {@link List} of {@link FeedbackAffectedStudentDTO} objects, each representing a student affected by the feedback.
     */
    public List<FeedbackAffectedStudentDTO> getAffectedStudentsWithFeedbackIds(long exerciseId, List<Long> feedbackIds) {
        return studentParticipationRepository.findAffectedStudentsByFeedbackIds(exerciseId, feedbackIds);
    }

    /**
     * Deletes long feedback texts for the provided list of feedback items to prevent duplicate entries in the {@link LongFeedbackTextRepository}.
     * <br>
     * This method processes the provided list of feedback items, identifies those with associated long feedback texts, and removes them in bulk
     * from the repository to avoid potential duplicate entry errors when saving new feedback entries.
     * <p>
     * Primarily used to ensure data consistency in the {@link LongFeedbackTextRepository}, especially during operations where feedback entries are
     * overridden or updated. The deletion is performed only for feedback items with a non-null ID and an associated long feedback text.
     * <p>
     * This approach reduces the need for individual deletion calls and performs batch deletion in a single database operation.
     * <p>
     * **Note:** This method should only be used for manually assessed submissions, not for fully automatic assessments, due to its dependency on the
     * {@link Result#updateAllFeedbackItems} method, which is designed for manual feedback management. Using this method with automatic assessments could
     * lead to unintended behavior or data inconsistencies.
     *
     * @param feedbackList The list of {@link Feedback} objects for which the long feedback texts are to be deleted. Only feedback items that have long feedback texts and a
     *                         non-null ID will be processed.
     * @param result       The {@link Result} object associated with the feedback items, used to update feedback list before processing.
     */
    public void deleteLongFeedback(List<Feedback> feedbackList, Result result) {
        if (feedbackList == null) {
            return;
        }
        List<Long> feedbackIdsWithLongText = feedbackList.stream().filter(feedback -> feedback.getHasLongFeedbackText() && feedback.getId() != null).map(Feedback::getId).toList();
        longFeedbackTextRepository.deleteByFeedbackIds(feedbackIdsWithLongText);
        List<Feedback> feedbacks = new ArrayList<>(feedbackList);
        result.updateAllFeedbackItems(feedbacks, true);
    }
}
