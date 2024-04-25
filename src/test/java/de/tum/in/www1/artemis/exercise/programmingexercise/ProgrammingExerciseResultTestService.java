package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_TOPIC;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.hestia.TestwiseCoverageTestUtil;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.BuildLogEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.web.rest.dto.ResultDTO;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for both scenarios:
 * 1) Jenkins + Gitlab
 */
@Service
public class ProgrammingExerciseResultTestService {

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private BuildLogEntryRepository buildLogEntryRepository;

    @Autowired
    private ProgrammingExerciseGradingService gradingService;

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository participationRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private GitService gitService;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExercise programmingExerciseWithStaticCodeAnalysis;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipationStaticCodeAnalysis;

    private String userPrefix;

    public void setup(String userPrefix) {
        this.userPrefix = userPrefix;
        userUtilService.addUsers(userPrefix, 2, 2, 0, 2);
        setupForProgrammingLanguage(ProgrammingLanguage.JAVA);
    }

    public void setupForProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, programmingLanguage);
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        programmingExerciseWithStaticCodeAnalysis = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, true, false, programmingLanguage);
        staticCodeAnalysisService.createDefaultCategories(programmingExerciseWithStaticCodeAnalysis);
        // This is done to avoid an unproxy issue in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student1");
        programmingExerciseStudentParticipationStaticCodeAnalysis = participationUtilService
                .addStudentParticipationForProgrammingExercise(programmingExerciseWithStaticCodeAnalysis, userPrefix + "student2");
    }

    public void tearDown() {
    }

    // Test
    public void shouldUpdateFeedbackInSemiAutomaticResult(AbstractBuildResultNotificationDTO buildResultNotification, String loginName) throws Exception {
        // Make sure we only have one participation
        participationRepository.deleteAll(participationRepository.findByExerciseId(programmingExercise.getId()));
        programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, loginName);

        // Add a student submission with two manual results and a semi automatic result
        var submission = programmingExerciseUtilService.createProgrammingSubmission(programmingExerciseStudentParticipation, false, TestConstants.COMMIT_HASH_STRING);
        var accessor = userUtilService.getUserByLogin(userPrefix + "instructor1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, accessor);
        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, accessor);
        participationUtilService.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC, accessor);

        // Add a manual feedback to the semi automatic result
        var feedback = new Feedback();
        feedback.setType(FeedbackType.MANUAL);
        feedback.setText("feedback1");
        feedback.setCredits(10.0);

        var resultsWithFeedback = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(programmingExercise.getId());
        var semiAutoResult = resultsWithFeedback.stream().filter(result -> result.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC).findAny().orElseThrow();
        participationUtilService.addFeedbackToResult(feedback, semiAutoResult);

        // Assert that the results have been created successfully.
        resultsWithFeedback = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(programmingExercise.getId());
        assertThat(resultsWithFeedback).hasSize(3);
        assertThat(resultsWithFeedback).filteredOn(result -> result.getAssessmentType() == AssessmentType.MANUAL).hasSize(2);
        assertThat(resultsWithFeedback).filteredOn(result -> result.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC).hasSize(1);

        // Re-trigger the build. We create a notification with feedback of a successful test
        userUtilService.changeUser(userPrefix + "instructor1");
        postResult(buildResultNotification);

        // Retrieve updated results
        var updatedResults = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(programmingExercise.getId());
        assertThat(updatedResults).containsExactlyInAnyOrderElementsOf(resultsWithFeedback);

        var semiAutoResultId = semiAutoResult.getId();
        semiAutoResult = updatedResults.stream().filter(result -> result.getId().equals(semiAutoResultId)).findFirst().orElseThrow();
        assertThat(semiAutoResult.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        // Assert that the SEMI_AUTOMATIC result has two feedbacks whereas the last one is the automatic one
        assertThat(semiAutoResult.getFeedbacks()).hasSize(2);
        assertThat(semiAutoResult.getFeedbacks().get(0).getType()).isEqualTo(FeedbackType.MANUAL);
        assertThat(semiAutoResult.getFeedbacks().get(1).getType()).isEqualTo(FeedbackType.AUTOMATIC);
    }

    private void postResult(AbstractBuildResultNotificationDTO requestBodyMap) throws Exception {
        final var alteredObj = convertBuildResultToJsonObject(requestBodyMap);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", ARTEMIS_AUTHENTICATION_TOKEN_VALUE);
        request.postWithoutLocation("/api/public/programming-exercises/new-result", alteredObj, HttpStatus.OK, httpHeaders);
    }

    public static Object convertBuildResultToJsonObject(AbstractBuildResultNotificationDTO requestBodyMap) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.convertValue(requestBodyMap, Object.class);
    }

    private ProgrammingExerciseTestCase createTest(String testName, long testId, ProgrammingExerciseTestCaseType testCaseType) {
        return createTest(testName, testId, testCaseType, Visibility.ALWAYS);
    }

    private ProgrammingExerciseTestCase createTest(String testName, long testId, ProgrammingExerciseTestCaseType testCaseType, Visibility visibility) {
        var testCase = new ProgrammingExerciseTestCase().exercise(programmingExercise).testName(testName).active(true).weight(1.).id(testId).bonusMultiplier(1.).bonusPoints(0.)
                .visibility(visibility);
        testCase.setType(testCaseType);
        return testCase;
    }

    // Test
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(AbstractBuildResultNotificationDTO resultNotification, boolean withFailedTest) {
        // reset saved test weights to be all 1
        var test2 = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(programmingExercise.getId(), "test2").orElseThrow();
        var test3 = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(programmingExercise.getId(), "test3").orElseThrow();
        test2.setWeight(1.);
        test3.setWeight(1.);
        programmingExerciseTestCaseRepository.saveAll(List.of(test2, test3));

        programmingExerciseUtilService.createProgrammingSubmission(programmingExerciseStudentParticipation, false);

        Set<ProgrammingExerciseTestCase> expectedTestCases = new HashSet<>();
        expectedTestCases.add(createTest("test1", 1L, ProgrammingExerciseTestCaseType.DEFAULT));
        expectedTestCases.add(createTest("test2", 2L, ProgrammingExerciseTestCaseType.BEHAVIORAL));
        expectedTestCases.add(createTest("test4", 4L, ProgrammingExerciseTestCaseType.BEHAVIORAL));
        test3 = createTest("test3", 3L, ProgrammingExerciseTestCaseType.DEFAULT, Visibility.AFTER_DUE_DATE);
        if (!withFailedTest) {
            // test3 should still exist but set to active = false since it's no longer part of the solution result
            // during this the test case type should also be updated
            test3.setActive(false);
            test3.setType(ProgrammingExerciseTestCaseType.BEHAVIORAL);
        }
        expectedTestCases.add(test3);

        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        final var result = gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultRequestBody);

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        // test1 - test3 already exist, test4 should be newly created now.
        // All tests must have active = true since they are now used in the new solution result
        assertThat(testCases).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise", "id", "tasks", "solutionEntries", "coverageEntries")
                .containsExactlyInAnyOrderElementsOf(expectedTestCases);

        assertThat(result).isNotNull();
        if (withFailedTest) {
            assertThat(result.getScore()).isEqualTo(75L);
        }
        else {
            assertThat(result.getScore()).isEqualTo(100L);
        }

        // Call again and shouldn't re-create new submission.
        gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultRequestBody);
        // One submission from the student participation and the other from solution participation
        var latestSubmissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(programmingExerciseStudentParticipation.getId());
        assertThat(latestSubmissions).hasSize(1);
        var latestSolutionSubmissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(solutionParticipation.getId());
        assertThat(latestSolutionSubmissions).hasSize(1);
    }

    // Test
    public void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(AbstractBuildResultNotificationDTO resultNotification, ProgrammingLanguage programmingLanguage) {
        final long participationId = programmingExerciseStudentParticipationStaticCodeAnalysis.getId();
        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        final var result = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipationStaticCodeAnalysis, resultRequestBody);
        assertThat(result).isNotNull();
        final var savedResult = resultRepository.findWithSubmissionAndFeedbackAndTeamStudentsByIdElseThrow(result.getId());

        // Should be one because programmingExerciseStudentParticipationStaticCodeAnalysis doesn't have a submission
        var submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId);
        assertThat(submissions).hasSize(1);

        // Create comparator to explicitly compare feedback attributes (equals only compares id)
        var scaFeedbackComparator = comparing(Feedback::getDetailText, nullsFirst(naturalOrder())).thenComparing(Feedback::getText, nullsFirst(naturalOrder()))
                .thenComparing(Feedback::getReference, nullsFirst(naturalOrder()));

        assertThat(result.getFeedbacks()).usingElementComparator(scaFeedbackComparator).containsAll(savedResult.getFeedbacks());
        assertThat(result.getFeedbacks().stream().filter(Feedback::isStaticCodeAnalysisFeedback).count())
                .isEqualTo(StaticCodeAnalysisTool.getToolsForProgrammingLanguage(programmingLanguage).size());

        // Call again and shouldn't re-create new submission.
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipationStaticCodeAnalysis, resultRequestBody);
        assertThat(programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId)).hasSameSizeAs(submissions);
    }

    // Test
    public void shouldGenerateNewManualResultIfManualAssessmentExists(AbstractBuildResultNotificationDTO resultNotification) {
        activateFourTests();

        var programmingSubmission = programmingExerciseUtilService.createProgrammingSubmission(programmingExerciseStudentParticipation, false);
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, userPrefix + "student1",
                userPrefix + "tutor1", AssessmentType.SEMI_AUTOMATIC, true);

        List<Feedback> feedback = ParticipationFactory.generateManualFeedback();
        feedback = feedbackRepository.saveAll(feedback);
        programmingSubmission.getFirstResult().addFeedbacks(feedback);
        resultRepository.save(programmingSubmission.getFirstResult());

        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        final var result = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultRequestBody);

        assertThat(result).isNotNull();

        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(result.getFeedbacks()).hasSize(6);
        assertThat(result.getFeedbacks().stream().filter((fb) -> fb.getType() == FeedbackType.AUTOMATIC).count()).isEqualTo(3);
        assertThat(result.getTestCaseCount()).isEqualTo(3);
        assertThat(result.getPassedTestCaseCount()).isEqualTo(3);

        // Call again and shouldn't re-create new submission.
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultRequestBody);
        assertThat(programmingSubmissionRepository.findAllByParticipationIdWithResults(programmingExerciseStudentParticipation.getId())).hasSize(1);
    }

    private void activateFourTests() {
        // Some test cases expect test1 to test4 to exist and be active.
        var test4 = new ProgrammingExerciseTestCase().exercise(programmingExercise).active(true).testName("test4").weight(1.).bonusMultiplier(1.).bonusPoints(0.);
        programmingExerciseTestCaseRepository.save(test4);
        var test2 = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(programmingExercise.getId(), "test2").orElseThrow().active(true);
        programmingExerciseTestCaseRepository.saveAll(List.of(test2, test4));
    }

    // Test
    public void shouldGenerateTestwiseCoverageFileReports(AbstractBuildResultNotificationDTO resultNotification) throws GitAPIException {
        // set testwise coverage analysis for programming exercise
        programmingExercise.setTestwiseCoverageEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        solutionProgrammingExerciseRepository.save(solutionParticipation);
        programmingExerciseUtilService.createProgrammingSubmission(solutionParticipation, false);

        // setup mocks
        doReturn(null).when(gitService).getOrCheckoutRepository(any(), eq(true));
        doNothing().when(gitService).resetToOriginHead(any());
        doNothing().when(gitService).pullIgnoreConflicts(any());
        doReturn(Collections.emptyMap()).when(gitService).listFilesAndFolders(any());

        var expectedReportsByTestName = TestwiseCoverageTestUtil.generateCoverageFileReportByTestName();

        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        final var result = gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultRequestBody);
        assertThat(result).isNotNull();
        var actualReportsByTestName = result.getCoverageFileReportsByTestCaseName();
        assertThat(actualReportsByTestName).usingRecursiveComparison().isEqualTo(expectedReportsByTestName);

        // the coverage result attribute is transient in the result and should not be saved to the database
        var resultFromDatabase = resultRepository.findByIdElseThrow(result.getId());
        assertThat(resultFromDatabase.getCoverageFileReportsByTestCaseName()).isNull();
    }

    // Test
    public void shouldIgnoreResultIfNotOnDefaultBranch(AbstractBuildResultNotificationDTO resultNotification) {
        solutionParticipation.setProgrammingExercise(programmingExercise);

        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        assertThatIllegalArgumentException().isThrownBy(() -> gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultRequestBody))
                .withMessageContaining("different branch");
    }

    // Test
    public void shouldCreateResultOnParticipationDefaultBranch(AbstractBuildResultNotificationDTO resultNotification) {
        programmingExerciseStudentParticipation.setProgrammingExercise(programmingExercise);
        programmingExerciseStudentParticipation.setBranch("branch");

        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        var result = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultRequestBody);

        assertThat(result).isNotNull();
    }

    // Test
    public void shouldIgnoreResultIfNotOnParticipationBranch(AbstractBuildResultNotificationDTO resultNotification) {
        programmingExerciseStudentParticipation.setBranch("default");
        programmingExerciseStudentParticipation.setProgrammingExercise(programmingExercise);

        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        assertThatIllegalArgumentException().isThrownBy(() -> gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultRequestBody))
                .withMessageContaining("different branch");
    }

    // Test
    public void shouldCreateResultOnCustomDefaultBranch(String defaultBranch, AbstractBuildResultNotificationDTO resultNotification) {
        programmingExercise.setBranch(defaultBranch);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        programmingExerciseStudentParticipation.setProgrammingExercise(programmingExercise);

        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        final var result = gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultRequestBody);
        assertThat(result).isNotNull();
    }

    // Test
    public void shouldCorrectlyNotifyStudentsAboutNewResults(AbstractBuildResultNotificationDTO resultNotification, WebsocketMessagingService websocketMessagingService)
            throws Exception {
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        var programmingSubmission = programmingExerciseUtilService.createProgrammingSubmission(programmingExerciseStudentParticipation, false);
        programmingExerciseStudentParticipation.addSubmission(programmingSubmission);
        programmingExerciseStudentParticipation = participationRepository.save(programmingExerciseStudentParticipation);

        postResult(resultNotification);

        // ensure that hidden feedback got filtered out (test2 is not active, test3 is hidden -> only 1 feedback visible)
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(eq(userPrefix + "student1"), eq(NEW_RESULT_TOPIC), argThat(arg -> {
            if (!(arg instanceof ResultDTO resultDTO)) {
                return false;
            }
            if (resultDTO.feedbacks().size() != 1) {
                return false;
            }
            var feedback = resultDTO.feedbacks().get(0);
            return feedback.id() != null && feedback.positive();
        }));
    }

    // Test
    public void shouldRemoveTestCaseNamesFromWebsocketNotification(AbstractBuildResultNotificationDTO resultNotification, WebsocketMessagingService websocketMessagingService)
            throws Exception {
        var programmingSubmission = programmingExerciseUtilService.createProgrammingSubmission(programmingExerciseStudentParticipation, false);
        programmingExerciseStudentParticipation.addSubmission(programmingSubmission);
        programmingExerciseStudentParticipation = participationRepository.save(programmingExerciseStudentParticipation);

        postResult(resultNotification);

        // ensure that the test case is set but the name does not get send to the student
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(eq(userPrefix + "student1"), eq(NEW_RESULT_TOPIC),
                argThat(arg -> arg instanceof ResultDTO resultDTO && resultDTO.feedbacks().size() == 1 && resultDTO.feedbacks().get(0).testCase().testName() == null));
    }

    // Test
    public void shouldUpdateParticipantScoresOnlyOnce(AbstractBuildResultNotificationDTO resultNotification, InstanceMessageSendService instanceMessageSendService) {
        final var resultRequestBody = convertBuildResultToJsonObject(resultNotification);
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultRequestBody);

        // check that exactly one update is scheduled
        verify(instanceMessageSendService, times(1)).sendParticipantScoreSchedule(programmingExercise.getId(), programmingExerciseStudentParticipation.getParticipant().getId(),
                null);
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public ProgrammingExerciseParticipation getSolutionParticipation() {
        return solutionParticipation;
    }

    public ProgrammingExerciseStudentParticipation getProgrammingExerciseStudentParticipation() {
        return programmingExerciseStudentParticipation;
    }
}
