package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_DUPLICATE_NOTIFICATION;
import static de.tum.in.www1.artemis.domain.ProgrammingSubmission.createFallbackSubmission;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.apache.commons.math3.util.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

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
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseGradingStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseGradingService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGradingService.class);

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingExerciseTestCaseService testCaseService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final ResultRepository resultRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final AuditEventRepository auditEventRepository;

    private final GroupNotificationService groupNotificationService;

    private final ResultService resultService;

    private final ExerciseDateService exerciseDateService;

    private final SubmissionPolicyService submissionPolicyService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService;

    private final ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository;

    private final BuildLogEntryService buildLogService;

    private final TestwiseCoverageService testwiseCoverageService;

    public ProgrammingExerciseGradingService(ProgrammingExerciseTestCaseService testCaseService, ProgrammingSubmissionService programmingSubmissionService,
            StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository, Optional<ContinuousIntegrationService> continuousIntegrationService,
            Optional<VersionControlService> versionControlService, SimpMessageSendingOperations messagingTemplate, StaticCodeAnalysisService staticCodeAnalysisService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            AuditEventRepository auditEventRepository, GroupNotificationService groupNotificationService, ResultService resultService, ExerciseDateService exerciseDateService,
            SubmissionPolicyService submissionPolicyService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService, ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository,
            BuildLogEntryService buildLogService, TestwiseCoverageService testwiseCoverageService) {
        this.testCaseService = testCaseService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.resultRepository = resultRepository;
        this.versionControlService = versionControlService;
        this.messagingTemplate = messagingTemplate;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.auditEventRepository = auditEventRepository;
        this.groupNotificationService = groupNotificationService;
        this.resultService = resultService;
        this.submissionPolicyService = submissionPolicyService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.exerciseDateService = exerciseDateService;
        this.programmingExerciseGitDiffReportService = programmingExerciseGitDiffReportService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
        this.buildLogService = buildLogService;
        this.testwiseCoverageService = testwiseCoverageService;
    }

    /**
     * Uses the given requestBody to extract the relevant information from it.
     * Fetches and attaches the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation the participation for which the build was finished
     * @param requestBody   RequestBody containing the build result and its feedback items
     * @return result after compilation
     */
    public Optional<Result> processNewProgrammingExerciseResult(@NotNull ProgrammingExerciseParticipation participation, @NotNull Object requestBody) {
        log.debug("Received new build result (NEW) for participation {}", participation.getId());

        Result newResult = null;
        try {
            var buildResult = continuousIntegrationService.get().convertBuildResult(requestBody);
            newResult = continuousIntegrationService.get().createResultFromBuildResult(buildResult, participation);

            // Fetch submission or create a fallback
            var latestSubmission = getSubmissionForBuildResult(participation.getId(), buildResult).orElseGet(() -> createAndSaveFallbackSubmission(participation, buildResult));
            latestSubmission.setBuildFailed("No tests found".equals(newResult.getResultString()));
            // Add artifacts to submission
            latestSubmission.setBuildArtifact(buildResult.hasArtifact());

            if (buildResult.hasLogs()) {
                var programmingLanguage = participation.getProgrammingExercise().getProgrammingLanguage();
                var buildLogs = buildResult.extractBuildLogs(programmingLanguage);

                continuousIntegrationService.get().extractBuildLogStatistics(latestSubmission, buildLogs);

                if (!buildResult.isBuildSuccessful()) {
                    buildLogs = buildLogService.removeUnnecessaryLogsForProgrammingLanguage(buildLogs, programmingLanguage);
                    var savedBuildLogs = buildLogService.saveBuildLogs(buildLogs, latestSubmission);

                    // Set the received logs in order to avoid duplicate entries (this removes existing logs)
                    latestSubmission.setBuildLogEntries(savedBuildLogs);
                }
            }

            // Note: we only set one side of the relationship because we don't know yet whether the result will actually be saved
            newResult.setSubmission(latestSubmission);
            newResult.setRatedIfNotExceeded(exerciseDateService.getDueDate(participation).orElse(null), latestSubmission);
            // NOTE: the result is not saved yet, but is connected to the submission, the submission is not completely saved yet
        }
        catch (ContinuousIntegrationException ex) {
            log.error("Result for participation " + participation.getId() + " could not be created", ex);
        }

        return Optional.ofNullable(newResult).map(result -> processNewProgrammingExerciseResult(participation, result));
    }

    /**
     * Retrieves the submission that is assigned to the specified participation and its commit hash matches the one from the build result.
     *
     * @param participationId id of the participation
     * @param buildResult     The build results
     * @return The submission or empty no submissions exist
     */
    protected Optional<ProgrammingSubmission> getSubmissionForBuildResult(Long participationId, AbstractBuildResultNotificationDTO buildResult) {
        var submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId);
        if (submissions.isEmpty()) {
            return Optional.empty();
        }

        return submissions.stream().filter(theSubmission -> {
            var commitHash = buildResult.getCommitHash(theSubmission.getType());
            return commitHash.isPresent() && commitHash.get().equals(theSubmission.getCommitHash());
        }).max(Comparator.comparing(ProgrammingSubmission::getSubmissionDate));
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
                submissionDate = versionControlService.get().getPushDate(participation, commitHash.get(), null);
            }
            catch (VersionControlException e) {
                log.error("Could not retrieve push date for participation " + participation.getId() + " and build plan " + participation.getBuildPlanId(), e);
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
     * @param newResult that contains the build result with its feedbacks.
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

        if (isSolutionParticipation) {
            // If the solution participation was updated, also trigger the template participation build.
            // This method will return without triggering the build if the submission is not of type TEST.
            triggerTemplateBuildIfTestCasesChanged(programmingExercise.getId(), programmingSubmission);

            // the test cases and the submission have been saved to the database previously, therefore we can add the reference to the coverage reports
            if (Boolean.TRUE.equals(programmingExercise.isTestwiseCoverageEnabled()) && Boolean.TRUE.equals(processedResult.isSuccessful())) {
                testwiseCoverageService.createTestwiseCoverageReport(newResult.getCoverageFileReportsByTestCaseName(), programmingExercise, programmingSubmission);
            }
        }

        if (isStudentParticipation) {
            // When a student receives a new result, we want to check whether we need to lock the participation
            // repository when a lock repository policy is present. At this point, we know that the programming
            // exercise exists.
            SubmissionPolicy submissionPolicy = programmingExerciseRepository.findWithSubmissionPolicyById(programmingExercise.getId()).get().getSubmissionPolicy();
            if (submissionPolicy instanceof LockRepositoryPolicy policy) {
                submissionPolicyService.handleLockRepositoryPolicy(processedResult, (Participation) participation, policy);
            }

            if (programmingSubmission.getLatestResult() != null && programmingSubmission.getLatestResult().isManual()) {
                // Note: in this case, we do not want to save the processedResult, but we only want to update the latest semi-automatic one
                Result updatedLatestSemiAutomaticResult = updateLatestSemiAutomaticResultWithNewAutomaticFeedback(programmingSubmission.getLatestResult().getId(), processedResult,
                        programmingExercise);
                // Adding back dropped submission
                updatedLatestSemiAutomaticResult.setSubmission(programmingSubmission);
                programmingSubmissionRepository.save(programmingSubmission);
                resultRepository.save(updatedLatestSemiAutomaticResult);

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
     *
     * Note: for the second correction it is important that we do not create additional semi-automatic results
     *
     * @param lastSemiAutomaticResultId The latest manual result for the same submission (which must exist in the database)
     * @param newAutomaticResult The new automatic result
     * @param programmingExercise The programming exercise
     * @return The updated semi-automatic result
     */
    private Result updateLatestSemiAutomaticResultWithNewAutomaticFeedback(long lastSemiAutomaticResultId, Result newAutomaticResult, ProgrammingExercise programmingExercise) {
        // Note: refetch the semi-automatic result with feedback and assessor
        var latestSemiAutomaticResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(lastSemiAutomaticResultId).get();
        // this makes it the most recent result, but optionally keeps the draft state of an unfinished manual result
        latestSemiAutomaticResult.setCompletionDate(latestSemiAutomaticResult.getCompletionDate() != null ? newAutomaticResult.getCompletionDate() : null);

        // remove old automatic feedback
        latestSemiAutomaticResult.getFeedbacks().removeIf(feedback -> feedback != null && feedback.getType() == FeedbackType.AUTOMATIC);

        // copy all feedback from the automatic result
        List<Feedback> copiedFeedbacks = newAutomaticResult.getFeedbacks().stream().map(Feedback::copyFeedback).toList();
        latestSemiAutomaticResult = resultService.addFeedbackToResult(latestSemiAutomaticResult, copiedFeedbacks, false);

        String resultString = updateManualResultString(newAutomaticResult.getResultString(), latestSemiAutomaticResult, programmingExercise);
        latestSemiAutomaticResult.setResultString(resultString);

        return resultRepository.save(latestSemiAutomaticResult);
    }

    /**
     * Trigger the build of the template repository, if the submission of the provided result is of type TEST.
     * Will use the commitHash of the submission for triggering the template build.
     *
     * If the submission of the provided result is not of type TEST, the method will return without triggering the build.
     *
     * @param programmingExerciseId ProgrammingExercise id that belongs to the result.
     * @param submission            ProgrammingSubmission
     */
    private void triggerTemplateBuildIfTestCasesChanged(long programmingExerciseId, ProgrammingSubmission submission) {
        // We only trigger the template build when the test repository was changed.
        // If the submission is from type TEST but already has a result, this build was not triggered by a test repository change
        if (!submission.belongsToTestRepository() || (submission.belongsToTestRepository() && submission.getResults() != null && !submission.getResults().isEmpty())) {
            return;
        }
        try {
            programmingSubmissionService.triggerTemplateBuildAndNotifyUser(programmingExerciseId, submission.getCommitHash(), SubmissionType.TEST);
        }
        catch (EntityNotFoundException ex) {
            // If for some reason the programming exercise does not have a template participation, we can only log and abort.
            log.error(
                    "Could not trigger the build of the template repository for the programming exercise id {} because no template participation could be found for the given exercise",
                    programmingExerciseId);
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
     * Updates an incoming result with the information of the exercises test cases. This update includes:
     * - Checking which test cases were not executed as this is not part of the bamboo build (not all test cases are executed in an exercise with sequential test runs)
     * - Checking the due date and the visibility.
     * - Recalculating the score based on the successful test cases weight vs the total weight of all test cases.
     *
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not change
     *
     * @param result   to modify with new score, result string & added feedbacks (not executed tests)
     * @param exercise the result belongs to.
     * @param isStudentParticipation boolean flag indicating weather the participation of the result is not a solution/template participation.
     * @return Result with updated feedbacks, score and result string.
     */
    public Result calculateScoreForResult(Result result, ProgrammingExercise exercise, boolean isStudentParticipation) {
        Set<ProgrammingExerciseTestCase> testCases = testCaseService.findActiveByExerciseId(exercise.getId());
        final Set<ProgrammingExerciseTestCase> testCasesForCurrentDate;
        // We don't filter the test cases for the solution/template participation's results as they are used as indicators for the instructor!
        if (isStudentParticipation) {
            testCasesForCurrentDate = filterTestCasesForCurrentDate(result.getParticipation(), testCases);
        }
        else {
            testCasesForCurrentDate = testCases;
        }
        return calculateScoreForResult(testCases, testCasesForCurrentDate, result, exercise, isStudentParticipation);
    }

    /**
     * Updates <b>all</b> latest results of the given exercise with the information of the exercises test cases.
     * <p>
     * This update includes:
     * <ul>
     *     <li>Checking which test cases were not executed as this is not part of the bamboo build (not all test cases are executed in an exercise with sequential test runs).</li>
     *     <li>Checking the due date and the visibility.</li>
     *     <li>Recalculating the score based on the successful test cases weight vs the total weight of all test cases.</li>
     * </ul>
     *
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not change.
     *
     * @param exercise whose results should be updated.
     * @return the results of the exercise that have been updated.
     */
    public List<Result> updateAllResults(final ProgrammingExercise exercise) {
        final Set<ProgrammingExerciseTestCase> testCases = testCaseService.findActiveByExerciseId(exercise.getId());

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
     * @param exercise whose results should be updated.
     * @return the results of the exercise that have been updated.
     */
    public List<Result> updateResultsOnlyRegularDueDateParticipations(final ProgrammingExercise exercise) {
        final Set<ProgrammingExerciseTestCase> testCases = testCaseService.findActiveByExerciseId(exercise.getId());

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
     * @param participation for which the results should be updated.
     * @return a list of updated results (maximum two: latest automatic, and latest manual result).
     */
    public List<Result> updateParticipationResults(final ProgrammingExerciseStudentParticipation participation) {
        final ProgrammingExercise exercise = participation.getProgrammingExercise();
        final Set<ProgrammingExerciseTestCase> testCases = testCaseService.findActiveByExerciseId(exercise.getId());
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
     * @param exercise the participations belong to.
     * @param allTestCases of the programming exercise.
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
     * @param exercise the template and solution belong to.
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
     * @param exercise the participation belongs to.
     * @param participation of a student in the exercise.
     * @param allTestCases of this exercise.
     * @param testCasesBeforeDueDate the test cases that are visible to the student before the due date.
     * @param testCasesAfterDueDate the test cases that are visible to the student after the due date.
     * @param applySubmissionPolicy true, if submission policies should be taken into account when updating the score.
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
     * @param user who triggered the re-evaluation.
     * @param exercise for which the evaluation was triggered.
     * @param course the exercise belongs to.
     * @param results of the exercise.
     */
    public void logReEvaluate(User user, ProgrammingExercise exercise, Course course, List<Result> results) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.RE_EVALUATE_RESULTS, "exercise=" + exercise.getTitle(), "course=" + course.getTitle(),
                "results=" + results.size());
        auditEventRepository.add(auditEvent);
        log.info("User {} triggered a re-evaluation of {} results for exercise {} with id {}", user.getLogin(), results.size(), exercise.getTitle(), exercise.getId());
    }

    /**
     * Filter all test cases from the score calculation that are never visible or ones with visibility "after due date" if the due date has not yet passed.
     * @param testCases which should be filtered.
     * @return testCases, but the ones based on the described visibility criterion removed.
     */
    private Set<ProgrammingExerciseTestCase> filterTestCasesForCurrentDate(Participation participation, Set<ProgrammingExerciseTestCase> testCases) {
        boolean isBeforeDueDate = exerciseDateService.isBeforeDueDate(participation);
        return filterTestCasesForStudents(testCases, isBeforeDueDate);
    }

    /**
     * Filters the test cases to only include the ones a student should be able to see.
     * @param testCases all test cases of an exercise.
     * @param isBeforeDueDate true, if the due date has not yet passed.
     * @return a set of test cases that are visible to the student.
     */
    private Set<ProgrammingExerciseTestCase> filterTestCasesForStudents(final Set<ProgrammingExerciseTestCase> testCases, boolean isBeforeDueDate) {
        return testCases.stream().filter(testCase -> !testCase.isInvisible()).filter(testCase -> !(isBeforeDueDate && testCase.isAfterDueDate())).collect(Collectors.toSet());
    }

    /**
     * Calculates the grading for a result and updates the feedbacks
     * @param testCases All test cases for the exercise
     * @param testCasesForCurrentDate Test cases for the exercise for the current date
     * @param result The result to be updated
     * @param exercise The current exercise
     * @param applySubmissionPolicy true, if submission policies should be taken into account when updating the score.
     * @return The updated result
     */
    private Result calculateScoreForResult(Set<ProgrammingExerciseTestCase> testCases, Set<ProgrammingExerciseTestCase> testCasesForCurrentDate, @NotNull Result result,
            ProgrammingExercise exercise, boolean applySubmissionPolicy) {
        List<Feedback> testCaseFeedback = new ArrayList<>();
        List<Feedback> staticCodeAnalysisFeedback = new ArrayList<>();
        for (var feedback : result.getFeedbacks()) {
            if (feedback.getType() != FeedbackType.AUTOMATIC) {
                continue;
            }

            if (feedback.isStaticCodeAnalysisFeedback()) {
                staticCodeAnalysisFeedback.add(feedback);
            }
            else {
                testCaseFeedback.add(feedback);
            }
        }

        // Remove feedback that is in an invisible sca category
        staticCodeAnalysisFeedback = staticCodeAnalysisService.categorizeScaFeedback(result, staticCodeAnalysisFeedback, exercise);

        if (applySubmissionPolicy) {
            SubmissionPolicy submissionPolicy = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exercise.getId()).getSubmissionPolicy();
            exercise.setSubmissionPolicy(submissionPolicy);
        }

        // Case 1: There are tests and test case feedback, find out which tests were not executed or should only count to the score after the due date.
        if (!testCasesForCurrentDate.isEmpty() && !testCaseFeedback.isEmpty() && !result.getFeedbacks().isEmpty()) {
            retainAutomaticFeedbacksWithTestCase(result, testCases);

            // Copy the visibility from test case to corresponding feedback
            setVisibilityForFeedbacksWithTestCase(result, testCases);

            // Add feedbacks for tests that were not executed ("test was not executed").
            createFeedbackForNotExecutedTests(result, testCasesForCurrentDate);

            // Add feedbacks for all duplicate test cases
            boolean hasDuplicateTestCases = createFeedbackForDuplicateTests(result, exercise);

            // Add feedback if submission penalty policy is active
            if (exercise.getSubmissionPolicy() instanceof SubmissionPenaltyPolicy penaltyPolicy) {
                submissionPolicyService.createFeedbackForPenaltyPolicy(result, penaltyPolicy);
            }

            // The score is always calculated from ALL (except visibility=never) test cases, regardless of the current date!
            final Set<ProgrammingExerciseTestCase> successfulTestCases = testCasesForCurrentDate.stream().filter(isSuccessful(result)).collect(Collectors.toSet());
            updateScore(result, testCases, successfulTestCases, staticCodeAnalysisFeedback, exercise, hasDuplicateTestCases, applySubmissionPolicy);
            updateResultString(result, testCasesForCurrentDate, successfulTestCases, staticCodeAnalysisFeedback, exercise, hasDuplicateTestCases, applySubmissionPolicy);
        }
        // Case 2: There are no test cases that are executed before the due date has passed. We need to do this to differentiate this case from a build error.
        else if (!testCases.isEmpty() && !result.getFeedbacks().isEmpty() && !testCaseFeedback.isEmpty()) {
            addFeedbackTestsNotExecuted(result, exercise, staticCodeAnalysisFeedback, applySubmissionPolicy);
        }
        // Case 3: If there is no test case feedback, the build has failed, or it has previously fallen under case 2. In this case we just return the original result without
        // changing it.

        return result;
    }

    /**
     * Adds the appropriate feedback to the result in case the automatic tests were not executed.
     * @param result to which the feedback should be added.
     * @param exercise to which the result belongs to.
     * @param staticCodeAnalysisFeedback that has been created for this result.
     * @param applySubmissionPolicy if the submission policy of the exercise should be applied.
     */
    private void addFeedbackTestsNotExecuted(final Result result, final ProgrammingExercise exercise, final List<Feedback> staticCodeAnalysisFeedback,
            boolean applySubmissionPolicy) {
        removeAllTestCaseFeedbackAndSetScoreToZero(result, staticCodeAnalysisFeedback);

        // Add feedbacks for all duplicate test cases
        boolean hasDuplicateTestCases = createFeedbackForDuplicateTests(result, exercise);

        // In this case, test cases won't be displayed but static code analysis feedback must be shown in the result string.
        updateResultString(result, Set.of(), Set.of(), staticCodeAnalysisFeedback, exercise, hasDuplicateTestCases, applySubmissionPolicy);
    }

    /**
     * Only keeps automatic feedbacks that also are associated with a test case.
     *
     * Does not remove static code analysis feedback.
     *
     * @param result of the build run.
     * @param testCases of the programming exercise.
     */
    private void retainAutomaticFeedbacksWithTestCase(Result result, final Set<ProgrammingExerciseTestCase> testCases) {
        // Remove automatic feedbacks not associated with test cases
        result.getFeedbacks().removeIf(feedback -> feedback.getType() == FeedbackType.AUTOMATIC && !feedback.isStaticCodeAnalysisFeedback()
                && testCases.stream().noneMatch(test -> test.getTestName().equalsIgnoreCase(feedback.getText())));

        // If there are no feedbacks left after filtering those not valid, also setHasFeedback to false.
        if (result.getFeedbacks().stream().noneMatch(feedback -> Boolean.FALSE.equals(feedback.isPositive())
                || feedback.getType() != null && (feedback.getType().equals(FeedbackType.MANUAL) || feedback.getType().equals(FeedbackType.MANUAL_UNREFERENCED)))) {
            result.setHasFeedback(false);
        }
    }

    /**
     * Sets the visibility on all feedbacks associated with a test case with the same name.
     * @param result of the build run.
     * @param allTests of the given programming exercise.
     */
    private void setVisibilityForFeedbacksWithTestCase(Result result, final Set<ProgrammingExerciseTestCase> allTests) {
        for (Feedback feedback : result.getFeedbacks()) {
            allTests.stream().filter(testCase -> testCase.getTestName().equalsIgnoreCase(feedback.getText())).findFirst()
                    .ifPresent(testCase -> feedback.setVisibility(testCase.getVisibility()));
        }
    }

    /**
     * Checks which tests were not executed and add a new Feedback for them to the exercise.
     * @param result   of the build run.
     * @param allTests of the given programming exercise.
     */
    private void createFeedbackForNotExecutedTests(Result result, Set<ProgrammingExerciseTestCase> allTests) {
        List<Feedback> feedbacksForNotExecutedTestCases = allTests.stream().filter(wasNotExecuted(result))
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
     */
    private boolean createFeedbackForDuplicateTests(Result result, ProgrammingExercise programmingExercise) {
        Set<String> uniqueFeedbackNames = new HashSet<>();
        // Find duplicate test cases from feedback which is automatic feedback
        Set<String> duplicateFeedbackNames = result.getFeedbacks().stream()
                .filter(feedback -> !feedback.isStaticCodeAnalysisFeedback() && FeedbackType.AUTOMATIC.equals(feedback.getType())).map(Feedback::getText)
                // Set.add() returns false if the lowerCase element was already in the set, this is how we find all duplicates
                .filter(feedbackName -> !uniqueFeedbackNames.add(feedbackName.toLowerCase())).collect(Collectors.toSet());

        if (!duplicateFeedbackNames.isEmpty()) {
            String duplicateDetailText = "This is a duplicate test case. Please review all your test cases and verify that your test cases have unique names!";
            List<Feedback> feedbacksForDuplicateTestCases = duplicateFeedbackNames.stream()
                    .map(feedbackName -> new Feedback().type(FeedbackType.AUTOMATIC).text(feedbackName + " - Duplicate Test Case!").detailText(duplicateDetailText).positive(false))
                    .toList();
            result.addFeedbacks(feedbacksForDuplicateTestCases);

            // Enables to view the result details in case all test cases are positive
            result.setHasFeedback(true);
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
     * @param result                     of the build run.
     * @param allTestCases               of a given programming exercise.
     * @param successfulTestCases        test cases with positive feedback.
     * @param staticCodeAnalysisFeedback of a given programming exercise.
     * @param programmingExercise        the given programming exercise.
     * @param hasDuplicateTestCases      indicates duplicate test cases.
     */
    private void updateScore(final Result result, final Set<ProgrammingExerciseTestCase> allTestCases, final Set<ProgrammingExerciseTestCase> successfulTestCases,
            final List<Feedback> staticCodeAnalysisFeedback, final ProgrammingExercise programmingExercise, boolean hasDuplicateTestCases, boolean applySubmissionPolicy) {
        if (hasDuplicateTestCases) {
            result.setScore(0D);
        }
        else {
            double score = calculateScore(programmingExercise, allTestCases, result, successfulTestCases, staticCodeAnalysisFeedback, applySubmissionPolicy);
            result.setScore(score);
        }

        result.getFeedbacks().forEach(feedback -> {
            if (feedback.getCredits() == null) {
                feedback.setCredits(0D);
            }
        });
    }

    /**
     * Calculates the score of automatic test cases for the given result with possible penalties applied.
     * @param programmingExercise the result belongs to.
     * @param allTests that should be considered in the score calculation.
     * @param result for which a score should be calculated.
     * @param successfulTestCases all test cases that passed for the submission.
     * @param staticCodeAnalysisFeedback that has been created for the submission.
     * @param applySubmissionPolicy true, if penalties from submission policies should be applied.
     * @return the final total score that should be given to the result.
     */
    private double calculateScore(final ProgrammingExercise programmingExercise, final Set<ProgrammingExerciseTestCase> allTests, final Result result,
            final Set<ProgrammingExerciseTestCase> successfulTestCases, final List<Feedback> staticCodeAnalysisFeedback, boolean applySubmissionPolicy) {
        if (successfulTestCases.isEmpty()) {
            return 0;
        }

        final double weightSum = allTests.stream().filter(testCase -> !testCase.isInvisible()).mapToDouble(ProgrammingExerciseTestCase::getWeight).sum();

        double successfulTestPoints = calculateSuccessfulTestPoints(programmingExercise, result, successfulTestCases, allTests.size(), weightSum);
        successfulTestPoints -= calculateTotalPenalty(programmingExercise, result.getParticipation(), staticCodeAnalysisFeedback, applySubmissionPolicy);

        if (successfulTestPoints < 0) {
            successfulTestPoints = 0;
        }

        // The score is calculated as a percentage of the maximum points
        return successfulTestPoints / programmingExercise.getMaxPoints() * 100.0;
    }

    /**
     * Calculates the total points that should be given for the successful test cases.
     *
     * Additionally, updates the feedback in the result for each passed test case with the points
     * received for that specific test case.
     *
     * Does not apply any penalties to the score yet.
     *
     * @param programmingExercise which the result belongs to.
     * @param result for which the points should be calculated.
     * @param successfulTestCases all test cases the submission passed.
     * @param totalTestCaseCount the total number of relevant test cases. This might not be the total
     *                           number of test cases in the exercise as some test cases are ignored
     *                           for the calculation before the exercise due date.
     * @param weightSum the sum of test case weights of all test cases that have to be considered.
     * @return the total score for this result without penalty deductions.
     */
    private double calculateSuccessfulTestPoints(final ProgrammingExercise programmingExercise, final Result result, final Set<ProgrammingExerciseTestCase> successfulTestCases,
            int totalTestCaseCount, double weightSum) {
        double successfulTestPoints = successfulTestCases.stream().mapToDouble(test -> {
            double credits = calculatePointsForTestCase(result, programmingExercise, test, totalTestCaseCount, weightSum);
            setCreditsForTestCaseFeedback(result, test, credits);
            return credits;
        }).sum();

        return capPointsAtMaximum(programmingExercise, successfulTestPoints);
    }

    /**
     * Caps the points at the maximum achievable number.
     *
     * The cap should be applied before the static code analysis penalty is subtracted as otherwise the penalty won't have any effect in some cases.
     * For example with maxPoints=20, points=30 and penalty=10, a student would still receive the full 20 points, if the points are not
     * capped before the penalty is subtracted. With the implemented order in place points will be capped to 20 points first, then the penalty is subtracted
     * resulting in 10 points.
     *
     * @param programmingExercise Used to determine the maximum allowed number of points.
     * @param points A number of points that may potentially be higher than allowed.
     * @return The number of points, but no more than the exercise allows for.
     */
    private double capPointsAtMaximum(final ProgrammingExercise programmingExercise, double points) {
        double maxPoints = programmingExercise.getMaxPoints() + Optional.ofNullable(programmingExercise.getBonusPoints()).orElse(0.0);
        double cappedPoints = points;

        if (Double.isNaN(points)) {
            cappedPoints = 0;
        }
        else if (points > maxPoints) {
            cappedPoints = maxPoints;
        }

        return cappedPoints;
    }

    /**
     * Updates the feedback corresponding to the test case with the given credits.
     * @param result which should be updated.
     * @param testCase the feedback that should be updated corresponds to.
     * @param credits that should be set in the feedback.
     */
    private void setCreditsForTestCaseFeedback(final Result result, final ProgrammingExerciseTestCase testCase, double credits) {
        // We need to compare testcases ignoring the case, because the testcaseRepository is case-insensitive
        result.getFeedbacks().stream().filter(fb -> FeedbackType.AUTOMATIC.equals(fb.getType()) && fb.getText().equalsIgnoreCase(testCase.getTestName())).findFirst()
                .ifPresent(feedback -> feedback.setCredits(credits));
    }

    /**
     * Calculates the points that should be awarded for a successful test case.
     * @param result used to determine if the calculation is performed for the solution.
     * @param programmingExercise the result belongs to.
     * @param test for which the points should be calculated.
     * @param totalTestCaseCount in the given exercise.
     * @param weightSum of all test cases in the exercise.
     * @return the points which should be awarded for successfully completing the test case.
     */
    private double calculatePointsForTestCase(final Result result, final ProgrammingExercise programmingExercise, final ProgrammingExerciseTestCase test, int totalTestCaseCount,
            double weightSum) {
        final boolean isWeightSumZero = Precision.equals(weightSum, 0, 1E-8);
        final double testPoints;

        // A weight-sum of zero would let the solution show an error to the instructor as the solution score must be
        // 100% of all reachable points. To prevent this, we weigh all test cases equally in such a case.
        if (isWeightSumZero && result.getParticipation() instanceof SolutionProgrammingExerciseParticipation) {
            testPoints = (1.0 / totalTestCaseCount) * programmingExercise.getMaxPoints();
        }
        else if (isWeightSumZero) {
            // this test case must have zero weight as well; avoid division by zero
            testPoints = 0D;
        }
        else {
            double testWeight = test.getWeight() * test.getBonusMultiplier();
            testPoints = (testWeight / weightSum) * programmingExercise.getMaxPoints();
        }

        return testPoints + test.getBonusPoints();
    }

    /**
     * Calculates a total penalty that should be applied to the score.
     *
     * This includes the penalties from static code analysis and of submission policies.
     *
     * @param programmingExercise the participation belongs to.
     * @param participation for which should be checked for possible penalties.
     * @param staticCodeAnalysisFeedback automatic feedback from static code analysis.
     * @param applySubmissionPolicy determines if the submission policy should be applied.
     * @return a total penalty that should be deducted from the score.
     */
    private double calculateTotalPenalty(final ProgrammingExercise programmingExercise, final Participation participation, final List<Feedback> staticCodeAnalysisFeedback,
            boolean applySubmissionPolicy) {
        double penalty = 0;

        int maxStaticCodeAnalysisPenalty = Optional.ofNullable(programmingExercise.getMaxStaticCodeAnalysisPenalty()).orElse(100);
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && maxStaticCodeAnalysisPenalty > 0) {
            penalty += calculateStaticCodeAnalysisPenalty(staticCodeAnalysisFeedback, programmingExercise);
        }

        if (applySubmissionPolicy && programmingExercise.getSubmissionPolicy() instanceof SubmissionPenaltyPolicy penaltyPolicy) {
            penalty += submissionPolicyService.calculateSubmissionPenalty(participation, penaltyPolicy);
        }

        return penalty;
    }

    /**
     * Calculates the total penalty over all static code analysis issues
     * @param staticCodeAnalysisFeedback The list of static code analysis feedback
     * @param programmingExercise The current exercise
     * @return The sum of all penalties, capped at the maximum allowed penalty
     */
    private double calculateStaticCodeAnalysisPenalty(final List<Feedback> staticCodeAnalysisFeedback, final ProgrammingExercise programmingExercise) {
        final var feedbackByCategory = staticCodeAnalysisFeedback.stream().collect(Collectors.groupingBy(Feedback::getStaticCodeAnalysisCategory));
        double codeAnalysisPenaltyPoints = 0;

        for (var category : staticCodeAnalysisService.findByExerciseId(programmingExercise.getId())) {
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

            // update credits of feedbacks in category
            if (!categoryFeedback.isEmpty()) {
                double perFeedbackPenalty = categoryPenaltyPoints / categoryFeedback.size();
                categoryFeedback.forEach(feedback -> feedback.setCredits(-perFeedbackPenalty));
            }

            codeAnalysisPenaltyPoints += categoryPenaltyPoints;
        }

        /*
         * Cap at the maximum allowed penalty for this exercise (maxStaticCodeAnalysisPenalty is in percent) The max penalty is applied to the maxScore. If no max penalty was
         * supplied, the value defaults to 100 percent. If for example maxScore is 6, maxBonus is 4 and the penalty is 50 percent, then a student can only lose 3 (0.5 * maxScore)
         * points due to static code analysis issues.
         */
        final var maxExercisePenaltyPoints = (double) Optional.ofNullable(programmingExercise.getMaxStaticCodeAnalysisPenalty()).orElse(100) / 100.0
                * programmingExercise.getMaxPoints();
        if (codeAnalysisPenaltyPoints > maxExercisePenaltyPoints) {
            codeAnalysisPenaltyPoints = maxExercisePenaltyPoints;
        }

        return codeAnalysisPenaltyPoints;
    }

    /**
     * Update the result's result string given the successful tests vs. all tests (x of y passed).
     * @param result                of the build run.
     * @param allTestCases          of the given programming exercise.
     * @param successfulTestCases   test cases with positive feedback.
     * @param scaFeedback           for the result
     * @param exercise              to which this result and the test cases belong
     * @param hasDuplicateTestCases indicates duplicate test cases
     */
    private void updateResultString(final Result result, final Set<ProgrammingExerciseTestCase> allTestCases, final Set<ProgrammingExerciseTestCase> successfulTestCases,
            final List<Feedback> scaFeedback, final ProgrammingExercise exercise, boolean hasDuplicateTestCases, boolean applySubmissionPolicy) {
        if (hasDuplicateTestCases) {
            result.setResultString("Error: Found duplicated tests!");
        }
        else {
            // Create a new result string that reflects passed, failed & not executed test cases.
            String newResultString = successfulTestCases.size() + " of " + allTestCases.size() + " passed";

            // Show number of found quality issues if static code analysis is enabled
            if (Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled())) {
                String issueTerm = scaFeedback.size() == 1 ? ", 1 issue" : ", " + scaFeedback.size() + " issues";
                newResultString += issueTerm;
            }

            if (applySubmissionPolicy) {
                newResultString += submissionPolicyService.calculateResultStringAttachment(exercise, result.getParticipation());
            }

            if (result.isManual()) {
                newResultString = updateManualResultString(newResultString, result, exercise);
            }
            result.setResultString(newResultString);
        }
    }

    /**
     * Update the result string of a manual result with the achieved points.
     * @param resultString The automatic part of the result string
     * @param result The result to add the result string
     * @param exercise The programming exercise
     * @return The updated result string
     */
    private String updateManualResultString(String resultString, Result result, ProgrammingExercise exercise) {
        // Calculate different scores for totalScore calculation and add points and maxScore to result string
        double maxScore = exercise.getMaxPoints();
        double points = result.calculateTotalPointsForProgrammingExercises();
        result.setScore(points, maxScore);
        return resultString + ", " + result.createResultString(points, maxScore);
    }

    /**
     * Remove all test case feedback information from a result and treat it as if it has a score of 0.
     * @param result Result containing all feedback
     * @param staticCodeAnalysisFeedback Static code analysis feedback to keep
     */
    private void removeAllTestCaseFeedbackAndSetScoreToZero(Result result, List<Feedback> staticCodeAnalysisFeedback) {
        result.setFeedbacks(staticCodeAnalysisFeedback);
        result.hasFeedback(!staticCodeAnalysisFeedback.isEmpty());
        result.setScore(0D);
    }

    /**
     * Check if the provided test was found in the result's feedbacks with positive = true.
     * @param result of the build run.
     * @return true if there is a positive feedback for a given test.
     */
    private Predicate<ProgrammingExerciseTestCase> isSuccessful(Result result) {
        // We need to compare testcases via lowercase, because the testcaseRepository is case-insensitive
        return testCase -> result.getFeedbacks().stream()
                .anyMatch(feedback -> feedback.getText() != null && feedback.getText().equalsIgnoreCase(testCase.getTestName()) && Boolean.TRUE.equals(feedback.isPositive()));
    }

    /**
     * Check if the provided test was not found in the result's feedbacks.
     * @param result of the build run.
     * @return true if there is no feedback for a given test.
     */
    private Predicate<ProgrammingExerciseTestCase> wasNotExecuted(Result result) {
        // We need to compare testcases via lowercase, because the testcaseRepository is case-insensitive
        return testCase -> result.getFeedbacks().stream()
                .noneMatch(feedback -> feedback.getType() == FeedbackType.AUTOMATIC && feedback.getText().equalsIgnoreCase(testCase.getTestName()));
    }

    /**
     * Calculates the statistics for the grading page.
     * @param exerciseId The current exercise
     * @return The statistics object
     */
    public ProgrammingExerciseGradingStatisticsDTO generateGradingStatistics(Long exerciseId) {
        // number of passed and failed tests per test case
        final var testCases = testCaseService.findByExerciseId(exerciseId);
        final var testCaseStatsMap = new HashMap<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats>();
        for (ProgrammingExerciseTestCase testCase : testCases) {
            testCaseStatsMap.put(testCase.getTestName(), new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(0, 0));
        }

        // number of students per amount of detected issues per category
        final var categories = staticCodeAnalysisService.findByExerciseId(exerciseId);
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
     * @param issuesAllStudents The overall issues map for all students
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
     * @param categoryIssuesMap The issues map for sca statistics
     * @param testCaseStatsMap The map for test case statistics
     * @param feedback The given feedback object
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
