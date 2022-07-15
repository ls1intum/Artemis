package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.util.*;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.hestia.TestwiseCoverageTestUtil;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.util.*;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for both scenarios:
 * 1) Bamboo + Bitbucket
 * 2) Jenkins + Gitlab
 */
@Service
public class ProgrammingExerciseResultTestService {

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE;

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private BuildLogEntryRepository buildLogEntryRepository;

    @Autowired
    private ProgrammingExerciseGradingService gradingService;

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private ProgrammingExerciseTestCaseService programmingExerciseTestCaseService;

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

    private ProgrammingExercise programmingExercise;

    private ProgrammingExercise programmingExerciseWithStaticCodeAnalysis;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipationStaticCodeAnalysis;

    public void setup() {
        database.addUsers(10, 2, 0, 2);
        setupForProgrammingLanguage(ProgrammingLanguage.JAVA);
    }

    public void setupForProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        Course course = database.addCourseWithOneProgrammingExercise(false, false, programmingLanguage);
        programmingExercise = programmingExerciseRepository.findAll().get(0);
        programmingExerciseWithStaticCodeAnalysis = database.addProgrammingExerciseToCourse(course, true, false, programmingLanguage);
        staticCodeAnalysisService.createDefaultCategories(programmingExerciseWithStaticCodeAnalysis);
        // This is done to avoid an unproxy issue in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).get();
        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        programmingExerciseStudentParticipationStaticCodeAnalysis = database.addStudentParticipationForProgrammingExercise(programmingExerciseWithStaticCodeAnalysis, "student2");
    }

    public void tearDown() {
        database.resetDatabase();
    }

    // Test
    public void shouldUpdateFeedbackInSemiAutomaticResult(AbstractBuildResultNotificationDTO buildResultNotification, String loginName) throws Exception {
        // Make sure we only have one participation
        participationRepository.deleteAll();
        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, loginName);

        // Add a student submission with two manual results and a semi automatic result
        var submission = database.createProgrammingSubmission(programmingExerciseStudentParticipation, false, TestConstants.COMMIT_HASH_STRING);
        var accessor = database.getUserByLogin("instructor1");
        database.addResultToSubmission(submission, AssessmentType.MANUAL, accessor);
        database.addResultToSubmission(submission, AssessmentType.MANUAL, accessor);
        database.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC, accessor);

        // Add a manual feedback to the semi automatic result
        var feedback = new Feedback();
        feedback.setType(FeedbackType.MANUAL);
        feedback.setText("feedback1");
        feedback.setCredits(10.0);

        var resultsWithFeedback = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(programmingExercise.getId());
        var semiAutoResult = resultsWithFeedback.get(2);
        database.addFeedbackToResult(feedback, semiAutoResult);

        // Assert that the results have been created successfully.
        resultsWithFeedback = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(programmingExercise.getId());
        assertThat(resultsWithFeedback).hasSize(3);
        assertThat(resultsWithFeedback.get(0).getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(resultsWithFeedback.get(1).getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(resultsWithFeedback.get(2).getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);

        // Re-trigger the build. We create a notification with feedback of a successful test
        database.changeUser("instructor1");
        postResult(buildResultNotification);

        // Retrieve updated results
        var updatedResults = resultRepository.findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(programmingExercise.getId());
        assertThat(updatedResults).hasSize(3);

        // Assert that the result order stays the same
        assertThat(updatedResults.get(0).getId()).isEqualTo(resultsWithFeedback.get(0).getId());
        assertThat(updatedResults.get(1).getId()).isEqualTo(resultsWithFeedback.get(1).getId());
        assertThat(updatedResults.get(2).getId()).isEqualTo(resultsWithFeedback.get(2).getId());

        // Assert that the last result is the SEMI_AUTOMATIC result
        semiAutoResult = updatedResults.get(2);
        assertThat(semiAutoResult.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        // Assert that the SEMI_AUTOMATIC result has two feedbacks whereas the last one is the automatic one
        assertThat(semiAutoResult.getFeedbacks()).hasSize(2);
        assertThat(semiAutoResult.getFeedbacks().get(0).getType()).isEqualTo(FeedbackType.MANUAL);
        assertThat(semiAutoResult.getFeedbacks().get(1).getType()).isEqualTo(FeedbackType.AUTOMATIC);
    }

    private void postResult(AbstractBuildResultNotificationDTO requestBodyMap) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        final var alteredObj = mapper.convertValue(requestBodyMap, Object.class);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", ARTEMIS_AUTHENTICATION_TOKEN_VALUE);
        request.postWithoutLocation("/api/" + NEW_RESULT_RESOURCE_PATH, alteredObj, HttpStatus.OK, httpHeaders);
    }

    private ProgrammingExerciseTestCase createTest(String testName, long testId, ProgrammingExerciseTestCaseType testCaseType) {
        var testCase = new ProgrammingExerciseTestCase().exercise(programmingExercise).testName(testName).active(true).weight(1.0).id(testId).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(Visibility.ALWAYS);
        testCase.setType(testCaseType);
        return testCase;
    }

    // Test
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(Object resultNotification, boolean withFailedTest) {
        database.createProgrammingSubmission(programmingExerciseStudentParticipation, false);

        Set<ProgrammingExerciseTestCase> expectedTestCases = new HashSet<>();
        expectedTestCases.add(createTest("test1", 1L, ProgrammingExerciseTestCaseType.BEHAVIORAL));
        expectedTestCases.add(createTest("test2", 2L, ProgrammingExerciseTestCaseType.BEHAVIORAL));
        expectedTestCases.add(createTest("test4", 4L, ProgrammingExerciseTestCaseType.BEHAVIORAL));
        if (withFailedTest) {
            expectedTestCases.add(createTest("test3", 3L, ProgrammingExerciseTestCaseType.BEHAVIORAL));
        }

        final var optionalResult = gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultNotification);

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseService.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).usingElementComparatorIgnoringFields("exercise", "id", "tasks", "solutionEntries", "coverageEntries")
                .containsExactlyInAnyOrderElementsOf(expectedTestCases);
        assertThat(optionalResult).isPresent();
        if (withFailedTest) {
            assertThat(optionalResult.get().getScore()).isEqualTo(75L);
        }
        else {
            assertThat(optionalResult.get().getScore()).isEqualTo(100L);
        }

        // Call again and shouldn't re-create new submission.
        gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultNotification);
        var latestSubmissions = programmingSubmissionRepository.findAll();
        // One submission from the student participation and the other from solution participation
        assertThat(latestSubmissions).hasSize(2);
    }

    // Test
    public void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(Object resultNotification, ProgrammingLanguage programmingLanguage) {
        final var optionalResult = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipationStaticCodeAnalysis, resultNotification);
        final var savedResult = resultRepository.findByIdWithEagerSubmissionAndFeedbackElseThrow(optionalResult.get().getId());

        // Should be one because programmingExerciseStudentParticipationStaticCodeAnalysis doesn't have a submission
        var submissions = programmingSubmissionRepository.findAll();
        assertThat(submissions).hasSize(1);

        // Create comparator to explicitly compare feedback attributes (equals only compares id)
        Comparator<? super Feedback> scaFeedbackComparator = (Comparator<Feedback>) (fb1, fb2) -> {
            if (Objects.equals(fb1.getDetailText(), fb2.getDetailText()) && Objects.equals(fb1.getText(), fb2.getText())
                    && Objects.equals(fb1.getReference(), fb2.getReference())) {
                return 0;
            }
            else {
                return 1;
            }
        };

        assertThat(optionalResult).isPresent();
        var result = optionalResult.get();
        assertThat(result.getFeedbacks()).usingElementComparator(scaFeedbackComparator).containsAll(savedResult.getFeedbacks());
        assertThat(result.getFeedbacks().stream().filter(Feedback::isStaticCodeAnalysisFeedback).count())
                .isEqualTo(StaticCodeAnalysisTool.getToolsForProgrammingLanguage(programmingLanguage).size());

        // Call again and shouldn't re-create new submission.
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipationStaticCodeAnalysis, resultNotification);
        assertThat(programmingSubmissionRepository.findAll()).hasSameSizeAs(submissions);
    }

    // Test
    public void shouldStoreBuildLogsForSubmission(Object resultNotification) {
        final var optionalResult = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);

        var submission = programmingSubmissionRepository.findFirstByParticipationIdOrderByLegalSubmissionDateDesc(programmingExerciseStudentParticipation.getId());
        var submissionWithLogs = programmingSubmissionRepository.findWithEagerBuildLogEntriesById(submission.get().getId());
        var expectedNoOfLogs = getNumberOfBuildLogs(resultNotification) - 3;  // 3 of those should be filtered
        assertThat(((ProgrammingSubmission) optionalResult.get().getSubmission()).getBuildLogEntries()).hasSize(expectedNoOfLogs);
        assertThat(submissionWithLogs.get().getBuildLogEntries()).hasSize(expectedNoOfLogs);

        // Call again and should not re-create new submission.
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);
        assertThat(programmingSubmissionRepository.findAll()).hasSize(1);
    }

    // Test
    public void shouldNotStoreBuildLogsForSubmission(Object resultNotification) {
        final var optionalResult = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);

        var submission = programmingSubmissionRepository.findFirstByParticipationIdOrderByLegalSubmissionDateDesc(programmingExerciseStudentParticipation.getId());
        var submissionWithLogs = programmingSubmissionRepository.findWithEagerBuildLogEntriesById(submission.get().getId());
        var expectedNoOfLogs = 0; // No logs should be stored because the build was successful
        assertThat(((ProgrammingSubmission) optionalResult.get().getSubmission()).getBuildLogEntries()).hasSize(expectedNoOfLogs);
        assertThat(submissionWithLogs.get().getBuildLogEntries()).hasSize(expectedNoOfLogs);

        // Call again and should not re-create new submission.
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);
        assertThat(programmingSubmissionRepository.findAll()).hasSize(1);
    }

    public void shouldSaveBuildLogsInBuildLogRepository(Object resultNotification) {
        buildLogEntryRepository.deleteAll();
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);

        var savedBuildLogs = buildLogEntryRepository.findAll();
        var expectedBuildLogs = getNumberOfBuildLogs(resultNotification) - 3; // 3 of those should be filtered

        assertThat(savedBuildLogs).hasSize(expectedBuildLogs);

        // Call again and should not re-create new submission.
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);
        assertThat(programmingSubmissionRepository.findAll()).hasSize(1);
    }

    public void shouldNotSaveBuildLogsInBuildLogRepository(Object resultNotification) {
        buildLogEntryRepository.deleteAll();
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);

        var savedBuildLogs = buildLogEntryRepository.findAll();
        var expectedBuildLogs = 0; // No logs should be stored because the build was successful

        assertThat(savedBuildLogs).hasSize(expectedBuildLogs);

        // Call again and should not re-create new submission.
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);
        assertThat(programmingSubmissionRepository.findAll()).hasSize(1);
    }

    // Test
    public void shouldGenerateNewManualResultIfManualAssessmentExists(Object resultNotification) {
        var programmingSubmission = database.createProgrammingSubmission(programmingExerciseStudentParticipation, false);
        programmingSubmission = database.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, "student1", "tutor1",
                AssessmentType.SEMI_AUTOMATIC, true);

        List<Feedback> feedback = ModelFactory.generateManualFeedback();
        feedback = feedbackRepository.saveAll(feedback);
        programmingSubmission.getFirstResult().addFeedbacks(feedback);
        resultRepository.save(programmingSubmission.getFirstResult());

        final var optionalResult = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);

        assertThat(optionalResult).isPresent();

        var result = optionalResult.get();

        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(result.getFeedbacks()).hasSize(6);
        assertThat(result.getFeedbacks().stream().filter((fb) -> fb.getType() == FeedbackType.AUTOMATIC).count()).isEqualTo(3);

        // Call again and shouldn't re-create new submission.
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);
        assertThat(programmingSubmissionRepository.findAll()).hasSize(1);
    }

    // Test
    public void shouldGenerateTestwiseCoverageFileReports(Object resultNotification) throws GitAPIException, InterruptedException {
        // set testwise coverage analysis for programming exercise
        programmingExercise.setTestwiseCoverageEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        solutionProgrammingExerciseRepository.save(solutionParticipation);
        database.createProgrammingSubmission(solutionParticipation, false);

        // setup mocks
        doReturn(null).when(gitService).getOrCheckoutRepository(any(), eq(true));
        doNothing().when(gitService).resetToOriginHead(any());
        doNothing().when(gitService).pullIgnoreConflicts(any());
        doReturn(Collections.emptyMap()).when(gitService).listFilesAndFolders(any());

        var expectedReportsByTestName = TestwiseCoverageTestUtil.generateCoverageFileReportByTestName();

        final var optionalResult = gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultNotification);
        assertThat(optionalResult).isPresent();
        var result = optionalResult.get();
        var actualReportsByTestName = result.getCoverageFileReportsByTestCaseName();
        assertThat(actualReportsByTestName).usingRecursiveComparison().isEqualTo(expectedReportsByTestName);

        // the coverage result attribute is transient in the result and should not be saved to the database
        var resultFromDatabase = resultRepository.findByIdElseThrow(result.getId());
        assertThat(resultFromDatabase.getCoverageFileReportsByTestCaseName()).isNull();
    }

    private int getNumberOfBuildLogs(Object resultNotification) {
        if (resultNotification instanceof BambooBuildResultNotificationDTO) {
            return ((BambooBuildResultNotificationDTO) resultNotification).getBuild().getJobs().iterator().next().getLogs().size();
        }
        throw new UnsupportedOperationException("Build logs are only part of the Bamboo notification");
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public ProgrammingExercise getProgrammingExerciseWithStaticCodeAnalysis() {
        return programmingExerciseWithStaticCodeAnalysis;
    }

    public ProgrammingExerciseParticipation getSolutionParticipation() {
        return solutionParticipation;
    }
}
