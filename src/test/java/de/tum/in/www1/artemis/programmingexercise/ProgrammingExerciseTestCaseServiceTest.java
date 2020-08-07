package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;

public class ProgrammingExerciseTestCaseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    DatabaseUtilService database;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExercise programmingExerciseWithBonus;

    private Result result;

    @BeforeEach
    public void setUp() {
        database.addUsers(1, 1, 0);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addCourseWithOneProgrammingExercise();
        result = new Result();
        var programmingExercises = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations();
        programmingExercise = programmingExercises.get(0);
        programmingExerciseWithBonus = programmingExercises.get(1);
        database.addTestCasesToProgrammingExercise(programmingExerciseWithBonus, true);
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        bambooRequestMockProvider.reset();
    }

    @Test
    public void shouldSetAllTestCasesToInactiveIfFeedbackListIsEmpty() {
        List<Feedback> feedbacks = new ArrayList<>();
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(3);

        assertThat(testCases.stream().noneMatch(ProgrammingExerciseTestCase::isActive)).isTrue();
    }

    @Test
    public void shouldUpdateActiveFlagsOfTestCases() {
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1"));
        feedbacks.add(new Feedback().text("test2"));
        feedbacks.add(new Feedback().text("test4"));
        feedbacks.add(new Feedback().text("test5"));
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(5);

        assertThat(testCases.stream().allMatch(testCase -> {
            if (testCase.getTestName().equals("test3")) {
                return !testCase.isActive();
            }
            else {
                return testCase.isActive();
            }
        })).isTrue();
    }

    @Test
    public void shouldGenerateNewTestCases() {
        testCaseRepository.deleteAll();

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1"));
        feedbacks.add(new Feedback().text("test2"));
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(2);

        assertThat(testCases.stream().allMatch(ProgrammingExerciseTestCase::isActive)).isTrue();
    }

    @Test
    public void shouldResetTestWeights() throws Exception {
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        when(gitService.getLastCommitHash(ArgumentMatchers.any())).thenReturn(ObjectId.fromString(dummyHash));
        database.addProgrammingParticipationWithResultForExercise(programmingExercise, "student1");
        new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId())).get(0).weight(50.0);
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());

        assertThat(programmingExercise.getTestCasesChanged()).isFalse();

        testCaseService.reset(programmingExercise.getId());

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExercise.getId()).get();
        assertThat(testCases.stream().mapToDouble(ProgrammingExerciseTestCase::getWeight).sum()).isEqualTo(testCases.size());
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isTrue();
        verify(groupNotificationService, times(1)).notifyInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, Constants.TEST_CASES_CHANGED_NOTIFICATION);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases-changed", true);

        // After a test case update, the solution repository should be build, so the ContinuousIntegrationService needs to be triggered and a submission created.
        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getCommitHash()).isEqualTo(dummyHash);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void shouldUpdateTestWeight() throws Exception {
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(any());

        database.addProgrammingParticipationWithResultForExercise(programmingExercise, "student1");

        ProgrammingExerciseTestCase testCase = testCaseRepository.findAll().get(0);

        Set<ProgrammingExerciseTestCaseDTO> programmingExerciseTestCaseDTOS = new HashSet<>();
        ProgrammingExerciseTestCaseDTO programmingExerciseTestCaseDTO = new ProgrammingExerciseTestCaseDTO();
        programmingExerciseTestCaseDTO.setId(testCase.getId());
        programmingExerciseTestCaseDTO.setWeight(400.0);
        programmingExerciseTestCaseDTOS.add(programmingExerciseTestCaseDTO);

        assertThat(programmingExercise.getTestCasesChanged()).isFalse();

        testCaseService.update(programmingExercise.getId(), programmingExerciseTestCaseDTOS);

        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExercise.getId()).get();

        assertThat(testCaseRepository.findById(testCase.getId()).get().getWeight()).isEqualTo(400);
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isTrue();
        verify(groupNotificationService, times(1)).notifyInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, Constants.TEST_CASES_CHANGED_NOTIFICATION);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases-changed", true);

        // After a test case update, the solution repository should be build, so the ContinuousIntegrationService needs to be triggered and a submission created.
        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getCommitHash()).isEqualTo(dummyHash);
    }

    @Test
    public void shouldNotUpdateResultIfNoTestCasesExist() {
        testCaseRepository.deleteAll();

        Long scoreBeforeUpdate = result.getScore();
        testCaseService.updateResultFromTestCases(result, programmingExercise, true);

        assertThat(result.getScore()).isEqualTo(scoreBeforeUpdate);
    }

    @Test
    public void shouldRecalculateScoreBasedOnTestCasesWeight() {
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.successful(false);
        Long scoreBeforeUpdate = result.getScore();

        testCaseService.updateResultFromTestCases(result, programmingExercise, true);

        Long expectedScore = 25L;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    public void shouldRecalculateScoreScoreWithoutExerciseBonusPoints() {
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(true).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.successful(false);
        Long scoreBeforeUpdate = result.getScore();

        testCaseService.updateResultFromTestCases(result, programmingExerciseWithBonus, true);

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        // Only one successful test because build and run after due date is set. Due to the bonus multiplier, 57% should be reached
        assertThat(result.getScore()).isEqualTo(57L);
        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    public void shouldRecalculateScoreCappedScoreWithExerciseBonusPoints() {
        // Add another test case with arbitrary high bonuses so that we run into the cap
        var testCases = testCaseRepository.findByExerciseId(programmingExerciseWithBonus.getId());
        testCases.add(new ProgrammingExerciseTestCase().testName("test4").weight(1.0).active(true).exercise(programmingExerciseWithBonus).afterDueDate(false).bonusMultiplier(1000D)
                .bonusPoints(1000D));
        testCaseRepository.saveAll(testCases);

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test4").positive(true).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        Long scoreBeforeUpdate = result.getScore();

        // Set max achievable bonus points for the exercise, which will determine the cap we run into
        Double maxPoints = programmingExercise.getMaxScore();
        Double maxBonusPoints = 10D;
        programmingExerciseWithBonus.setBonusPoints(maxBonusPoints);
        var expectedScore = (long) ((maxPoints + maxBonusPoints) * 100. / maxPoints);

        testCaseService.updateResultFromTestCases(result, programmingExerciseWithBonus, true);

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    public void shouldRecalculateScoreWithMultiplierAndBonusPoints() {
        // Add another active test case to increase the total weight
        var testCases = testCaseRepository.findByExerciseId(programmingExerciseWithBonus.getId());
        testCases.add(new ProgrammingExerciseTestCase().testName("test4").weight(3.0).active(true).exercise(programmingExerciseWithBonus).afterDueDate(false).bonusMultiplier(1D)
                .bonusPoints(0D));
        testCaseRepository.saveAll(testCases);

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(false).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test4").positive(false).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.successful(false);
        Long scoreBeforeUpdate = result.getScore();

        // Set bonus points for exercise arbitrary high so that the score won't get capped
        programmingExerciseWithBonus.setBonusPoints(100000D);
        // Only feedback for test case 'test1' is positive
        var activeTestCases = testCaseRepository.findByExerciseIdAndActive(programmingExerciseWithBonus.getId(), true);
        double totalWeight = activeTestCases.stream().mapToDouble(ProgrammingExerciseTestCase::getWeight).sum();
        double positiveTestCaseWeight = activeTestCases.stream().filter(testCase -> testCase.getTestName().equals("test1"))
                .mapToDouble(testCase -> testCase.getWeight() * testCase.getBonusMultiplier()).sum();
        double positiveTestCaseBonusPoints = activeTestCases.stream().filter(testCase -> testCase.getTestName().equals("test1"))
                .mapToDouble(ProgrammingExerciseTestCase::getBonusPoints).sum();
        double positiveTestCaseBonusScore = positiveTestCaseBonusPoints / programmingExerciseWithBonus.getMaxScore() * 100.;
        long expectedScore = (long) (positiveTestCaseWeight * 100. / totalWeight + positiveTestCaseBonusScore);

        testCaseService.updateResultFromTestCases(result, programmingExerciseWithBonus, true);

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
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
        Long scoreBeforeUpdate = result.getScore();

        testCaseService.updateResultFromTestCases(result, programmingExercise, true);

        // All available test cases are fulfilled, however there are more test cases that will be run after due date.
        Long expectedScore = 25L;

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
        Long scoreBeforeUpdate = result.getScore();

        testCaseService.updateResultFromTestCases(result, programmingExercise, false);

        // All available test cases are fulfilled.
        Long expectedScore = 25L;

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
        Long scoreBeforeUpdate = result.getScore();

        testCaseService.updateResultFromTestCases(result, programmingExercise, true);

        // All available test cases are fulfilled.
        Long expectedScore = 25L;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getResultString()).isEqualTo("1 of 2 passed");
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
        // The feedback of the after due date test case must be kept.
        assertThat(result.getFeedbacks().stream().noneMatch(feedback -> feedback.getText().equals("test3"))).isEqualTo(false);
    }

    @Test
    public void shouldGenerateZeroScoreIfThereAreNoTestCasesBeforeDueDate() {
        // Set programming exercise due date in future.
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(10));

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test2").positive(true).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().text("test3").positive(false).type(FeedbackType.AUTOMATIC));
        result.feedbacks(feedbacks);
        result.successful(false);
        Long scoreBeforeUpdate = result.getScore();

        // Set all test cases of the programming exercise to be executed after due date.
        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        for (ProgrammingExerciseTestCase testCase : testCases) {
            testCase.setAfterDueDate(true);
        }
        testCaseRepository.saveAll(testCases);

        testCaseService.updateResultFromTestCases(result, programmingExercise, true);

        // No test case was executed.
        Long expectedScore = 0L;

        assertThat(scoreBeforeUpdate).isNotEqualTo(result.getScore());
        assertThat(result.getResultString()).isEqualTo("0 of 0 passed");
        assertThat(result.getScore()).isEqualTo(expectedScore);
        assertThat(result.isSuccessful()).isFalse();
        // The feedback must be empty as not test should be executed yet.
        assertThat(result.getFeedbacks()).hasSize(0);
    }
}
