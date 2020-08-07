package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;

public class ProgrammingExerciseTestCaseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    @Autowired
    ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    DatabaseUtilService database;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExercise programmingExerciseWithBonus;

    private Result result;

    @BeforeEach
    public void setUp() {
        database.addUsers(5, 1, 0);
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
        assertThat(result.isSuccessful()).isFalse();
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

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void shouldReEvaluateScoreOfTheCorrectResults() {
        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseService.findWithTemplateAndSolutionParticipationWithResultsById(programmingExercise.getId());

        var testCases = testCaseService.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity()));
        testCases.get("test1").active(true).afterDueDate(false).setWeight(1);
        testCases.get("test2").active(true).afterDueDate(false).setWeight(1);
        testCases.get("test3").active(true).afterDueDate(false).setWeight(2);
        testCaseRepository.saveAll(testCases.values());

        // template does not pass any tests
        var participationTemplate = programmingExercise.getTemplateParticipation();
        {
            // score 0 %
            var resultTemplate = new Result().participation(participationTemplate).resultString("x of y passed").successful(false).rated(true).score(100L);
            resultTemplate = updateAutomaticResult(resultTemplate, false, false, false);

            participationTemplate.setResults(Set.of(resultTemplate));
            participationTemplate = participationRepository.save(participationTemplate);
        }

        // solution passes most tests but is still faulty
        var participationSolution = programmingExercise.getSolutionParticipation();
        {
            // score 75 %
            var resultSolution = new Result().participation(participationSolution).resultString("x of y passed").successful(false).rated(true).score(100L);
            resultSolution = updateAutomaticResult(resultSolution, false, true, true);

            participationSolution.setResults(Set.of(resultSolution));
            participationSolution = participationRepository.save(participationSolution);
        }

        // student1 only has one automatic result
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        {
            // score 50 %
            var result1 = new Result().participation(participation1).resultString("x of y passed").successful(false).rated(true).score(100L);
            result1 = updateAutomaticResult(result1, true, true, false);

            participation1.setResults(Set.of(result1));
            participation1 = studentParticipationRepository.save(participation1);
        }

        // student2 has an automatic result and a manual result as well
        var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        {
            // score 75 %
            var result2a = new Result().participation(participation2).resultString("x of y passed").successful(false).rated(true).score(100L);
            result2a = updateAutomaticResult(result2a, true, false, true);

            // score 100 %
            var result2b = new Result().participation(participation2).resultString("x of y passed").successful(false).rated(true).score(100L);
            result2b.feedbacks(List.of(new Feedback().text("Well done!").positive(true).type(FeedbackType.MANUAL))) //
                    .score(100L) //
                    .rated(true) //
                    .hasFeedback(true) //
                    .successful(true) //
                    .completionDate(ZonedDateTime.now()) //
                    .assessmentType(AssessmentType.MANUAL);
            result2b = resultRepository.save(result2b);

            participation2.setResults(Set.of(result2a, result2b));
            participation2 = studentParticipationRepository.save(participation2);
        }

        // student3 only started the exercise, but did not submit anything
        var participation3 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student3");

        // student4 only has one automatic result
        var participation4 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student4");
        {
            // score 100 %
            var result4 = new Result().participation(participation4).resultString("x of y passed").successful(false).rated(true).score(100L);
            ;
            result4 = updateAutomaticResult(result4, true, true, true);

            participation4.setResults(Set.of(result4));
            participation4 = studentParticipationRepository.save(participation4);
        }

        // student5 has a build failure
        var participation5 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student5");
        {
            // Build Failed
            var result5 = new Result().participation(participation5) //
                    .feedbacks(List.of()) //
                    .score(0L) //
                    .resultString("Build Failed") //
                    .rated(true) //
                    .hasFeedback(false) //
                    .successful(false) //
                    .completionDate(ZonedDateTime.now()) //
                    .assessmentType(AssessmentType.AUTOMATIC);
            testCaseService.updateResultFromTestCases(result5, programmingExercise, true);
            result5 = resultRepository.save(result5);

            participation5.setResults(Set.of(result5));
            participation5 = studentParticipationRepository.save(participation5);
        }

        // change test case weights
        testCases.get("test1").setWeight(0);
        testCases.get("test2").setWeight(1);
        testCases.get("test3").setWeight(3);
        testCaseRepository.saveAll(testCases.values());

        // re-evaluate
        var updatedResults = testCaseService.updateAllResultsFromTestCases(programmingExercise);
        resultRepository.saveAll(updatedResults);

        // Tests
        programmingExercise = programmingExerciseService.findWithTemplateAndSolutionParticipationWithResultsById(programmingExercise.getId());

        // template 0 %
        {
            var participation = programmingExercise.getTemplateParticipation();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            assertThat(singleResult.getScore()).isEqualTo(0L);
            assertThat(singleResult.getResultString()).isEqualTo("0 of 3 passed");
            assertThat(singleResult.getHasFeedback()).isTrue();
            assertThat(singleResult.getFeedbacks()).hasSize(3);
            assertThat(singleResult.getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // solution 100 %
        {
            var participation = programmingExercise.getSolutionParticipation();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            assertThat(singleResult.getScore()).isEqualTo(100L);
            assertThat(singleResult.getResultString()).isEqualTo("2 of 3 passed");
            assertThat(singleResult.getHasFeedback()).isTrue();
            assertThat(singleResult.getFeedbacks()).hasSize(3);
            assertThat(singleResult.getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // student1 25 %
        {
            var participation = participationRepository.findByIdWithLatestResultAndFeedbacks(participation1.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            assertThat(singleResult.getScore()).isEqualTo(25L);
            assertThat(singleResult.getResultString()).isEqualTo("2 of 3 passed");
            assertThat(singleResult.getHasFeedback()).isTrue();
            assertThat(singleResult.getFeedbacks()).hasSize(3);
            assertThat(singleResult.getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // student2 100 % / 75 %
        {
            var participation = participationRepository.findByIdWithLatestResultAndFeedbacks(participation2.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(2);

            var manualResultOptional = results.stream().filter(result -> result.getAssessmentType() == AssessmentType.MANUAL).findAny();
            assertThat(manualResultOptional).isPresent();
            var manualResult = manualResultOptional.get();
            assertThat(manualResult.getScore()).isEqualTo(100L);
            assertThat(manualResult.getResultString()).isEqualTo("2 of 3 passed");
            assertThat(manualResult.getHasFeedback()).isTrue();
            assertThat(manualResult.getFeedbacks()).hasSize(3);
            assertThat(manualResult).isEqualTo(participation.findLatestResult());

            var automaticResultOptional = results.stream().filter(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC).findAny();
            assertThat(automaticResultOptional).isPresent();
            var automaticResult = automaticResultOptional.get();
            assertThat(automaticResult.getScore()).isEqualTo(75L);
            assertThat(automaticResult.getResultString()).isEqualTo("2 of 3 passed");
            assertThat(manualResult.getHasFeedback()).isTrue();
            assertThat(automaticResult.getFeedbacks()).hasSize(3);
        }

        // student3 no result
        {
            var participation = participationRepository.findByIdWithLatestResultAndFeedbacks(participation3.getId()).get();
            var results = participation.getResults();
            assertThat(results).isNullOrEmpty();
            assertThat(participation.findLatestResult()).isNull();
        }

        // student4 100%
        {
            var participation = participationRepository.findByIdWithLatestResultAndFeedbacks(participation4.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            assertThat(singleResult.getScore()).isEqualTo(100L);
            assertThat(singleResult.getResultString()).isEqualTo("3 of 3 passed");
            assertThat(singleResult.getHasFeedback()).isFalse();
            assertThat(singleResult.getFeedbacks()).hasSize(3);
            assertThat(singleResult.getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

        // student5 Build Failed
        {
            var participation = participationRepository.findByIdWithLatestResultAndFeedbacks(participation5.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            assertThat(singleResult.getScore()).isEqualTo(0L);
            assertThat(singleResult.getResultString()).isEqualTo("Build Failed");
            assertThat(singleResult.getHasFeedback()).isFalse();
            assertThat(singleResult.getFeedbacks()).isEmpty();
            assertThat(singleResult.getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
    }

    private Result updateAutomaticResult(Result result, boolean test1Passes, boolean test2Passes, boolean test3Passes) {
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
        testCaseService.updateResultFromTestCases(result, programmingExercise, true);
        return resultRepository.save(result);
    }
}
