package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_DUPLICATE_NOTIFICATION;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseGradingResource;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseGradingStatisticsDTO;

/**
 * Tests the {@link ProgrammingExerciseGradingService}.
 * <p>
 * This includes: calculation of (re-)evaluation automatic feedback, test cases, static code analysis, result scores and points.
 * <p>
 * This <b>abstract</b> test class two nested subclasses that run all tests for different exercise setups:
 * <ul>
 * <li> {@link CourseProgrammingExerciseGradingServiceTest} - for exercises in courses.</li>
 * <li>{@link ExamProgrammingExerciseGradingServiceTest} - for exercises in an exam setting.</li>
 * </ul>
 */
abstract class ProgrammingExerciseGradingServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseGradingService gradingService;

    private ProgrammingExercise programmingExerciseSCAEnabled;

    private ProgrammingExercise programmingExercise;

    private Result result;

    private final Double offsetByTenThousandth = 0.0001;

    @BeforeEach
    void setUp() {
        database.addUsers(5, 1, 0, 1);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        programmingExercise = generateDefaultProgrammingExercise();
        database.addTestCasesToProgrammingExercise(programmingExercise);

        programmingExerciseSCAEnabled = generateScaProgrammingExercise();
        database.addTestCasesToProgrammingExercise(programmingExerciseSCAEnabled);

        ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        result = new Result();
        result.setParticipation(participation);
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    /**
     * Generates a new default, non-SCA programming exercise with 42.0 points, no bonus points and no test cases.
     */
    abstract ProgrammingExercise generateDefaultProgrammingExercise();

    /**
     * Generates a new programming exercise with SCA enabled and 42.0 points, no bonus points, no test cases and a SCA penalty of 40%. The SCA categories must be saved in the database.
     */
    abstract ProgrammingExercise generateScaProgrammingExercise();

    /**
     * Set the date after which students cannot work on the exercise anymore. May differ depending on the type of the exercise.
     */
    abstract ProgrammingExercise changeRelevantExerciseEndDate(ProgrammingExercise programmingExercise, ZonedDateTime endDate);

    /**
     * Test class for COURSE exercises
     */
    public static class CourseProgrammingExerciseGradingServiceTest extends ProgrammingExerciseGradingServiceTest {

        @Override
        ProgrammingExercise generateDefaultProgrammingExercise() {
            Course course = database.addEmptyCourse();
            Long programmingExerciseId = database.addProgrammingExerciseToCourse(course, false).getId();
            return super.programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExerciseId);
        }

        @Override
        ProgrammingExercise generateScaProgrammingExercise() {
            Long programmingExerciseId = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories().getId();
            return super.programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExerciseId);
        }

        @Override
        ProgrammingExercise changeRelevantExerciseEndDate(ProgrammingExercise programmingExercise, ZonedDateTime endDate) {
            programmingExercise.setDueDate(endDate);
            return super.programmingExerciseRepository.save(programmingExercise);
        }
    }

    /**
     * Test class for EXAM exercises
     */
    public static class ExamProgrammingExerciseGradingServiceTest extends ProgrammingExerciseGradingServiceTest {

        @Override
        ProgrammingExercise generateDefaultProgrammingExercise() {
            ProgrammingExercise programmingExercise = newExamProgrammingExercise();
            return super.programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
        }

        @Override
        ProgrammingExercise generateScaProgrammingExercise() {
            ProgrammingExercise programmingExercise = newExamProgrammingExercise();
            database.addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);
            return super.programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
        }

        private ProgrammingExercise newExamProgrammingExercise() {
            ExerciseGroup group = database.addExerciseGroupWithExamAndCourse(true);
            ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExerciseForExam(group);
            // Adjust settings so that exam and course exercises can use the same tests
            programmingExercise.setMaxPoints(42.0);
            programmingExercise.setMaxStaticCodeAnalysisPenalty(40);
            programmingExercise = super.programmingExerciseRepository.save(programmingExercise);
            programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
            programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
            return programmingExercise;
        }

        @Override
        ProgrammingExercise changeRelevantExerciseEndDate(ProgrammingExercise programmingExercise, ZonedDateTime endDate) {
            Exam exam = programmingExercise.getExamViaExerciseGroupOrCourseMember();
            // Only change the exam end date, as exam exercises don't have individual dates (all dates are null)
            exam.setEndDate(endDate);
            super.examRepository.save(exam);
            return programmingExercise;
        }
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
        bambooRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldNotUpdateResultIfNoTestCasesExist() {
        testCaseRepository.deleteAll();

        Double scoreBeforeUpdate = result.getScore();
        gradingService.calculateScoreForResult(result, programmingExercise, true);

        assertThat(result.getScore()).isEqualTo(scoreBeforeUpdate);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldAddFeedbackForDuplicateTestCases() {
        // Adjust existing test cases to our need
        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).visibility(Visibility.ALWAYS);
        testCases.get("test2").active(true).visibility(Visibility.ALWAYS);
        testCases.get("test3").active(true).visibility(Visibility.ALWAYS);
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
                .filter(feedback -> feedback.getDetailText() != null && feedback.getDetailText().contains("This is a duplicate test case.")).toList();
        assertThat(result.getScore()).isEqualTo(0D);
        assertThat(duplicateFeedbackEntries).hasSize(2);
        int countOfNewFeedbacks = originalFeedbackSize + duplicateFeedbackEntries.size();
        assertThat(result.getFeedbacks()).hasSize(countOfNewFeedbacks);
        String notificationText = TEST_CASES_DUPLICATE_NOTIFICATION + "test3, test1";
        verify(groupNotificationService).notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(programmingExercise, notificationText);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldRecalculateScoreBasedOnTestCasesWeightAutomatic() {
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test4").positive(false).type(FeedbackType.AUTOMATIC));
        result.setFeedbacks(feedbacks);
        result.setSuccessful(false);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        Double scoreBeforeUpdate = result.getScore();

        gradingService.calculateScoreForResult(result, programmingExercise, true);

        Double expectedScore = 25D;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldRecalculateScoreBasedOnTestCasesWeightManual() {
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
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldSetScoreCorrectlyIfWeightSumIsReallyBigOrReallySmall() {
        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).visibility(Visibility.ALWAYS).weight(0.);
        testCases.get("test2").active(true).visibility(Visibility.ALWAYS).weight(0.00000000000000001);
        testCases.get("test3").active(false).weight(0.);
        testCaseRepository.saveAll(testCases.values());

        Result result = new Result();
        result.addFeedback(new Feedback().result(result).text("test1").positive(false).type(FeedbackType.AUTOMATIC));
        result.addFeedback(new Feedback().result(result).text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        result.rated(true).hasFeedback(true).successful(false).completionDate(ZonedDateTime.now()).assessmentType(AssessmentType.AUTOMATIC);

        result = gradingService.calculateScoreForResult(result, programmingExercise, false);
        assertThat(result.getScore()).isZero();
        testCases.get("test2").active(true).visibility(Visibility.ALWAYS).weight(0.8000000000);
        testCaseRepository.saveAll(testCases.values());

        result = gradingService.calculateScoreForResult(result, programmingExercise, false);
        assertThat(result.getScore()).isPositive();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldRecalculateScoreWithTestCaseBonusButNoExerciseBonus() {
        // Set up test cases with bonus
        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).visibility(Visibility.ALWAYS).weight(5.).bonusMultiplier(1D).setBonusPoints(7D);
        testCases.get("test2").active(true).visibility(Visibility.ALWAYS).weight(2.).bonusMultiplier(2D).setBonusPoints(0D);
        testCases.get("test3").active(true).visibility(Visibility.ALWAYS).weight(3.).bonusMultiplier(1D).setBonusPoints(10.5D);

        testCaseRepository.saveAll(testCases.values());

        Participation participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");

        var result1 = new Result();
        result1.setParticipation(participation);
        result1 = updateAndSaveAutomaticResult(result1, false, false, true);

        var result2 = new Result();
        result2.setParticipation(participation);
        result2 = updateAndSaveAutomaticResult(result2, true, false, false);

        var result3 = new Result();
        result3.setParticipation(participation);
        result3 = updateAndSaveAutomaticResult(result3, false, true, false);

        var result4 = new Result();
        result4.setParticipation(participation);
        result4 = updateAndSaveAutomaticResult(result4, false, true, true);

        var result5 = new Result();
        result5.setParticipation(participation);
        result5 = updateAndSaveAutomaticResult(result5, true, true, true);

        var result6 = new Result();
        result6.setParticipation(participation);
        result6 = updateAndSaveAutomaticResult(result6, false, false, false);

        // Build failure
        var resultBF = new Result().feedbacks(List.of()).rated(true).score(0D).hasFeedback(false).completionDate(ZonedDateTime.now()).assessmentType(AssessmentType.AUTOMATIC);
        resultBF.setParticipation(participation);
        gradingService.calculateScoreForResult(resultBF, programmingExercise, true);

        // Missing feedback
        var resultMF = new Result();
        resultMF.setParticipation(participation);
        var feedbackMF = new Feedback().result(result).text("test3").positive(true).type(FeedbackType.AUTOMATIC).result(resultMF);
        resultMF.feedbacks(new ArrayList<>(List.of(feedbackMF))) // List must be mutable
                .rated(true).score(0D).hasFeedback(true).completionDate(ZonedDateTime.now()).assessmentType(AssessmentType.AUTOMATIC);
        gradingService.calculateScoreForResult(resultMF, programmingExercise, true);

        // Assertions result1 - calculated
        assertThat(result1.getScore()).isEqualTo(55D, Offset.offset(offsetByTenThousandth));
        assertThat(result1.getHasFeedback()).isTrue();
        assertThat(result1.isSuccessful()).isFalse();
        assertThat(result1.getFeedbacks()).hasSize(3);

        // Assertions result2 - calculated
        assertThat(result2.getScore()).isEqualTo(66.7);
        assertThat(result2.getHasFeedback()).isTrue();
        assertThat(result2.isSuccessful()).isFalse();
        assertThat(result2.getFeedbacks()).hasSize(3);

        // Assertions result3 - calculated
        assertThat(result3.getScore()).isEqualTo(40D);
        assertThat(result3.getHasFeedback()).isTrue();
        assertThat(result3.isSuccessful()).isFalse();
        assertThat(result3.getFeedbacks()).hasSize(3);

        // Assertions result4 - calculated
        assertThat(result4.getScore()).isEqualTo(95D, Offset.offset(offsetByTenThousandth));
        assertThat(result4.getHasFeedback()).isTrue();
        assertThat(result4.isSuccessful()).isFalse();
        assertThat(result4.getFeedbacks()).hasSize(3);

        // Assertions result5 - capped to 100
        assertThat(result5.getScore()).isEqualTo(100D);
        assertThat(result5.getHasFeedback()).isFalse();
        assertThat(result5.isSuccessful()).isTrue();
        assertThat(result5.getFeedbacks()).hasSize(3);

        // Assertions result6 - only negative feedback
        assertThat(result6.getScore()).isEqualTo(0D);
        assertThat(result6.getHasFeedback()).isTrue();
        assertThat(result6.isSuccessful()).isFalse();
        assertThat(result6.getFeedbacks()).hasSize(3);

        // Assertions resultBF - build failure
        assertThat(resultBF.getScore()).isEqualTo(0D);
        assertThat(resultBF.getHasFeedback()).isFalse();
        assertThat(resultBF.isSuccessful()).isNull(); // Won't get touched by the service method
        assertThat(resultBF.getFeedbacks()).isEmpty();

        // Assertions resultMF - missing feedback will be created but is negative
        assertThat(resultMF.getScore()).isEqualTo(55D, Offset.offset(offsetByTenThousandth));
        assertThat(resultMF.getHasFeedback()).isFalse(); // Generated missing feedback is omitted
        assertThat(resultMF.isSuccessful()).isFalse();
        assertThat(resultMF.getFeedbacks()).hasSize(3); // Feedback is created for test cases if missing
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldRecalculateScoreWithTestCaseBonusAndExerciseBonus() {
        // Set up test cases with bonus
        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).visibility(Visibility.ALWAYS).weight(4.).bonusMultiplier(1D).setBonusPoints(0D);
        testCases.get("test2").active(true).visibility(Visibility.ALWAYS).weight(3.).bonusMultiplier(3D).setBonusPoints(21D);
        testCases.get("test3").active(true).visibility(Visibility.ALWAYS).weight(3.).bonusMultiplier(2D).setBonusPoints(14D);
        testCaseRepository.saveAll(testCases.values());

        // Score should be capped at 200%
        programmingExercise.setBonusPoints(programmingExercise.getMaxPoints());
        programmingExerciseRepository.save(programmingExercise);

        Participation participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");

        var result1 = new Result();
        result1.setParticipation(participation);
        result1 = updateAndSaveAutomaticResult(result1, false, false, true);

        var result2 = new Result();
        result2.setParticipation(participation);
        result2 = updateAndSaveAutomaticResult(result2, true, false, true);

        var result3 = new Result();
        result3.setParticipation(participation);
        result3 = updateAndSaveAutomaticResult(result3, true, true, false);

        var result4 = new Result();
        result4.setParticipation(participation);
        result4 = updateAndSaveAutomaticResult(result4, false, true, true);

        // Assertions result1 - calculated
        assertThat(result1.getScore()).isEqualTo(93.3);
        assertThat(result1.getHasFeedback()).isTrue();
        assertThat(result1.isSuccessful()).isFalse();
        assertThat(result1.getFeedbacks()).hasSize(3);

        // Assertions result2 - calculated
        assertThat(result2.getScore()).isEqualTo(133.3);
        assertThat(result2.getHasFeedback()).isTrue();
        assertThat(result2.isSuccessful()).isTrue();
        assertThat(result2.getFeedbacks()).hasSize(3);

        // Assertions result3 - calculated
        assertThat(result3.getScore()).isEqualTo(180D, Offset.offset(offsetByTenThousandth));
        assertThat(result3.getHasFeedback()).isTrue();
        assertThat(result3.isSuccessful()).isTrue();
        assertThat(result3.getFeedbacks()).hasSize(3);

        // Assertions result4 - capped at 200%
        assertThat(result4.getScore()).isEqualTo(200D);
        assertThat(result4.getHasFeedback()).isTrue();
        assertThat(result4.isSuccessful()).isTrue();
        assertThat(result4.getFeedbacks()).hasSize(3);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldRemoveTestsWithAfterDueDateFlagIfDueDateHasNotPassed() {
        // Set programming exercise due date in future.
        programmingExercise = changeRelevantExerciseEndDate(programmingExercise, ZonedDateTime.now().plusHours(10));

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
        assertThat(result.isSuccessful()).isFalse();
        // The feedback of the after due date test case must still be there but have its visibility set to AFTER_DUE_DATE.
        assertThat(result.getFeedbacks().stream().filter(feedback -> feedback.getVisibility() == Visibility.AFTER_DUE_DATE).map(Feedback::getText)).containsExactly("test3");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldNotIncludeTestsInResultWithAfterDueDateFlagIfDueDateHasNotPassedForNonStudentParticipation() {
        // Set programming exercise due date in future.
        programmingExercise = changeRelevantExerciseEndDate(programmingExercise, ZonedDateTime.now().plusHours(10));

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
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFeedbacks().stream().filter(f -> f.getVisibility() == Visibility.ALWAYS)).hasSize(1);
        assertThat(result.getFeedbacks().stream().filter(f -> f.getVisibility() == Visibility.AFTER_DUE_DATE)).hasSize(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldKeepTestsWithAfterDueDateFlagIfDueDateHasPassed() {
        // Set programming exercise due date in the past.
        programmingExercise = changeRelevantExerciseEndDate(programmingExercise, ZonedDateTime.now().minusHours(10));
        result.getParticipation().setExercise(programmingExercise);

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
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
        // The feedback of the after due date test case must be kept.
        assertThat(result.getFeedbacks().stream().noneMatch(feedback -> "test3".equals(feedback.getText()))).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldReEvaluateScoreOfTheCorrectResults() throws Exception {
        programmingExercise = (ProgrammingExercise) database.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        var testCases = createTestCases(false);
        var testParticipations = createTestParticipations();
        changeTestCaseWeights(testCases);

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
            testParticipationResult(singleResult, 0D, true, 3, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }

        // solution 100 %
        {
            var participation = programmingExercise.getSolutionParticipation();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 100D, true, 3, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }

        verifyStudentScoreCalculations(testParticipations);
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldNotIncludeTestsMarkedAsNeverVisibleInScoreCalculation(boolean isAfterDueDate) throws Exception {
        // test case marked as never should not affect score for students neither before nor after due date
        if (isAfterDueDate) {
            programmingExercise = changeRelevantExerciseEndDate(programmingExercise, ZonedDateTime.now().minusHours(10));
        }
        else {
            // Set programming exercise due date in future.
            programmingExercise = changeRelevantExerciseEndDate(programmingExercise, ZonedDateTime.now().plusHours(10));
        }

        var invisibleTestCase = new ProgrammingExerciseTestCase().testName("test4").exercise(programmingExercise);
        testCaseRepository.save(invisibleTestCase);

        programmingExercise = (ProgrammingExercise) database.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        final var testCases = createTestCases(true);
        final var testParticipations = createTestParticipations();
        changeTestCaseWeights(testCases);

        // re-evaluate
        final var endpoint = ProgrammingExerciseGradingResource.RE_EVALUATE.replace("{exerciseId}", programmingExercise.getId().toString());
        final var response = request.putWithResponseBody(ROOT + endpoint, "{}", Integer.class, HttpStatus.OK);
        assertThat(response).isEqualTo(7);

        // this fixes an issue with the authentication context after a mock request
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());

        // Tests
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        // the invisible test case should however be visible for the template and solution repos

        // template 0 %
        {
            var participation = programmingExercise.getTemplateParticipation();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 0D, true, 4, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }

        // solution 100 %
        {
            var participation = programmingExercise.getSolutionParticipation();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 100D, true, 4, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }

        verifyStudentScoreCalculations(testParticipations);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateTheLatestResultOfASingleParticipation() {
        programmingExercise = (ProgrammingExercise) database.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        final var testCases = createTestCases(false);
        final var testParticipations = createTestParticipations();
        changeTestCaseWeights(testCases);

        for (int student = 1; student <= 5; ++student) {
            final var testParticipation = (ProgrammingExerciseStudentParticipation) testParticipations[student - 1];
            final List<Result> updatedResults = programmingExerciseGradingService.updateParticipationResults(testParticipation);
            resultRepository.saveAll(updatedResults);

            verifyStudentScoreCalculation(testParticipations, student);
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateOnlyResultsForParticipationsWithoutIndividualDueDate() {
        programmingExercise = (ProgrammingExercise) database.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        final var testCases = createTestCases(false);
        final var testParticipations = createTestParticipations();
        changeTestCaseWeights(testCases);

        var participationWithIndividualDueDate = testParticipations[3];
        participationWithIndividualDueDate.setIndividualDueDate(ZonedDateTime.now().plusHours(4));
        participationWithIndividualDueDate = studentParticipationRepository.save((StudentParticipation) participationWithIndividualDueDate);
        final Long participationWithIndividualDueDateId = participationWithIndividualDueDate.getId();

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        final var updated = programmingExerciseGradingService.updateResultsOnlyRegularDueDateParticipations(programmingExercise);
        // four student results + template + solution
        assertThat(updated).hasSize(6);

        final var updatedParticipationIds = updated.stream().map(result -> result.getParticipation().getId()).collect(Collectors.toSet());
        assertThat(updatedParticipationIds).hasSize(5).allMatch(participationId -> !Objects.equals(participationId, participationWithIndividualDueDateId));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testWeightSumZero() {
        programmingExercise = (ProgrammingExercise) database.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(programmingExercise.getId());

        final var testCases = createTestCases(false);
        createTestParticipations();

        for (final var testCase : testCases.values()) {
            testCase.setWeight(0D);
        }
        testCases.get("test1").setBonusMultiplier(1.4D);
        testCaseRepository.saveAll(testCases.values());

        final var updatedResults = programmingExerciseGradingService.updateAllResults(programmingExercise);
        assertThat(updatedResults).hasSize(7);

        // even though the test case weights are all zero, the solution should receive a score
        // => every test case is weighted with 1.0 in that case
        final var updatedSolution = updatedResults.stream().filter(result -> result.getParticipation() instanceof SolutionProgrammingExerciseParticipation).findFirst().get();
        assertThat(updatedSolution.getScore()).isCloseTo(66.7, Offset.offset(offsetByTenThousandth));

        final var updatedStudentResults = updatedResults.stream().filter(result -> result.getParticipation() instanceof StudentParticipation).toList();
        assertThat(updatedStudentResults).hasSize(5);

        for (final var result : updatedStudentResults) {
            result.getFeedbacks().stream().filter(feedback -> Boolean.TRUE.equals(feedback.isPositive())).filter(feedback -> FeedbackType.AUTOMATIC.equals(feedback.getType()))
                    .forEach(feedback -> {
                        double bonusPoints = testCases.get(feedback.getText()).getBonusPoints();
                        assertThat(feedback.getCredits()).isEqualTo(bonusPoints);
                    });
        }
    }

    private Map<String, ProgrammingExerciseTestCase> createTestCases(boolean withAdditionalInvisibleTestCase) {
        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).visibility(Visibility.ALWAYS).setWeight(1.);
        testCases.get("test2").active(true).visibility(Visibility.ALWAYS).setWeight(1.);
        testCases.get("test3").active(true).visibility(Visibility.ALWAYS).setWeight(2.);
        if (withAdditionalInvisibleTestCase) {
            testCases.get("test4").active(true).visibility(Visibility.NEVER).setWeight(1.);
        }
        testCaseRepository.saveAll(testCases.values());

        return testCases;
    }

    private void changeTestCaseWeights(final Map<String, ProgrammingExerciseTestCase> testCases) {
        // change test case weights
        testCases.get("test1").setWeight(0.);
        testCases.get("test2").setWeight(1.);
        testCases.get("test3").setWeight(3.);
        testCaseRepository.saveAll(testCases.values());
    }

    private void verifyStudentScoreCalculations(final Participation[] testParticipations) {
        for (int student = 1; student <= 5; ++student) {
            verifyStudentScoreCalculation(testParticipations, student);
        }
    }

    private void verifyStudentScoreCalculation(final Participation[] testParticipations, int student) {
        var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(testParticipations[student - 1].getId()).get();
        var results = participation.getResults();

        if (student == 1) {
            // student1 25 %
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 25D, true, 3, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }
        else if (student == 2) {
            // student2 61% % / 75 %
            assertThat(results).hasSize(2);

            var manualResultOptional = results.stream().filter(result -> result.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC).findAny();
            assertThat(manualResultOptional).isPresent();
            testParticipationResult(manualResultOptional.get(), 86D, true, 6, AssessmentType.SEMI_AUTOMATIC);
            assertThat(manualResultOptional).contains(participation.findLatestLegalResult());

            var automaticResultOptional = results.stream().filter(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC).findAny();
            assertThat(automaticResultOptional).isPresent();
            testParticipationResult(automaticResultOptional.get(), 75D, true, 3, AssessmentType.AUTOMATIC);
        }
        else if (student == 3) {
            // student3 no result
            assertThat(results).isNullOrEmpty();
            assertThat(participation.findLatestLegalResult()).isNull();
        }
        else if (student == 4) {
            // student4 100%
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 100D, false, 3, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }
        else if (student == 5) {
            // student5 Build failed
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 0D, false, 0, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
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
            var resultTemplate = new Result().participation(participationTemplate).successful(false).rated(true).score(100D);
            participationTemplate.setResults(Set.of(resultTemplate));
            updateAndSaveAutomaticResult(resultTemplate, false, false, false);
        }

        // solution passes most tests but is still faulty
        var participationSolution = programmingExercise.getSolutionParticipation();
        {
            // score 75 %
            var resultSolution = new Result().participation(participationSolution).successful(false).rated(true).score(100D);
            participationSolution.setResults(Set.of(resultSolution));
            updateAndSaveAutomaticResult(resultSolution, false, true, true);
        }

        // student1 only has one automatic result
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        {
            // score 50 %
            var result1 = new Result().participation(participation1).successful(false).rated(true).score(100D);
            participation1.setResults(Set.of(result1));
            updateAndSaveAutomaticResult(result1, true, true, false);
        }
        testParticipations[0] = participation1;

        // student2 has an automatic result and a manual result as well
        var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        {
            // score 75 %
            var result2a = new Result().participation(participation2).successful(false).rated(true).score(100D);
            result2a = updateAndSaveAutomaticResult(result2a, true, false, true);

            // score 61 %
            var result2b = new Result().participation(participation2).score(61D).successful(false).rated(true).hasFeedback(true).completionDate(ZonedDateTime.now())
                    .assessmentType(AssessmentType.SEMI_AUTOMATIC);
            result2b.addFeedback(new Feedback().result(result2b).text("test1").positive(false).type(FeedbackType.AUTOMATIC).credits(0.00));
            result2b.addFeedback(new Feedback().result(result2b).text("test2").positive(false).type(FeedbackType.AUTOMATIC).credits(0.00));
            result2b.addFeedback(new Feedback().result(result2b).text("test3").positive(true).type(FeedbackType.AUTOMATIC).credits(50.00));
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
            var result4 = new Result().participation(participation4).successful(false).rated(true).score(100D);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldRemoveInvisibleStaticCodeAnalysisFeedbackOnGrading() throws Exception {
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student1");
        var result1 = new Result().participation(participation1).successful(false).rated(true).score(100D);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldCalculateScoreWithStaticCodeAnalysisPenaltiesWithoutCaps() {
        activateAllTestCases(false);

        // Remove category penalty limits
        var updatedCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId()).stream().peek(category -> category.setMaxPenalty(null))
                .toList();
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
            testParticipationResult(singleResult, 60D, true, 24, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation2.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 71.4, true, 8, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
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
            testParticipationResult(singleResult, 0D, true, 24, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation4.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 11.9, true, 12, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldCalculateScoreWithStaticCodeAnalysisPenaltiesWithBonus() {
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
            testParticipationResult(singleResult, 79.0, true, 9, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }

        // Remove max penalty from exercise
        programmingExerciseSCAEnabled.setMaxStaticCodeAnalysisPenalty(null);
        programmingExerciseRepository.save(programmingExerciseSCAEnabled);

        // Remove category penalty limits
        var updatedCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId()).stream().peek(category -> category.setMaxPenalty(null))
                .toList();
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
            testParticipationResult(singleResult, 19.0, true, 19, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldCalculateScoreWithStaticCodeAnalysisPenalties() {
        activateAllTestCases(false);

        var participations = createTestParticipationsWithResults();

        double[] expectedScores = { 4.8, 40.5, 0, 26.2, 60 };
        int[] expectedFeedbackSize = { 5, 7, 10, 9, 14 };

        for (int i = 0; i < participations.size(); i++) {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participations.get(i).getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, expectedScores[i], true, expectedFeedbackSize[i], AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestLegalResult());
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldCalculateCorrectStatistics() throws Exception {
        activateAllTestCases(false);
        createTestParticipationsWithResults();

        // get statistics
        final var endpoint = ProgrammingExerciseGradingResource.STATISTICS.replace("{exerciseId}", programmingExerciseSCAEnabled.getId().toString());
        final var statistics = request.get(ROOT + endpoint, HttpStatus.OK, ProgrammingExerciseGradingStatisticsDTO.class);

        assertThat(statistics.getNumParticipations()).isEqualTo(5);

        var testCaseStatsMap = new HashMap<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats>();
        testCaseStatsMap.put("test1", new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(5, 0));
        testCaseStatsMap.put("test2", new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(2, 3));
        testCaseStatsMap.put("test3", new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(2, 3));

        // check some additional methods to increase test coverage
        var test1 = testCaseStatsMap.get("test1");
        var test2 = testCaseStatsMap.get("test2");
        assertThat(test1.getNumFailed()).isZero();
        assertThat(test1.getNumPassed()).isEqualTo(5);
        assertThat(test1.hashCode()).isNotEqualTo(test2.hashCode());
        assertThat(test1).isNotEqualTo(test2).isNotNull();

        assertThat(statistics.getTestCaseStatsMap()).containsExactlyInAnyOrderEntriesOf(testCaseStatsMap);

        var categoryIssuesMap = new HashMap<String, Map<Integer, Integer>>();
        categoryIssuesMap.put("Bad Practice", Map.of(2, 1, 5, 3));
        categoryIssuesMap.put("Code Style", Map.of(1, 3, 5, 1));
        categoryIssuesMap.put("Potential Bugs", Map.of(1, 5));
        categoryIssuesMap.put("Miscellaneous", Map.of());

        assertThat(statistics.getCategoryIssuesMap()).containsExactlyInAnyOrderEntriesOf(categoryIssuesMap);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldGetCorrectLatestAutomaticResults() {
        createTestParticipationsWithResults();
        var results = resultRepository.findLatestAutomaticResultsWithEagerFeedbacksForExercise(programmingExerciseSCAEnabled.getId());
        assertThat(results).hasSize(5);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldGetCorrectLatestAutomaticResultsWithMultipleResults() {
        createTestParticipationsWithMultipleResults();
        // this method is tested. It should probably be improved as there is an inner query
        var results = resultRepository.findLatestAutomaticResultsWithEagerFeedbacksForExercise(programmingExerciseSCAEnabled.getId());
        var allResults = resultRepository.findAllByExerciseId(programmingExerciseSCAEnabled.getId());
        assertThat(results).hasSize(5);
        assertThat(allResults).hasSize(6);
    }

    private void activateAllTestCases(boolean withBonus) {
        var testCases = new ArrayList<>(testCaseService.findByExerciseId(programmingExerciseSCAEnabled.getId()));
        var bonusMultiplier = withBonus ? 2D : null;
        var bonusPoints = withBonus ? 4D : null;
        testCases.get(0).active(true).visibility(Visibility.ALWAYS).bonusMultiplier(bonusMultiplier).bonusPoints(bonusPoints);
        testCases.get(1).active(true).visibility(Visibility.ALWAYS).bonusMultiplier(bonusMultiplier).bonusPoints(bonusPoints);
        testCases.get(2).active(true).visibility(Visibility.ALWAYS).bonusMultiplier(bonusMultiplier).bonusPoints(bonusPoints);
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

    private void createTestParticipationsWithMultipleResults() {

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

    }

    private void testParticipationResult(Result result, Double score, boolean hasFeedback, int feedbackSize, AssessmentType assessmentType) {
        assertThat(result.getScore()).isEqualTo(score, Offset.offset(offsetByTenThousandth));
        assertThat(result.getHasFeedback()).isEqualTo(hasFeedback);
        assertThat(result.getFeedbacks()).hasSize(feedbackSize);
        assertThat(result.getAssessmentType()).isEqualTo(assessmentType);
    }

    private void updateAndSaveAutomaticResult(Result result, boolean test1Passes, boolean test2Passes, boolean test3Passes, int issuesCategory1, int issuesCategory2) {
        updateAndSaveAutomaticResult(result, test1Passes, test2Passes, test3Passes, issuesCategory1, issuesCategory2, ZonedDateTime.now());
    }

    private void updateAndSaveAutomaticResult(Result result, boolean test1Passes, boolean test2Passes, boolean test3Passes, int issuesCategory1, int issuesCategory2,
            ZonedDateTime completionDate) {
        result.addFeedback(new Feedback().result(result).text("test1").positive(test1Passes).positive(test1Passes).type(FeedbackType.AUTOMATIC));
        result.addFeedback(new Feedback().result(result).text("test2").positive(test2Passes).positive(test2Passes).type(FeedbackType.AUTOMATIC));
        result.addFeedback(new Feedback().result(result).text("test3").positive(test3Passes).positive(test3Passes).type(FeedbackType.AUTOMATIC));

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
