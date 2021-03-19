package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_DUPLICATE_NOTIFICATION;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseGradingResource;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseGradingStatisticsDTO;

public class ProgrammingExerciseGradingServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    ProgrammingExerciseGradingService gradingService;

    private ProgrammingExercise programmingExerciseSCAEnabled;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation;

    private Result result;

    private Double offsetByTenThousandth = 0.0001;

    @BeforeEach
    public void setUp() {
        database.addUsers(5, 1, 1);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        programmingExerciseSCAEnabled = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        database.addTestCasesToProgrammingExercise(programmingExerciseSCAEnabled);
        var programmingExercises = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations();
        programmingExercise = programmingExercises.get(0);

        participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        result = new Result();
        result.setParticipation(participation);
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        bambooRequestMockProvider.reset();
    }

    @Test
    public void shouldNotUpdateResultIfNoTestCasesExist() {
        testCaseRepository.deleteAll();

        Double scoreBeforeUpdate = result.getScore();
        gradingService.calculateScoreForResult(result, programmingExercise, true);

        assertThat(result.getScore()).isEqualTo(scoreBeforeUpdate);
    }

    @Test
    public void shouldAddFeedbackForDuplicateTestCases() {
        // Adjust existing test cases to our need
        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).afterDueDate(false);
        testCases.get("test2").active(true).afterDueDate(false);
        testCases.get("test3").active(true).afterDueDate(false);
        testCaseRepository.saveAll(testCases.values());

        // Create feedback with duplicate content for test1 and test3
        // This mimics that two new testcases are going to be found as testcases but those are duplicate
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(false).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.rated(true).hasFeedback(true).successful(false).completionDate(ZonedDateTime.now()).assessmentType(AssessmentType.AUTOMATIC);
        int originalFeedbackSize = result.getFeedbacks().size();

        gradingService.calculateScoreForResult(result, programmingExercise, true);

        var duplicateFeedbackEntries = result.getFeedbacks().stream()
                .filter(feedback -> feedback.getDetailText() != null && feedback.getDetailText().contains("This is a duplicate test case.")).collect(Collectors.toList());
        assertThat(result.getScore()).isEqualTo(0D);
        assertThat(duplicateFeedbackEntries.size()).isEqualTo(2);
        int countOfNewFeedbacks = originalFeedbackSize + duplicateFeedbackEntries.size();
        assertThat(result.getFeedbacks().size()).isEqualTo(countOfNewFeedbacks);
        assertThat(result.getResultString()).isEqualTo("Error: Found duplicated tests!");
        String notificationText = TEST_CASES_DUPLICATE_NOTIFICATION + "test3, test1";
        verify(groupNotificationService).notifyInstructorGroupAboutDuplicateTestCasesForExercise(programmingExercise, notificationText);
    }

    @Test
    public void shouldRecalculateScoreBasedOnTestCasesWeightAutomatic() {
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test4").positive(false).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.successful(false);
        result.assessmentType(AssessmentType.AUTOMATIC);
        Double scoreBeforeUpdate = result.getScore();

        gradingService.calculateScoreForResult(result, programmingExercise, true);

        Double expectedScore = 25D;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    public void shouldRecalculateScoreBasedOnTestCasesWeightManual() {
        List<Feedback> feedbacks = new ArrayList<>();
        // we deliberately don't set the credits here, null must work as well
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test4").positive(false).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("manual").positive(false).type(FeedbackType.MANUAL_UNREFERENCED));
        result.feedbacks(feedbacks);
        result.successful(false);
        result.rated(true);
        result.assessmentType(AssessmentType.SEMI_AUTOMATIC);
        Double scoreBeforeUpdate = result.getScore();

        gradingService.calculateScoreForResult(result, programmingExercise, true);

        Double expectedScore = 25D;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(result.getFeedbacks().stream().filter(f -> f.getType() == FeedbackType.MANUAL_UNREFERENCED)).isNotEmpty();
        assertThat(result.getResultString()).isEqualTo(String.format("1 of 1 passed, %.1f of 42 points", 10.5));
    }

    @Test
    public void shouldRecalculateScoreWithTestCaseBonusButNoExerciseBonus() {
        // Set up test cases with bonus
        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).afterDueDate(false).weight(5.).bonusMultiplier(1D).setBonusPoints(7D);
        testCases.get("test2").active(true).afterDueDate(false).weight(2.).bonusMultiplier(2D).setBonusPoints(0D);
        testCases.get("test3").active(true).afterDueDate(false).weight(3.).bonusMultiplier(1D).setBonusPoints(10.5D);
        testCaseRepository.saveAll(testCases.values());

        var result1 = new Result();
        result1 = updateAndSaveAutomaticResult(result1, false, false, true);

        var result2 = new Result();
        result2 = updateAndSaveAutomaticResult(result2, true, false, false);

        var result3 = new Result();
        result3 = updateAndSaveAutomaticResult(result3, false, true, false);

        var result4 = new Result();
        result4 = updateAndSaveAutomaticResult(result4, false, true, true);

        var result5 = new Result();
        result5 = updateAndSaveAutomaticResult(result5, true, true, true);

        var result6 = new Result();
        result6 = updateAndSaveAutomaticResult(result6, false, false, false);

        // Build failure
        var resultBF = new Result().feedbacks(List.of()).rated(true).score(0D).hasFeedback(false).resultString("Build Failed").completionDate(ZonedDateTime.now())
                .assessmentType(AssessmentType.AUTOMATIC);
        gradingService.calculateScoreForResult(resultBF, programmingExercise, true);

        // Missing feedback
        var resultMF = new Result();
        var feedbackMF = new Feedback().result(result).text("test3").positive(true).type(FeedbackType.AUTOMATIC).result(resultMF);
        resultMF.feedbacks(new ArrayList<>(List.of(feedbackMF))) // List must be mutable
                .rated(true).score(0D).hasFeedback(true).completionDate(ZonedDateTime.now()).assessmentType(AssessmentType.AUTOMATIC);
        gradingService.calculateScoreForResult(resultMF, programmingExercise, true);

        // Assertions result1 - calculated
        assertThat(result1.getScore()).isEqualTo(55D, Offset.offset(offsetByTenThousandth));
        assertThat(result1.getResultString()).isEqualTo("1 of 3 passed");
        assertThat(result1.getHasFeedback()).isTrue();
        assertThat(result1.isSuccessful()).isFalse();
        assertThat(result1.getFeedbacks()).hasSize(3);

        // Assertions result2 - calculated
        assertThat(result2.getScore()).isEqualTo(66.6667);
        assertThat(result2.getResultString()).isEqualTo("1 of 3 passed");
        assertThat(result2.getHasFeedback()).isTrue();
        assertThat(result2.isSuccessful()).isFalse();
        assertThat(result2.getFeedbacks()).hasSize(3);

        // Assertions result3 - calculated
        assertThat(result3.getScore()).isEqualTo(40D);
        assertThat(result3.getResultString()).isEqualTo("1 of 3 passed");
        assertThat(result3.getHasFeedback()).isTrue();
        assertThat(result3.isSuccessful()).isFalse();
        assertThat(result3.getFeedbacks()).hasSize(3);

        // Assertions result4 - calculated
        assertThat(result4.getScore()).isEqualTo(95D, Offset.offset(offsetByTenThousandth));
        assertThat(result4.getResultString()).isEqualTo("2 of 3 passed");
        assertThat(result4.getHasFeedback()).isTrue();
        assertThat(result4.isSuccessful()).isFalse();
        assertThat(result4.getFeedbacks()).hasSize(3);

        // Assertions result5 - capped to 100
        assertThat(result5.getScore()).isEqualTo(100D);
        assertThat(result5.getResultString()).isEqualTo("3 of 3 passed");
        assertThat(result5.getHasFeedback()).isFalse();
        assertThat(result5.isSuccessful()).isTrue();
        assertThat(result5.getFeedbacks()).hasSize(3);

        // Assertions result6 - only negative feedback
        assertThat(result6.getScore()).isEqualTo(0D);
        assertThat(result6.getResultString()).isEqualTo("0 of 3 passed");
        assertThat(result6.getHasFeedback()).isTrue();
        assertThat(result6.isSuccessful()).isFalse();
        assertThat(result6.getFeedbacks()).hasSize(3);

        // Assertions resultBF - build failure
        assertThat(resultBF.getScore()).isEqualTo(0D);
        assertThat(resultBF.getResultString()).isEqualTo("Build Failed"); // Won't get touched by the service method
        assertThat(resultBF.getHasFeedback()).isFalse();
        assertThat(resultBF.isSuccessful()).isNull(); // Won't get touched by the service method
        assertThat(resultBF.getFeedbacks()).hasSize(0);

        // Assertions resultMF - missing feedback will be created but is negative
        assertThat(resultMF.getScore()).isEqualTo(55D, Offset.offset(offsetByTenThousandth));
        assertThat(resultMF.getResultString()).isEqualTo("1 of 3 passed");
        assertThat(resultMF.getHasFeedback()).isFalse(); // Generated missing feedback is omitted
        assertThat(resultMF.isSuccessful()).isFalse();
        assertThat(resultMF.getFeedbacks()).hasSize(3); // Feedback is created for test cases if missing
    }

    @Test
    public void shouldRecalculateScoreWithTestCaseBonusAndExerciseBonus() {
        // Set up test cases with bonus
        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).afterDueDate(false).weight(4.).bonusMultiplier(1D).setBonusPoints(0D);
        testCases.get("test2").active(true).afterDueDate(false).weight(3.).bonusMultiplier(3D).setBonusPoints(21D);
        testCases.get("test3").active(true).afterDueDate(false).weight(3.).bonusMultiplier(2D).setBonusPoints(14D);
        testCaseRepository.saveAll(testCases.values());

        // Score should be capped at 200%
        programmingExercise.setBonusPoints(programmingExercise.getMaxPoints());
        programmingExerciseRepository.save(programmingExercise);

        var result1 = new Result();
        result1 = updateAndSaveAutomaticResult(result1, false, false, true);

        var result2 = new Result();
        result2 = updateAndSaveAutomaticResult(result2, true, false, true);

        var result3 = new Result();
        result3 = updateAndSaveAutomaticResult(result3, true, true, false);

        var result4 = new Result();
        result4 = updateAndSaveAutomaticResult(result4, false, true, true);

        // Assertions result1 - calculated
        assertThat(result1.getScore()).isEqualTo(93.3333);
        assertThat(result1.getResultString()).isEqualTo("1 of 3 passed");
        assertThat(result1.getHasFeedback()).isTrue();
        assertThat(result1.isSuccessful()).isFalse();
        assertThat(result1.getFeedbacks()).hasSize(3);

        // Assertions result2 - calculated
        assertThat(result2.getScore()).isEqualTo(133.3333);
        assertThat(result2.getResultString()).isEqualTo("2 of 3 passed");
        assertThat(result2.getHasFeedback()).isTrue();
        assertThat(result2.isSuccessful()).isTrue();
        assertThat(result2.getFeedbacks()).hasSize(3);

        // Assertions result3 - calculated
        assertThat(result3.getScore()).isEqualTo(180D, Offset.offset(offsetByTenThousandth));
        assertThat(result3.getResultString()).isEqualTo("2 of 3 passed");
        assertThat(result3.getHasFeedback()).isTrue();
        assertThat(result3.isSuccessful()).isTrue();
        assertThat(result3.getFeedbacks()).hasSize(3);

        // Assertions result4 - capped at 200%
        assertThat(result4.getScore()).isEqualTo(200D);
        assertThat(result4.getResultString()).isEqualTo("2 of 3 passed");
        assertThat(result4.getHasFeedback()).isTrue();
        assertThat(result4.isSuccessful()).isTrue();
        assertThat(result4.getFeedbacks()).hasSize(3);
    }

    @Test
    public void shouldRemoveTestsWithAfterDueDateFlagIfDueDateHasNotPassed() {

        // Set programming exercise due date in future.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(10));

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.successful(false);
        result.assessmentType(AssessmentType.AUTOMATIC);
        Double scoreBeforeUpdate = result.getScore();

        gradingService.calculateScoreForResult(result, programmingExercise, true);

        // All available test cases are fulfilled, however there are more test cases that will be run after due date.
        Double expectedScore = 25D;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.getResultString()).isEqualTo("1 of 1 passed");
        assertThat(result.isSuccessful()).isFalse();
        // The feedback of the after due date test case must be removed.
        assertThat(result.getFeedbacks().stream().noneMatch(feedback -> feedback.getText().equals("test3"))).isEqualTo(true);
    }

    @Test
    public void shouldNotRemoveTestsWithAfterDueDateFlagIfDueDateHasNotPassedForNonStudentParticipation() {
        // Set programming exercise due date in future.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(10));

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.successful(false);
        result.assessmentType(AssessmentType.AUTOMATIC);
        Double scoreBeforeUpdate = result.getScore();

        gradingService.calculateScoreForResult(result, programmingExercise, false);

        // All available test cases are fulfilled.
        Double expectedScore = 25D;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getResultString()).isEqualTo("1 of 2 passed");
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFeedbacks()).hasSize(2);
    }

    @Test
    public void shouldKeepTestsWithAfterDueDateFlagIfDueDateHasPassed() {
        // Set programming exercise due date in past.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(10));

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.successful(false);
        result.assessmentType(AssessmentType.AUTOMATIC);
        Double scoreBeforeUpdate = result.getScore();

        gradingService.calculateScoreForResult(result, programmingExercise, true);

        // All available test cases are fulfilled.
        Double expectedScore = 25D;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getResultString()).isEqualTo("1 of 2 passed");
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
        // The feedback of the after due date test case must be kept.
        assertThat(result.getFeedbacks().stream().noneMatch(feedback -> feedback.getText().equals("test3"))).isEqualTo(false);
    }

    private void testAndAssertZeroScoreIfThereAreNoTestCasesBeforeDueDate(ProgrammingExercise programmingExercise, String expectedResultString, int expectedFeedbackSize) {
        // Set programming exercise due date in future.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(10));
        Double scoreBeforeUpdate = result.getScore();

        // Set all test cases of the programming exercise to be executed after due date.
        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        for (ProgrammingExerciseTestCase testCase : testCases) {
            testCase.setAfterDueDate(true);
        }
        testCaseRepository.saveAll(testCases);

        gradingService.calculateScoreForResult(result, programmingExercise, true);

        // No test case was executed.
        Double expectedScore = 0D;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getResultString()).isEqualTo(expectedResultString);
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
        // The feedback must be empty as not test should be executed yet.
        assertThat(result.getFeedbacks()).hasSize(expectedFeedbackSize);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void shouldReEvaluateScoreOfTheCorrectResults() throws Exception {

        programmingExercise = (ProgrammingExercise) database.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).afterDueDate(false).setWeight(1.);
        testCases.get("test2").active(true).afterDueDate(false).setWeight(1.);
        testCases.get("test3").active(true).afterDueDate(false).setWeight(2.);
        testCaseRepository.saveAll(testCases.values());

        var testParticipations = createTestParticipations();

        // change test case weights
        testCases.get("test1").setWeight(0.);
        testCases.get("test2").setWeight(1.);
        testCases.get("test3").setWeight(3.);
        testCaseRepository.saveAll(testCases.values());

        // re-evaluate
        final var endpoint = ProgrammingExerciseGradingResource.RE_EVALUATE.replace("{exerciseId}", programmingExercise.getId().toString());
        final var response = request.putWithResponseBody(ROOT + endpoint, "{}", Integer.class, HttpStatus.OK);
        assertThat(response).isEqualTo(7);

        // this fixes an issue with the authentication context after a mock request
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());

        // Tests
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        // template 0 %
        {
            var participation = programmingExercise.getTemplateParticipation();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 0D, "0 of 3 passed", true, 3, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // solution 100 %
        {
            var participation = programmingExercise.getSolutionParticipation();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 100D, "2 of 3 passed", true, 3, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // student1 25 %
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(testParticipations[0].getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 25D, "2 of 3 passed", true, 3, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // student2 61% % / 75 %
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(testParticipations[1].getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(2);

            var manualResultOptional = results.stream().filter(result -> result.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC).findAny();
            assertThat(manualResultOptional).isPresent();
            testParticipationResult(manualResultOptional.get(), 86D, "1 of 3 passed, 86 of 100 points", true, 6, AssessmentType.SEMI_AUTOMATIC);
            assertThat(manualResultOptional.get()).isEqualTo(participation.findLatestResult());

            var automaticResultOptional = results.stream().filter(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC).findAny();
            assertThat(automaticResultOptional).isPresent();
            testParticipationResult(automaticResultOptional.get(), 75D, "2 of 3 passed", true, 3, AssessmentType.AUTOMATIC);
        }

        // student3 no result
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(testParticipations[2].getId()).get();
            var results = participation.getResults();
            assertThat(results).isNullOrEmpty();
            assertThat(participation.findLatestResult()).isNull();
        }

        // student4 100%
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(testParticipations[3].getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 100D, "3 of 3 passed", false, 3, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // student5 Build Failed
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(testParticipations[4].getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 0D, "Build Failed", false, 0, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
    }

    private Result updateAndSaveAutomaticResult(Result result, boolean test1Passes, boolean test2Passes, boolean test3Passes) {
        var feedback1 = new Feedback().result(result).text("test1").positive(test1Passes).type(FeedbackType.AUTOMATIC);
        result.addFeedback(feedback1);
        var feedback2 = new Feedback().result(result).text("test2").positive(test2Passes).type(FeedbackType.AUTOMATIC);
        result.addFeedback(feedback2);
        var feedback3 = new Feedback().result(result).text("test3").positive(test3Passes).type(FeedbackType.AUTOMATIC);
        result.addFeedback(feedback3);
        result.rated(true) //
                .hasFeedback(true) //
                .successful(test1Passes && test2Passes && test3Passes) //
                .completionDate(ZonedDateTime.now()) //
                .assessmentType(AssessmentType.AUTOMATIC);
        gradingService.calculateScoreForResult(result, programmingExercise, true);
        return resultRepository.save(result);
    }

    private Participation[] createTestParticipations() {

        var testParticipations = new Participation[5];

        // template does not pass any tests
        var participationTemplate = programmingExercise.getTemplateParticipation();
        {
            // score 0 %
            var resultTemplate = new Result().participation(participationTemplate).resultString("x of y passed").successful(false).rated(true).score(100D);
            participationTemplate.setResults(Set.of(resultTemplate));
            resultTemplate = updateAndSaveAutomaticResult(resultTemplate, false, false, false);
        }

        // solution passes most tests but is still faulty
        var participationSolution = programmingExercise.getSolutionParticipation();
        {
            // score 75 %
            var resultSolution = new Result().participation(participationSolution).resultString("x of y passed").successful(false).rated(true).score(100D);
            participationSolution.setResults(Set.of(resultSolution));
            updateAndSaveAutomaticResult(resultSolution, false, true, true);
        }

        // student1 only has one automatic result
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        {
            // score 50 %
            var result1 = new Result().participation(participation1).resultString("x of y passed").successful(false).rated(true).score(100D);
            participation1.setResults(Set.of(result1));
            updateAndSaveAutomaticResult(result1, true, true, false);
        }
        testParticipations[0] = participation1;

        // student2 has an automatic result and a manual result as well
        var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        {
            // score 75 %
            var result2a = new Result().participation(participation2).resultString("x of y passed").successful(false).rated(true).score(100D);
            result2a = updateAndSaveAutomaticResult(result2a, true, false, true);

            // score 61 %
            var result2b = new Result().participation(participation2).score(61D).successful(false).rated(true).hasFeedback(true).completionDate(ZonedDateTime.now())
                    .assessmentType(AssessmentType.SEMI_AUTOMATIC);
            result2b.addFeedback(new Feedback().result(result2b).text("test1").positive(false).type(FeedbackType.AUTOMATIC).credits(0.00));
            result2b.addFeedback(new Feedback().result(result2b).text("test2").positive(false).type(FeedbackType.AUTOMATIC).credits(0.00));
            result2b.addFeedback(new Feedback().result(result2b).text("test3").positive(true).type(FeedbackType.AUTOMATIC).credits(0.00));
            result2b.addFeedback(new Feedback().result(result2b).detailText("Well done referenced!").credits(1.00).type(FeedbackType.MANUAL));
            result2b.addFeedback(new Feedback().result(result2b).detailText("Well done unreferenced!").credits(10.00).type(FeedbackType.MANUAL_UNREFERENCED));
            result2b.addFeedback(new Feedback().result(result2b).detailText("Well done general!").credits(0.00));

            gradingService.calculateScoreForResult(result2b, programmingExercise, true);
            assertThat(result2b.getScore()).isEqualTo(61);
            result2b = resultRepository.save(result2b);
            participation2.setResults(Set.of(result2a, result2b));
        }
        testParticipations[1] = participation2;

        // student3 only started the exercise, but did not submit anything
        var participation3 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student3");
        testParticipations[2] = participation3;

        // student4 only has one automatic result
        var participation4 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student4");
        {
            // score 100 %
            var result4 = new Result().participation(participation4).resultString("x of y passed").successful(false).rated(true).score(100D);
            result4 = updateAndSaveAutomaticResult(result4, true, true, true);
            participation4.setResults(Set.of(result4));
        }
        testParticipations[3] = participation4;

        // student5 has a build failure
        var participation5 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student5");
        {
            // Build Failed
            var result5 = new Result().participation(participation5) //
                    .feedbacks(List.of()) //
                    .score(0D) //
                    .resultString("Build Failed") //
                    .rated(true) //
                    .hasFeedback(false) //
                    .successful(false) //
                    .completionDate(ZonedDateTime.now()) //
                    .assessmentType(AssessmentType.AUTOMATIC);
            gradingService.calculateScoreForResult(result5, programmingExercise, true);
            result5 = resultRepository.save(result5);
            participation5.setResults(Set.of(result5));
        }
        testParticipations[4] = participation5;

        return testParticipations;
    }

    @Test
    public void shouldRemoveInvisibleStaticCodeAnalysisFeedbackOnGrading() throws Exception {

        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student1");
        var result1 = new Result().participation(participation1).resultString("x of y passed").successful(false).rated(true).score(100D);
        // Add some positive test case feedback otherwise the service method won't execute
        result1.addFeedback(new Feedback().result(result1).text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        // Add feedback which belongs to INACTIVE category
        var feedback1 = new Feedback().result(result1).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("CHECKSTYLE")
                .detailText("{\"category\": \"miscellaneous\"}").type(FeedbackType.AUTOMATIC);
        result1.addFeedback(feedback1);
        // Add feedback without category
        var feedback2 = new Feedback().result(result1).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("CHECKSTYLE").detailText("").type(FeedbackType.AUTOMATIC);
        result1.addFeedback(feedback2);
        // Add feedback with unsupported rule
        var feedback3 = new Feedback().result(result1).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("CHECKSTYLE")
                .detailText("{\"category\": \"doesNotExist\"}").type(FeedbackType.AUTOMATIC);
        result1.addFeedback(feedback3);

        Result updatedResult = gradingService.calculateScoreForResult(result1, programmingExerciseSCAEnabled, true);

        assertThat(updatedResult.getFeedbacks()).hasSize(1);
        assertThat(updatedResult.getFeedbacks()).doesNotContain(feedback1, feedback2, feedback3);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void shouldCalculateScoreWithStaticCodeAnalysisPenaltiesWithoutCaps() {
        activateAllTestCases(false);

        // Remove category penalty limits
        var updatedCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId()).stream().peek(category -> category.setMaxPenalty(null))
                .collect(Collectors.toList());
        staticCodeAnalysisCategoryRepository.saveAll(updatedCategories);

        // create results for tests without category penalty limits
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student1");
        {
            // Capped by limit for exercise -> Score 60
            var result1 = new Result().participation(participation1);
            participation1.setResults(Set.of(result1));
            updateAndSaveAutomaticResult(result1, true, true, true, 10, 10);
        }
        var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student2");
        {
            // Testcase points: 42; Penalty: 4*3 = 12; Score: 71
            var result2 = new Result().participation(participation2);
            participation1.setResults(Set.of(result2));
            updateAndSaveAutomaticResult(result2, true, true, true, 4, 0);
        }
        // check results without category penalty limits
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation1.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 60D, "3 of 3 passed, 21 issues", true, 24, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation2.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 71.42857142857143, "3 of 3 passed, 5 issues", true, 8, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // Also remove max penalty from exercise
        programmingExerciseSCAEnabled.setMaxStaticCodeAnalysisPenalty(null);
        programmingExerciseRepository.save(programmingExerciseSCAEnabled);

        // create results for tests without any limits
        var participation3 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student3");
        {
            // Penalty will be higher than points -> score 0
            var result3 = new Result().participation(participation3);
            participation3.setResults(Set.of(result3));
            updateAndSaveAutomaticResult(result3, true, true, true, 10, 10);
        }
        var participation4 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student4");
        {
            // Testcase points: 35; Penalty: 5*3 + 3*5 = 30; Score: 11
            var result4 = new Result().participation(participation4);
            participation4.setResults(Set.of(result4));
            updateAndSaveAutomaticResult(result4, false, true, true, 5, 3);
        }
        // check results without any limits
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation3.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 0D, "3 of 3 passed, 21 issues", true, 24, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation4.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 11.904761904761903, "2 of 3 passed, 9 issues", true, 12, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void shouldCalculateScoreWithStaticCodeAnalysisPenaltiesWithBonus() throws Exception {
        activateAllTestCases(true);

        // Set bonus points for exercise
        programmingExerciseSCAEnabled.setBonusPoints(8D);
        programmingExerciseRepository.save(programmingExerciseSCAEnabled);

        // create results
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student1");
        {
            // Test case points are capped at 50 first, then the penalty of 19 is calculated but capped at at 40 percent of the maxScore -> score = (50-16.8)/42
            var result1 = new Result().participation(participation1);
            participation1.setResults(Set.of(result1));
            updateAndSaveAutomaticResult(result1, true, true, true, 3, 2);
        }

        // check results
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation1.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 79.04761904761905, "3 of 3 passed, 6 issues", true, 9, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // Remove max penalty from exercise
        programmingExerciseSCAEnabled.setMaxStaticCodeAnalysisPenalty(null);
        programmingExerciseRepository.save(programmingExerciseSCAEnabled);

        // Remove category penalty limits
        var updatedCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId()).stream().peek(category -> category.setMaxPenalty(null))
                .collect(Collectors.toList());
        staticCodeAnalysisCategoryRepository.saveAll(updatedCategories);

        // create result without limits
        var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student2");
        {
            // Test case points are capped at 50 first, then the penalty of 55 is calculated but capped at at 100 percent of the maxScore, which means only the achieved bonus
            // points remain
            var result2 = new Result().participation(participation2);
            participation2.setResults(Set.of(result2));
            updateAndSaveAutomaticResult(result2, true, true, true, 10, 5);
        }

        // check result without limits
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation2.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 19.047619047619047, "3 of 3 passed, 16 issues", true, 19, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void shouldCalculateScoreWithStaticCodeAnalysisPenalties() {
        activateAllTestCases(false);

        var participations = createTestParticipationsWithResults();

        // check results
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participations.get(0).getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 4.761904761904762, "1 of 3 passed, 2 issues", true, 5, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participations.get(1).getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 40.476190476190474, "2 of 3 passed, 4 issues", true, 7, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participations.get(2).getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 0D, "1 of 3 passed, 7 issues", true, 10, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participations.get(3).getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 26.190476190476193, "2 of 3 passed, 6 issues", true, 9, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participations.get(4).getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 60D, "3 of 3 passed, 11 issues", true, 14, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
    }

    @Test
    public void shouldCalculateCorrectStatistics() {
        activateAllTestCases(false);
        createTestParticipationsWithResults();

        var statistics = gradingService.generateGradingStatistics(programmingExerciseSCAEnabled.getId());

        assertThat(statistics.getNumParticipations()).isEqualTo(5);

        var testCaseStatsMap = new HashMap<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats>();
        testCaseStatsMap.put("test1", new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(5, 0));
        testCaseStatsMap.put("test2", new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(2, 3));
        testCaseStatsMap.put("test3", new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(2, 3));

        assertThat(statistics.getTestCaseStatsMap()).containsExactlyInAnyOrderEntriesOf(testCaseStatsMap);

        var categoryIssuesMap = new HashMap<String, Map<Integer, Integer>>();
        categoryIssuesMap.put("Bad Practice", Map.of(2, 1, 5, 3));
        categoryIssuesMap.put("Code Style", Map.of(1, 3, 5, 1));
        categoryIssuesMap.put("Potential Bugs", Map.of(1, 5));
        categoryIssuesMap.put("Miscellaneous", Map.of());

        assertThat(statistics.getCategoryIssuesMap()).containsExactlyInAnyOrderEntriesOf(categoryIssuesMap);

    }

    @Test
    public void shouldGetCorrectLatestAutomaticResults() {
        createTestParticipationsWithResults();
        var results = resultRepository.findLatestAutomaticResultsWithEagerFeedbacksForExercise(programmingExerciseSCAEnabled.getId());
        assertThat(results.size()).isEqualTo(5);
    }

    @Test
    public void shouldGetCorrectLatestAutomaticResultsWithMultipleResults() {
        createTestParticipationsWithMultipleResults();
        // this method is tested. It should probably be improved as there is an inner query
        var results = resultRepository.findLatestAutomaticResultsWithEagerFeedbacksForExercise(programmingExerciseSCAEnabled.getId());
        var allResults = resultRepository.findAllByExerciseId(programmingExerciseSCAEnabled.getId());
        assertThat(results.size()).isEqualTo(5);
        assertThat(allResults.size()).isEqualTo(6);
    }

    private void activateAllTestCases(boolean withBonus) {
        var testCases = new ArrayList<>(testCaseService.findByExerciseId(programmingExerciseSCAEnabled.getId()));
        var bonusMultiplier = withBonus ? 2D : null;
        var bonusPoints = withBonus ? 4D : null;
        testCases.get(0).active(true).afterDueDate(false).bonusMultiplier(bonusMultiplier).bonusPoints(bonusPoints);
        testCases.get(1).active(true).afterDueDate(false).bonusMultiplier(bonusMultiplier).bonusPoints(bonusPoints);
        testCases.get(2).active(true).afterDueDate(false).bonusMultiplier(bonusMultiplier).bonusPoints(bonusPoints);
        testCaseRepository.saveAll(testCases);
    }

    private List<Participation> createTestParticipationsWithResults() {

        // create results
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student1");
        {
            // Testcases: 1/6 * 42 = 7; Penalty: min(5, 0.2 * 42) = 5; Score: (int) ((7-5) / 42) = 4
            var result1 = new Result().participation(participation1);
            participation1.setResults(Set.of(result1));
            updateAndSaveAutomaticResult(result1, true, false, false, 0, 1);
        }
        var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student2");
        {
            // Testcases: 4/6 * 42 = 28; Penalty: 11; Score: (int) ((28-11) / 42)) = 40
            var result2 = new Result().participation(participation2);
            participation2.setResults(Set.of(result2));
            updateAndSaveAutomaticResult(result2, true, false, true, 2, 1);
        }
        var participation3 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student3");
        {
            // Points capped at zero, score can't be negative
            var result3 = new Result().participation(participation3);
            participation3.setResults(Set.of(result3));
            updateAndSaveAutomaticResult(result3, true, false, false, 5, 1);
        }
        var participation4 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student4");
        {
            // Run into category cap of 10: -> Testcases: 3/6 * 42 = 21; Penalty: 10; Score: (int) ((21-10) / 42)) = 26
            var result4 = new Result().participation(participation4);
            participation4.setResults(Set.of(result4));
            updateAndSaveAutomaticResult(result4, true, true, false, 5, 0);
        }
        var participation5 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student5");
        {
            // Run into max exercise penalty cap of 40 percent and all test cases pass -> score 60 percent
            var result5 = new Result().participation(participation5);
            participation5.setResults(Set.of(result5));
            updateAndSaveAutomaticResult(result5, true, true, true, 5, 5);
        }

        return List.of(participation1, participation2, participation3, participation4, participation5);
    }

    private List<Participation> createTestParticipationsWithMultipleResults() {

        // create results
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student1");
        {
            // Testcases: 1/6 * 42 = 7; Penalty: min(5, 0.2 * 42) = 5; Score: (int) ((7-5) / 42) = 4
            var result11 = new Result().participation(participation1);
            var result1 = new Result().participation(participation1);
            participation1.setResults(Set.of(result11, result1));
            updateAndSaveAutomaticResult(result11, false, false, false, 0, 1, ZonedDateTime.now().minusMinutes(1));
            updateAndSaveAutomaticResult(result1, true, false, false, 0, 1);
        }
        var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student2");
        {
            // Testcases: 4/6 * 42 = 28; Penalty: 11; Score: (int) ((28-11) / 42)) = 40
            var result2 = new Result().participation(participation2);
            participation2.setResults(Set.of(result2));
            updateAndSaveAutomaticResult(result2, true, false, true, 2, 1);
        }
        var participation3 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student3");
        {
            // Points capped at zero, score can't be negative
            var result3 = new Result().participation(participation3);
            participation3.setResults(Set.of(result3));
            updateAndSaveAutomaticResult(result3, true, false, false, 5, 1);
        }
        var participation4 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student4");
        {
            // Run into category cap of 10: -> Testcases: 3/6 * 42 = 21; Penalty: 10; Score: (int) ((21-10) / 42)) = 26
            var result4 = new Result().participation(participation4);
            participation4.setResults(Set.of(result4));
            updateAndSaveAutomaticResult(result4, true, true, false, 5, 0);
        }
        var participation5 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student5");
        {
            // Run into max exercise penalty cap of 40 percent and all test cases pass -> score 60 percent
            var result5 = new Result().participation(participation5);
            participation5.setResults(Set.of(result5));
            updateAndSaveAutomaticResult(result5, true, true, true, 5, 5);
        }

        return List.of(participation1, participation2, participation3, participation4, participation5);
    }

    private void testParticipationResult(Result result, Double score, String resultString, boolean hasFeedback, int feedbackSize, AssessmentType assessmentType) {
        assertThat(result.getScore()).isEqualTo(score, Offset.offset(offsetByTenThousandth));
        assertThat(result.getResultString()).isEqualTo(resultString);
        assertThat(result.getHasFeedback()).isEqualTo(hasFeedback);
        assertThat(result.getFeedbacks()).hasSize(feedbackSize);
        assertThat(result.getAssessmentType()).isEqualTo(assessmentType);
    }

    private void updateAndSaveAutomaticResult(Result result, boolean test1Passes, boolean test2Passes, boolean test3Passes, int issuesCategory1, int issuesCategory2) {
        updateAndSaveAutomaticResult(result, test1Passes, test2Passes, test3Passes, issuesCategory1, issuesCategory2, ZonedDateTime.now());
    }

    private void updateAndSaveAutomaticResult(Result result, boolean test1Passes, boolean test2Passes, boolean test3Passes, int issuesCategory1, int issuesCategory2,
            ZonedDateTime completionDate) {
        result.addFeedback(new Feedback().result(result).text("test1").positive(test1Passes).type(FeedbackType.AUTOMATIC));
        result.addFeedback(new Feedback().result(result).text("test2").positive(test2Passes).type(FeedbackType.AUTOMATIC));
        result.addFeedback(new Feedback().result(result).text("test3").positive(test3Passes).type(FeedbackType.AUTOMATIC));

        for (int i = 0; i < issuesCategory1; i++) {
            result.addFeedback(new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("SPOTBUGS")
                    .detailText("{\"category\": \"BAD_PRACTICE\"}").type(FeedbackType.AUTOMATIC).positive(false));
        }
        for (int i = 0; i < issuesCategory2; i++) {
            result.addFeedback(new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("SPOTBUGS").detailText("{\"category\": \"STYLE\"}")
                    .type(FeedbackType.AUTOMATIC).positive(false));
        }

        var feedbackForInactiveCategory = ModelFactory.createSCAFeedbackWithInactiveCategory(result);
        result.addFeedback(feedbackForInactiveCategory);

        result.addFeedback(new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("SPOTBUGS").detailText("{\"category\": \"CORRECTNESS\"}")
                .type(FeedbackType.AUTOMATIC).positive(false));

        result.rated(true) //
                .hasFeedback(true) //
                .successful(test1Passes && test2Passes && test3Passes) //
                .completionDate(completionDate) //
                .assessmentType(AssessmentType.AUTOMATIC);

        gradingService.calculateScoreForResult(result, programmingExerciseSCAEnabled, true);

        resultRepository.save(result);
    }
}
