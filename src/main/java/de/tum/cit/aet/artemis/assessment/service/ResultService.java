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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackAnalysisResponseDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDetailDTO;
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
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.lti.service.LtiNewResultService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.hestia.ProgrammingExerciseTaskService;

@Profile(PROFILE_CORE)
@Service
public class ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final UserRepository userRepository;

    private final ResultRepository resultRepository;

    private final Optional<LtiNewResultService> ltiNewResultService;

    private final ResultWebsocketService resultWebsocketService;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final RatingRepository ratingRepository;

    private final FeedbackRepository feedbackRepository;

    private final ComplaintRepository complaintRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseDateService exerciseDateService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final StudentExamRepository studentExamRepository;

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    private final BuildJobRepository buildJobRepository;

    private final BuildLogEntryService buildLogEntryService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public ResultService(UserRepository userRepository, ResultRepository resultRepository, Optional<LtiNewResultService> ltiNewResultService,
            ResultWebsocketService resultWebsocketService, ComplaintResponseRepository complaintResponseRepository, RatingRepository ratingRepository,
            FeedbackRepository feedbackRepository, LongFeedbackTextRepository longFeedbackTextRepository, ComplaintRepository complaintRepository,
            ParticipantScoreRepository participantScoreRepository, AuthorizationCheckService authCheckService, ExerciseDateService exerciseDateService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, StudentExamRepository studentExamRepository,
            BuildJobRepository buildJobRepository, BuildLogEntryService buildLogEntryService, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.ltiNewResultService = ltiNewResultService;
        this.resultWebsocketService = resultWebsocketService;
        this.complaintResponseRepository = complaintResponseRepository;
        this.ratingRepository = ratingRepository;
        this.feedbackRepository = feedbackRepository;
        this.longFeedbackTextRepository = longFeedbackTextRepository;
        this.complaintRepository = complaintRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.authCheckService = authCheckService;
        this.exerciseDateService = exerciseDateService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.studentExamRepository = studentExamRepository;
        this.buildJobRepository = buildJobRepository;
        this.buildLogEntryService = buildLogEntryService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
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

            if (savedResult.getParticipation() instanceof ProgrammingExerciseStudentParticipation && ltiNewResultService.isPresent()) {
                ltiNewResultService.get().onNewResult((StudentParticipation) savedResult.getParticipation());
            }

            resultWebsocketService.broadcastNewResult(savedResult.getParticipation(), savedResult);
        }
        return savedResult;
    }

    public Result createNewRatedManualResult(Result result) {
        return createNewManualResult(result, true);
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
        this.filterSensitiveInformationIfNecessary(result.getParticipation(), result);

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
        Exam exam = exercise.getExerciseGroup().getExam();
        boolean shouldResultsBePublished = exam.resultsPublished();
        if (!shouldResultsBePublished && exam.isTestExam() && participation instanceof StudentParticipation studentParticipation) {
            var participant = studentParticipation.getParticipant();
            var studentExamOptional = studentExamRepository.findByExamIdAndUserId(exam.getId(), participant.getId());
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
     * Returns the matching template, solution or student participation for a given build plan key.
     *
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
        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository
                .findWithResultsAndExerciseAndTeamStudentsByBuildPlanId(planKey);
        ProgrammingExerciseStudentParticipation participation = null;
        if (!participations.isEmpty()) {
            participation = participations.getFirst();
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
     * @param exercise        which the results belong to.
     * @param participations  the participations for which the results should be returned
     * @param withSubmissions true, if each result should also contain the submissions.
     * @return a list of results as described above for the given exercise.
     */
    public List<Result> resultsForExercise(Exercise exercise, Set<StudentParticipation> participations, boolean withSubmissions) {
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
     *
     * @param participationId the id of the participation
     * @param resultId        the id of the result
     * @param role            the minimum role required to access the result
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

    /**
     * Get a map of result ids to the respective build job ids if build log files for this build job exist.
     *
     * @param results the results for which to check the availability of build logs
     * @return a map of result ids to respective build job ids if the build log files exist, null otherwise
     */
    public Map<Long, String> getLogsAvailabilityForResults(List<Result> results) {

        Map<Long, String> logsAvailability = new HashMap<>();

        List<Long> resultIds = results.stream().map(Result::getId).toList();

        Map<Long, String> resultBuildJobSet = buildJobRepository.findBuildJobIdsForResultIds(resultIds).stream()
                .collect(Collectors.toMap(ResultBuildJob::resultId, ResultBuildJob::buildJobId, (existing, replacement) -> existing));

        for (Long resultId : resultIds) {
            String buildJobId = resultBuildJobSet.get(resultId);
            if (buildJobId != null) {

                if (buildLogEntryService.buildJobHasLogFile(buildJobId)) {
                    logsAvailability.put(resultId, buildJobId);
                }
                else {
                    logsAvailability.put(resultId, null);
                }
            }
            else {
                logsAvailability.put(resultId, null);
            }
        }
        return logsAvailability;
    }

    @NotNull
    private List<Feedback> saveFeedbackWithHibernateWorkaround(@NotNull Result result, List<Feedback> feedbackList) {
        // Avoid hibernate exception
        List<Feedback> savedFeedbacks = new ArrayList<>();
        // Collect ids of feedbacks that have long feedback.
        List<Long> feedbackIdsWithLongFeedback = feedbackList.stream().filter(feedback -> feedback.getId() != null && feedback.getHasLongFeedbackText()).map(Feedback::getId)
                .toList();
        // Get long feedback list from the database.
        List<LongFeedbackText> longFeedbackTextList = longFeedbackTextRepository.findByFeedbackIds(feedbackIdsWithLongFeedback);

        // Convert list to map for accessing later.
        Map<Long, LongFeedbackText> longLongFeedbackTextMap = longFeedbackTextList.stream()
                .collect(Collectors.toMap(longFeedbackText -> longFeedbackText.getFeedback().getId(), longFeedbackText -> longFeedbackText));
        feedbackList.forEach(feedback -> {
            // cut association to parent object
            feedback.setResult(null);
            LongFeedbackText longFeedback = null;
            // look for long feedback that parent feedback has and cut the association between them.
            if (feedback.getId() != null && feedback.getHasLongFeedbackText()) {
                longFeedback = longLongFeedbackTextMap.get(feedback.getId());
                if (longFeedback != null) {
                    feedback.clearLongFeedback();
                }
            }
            // persist the child object without an association to the parent object.
            feedback = feedbackRepository.saveAndFlush(feedback);
            // restore the association to the parent object
            feedback.setResult(result);

            // restore the association of the long feedback to the parent feedback
            if (longFeedback != null) {
                feedback.setDetailText(longFeedback.getText());
            }
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

    /**
     * Retrieves paginated and filtered aggregated feedback details for a given exercise, calculating relative counts based on the total number of distinct results.
     * The task numbers are assigned based on the associated test case names, using the set of tasks fetched from the database.
     * <br>
     * For each feedback detail:
     * 1. The relative count is calculated as a percentage of the total number of distinct results for the exercise.
     * 2. The task number is determined by matching the test case name with the tasks.
     * <br>
     * The method supports filtering by a search term across feedback details, test case names, counts, task numbers, and relative counts.
     * Sorting is applied based on the specified column and order (ascending or descending).
     * The result is paginated based on the provided page number and page size.
     *
     * @param exerciseId The ID of the exercise for which feedback details should be retrieved.
     * @param search     The pageable search DTO containing page number, page size, sorting options, and a search term for filtering results.
     * @return A {@link FeedbackAnalysisResponseDTO} object containing:
     *         - a {@link SearchResultPageDTO} of paginated feedback details, and
     *         - the total number of distinct results (distinctResultCount) for the exercise.
     */
    public FeedbackAnalysisResponseDTO getFeedbackDetailsOnPage(long exerciseId, SearchTermPageableSearchDTO<String> search) {
        long distinctResultCount = studentParticipationRepository.countDistinctResultsByExerciseId(exerciseId);
        Set<ProgrammingExerciseTask> tasks = programmingExerciseTaskService.getTasksWithUnassignedTestCases(exerciseId);

        List<FeedbackDetailDTO> feedbackDetails = studentParticipationRepository.findAggregatedFeedbackByExerciseId(exerciseId);
        String searchTerm = search.getSearchTerm() != null ? search.getSearchTerm().toLowerCase() : "";

        long totalFeedbackCount = feedbackDetails.size();

        Predicate<FeedbackDetailDTO> matchesSearchTerm = detail -> searchTerm.isEmpty() || detail.detailText().toLowerCase().contains(searchTerm)
                || detail.testCaseName().toLowerCase().contains(searchTerm) || String.valueOf(detail.count()).contains(searchTerm)
                || String.valueOf(detail.taskNumber()).contains(searchTerm) || String.valueOf(detail.relativeCount()).contains(searchTerm);

        feedbackDetails = feedbackDetails.stream().map(detail -> {
            double relativeCount = (detail.count() * 100.0) / distinctResultCount;
            int taskNumber = IntStream.range(0, tasks.size())
                    .filter(i -> tasks.stream().toList().get(i).getTestCases().stream().anyMatch(tc -> tc.getTestName().equals(detail.testCaseName()))).findFirst().orElse(-1) + 1;

            return new FeedbackDetailDTO(detail.count(), relativeCount, detail.detailText(), detail.testCaseName(), taskNumber);
        }).filter(matchesSearchTerm).collect(Collectors.toList());

        Map<String, Comparator<FeedbackDetailDTO>> comparators = Map.of("count", Comparator.comparingLong(FeedbackDetailDTO::count), "detailText",
                Comparator.comparing(FeedbackDetailDTO::detailText, String.CASE_INSENSITIVE_ORDER), "testCaseName",
                Comparator.comparing(FeedbackDetailDTO::testCaseName, String.CASE_INSENSITIVE_ORDER), "taskNumber", Comparator.comparingInt(FeedbackDetailDTO::taskNumber),
                "relativeCount", Comparator.comparingDouble(FeedbackDetailDTO::relativeCount));

        Comparator<FeedbackDetailDTO> comparator = comparators.getOrDefault(search.getSortedColumn(), (a, b) -> 0);
        feedbackDetails.sort(search.getSortingOrder() == SortingOrder.ASCENDING ? comparator : comparator.reversed());

        int pageSize = search.getPageSize();
        int page = search.getPage();

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, feedbackDetails.size());

        List<FeedbackDetailDTO> paginatedFeedbackDetails = feedbackDetails.subList(start, end);

        int totalPages = (feedbackDetails.size() + pageSize - 1) / pageSize;
        return new FeedbackAnalysisResponseDTO(new SearchResultPageDTO<>(paginatedFeedbackDetails, totalPages), totalFeedbackCount);
    }
}
