package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_DUPLICATE_NOTIFICATION;
import static de.tum.in.www1.artemis.domain.ProgrammingSubmission.createFallbackSubmission;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.math3.util.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationResultService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseGradingStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseGradingService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGradingService.class);

    private final Optional<ContinuousIntegrationResultService> continuousIntegrationResultService;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingExerciseFeedbackService programmingExerciseFeedbackService;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final ResultRepository resultRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final AuditEventRepository auditEventRepository;

    private final GroupNotificationService groupNotificationService;

    private final ResultService resultService;

    private final ExerciseDateService exerciseDateService;

    private final SubmissionPolicyService submissionPolicyService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final BuildLogEntryService buildLogService;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final FeedbackService feedbackService;

    public ProgrammingExerciseGradingService(StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository,
            Optional<ContinuousIntegrationResultService> continuousIntegrationResultService, Optional<VersionControlService> versionControlService,
            ProgrammingExerciseFeedbackService programmingExerciseFeedbackService, WebsocketMessagingService websocketMessagingService,
            ProgrammingExerciseTestCaseRepository testCaseRepository, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            AuditEventRepository auditEventRepository, GroupNotificationService groupNotificationService, ResultService resultService, ExerciseDateService exerciseDateService,
            SubmissionPolicyService submissionPolicyService, ProgrammingExerciseRepository programmingExerciseRepository, BuildLogEntryService buildLogService,
            StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository, FeedbackService feedbackService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.continuousIntegrationResultService = continuousIntegrationResultService;
        this.resultRepository = resultRepository;
        this.versionControlService = versionControlService;
        this.programmingExerciseFeedbackService = programmingExerciseFeedbackService;
        this.websocketMessagingService = websocketMessagingService;
        this.testCaseRepository = testCaseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.auditEventRepository = auditEventRepository;
        this.groupNotificationService = groupNotificationService;
        this.resultService = resultService;
        this.submissionPolicyService = submissionPolicyService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.exerciseDateService = exerciseDateService;
        this.buildLogService = buildLogService;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.feedbackService = feedbackService;
    }

    /**
     * Uses the given requestBody to extract the relevant information from it.
     * Fetches and attaches the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation the participation for which the build was finished
     * @param requestBody   RequestBody containing the build result and its feedback items
     * @return result after compilation (can only be null in case an error occurs)
     */
    @Nullable
    public Result processNewProgrammingExerciseResult(@NotNull ProgrammingExerciseParticipation participation, @NotNull Object requestBody) {
        log.debug("Received new build result (NEW) for participation {}", participation.getId());

        try {
            ContinuousIntegrationResultService ciResultService = continuousIntegrationResultService.orElseThrow();
            var buildResult = ciResultService.convertBuildResult(requestBody);
            checkCorrectBranchElseThrow(participation, buildResult);

            Result newResult = ciResultService.createResultFromBuildResult(buildResult, participation);

            // Fetch submission or create a fallback
            var latestSubmission = getSubmissionForBuildResult(participation.getId(), buildResult).orElseGet(() -> createAndSaveFallbackSubmission(participation, buildResult));
            latestSubmission.setBuildFailed(newResult.getFeedbacks().stream().allMatch(Feedback::isStaticCodeAnalysisFeedback));
            // Add artifacts to submission
            latestSubmission.setBuildArtifact(buildResult.hasArtifact());

            if (buildResult.hasLogs()) {
                var programmingLanguage = participation.getProgrammingExercise().getProgrammingLanguage();
                var projectType = participation.getProgrammingExercise().getProjectType();
                var buildLogs = buildResult.extractBuildLogs(programmingLanguage);

                ciResultService.extractAndPersistBuildLogStatistics(latestSubmission, programmingLanguage, projectType, buildLogs);

                if (latestSubmission.isBuildFailed()) {
                    buildLogs = buildLogService.removeUnnecessaryLogsForProgrammingLanguage(buildLogs, programmingLanguage);
                    var savedBuildLogs = buildLogService.saveBuildLogs(buildLogs, latestSubmission);

                    // Set the received logs in order to avoid duplicate entries (this removes existing logs)
                    latestSubmission.setBuildLogEntries(savedBuildLogs);
                }
            }

            // Note: we only set one side of the relationship because we don't know yet whether the result will actually be saved
            newResult.setSubmission(latestSubmission);
            newResult.setRatedIfNotAfterDueDate();
            // NOTE: the result is not saved yet, but is connected to the submission, the submission is not completely saved yet
            return processNewProgrammingExerciseResult(participation, newResult);
        }
        catch (ContinuousIntegrationException ex) {
            log.error("Result for participation {} could not be created", participation.getId(), ex);
            return null;
        }
    }

    /**
     * Checks that the build result belongs to the default branch of the student participation (in case it has a branch).
     * For all other cases (template/solution or student participation without a branch) it falls back to check the default branch of the programming exercise.
     *
     * @param participation The programming exercise participation in which the submission was made (including a reference to the programming exercise)
     * @param buildResult   The build result received from the CI system.
     * @throws IllegalArgumentException Thrown if the result does not belong to the default branch of the exercise.
     */
    private void checkCorrectBranchElseThrow(final ProgrammingExerciseParticipation participation, final AbstractBuildResultNotificationDTO buildResult)
            throws IllegalArgumentException {
        // If the branch is not present, it might be because the assignment repo did not change because only the test repo was changed
        buildResult.getBranchNameFromAssignmentRepo().ifPresent(branchName -> {
            String participationDefaultBranch = null;
            if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
                participationDefaultBranch = versionControlService.orElseThrow().getOrRetrieveBranchOfStudentParticipation(studentParticipation);
            }
            if (Strings.isNullOrEmpty(participationDefaultBranch)) {
                participationDefaultBranch = versionControlService.orElseThrow().getOrRetrieveBranchOfExercise(participation.getProgrammingExercise());
            }

            if (!Objects.equals(branchName, participationDefaultBranch)) {
                throw new IllegalArgumentException("Result was produced for a different branch than the default branch");
            }
        });
    }

    /**
     * Retrieves the submission that is assigned to the specified participation and its commit hash matches the one from the build result.
     *
     * @param participationId id of the participation
     * @param buildResult     The build result
     * @return The submission or empty if no submissions exist
     */
    protected Optional<ProgrammingSubmission> getSubmissionForBuildResult(Long participationId, AbstractBuildResultNotificationDTO buildResult) {
        var submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId);
        if (submissions.isEmpty()) {
            return Optional.empty();
        }

        return submissions.stream().filter(theSubmission -> {
            var commitHash = buildResult.getCommitHash(theSubmission.getType());
            return commitHash.isPresent() && commitHash.get().equals(theSubmission.getCommitHash());
        }).max(Comparator.naturalOrder());
    }

    @NotNull
    protected ProgrammingSubmission createAndSaveFallbackSubmission(ProgrammingExerciseParticipation participation, AbstractBuildResultNotificationDTO buildResult) {
        final var commitHash = buildResult.getCommitHash(SubmissionType.MANUAL);
        if (commitHash.isEmpty()) {
            log.error("Could not find commit hash for participation {}, build plan {}", participation.getId(), participation.getBuildPlanId());
        }
        log.warn("Could not find pending ProgrammingSubmission for Commit Hash {} (Participation {}, Build Plan {}). Will create a new one subsequently...", commitHash,
                participation.getId(), participation.getBuildPlanId());
        // We always take the build run date as the fallback solution
        ZonedDateTime submissionDate = buildResult.getBuildRunDate();
        if (commitHash.isPresent()) {
            try {
                // Try to get the actual date, the push might be 10s - 3min earlier, depending on how long the build takes.
                // Note: the whole method is a fallback in case creating the submission initially (when the user pushed the code) was not successful for whatever reason
                // This is also the case when a new programming exercise is created and the local CI system builds and tests the template and solution repositories for the first
                // time.
                submissionDate = versionControlService.orElseThrow().getPushDate(participation, commitHash.get(), null);
            }
            catch (VersionControlException e) {
                log.error("Could not retrieve push date for participation {} and build plan {}", participation.getId(), participation.getBuildPlanId(), e);
            }
        }
        var submission = createFallbackSubmission(participation, submissionDate, commitHash.orElse(null));
        // Save to avoid TransientPropertyValueException.
        return programmingSubmissionRepository.save(submission);
    }

    /**
     * Fetches and attaches the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation the new result should belong to.
     * @param newResult     that contains the build result with its feedbacks.
     * @return the result after processing and persisting.
     */
    private Result processNewProgrammingExerciseResult(final ProgrammingExerciseParticipation participation, final Result newResult) {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        boolean isSolutionParticipation = participation instanceof SolutionProgrammingExerciseParticipation;
        boolean isTemplateParticipation = participation instanceof TemplateProgrammingExerciseParticipation;
        boolean isStudentParticipation = !isSolutionParticipation && !isTemplateParticipation;

        // Find out which test cases were executed and calculate the score according to their status and weight.
        // This needs to be done as some test cases might not have been executed.
        // When the result is from a solution participation, extract the feedback items (= test cases) and store them in our database.
        if (isSolutionParticipation) {
            extractTestCasesFromResult(programmingExercise, newResult);
        }

        Result processedResult = calculateScoreForResult(newResult, programmingExercise, isStudentParticipation);

        // Note: This programming submission might already have multiple results, however they do not contain the assessor or the feedback
        var programmingSubmission = (ProgrammingSubmission) processedResult.getSubmission();

        if (isStudentParticipation) {
            // When a student receives a new result, we want to check whether we need to lock the participation and the
            // repository when a lock repository policy is present. At this point, we know that the programming
            // exercise exists and that the participation must be a ProgrammingExerciseStudentParticipation.
            // Only lock the repository and the participation if the participation is not for a test run (i.e. for a course exercise practice repository or for an instructor exam
            // test run repository).
            // Student test exam participations will still be locked by this.
            SubmissionPolicy submissionPolicy = programmingExerciseRepository.findWithSubmissionPolicyById(programmingExercise.getId()).orElseThrow().getSubmissionPolicy();
            if (submissionPolicy instanceof LockRepositoryPolicy policy && !((ProgrammingExerciseStudentParticipation) participation).isPracticeMode()) {
                submissionPolicyService.handleLockRepositoryPolicy(processedResult, (Participation) participation, policy);
            }

            if (programmingSubmission.getLatestResult() != null && programmingSubmission.getLatestResult().isManual() && !((Participation) participation).isPracticeMode()) {
                // Note: in this case, we do not want to save the processedResult, but we only want to update the latest semi-automatic one
                Result updatedLatestSemiAutomaticResult = updateLatestSemiAutomaticResultWithNewAutomaticFeedback(programmingSubmission.getLatestResult().getId(), processedResult);
                // Adding back dropped submission
                updatedLatestSemiAutomaticResult.setSubmission(programmingSubmission);
                programmingSubmissionRepository.save(programmingSubmission);
                updatedLatestSemiAutomaticResult = resultRepository.save(updatedLatestSemiAutomaticResult);

                return updatedLatestSemiAutomaticResult;
            }
        }

        // Finally, save the new result once and make sure the order column between submission and result is maintained

        // workaround to avoid org.hibernate.HibernateException: null index column for collection: de.tum.in.www1.artemis.domain.Submission.results
        processedResult.setSubmission(null);

        processedResult = resultRepository.save(processedResult);
        processedResult.setSubmission(programmingSubmission);
        programmingSubmission.addResult(processedResult);
        programmingSubmissionRepository.save(programmingSubmission);

        return processedResult;
    }

    /**
     * Updates an existing semi-automatic result with automatic feedback from another result.
     * <p>
     * Note: for the second correction it is important that we do not create additional semi-automatic results
     *
     * @param lastSemiAutomaticResultId The latest manual result for the same submission (which must exist in the database)
     * @param newAutomaticResult        The new automatic result
     * @return The updated semi-automatic result
     */
    private Result updateLatestSemiAutomaticResultWithNewAutomaticFeedback(long lastSemiAutomaticResultId, Result newAutomaticResult) {
        // Note: fetch the semi-automatic result with feedback and assessor again from the database
        var latestSemiAutomaticResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(lastSemiAutomaticResultId)
                .orElseThrow(() -> new EntityNotFoundException("Result", lastSemiAutomaticResultId));
        // this makes it the most recent result, but optionally keeps the draft state of an unfinished manual result
        latestSemiAutomaticResult.setCompletionDate(latestSemiAutomaticResult.getCompletionDate() != null ? newAutomaticResult.getCompletionDate() : null);

        // remove old automatic feedback
        latestSemiAutomaticResult.getFeedbacks().removeIf(feedback -> feedback != null && feedback.getType() == FeedbackType.AUTOMATIC);

        // copy all feedback from the automatic result
        List<Feedback> copiedFeedbacks = newAutomaticResult.getFeedbacks().stream().map(feedbackService::copyFeedback).toList();
        latestSemiAutomaticResult = resultService.addFeedbackToResult(latestSemiAutomaticResult, copiedFeedbacks, false);

        latestSemiAutomaticResult.setTestCaseCount(newAutomaticResult.getTestCaseCount());
        latestSemiAutomaticResult.setPassedTestCaseCount(newAutomaticResult.getPassedTestCaseCount());
        latestSemiAutomaticResult.setCodeIssueCount(newAutomaticResult.getCodeIssueCount());

        Exercise exercise = latestSemiAutomaticResult.getParticipation().getExercise();
        latestSemiAutomaticResult.setScore(latestSemiAutomaticResult.calculateTotalPointsForProgrammingExercises(), exercise.getMaxPoints(),
                exercise.getCourseViaExerciseGroupOrCourseMember());

        return resultRepository.save(latestSemiAutomaticResult);
    }

    /**
     * Generates test cases from the given result's feedbacks & notifies the subscribing users about the test cases if they have changed. Has the side effect of sending a message
     * through the websocket!
     *
     * @param exercise the programming exercise for which the test cases should be extracted from the new result
     * @param result   from which to extract the test cases.
     */
    private void extractTestCasesFromResult(ProgrammingExercise exercise, Result result) {
        boolean haveTestCasesChanged = programmingExerciseFeedbackService.generateTestCasesFromFeedbacks(result.getFeedbacks(), exercise);
        if (haveTestCasesChanged) {
            // Notify the client about the updated testCases
            Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(exercise.getId());
            websocketMessagingService.sendMessage("/topic/programming-exercises/" + exercise.getId() + "/test-cases", testCases);
        }
    }

    /**
     * Updates an incoming result with the information of the exercises test cases. This update includes:
     * - Checking which test cases were not executed as this is not part of the bamboo build (not all test cases are executed in an exercise with sequential test runs)
     * - Checking the due date and the visibility.
     * - Recalculating the score based on the successful test cases weight vs the total weight of all test cases.
     * <p>
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not change
     *
     * @param result                 to modify with new score and added feedbacks (not executed tests)
     * @param exercise               the result belongs to.
     * @param isStudentParticipation boolean flag indicating weather the participation of the result is not a solution/template participation.
     * @return Result with updated feedbacks and score
     */
    public Result calculateScoreForResult(Result result, ProgrammingExercise exercise, boolean isStudentParticipation) {
        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
        var relevantTestCases = testCases;

        // We don't filter the test cases for the solution/template participation's results as they are used as indicators for the instructor!
        if (isStudentParticipation) {
            relevantTestCases = filterRelevantTestCasesForStudent(testCases, result.getParticipation());
        }

        // We only apply submission policies if it is a student participation
        return calculateScoreForResult(testCases, relevantTestCases, result, exercise, isStudentParticipation);
    }

    /**
     * Updates <b>all</b> latest results of the given exercise with the information of the exercises test cases.
     * <p>
     * This update includes:
     * <ul>
     * <li>Checking which test cases were not executed as this is not part of the bamboo build (not all test cases are executed in an exercise with sequential test runs).</li>
     * <li>Checking the due date and the visibility.</li>
     * <li>Recalculating the score based on the successful test cases weight vs the total weight of all test cases.</li>
     * </ul>
     *
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not
     * change.
     *
     * @param exercise whose results should be updated.
     * @return the results of the exercise that have been updated.
     */
    public List<Result> updateAllResults(final ProgrammingExercise exercise) {
        final Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);

        final Stream<Result> updatedTemplateAndSolutionResult = updateTemplateAndSolutionResults(exercise, testCases);

        final List<StudentParticipation> studentParticipations = new ArrayList<>();
        // We only update the latest automatic results here, later manual assessments are not affected
        studentParticipations.addAll(studentParticipationRepository.findByExerciseIdWithLatestAutomaticResultAndFeedbacks(exercise.getId()));
        // Also update manual results
        studentParticipations.addAll(studentParticipationRepository.findByExerciseIdWithManualResultAndFeedbacks(exercise.getId()));

        final Stream<Result> updatedStudentResults = updateResults(exercise, testCases, studentParticipations);

        return Stream.concat(updatedTemplateAndSolutionResult, updatedStudentResults).toList();
    }

    /**
     * Updates the latest results of all participations that do not have an individual due date. This includes the template and solution participation.
     * <p>
     * For details what will be updated for individual results, see {@link ProgrammingExerciseGradingService#updateAllResults}.
     *
     * @param exercise whose results should be updated.
     * @return the results of the exercise that have been updated.
     */
    public List<Result> updateResultsOnlyRegularDueDateParticipations(final ProgrammingExercise exercise) {
        final Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);

        final Stream<Result> updatedTemplateAndSolutionResult = updateTemplateAndSolutionResults(exercise, testCases);

        final List<StudentParticipation> studentParticipations = new ArrayList<>();
        // We only update the latest automatic results here, later manual assessments are not affected
        studentParticipations.addAll(studentParticipationRepository.findByExerciseIdWithLatestAutomaticResultAndFeedbacksWithoutIndividualDueDate(exercise.getId()));
        // Also update manual results
        studentParticipations.addAll(studentParticipationRepository.findByExerciseIdWithManualResultAndFeedbacksWithoutIndividualDueDate(exercise.getId()));

        final Stream<Result> updatedStudentResults = updateResults(exercise, testCases, studentParticipations);

        return Stream.concat(updatedTemplateAndSolutionResult, updatedStudentResults).toList();
    }

    /**
     * Updates the latest result scores of the given participation.
     * <p>
     * For details what will be updated, see {@link ProgrammingExerciseGradingService#updateAllResults}.
     *
     * @param participation for which the results should be updated.
     * @return a list of updated results (maximum two: latest automatic, and latest manual result).
     */
    public List<Result> updateParticipationResults(final ProgrammingExerciseStudentParticipation participation) {
        final ProgrammingExercise exercise = participation.getProgrammingExercise();
        final Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
        final Set<ProgrammingExerciseTestCase> testCasesBeforeDueDate = filterTestCasesForStudents(testCases, true);
        final Set<ProgrammingExerciseTestCase> testCasesAfterDueDate = filterTestCasesForStudents(testCases, false);

        final Optional<Result> updatedAutomaticResult = studentParticipationRepository.findByIdWithLatestAutomaticResultAndFeedbacks(participation.getId())
                .flatMap(studentParticipation -> updateLatestResult(exercise, studentParticipation, testCases, testCasesBeforeDueDate, testCasesAfterDueDate, true));
        final Optional<Result> updatedManualResult = studentParticipationRepository.findByIdWithManualResultAndFeedbacks(participation.getId())
                .flatMap(studentParticipation -> updateLatestResult(exercise, studentParticipation, testCases, testCasesBeforeDueDate, testCasesAfterDueDate, true));

        return Stream.of(updatedAutomaticResult, updatedManualResult).flatMap(Optional::stream).toList();
    }

    /**
     * Updates the latest results for the given participations.
     *
     * @param exercise       the participations belong to.
     * @param allTestCases   of the programming exercise.
     * @param participations for which the latest results should be updated.
     * @return all results that have been updated.
     */
    private Stream<Result> updateResults(final ProgrammingExercise exercise, final Set<ProgrammingExerciseTestCase> allTestCases, final List<StudentParticipation> participations) {
        final Set<ProgrammingExerciseTestCase> testCasesBeforeDueDate = filterTestCasesForStudents(allTestCases, true);
        final Set<ProgrammingExerciseTestCase> testCasesAfterDueDate = filterTestCasesForStudents(allTestCases, false);

        return participations.stream().map(participation -> updateLatestResult(exercise, participation, allTestCases, testCasesBeforeDueDate, testCasesAfterDueDate, true))
                .flatMap(Optional::stream);
    }

    /**
     * Updates the latest results for the template and solution participation.
     *
     * @param exercise  the template and solution belong to.
     * @param testCases of the exercise.
     * @return a stream of results that have been updated.
     *         (maximum length two; if template and/or solution do not have a results, then fewer)
     */
    private Stream<Result> updateTemplateAndSolutionResults(final ProgrammingExercise exercise, final Set<ProgrammingExerciseTestCase> testCases) {
        final Optional<Result> templateResult = templateProgrammingExerciseParticipationRepository
                .findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(exercise.getId())
                .flatMap(templateParticipation -> updateLatestResult(exercise, templateParticipation, testCases, testCases, testCases, false));

        final Optional<Result> solutionResult = solutionProgrammingExerciseParticipationRepository
                .findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(exercise.getId())
                .flatMap(solutionParticipation -> updateLatestResult(exercise, solutionParticipation, testCases, testCases, testCases, false));

        return Stream.of(templateResult, solutionResult).flatMap(Optional::stream);
    }

    /**
     * Updates the score for the latest result of the given participation.
     *
     * @param exercise               the participation belongs to.
     * @param participation          of a student in the exercise.
     * @param allTestCases           of this exercise.
     * @param testCasesBeforeDueDate the test cases that are visible to the student before the due date.
     * @param testCasesAfterDueDate  the test cases that are visible to the student after the due date.
     * @param applySubmissionPolicy  true, if submission policies should be taken into account when updating the score.
     * @return the latest result with an updated score, or nothing if the participation had no results.
     */
    private Optional<Result> updateLatestResult(ProgrammingExercise exercise, Participation participation, Set<ProgrammingExerciseTestCase> allTestCases,
            Set<ProgrammingExerciseTestCase> testCasesBeforeDueDate, Set<ProgrammingExerciseTestCase> testCasesAfterDueDate, boolean applySubmissionPolicy) {
        final Result result = participation.findLatestLegalResult();
        if (result == null) {
            return Optional.empty();
        }

        boolean isBeforeDueDate = exerciseDateService.isBeforeDueDate(participation);
        final Set<ProgrammingExerciseTestCase> testCasesForCurrentDate = isBeforeDueDate ? testCasesBeforeDueDate : testCasesAfterDueDate;

        calculateScoreForResult(allTestCases, testCasesForCurrentDate, result, exercise, applySubmissionPolicy);

        return Optional.of(result);
    }

    /**
     * Creates an audit event logging that a re-evaluation was triggered.
     *
     * @param user     who triggered the re-evaluation.
     * @param exercise for which the evaluation was triggered.
     * @param course   the exercise belongs to.
     * @param results  of the exercise.
     */
    public void logReEvaluate(User user, ProgrammingExercise exercise, Course course, List<Result> results) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.RE_EVALUATE_RESULTS, "exercise=" + exercise.getTitle(), "course=" + course.getTitle(),
                "results=" + results.size());
        auditEventRepository.add(auditEvent);
        log.info("User {} triggered a re-evaluation of {} results for exercise {} with id {}", user.getLogin(), results.size(), exercise.getTitle(), exercise.getId());
    }

    /**
     * Filter all test cases from the score calculation that are never visible or ones with visibility "after due date" if the due date has not yet passed.
     *
     * @param testCases which should be filtered.
     * @return testCases, but the ones based on the described visibility criterion removed.
     */
    private Set<ProgrammingExerciseTestCase> filterRelevantTestCasesForStudent(Set<ProgrammingExerciseTestCase> testCases, Participation participation) {
        boolean isBeforeDueDate = exerciseDateService.isBeforeDueDate(participation);
        return filterTestCasesForStudents(testCases, isBeforeDueDate);
    }

    /**
     * Filters the test cases to only include the ones a student should be able to see.
     *
     * @param testCases       all test cases of an exercise.
     * @param isBeforeDueDate true, if the due date has not yet passed.
     * @return a set of test cases that are visible to the student.
     */
    private Set<ProgrammingExerciseTestCase> filterTestCasesForStudents(final Set<ProgrammingExerciseTestCase> testCases, boolean isBeforeDueDate) {
        return testCases.stream().filter(testCase -> !testCase.isInvisible()).filter(testCase -> !(isBeforeDueDate && testCase.isAfterDueDate())).collect(Collectors.toSet());
    }

    /**
     * @param exercise                   the result belongs to
     * @param result                     of the build run.
     * @param testCases                  all test cases of a given programming exercise.
     * @param successfulTestCases        test cases with positive feedback. i.e. there exists a feedback that is positive
     * @param staticCodeAnalysisFeedback of a given programming exercise.
     * @param weightSum                  the sum of all weights of test cases that are visible
     */
    private record ScoreCalculationData(ProgrammingExercise exercise, Result result, Set<ProgrammingExerciseTestCase> testCases,
            Set<ProgrammingExerciseTestCase> successfulTestCases, List<Feedback> staticCodeAnalysisFeedback, double weightSum) {

        ScoreCalculationData(ProgrammingExercise exercise, Result result, Set<ProgrammingExerciseTestCase> testCases, Set<ProgrammingExerciseTestCase> successfulTestCases,
                List<Feedback> staticCodeAnalysisFeedback) {
            this(exercise, result, testCases, successfulTestCases, staticCodeAnalysisFeedback, calculateWeightSum(testCases));
        }

        private static double calculateWeightSum(final Set<ProgrammingExerciseTestCase> testCases) {
            return testCases.stream().filter(testCase -> !testCase.isInvisible()).mapToDouble(ProgrammingExerciseTestCase::getWeight).sum();
        }

        public Participation participation() {
            return result.getParticipation();
        }
    }

    /**
     * Calculates the grading for a result and updates the feedbacks
     *
     * @param testCases             All test cases for the exercise
     * @param relevantTestCases     Test cases relevant at the current due date depending on visibility and permission
     * @param result                The result to be updated
     * @param exercise              The current exercise
     * @param applySubmissionPolicy true, if submission policies should be taken into account when updating the score.
     * @return The updated result
     */
    private Result calculateScoreForResult(Set<ProgrammingExerciseTestCase> testCases, Set<ProgrammingExerciseTestCase> relevantTestCases, @NotNull Result result,
            ProgrammingExercise exercise, boolean applySubmissionPolicy) {
        List<Feedback> automaticFeedbacks = result.getFeedbacks().stream().filter(feedback -> FeedbackType.AUTOMATIC.equals(feedback.getType())).toList();
        List<Feedback> staticCodeAnalysisFeedback = new ArrayList<>();
        List<Feedback> testCaseFeedback = new ArrayList<>();

        for (Feedback automaticFeedback : automaticFeedbacks) {
            if (automaticFeedback.isStaticCodeAnalysisFeedback()) {
                staticCodeAnalysisFeedback.add(automaticFeedback);
            }
            else {
                testCaseFeedback.add(automaticFeedback); // if feedback isn't static code analysis here, then it has to be test case feedback
            }
        }

        // Remove feedback that is in an invisible SCA category
        staticCodeAnalysisFeedback = staticCodeAnalysisCategoryRepository.categorizeScaFeedback(result, staticCodeAnalysisFeedback, exercise);

        if (applySubmissionPolicy) {
            SubmissionPolicy submissionPolicy = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exercise.getId()).getSubmissionPolicy();
            exercise.setSubmissionPolicy(submissionPolicy);
        }

        // Case 1: There are tests and test case feedback, find out which tests were not executed or should only count to the score after the due date.
        if (!relevantTestCases.isEmpty() && !testCaseFeedback.isEmpty() && !result.getFeedbacks().isEmpty()) {
            filterAutomaticFeedbacksWithoutTestCase(result, testCases);
            setVisibilityForFeedbacksWithTestCase(result, testCases);

            createFeedbackForNotExecutedTests(result, relevantTestCases);
            boolean hasDuplicateTestCases = createFeedbacksForDuplicateTests(result, exercise);
            createSubmissionPolicyFeedback(result, exercise);

            final Set<ProgrammingExerciseTestCase> successfulTestCases = relevantTestCases.stream().filter(testCase -> testCase.isSuccessful(result)).collect(Collectors.toSet());

            var scoreCalculationData = new ScoreCalculationData(exercise, result, testCases, successfulTestCases, staticCodeAnalysisFeedback);
            // The score is always calculated from ALL (except visibility=never) test cases, regardless of the current date!

            updateResultScore(scoreCalculationData, hasDuplicateTestCases, applySubmissionPolicy);
            updateFeedbackCredits(scoreCalculationData);

            result.setTestCaseCount(relevantTestCases.size());
            result.setPassedTestCaseCount(successfulTestCases.size());
            result.setCodeIssueCount(staticCodeAnalysisFeedback.size());

            if (result.isManual()) {
                result.setScore(result.calculateTotalPointsForProgrammingExercises(), exercise.getMaxPoints(), exercise.getCourseViaExerciseGroupOrCourseMember());
            }
        }
        // Case 2: There are no test cases that are executed before the due date has passed. We need to do this to differentiate this case from a build error.
        else if (!testCases.isEmpty() && !result.getFeedbacks().isEmpty() && !testCaseFeedback.isEmpty()) {
            addFeedbackTestsNotExecuted(result, exercise, staticCodeAnalysisFeedback);
        }

        // Case 3: If there is no test case feedback, the build has failed, or it has previously fallen under case 2. In this case we just return the original result without
        // changing it.

        return result;
    }

    private void createSubmissionPolicyFeedback(Result result, ProgrammingExercise exercise) {
        if (exercise.getSubmissionPolicy() instanceof SubmissionPenaltyPolicy penaltyPolicy) {
            submissionPolicyService.createFeedbackForPenaltyPolicy(result, penaltyPolicy);
        }
    }

    /**
     * Adds the appropriate feedback to the result in case the automatic test cases were not executed.
     *
     * @param result                     to which the feedback should be added.
     * @param exercise                   to which the result belongs to.
     * @param staticCodeAnalysisFeedback that has been created for this result.
     */
    private void addFeedbackTestsNotExecuted(final Result result, final ProgrammingExercise exercise, final List<Feedback> staticCodeAnalysisFeedback) {
        removeAllTestCaseFeedbackAndSetScoreToZero(result, staticCodeAnalysisFeedback);

        createFeedbacksForDuplicateTests(result, exercise);
    }

    /**
     * Only keeps automatic feedbacks that also are associated with a test case.
     * <p>
     * Does not remove static code analysis feedback.
     *
     * @param result    of the build run.
     * @param testCases of the programming exercise.
     */
    private void filterAutomaticFeedbacksWithoutTestCase(Result result, final Set<ProgrammingExerciseTestCase> testCases) {
        result.getFeedbacks().removeIf(feedback -> feedback.getType() == FeedbackType.AUTOMATIC && !feedback.isStaticCodeAnalysisFeedback()
                && testCases.stream().noneMatch(test -> test.getTestName().equalsIgnoreCase(feedback.getText())));
    }

    /**
     * Sets the visibility on all feedbacks associated with a test case with the same name.
     *
     * @param result    of the build run.
     * @param testCases of the given programming exercise.
     */
    private void setVisibilityForFeedbacksWithTestCase(Result result, final Set<ProgrammingExerciseTestCase> testCases) {
        for (Feedback feedback : result.getFeedbacks()) {
            testCases.stream().filter(testCase -> testCase.getTestName().equalsIgnoreCase(feedback.getText())).findFirst()
                    .ifPresent(testCase -> feedback.setVisibility(testCase.getVisibility()));
        }
    }

    /**
     * Checks which test cases were not executed and add a new Feedback for them to the exercise.
     *
     * @param result    of the build run.
     * @param testCases of the given programming exercise.
     */
    private void createFeedbackForNotExecutedTests(Result result, Set<ProgrammingExerciseTestCase> testCases) {
        List<Feedback> feedbacksForNotExecutedTestCases = testCases.stream().filter(testCase -> testCase.wasNotExecuted(result))
                .map(testCase -> new Feedback().type(FeedbackType.AUTOMATIC).text(testCase.getTestName()).detailText("Test was not executed.")).toList();

        result.addFeedbacks(feedbacksForNotExecutedTestCases);
    }

    /**
     * Checks which feedback entries have the same name and therefore indicate multiple testcases with the same name.
     * These duplicate testcases are added as a feedback element to the result.
     * The instructor and tutors are notified via a group notification.
     *
     * @param result              of the build run.
     * @param programmingExercise the current programming exercise.
     * @return true if result has duplicate test cases
     */
    private boolean createFeedbacksForDuplicateTests(Result result, ProgrammingExercise programmingExercise) {
        Set<String> uniqueFeedbackNames = new HashSet<>();
        Set<String> duplicateFeedbackNames = result.getFeedbacks().stream()
                .filter(feedback -> !feedback.isStaticCodeAnalysisFeedback() && FeedbackType.AUTOMATIC.equals(feedback.getType())).map(Feedback::getText)
                // Set.add() returns false if the feedbackName.toLowerCase is already contained in the set
                .filter(feedbackName -> !uniqueFeedbackNames.add(feedbackName.toLowerCase())).collect(Collectors.toSet());

        if (!duplicateFeedbackNames.isEmpty()) {
            String duplicateDetailText = "This is a duplicate test case. Please review all your test cases and verify that your test cases have unique names!";
            List<Feedback> feedbacksForDuplicateTestCases = duplicateFeedbackNames.stream()
                    .map(feedbackName -> new Feedback().type(FeedbackType.AUTOMATIC).text(feedbackName + " - Duplicate Test Case!").detailText(duplicateDetailText).positive(false))
                    .toList();
            result.addFeedbacks(feedbacksForDuplicateTestCases);

            String notificationText = TEST_CASES_DUPLICATE_NOTIFICATION + String.join(", ", duplicateFeedbackNames);
            groupNotificationService.notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(programmingExercise, notificationText);

            return true;
        }

        return false;
    }

    /**
     * Update the score given the positive tests score divided by all tests' score.
     * Takes weight, bonus multiplier and absolute bonus points into account.
     * All tests in this case do not include ones with visibility=never.
     */
    private void updateResultScore(ScoreCalculationData scoreCalculationData, boolean hasDuplicateTestCases, boolean applySubmissionPolicy) {
        double score = 0D;

        if (!hasDuplicateTestCases) {
            score = calculateScore(scoreCalculationData, applySubmissionPolicy);
        }

        scoreCalculationData.result().setScore(score, scoreCalculationData.exercise().getCourseViaExerciseGroupOrCourseMember());
    }

    private void updateFeedbackCredits(ScoreCalculationData scoreCalculationData) {
        // Set credits for successful test cases
        scoreCalculationData.testCases().stream().filter(testCase -> testCase.isSuccessful(scoreCalculationData.result())).forEach(testCase -> {
            double credits = calculatePointsForTestCase(testCase, scoreCalculationData);
            setCreditsForTestCaseFeedback(credits, testCase, scoreCalculationData.result());
        });

        scoreCalculationData.result().getFeedbacks().stream().filter(feedback -> feedback.getCredits() == null).forEach(feedback -> feedback.setCredits(0D));
    }

    /**
     * Calculates the score of automatic test cases for the given result with possible penalties applied.
     *
     * @return the final total score in percent that should be given to the result.
     */
    private double calculateScore(ScoreCalculationData scoreCalculationData, boolean applySubmissionPolicy) {

        double points = calculateSuccessfulTestPoints(scoreCalculationData);
        points -= calculateTotalPenalty(scoreCalculationData, applySubmissionPolicy);

        points = Math.max(0, points);

        // The score is calculated as a percentage of the maximum points
        return points / scoreCalculationData.exercise().getMaxPoints() * 100.0;
    }

    /**
     * Calculates the total points that should be given for the successful test cases.
     * <p>
     * Additionally, updates the feedback in the result for each passed test case with the points
     * received for that specific test case.
     * <p>
     * Does not apply any penalties to the score yet.
     *
     * @return the total score for this result without penalty deductions.
     */
    private double calculateSuccessfulTestPoints(ScoreCalculationData scoreCalculationData) {
        Set<ProgrammingExerciseTestCase> successfulTestCases = scoreCalculationData.successfulTestCases();
        double successfulTestPoints = successfulTestCases.stream().mapToDouble(test -> calculatePointsForTestCase(test, scoreCalculationData)).sum();

        return capPointsAtMaximum(scoreCalculationData.exercise(), successfulTestPoints);
    }

    /**
     * Caps the points at the maximum achievable number.
     * <p>
     * The cap should be applied before the static code analysis penalty is subtracted as otherwise the penalty won't have any effect in some cases.
     * For example with maxPoints=20, points=30 and penalty=10, a student would still receive the full 20 points, if the points are not
     * capped before the penalty is subtracted. With the implemented order in place points will be capped to 20 points first, then the penalty is subtracted
     * resulting in 10 points.
     *
     * @param programmingExercise Used to determine the maximum allowed number of points.
     * @param points              A number of points that may potentially be higher than allowed.
     * @return The number of points, but no more than the exercise allows for.
     */
    private double capPointsAtMaximum(final ProgrammingExercise programmingExercise, double points) {
        if (Double.isNaN(points)) {
            return 0;
        }

        double maxPoints = programmingExercise.getMaxPoints() + Objects.requireNonNullElse(programmingExercise.getBonusPoints(), 0D);

        return Math.min(points, maxPoints);
    }

    /**
     * Updates the feedback corresponding to the test case with the given credits.
     *
     * @param credits  that should be set in the feedback.
     * @param testCase the feedback that should be updated corresponds to.
     * @param result   from which the result is taken and updated.
     */
    private void setCreditsForTestCaseFeedback(double credits, final ProgrammingExerciseTestCase testCase, final Result result) {
        // We need to compare testcases ignoring the case, because the testcaseRepository is case-insensitive
        result.getFeedbacks().stream().filter(fb -> FeedbackType.AUTOMATIC.equals(fb.getType()) && fb.getText().equalsIgnoreCase(testCase.getTestName())).findFirst()
                .ifPresent(feedback -> feedback.setCredits(credits));
    }

    /**
     * Calculates the points that should be awarded for a successful test case.
     *
     * @param testCase for which the points should be calculated.
     * @return the points which should be awarded for successfully completing the test case.
     */
    private double calculatePointsForTestCase(final ProgrammingExerciseTestCase testCase, ScoreCalculationData scoreCalculationData) {
        final int totalTestCaseCount = scoreCalculationData.testCases().size();

        final boolean isWeightSumZero = Precision.equals(scoreCalculationData.weightSum(), 0, 1E-8);
        final double testPoints;
        double exerciseMaxPoints = scoreCalculationData.exercise().getMaxPoints();

        // In case of a weight-sum of zero the instructor must be able to distinguish between a working solution
        // (all tests passed, 0 points) and a solution with test failures.
        // Only the second case should show a warning while the first case is considered as 100%.
        // Therefore, all test cases have equal weight in such a case.
        if (isWeightSumZero && scoreCalculationData.participation() instanceof SolutionProgrammingExerciseParticipation) {
            testPoints = (1.0 / totalTestCaseCount) * exerciseMaxPoints;
        }
        else if (isWeightSumZero) {
            // this test case must have zero weight as well; avoid division by zero
            testPoints = 0D;
        }
        else {
            double testWeight = testCase.getWeight() * testCase.getBonusMultiplier();
            testPoints = (testWeight / scoreCalculationData.weightSum()) * exerciseMaxPoints;
        }

        return testPoints + testCase.getBonusPoints();
    }

    /**
     * Calculates a total penalty that should be applied to the score.
     * <p>
     * This includes the penalties from static code analysis and of submission policies.
     *
     * @return a total penalty that should be deducted from the score.
     */
    private double calculateTotalPenalty(ScoreCalculationData scoreCalculationData, boolean applySubmissionPolicy) {
        double penalty = 0;
        var exercise = scoreCalculationData.exercise();
        int maxStaticCodeAnalysisPenalty = Optional.ofNullable(exercise.getMaxStaticCodeAnalysisPenalty()).orElse(100);
        if (Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) && maxStaticCodeAnalysisPenalty > 0) {
            penalty += calculateStaticCodeAnalysisPenalty(scoreCalculationData.staticCodeAnalysisFeedback(), exercise);
        }

        if (applySubmissionPolicy && exercise.getSubmissionPolicy() instanceof SubmissionPenaltyPolicy penaltyPolicy) {
            penalty += submissionPolicyService.calculateSubmissionPenalty(scoreCalculationData.participation(), penaltyPolicy);
        }

        return penalty;
    }

    /**
     * Calculates the total penalty over all static code analysis issues.
     * Also updates the credits of each SCA feedback item as a side effect.
     * This allows other parts of Artemis a more simplified score calculation by just summing up all feedback points.
     *
     * @param staticCodeAnalysisFeedback The list of static code analysis feedback
     * @param programmingExercise        The current exercise
     * @return The sum of all penalties, capped at the maximum allowed penalty
     */
    private double calculateStaticCodeAnalysisPenalty(final List<Feedback> staticCodeAnalysisFeedback, final ProgrammingExercise programmingExercise) {
        final var feedbackByCategory = staticCodeAnalysisFeedback.stream().collect(Collectors.groupingBy(Feedback::getStaticCodeAnalysisCategory));
        final double maxExercisePenaltyPoints = Objects.requireNonNullElse(programmingExercise.getMaxStaticCodeAnalysisPenalty(), 100) / 100.0 * programmingExercise.getMaxPoints();
        double overallPenaltyPoints = 0;

        for (var category : staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExercise.getId())) {
            if (!category.getState().equals(CategoryState.GRADED)) {
                continue;
            }

            // get all feedback in this category
            List<Feedback> categoryFeedback = feedbackByCategory.getOrDefault(category.getName(), List.of());

            // calculate the sum of all per-feedback penalties
            double categoryPenaltyPoints = categoryFeedback.size() * category.getPenalty();

            // cap at the maximum allowed penalty for this category
            if (category.getMaxPenalty() != null && categoryPenaltyPoints > category.getMaxPenalty()) {
                categoryPenaltyPoints = category.getMaxPenalty();
            }

            // Cap at the maximum allowed penalty for this exercise (maxStaticCodeAnalysisPenalty is in percent) The max penalty is applied to the maxScore. If no max penalty
            // was supplied, the value defaults to 100 percent. If for example maxScore is 6, maxBonus is 4 and the penalty is 50 percent, then a student can only lose
            // 3 (0.5 * maxScore) points due to static code analysis issues.
            if (overallPenaltyPoints + categoryPenaltyPoints > maxExercisePenaltyPoints) {
                categoryPenaltyPoints = maxExercisePenaltyPoints - overallPenaltyPoints;
            }
            overallPenaltyPoints += categoryPenaltyPoints;

            // update credits of feedbacks in category
            if (!categoryFeedback.isEmpty()) {
                double perFeedbackPenalty = categoryPenaltyPoints / categoryFeedback.size();
                categoryFeedback.forEach(feedback -> feedback.setCredits(-perFeedbackPenalty));
            }
        }

        return overallPenaltyPoints;
    }

    /**
     * Remove all test case feedback information from a result and treat it as if it has a score of 0.
     *
     * @param result                     Result containing all feedback
     * @param staticCodeAnalysisFeedback Static code analysis feedback to keep
     */
    private void removeAllTestCaseFeedbackAndSetScoreToZero(Result result, List<Feedback> staticCodeAnalysisFeedback) {
        result.setFeedbacks(staticCodeAnalysisFeedback);
        result.setScore(0D);
        result.setTestCaseCount(0);
        result.setPassedTestCaseCount(0);
        result.setCodeIssueCount(0);
    }

    /**
     * Calculates the statistics for the grading page.
     *
     * @param exerciseId The current exercise
     * @return The statistics object
     */
    public ProgrammingExerciseGradingStatisticsDTO generateGradingStatistics(Long exerciseId) {
        // number of passed and failed tests per test case
        final var testCases = testCaseRepository.findByExerciseId(exerciseId);
        final var testCaseStatsMap = new HashMap<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats>();
        for (ProgrammingExerciseTestCase testCase : testCases) {
            testCaseStatsMap.put(testCase.getTestName(), new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(0, 0));
        }

        // number of students per amount of detected issues per category
        final Set<StaticCodeAnalysisCategory> categories = staticCodeAnalysisCategoryRepository.findByExerciseId(exerciseId);
        final var categoryIssuesStudentsMap = new HashMap<String, Map<Integer, Integer>>();
        for (StaticCodeAnalysisCategory category : categories) {
            categoryIssuesStudentsMap.put(category.getName(), new HashMap<>());
        }

        final var results = resultRepository.findLatestAutomaticResultsWithEagerFeedbacksForExercise(exerciseId);
        for (Result result : results) {
            // number of detected issues per category for this result
            final var categoryIssuesMap = new HashMap<String, Integer>();
            for (var feedback : result.getFeedbacks()) {
                addFeedbackToStatistics(categoryIssuesMap, testCaseStatsMap, feedback);
            }

            mergeCategoryIssuesMap(categoryIssuesStudentsMap, categoryIssuesMap);
        }

        final var statistics = new ProgrammingExerciseGradingStatisticsDTO();
        statistics.setNumParticipations(results.size());
        statistics.setTestCaseStatsMap(testCaseStatsMap);
        statistics.setCategoryIssuesMap(categoryIssuesStudentsMap);

        return statistics;
    }

    /**
     * Merges the result map of a single student with the overall issues map
     *
     * @param issuesAllStudents   The overall issues map for all students
     * @param issuesSingleStudent The issues map for one student
     */
    private void mergeCategoryIssuesMap(final Map<String, Map<Integer, Integer>> issuesAllStudents, final Map<String, Integer> issuesSingleStudent) {
        for (var entry : issuesSingleStudent.entrySet()) {
            final String category = entry.getKey();
            final Integer issueCount = entry.getValue();

            issuesAllStudents.putIfAbsent(category, new HashMap<>());

            var issuesStudentsMap = issuesAllStudents.get(category);
            issuesStudentsMap.putIfAbsent(issueCount, 0);
            // add 1 to the number of students for the category & issues
            issuesStudentsMap.merge(issueCount, 1, Integer::sum);
        }
    }

    /**
     * Analyses the feedback and updates the statistics maps
     *
     * @param categoryIssuesMap The issues map for sca statistics
     * @param testCaseStatsMap  The map for test case statistics
     * @param feedback          The given feedback object
     */
    private void addFeedbackToStatistics(final Map<String, Integer> categoryIssuesMap, final Map<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats> testCaseStatsMap,
            final Feedback feedback) {
        if (feedback.isStaticCodeAnalysisFeedback()) {
            String categoryName = feedback.getText().substring(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length());
            if (categoryName.isEmpty()) {
                return;
            }
            categoryIssuesMap.compute(categoryName, (category, count) -> count == null ? 1 : count + 1);
        }
        else if (FeedbackType.AUTOMATIC.equals(feedback.getType())) {
            String testName = feedback.getText();
            testCaseStatsMap.putIfAbsent(testName, new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(0, 0));
            testCaseStatsMap.get(testName).updateWithFeedback(feedback);
        }
    }
}
