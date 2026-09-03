package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.service.MilestoneExercisePointsService;
import de.tum.cit.aet.artemis.programming.service.MilestoneScoreScheduleService;
import de.tum.cit.aet.artemis.programming.service.MilestoneScoreService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

/**
 * Tests the milestone/user-story grading fan-out ({@link de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService#fanOutResultToUserStoryExercise}) and the
 * retroactive backfill of a newly created {@link UserStoryExercise} ({@link ParticipationService#provisionParticipationsForNewUserStoryExercise}).
 * <p>
 * Deliberately avoids the VCS/CI test infrastructure: both mechanisms under test only duplicate already-existing
 * database rows (submission/result/feedback/participation), they never touch a real repository or build plan.
 */
class UserStoryExerciseGradingFanOutTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userstoryfanout";

    @Autowired
    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private MilestoneExercisePointsService milestoneExercisePointsService;

    @Autowired
    private MilestoneScoreService milestoneScoreService;

    @Autowired
    private MilestoneScoreScheduleService milestoneScoreScheduleService;

    private Course course;

    private MilestoneExercise milestoneExercise;

    private MilestoneExerciseGroup group;

    private String studentLogin;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        studentLogin = TEST_PREFIX + "student1";

        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        milestoneExercise = new MilestoneExercise();
        milestoneExercise.setTitle("Milestone");
        milestoneExercise.setShortName("milestone" + TEST_PREFIX);
        milestoneExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        milestoneExercise.setCourse(course);
        milestoneExercise.setMaxPoints(0.0);
        milestoneExercise.generateAndSetProjectKey();
        milestoneExercise = (MilestoneExercise) programmingExerciseRepository.save(milestoneExercise);

        group = new MilestoneExerciseGroup();
        group.setTitle("Milestone Group " + TEST_PREFIX);
        group.setMilestoneExercise(milestoneExercise);
        group = (MilestoneExerciseGroup) exerciseVariantGroupRepository.save(group);

        course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(course.getId());
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);
    }

    @AfterEach
    void tearDown() {
        // The schedule service is a singleton shared by the whole context, so a test that activates it must switch it
        // back off - otherwise its background recomputations keep running against later tests' data and corrupt their
        // sessions.
        milestoneScoreScheduleService.shutdown();
    }

    private UserStoryExercise createUserStoryExercise(String shortNameSuffix) {
        UserStoryExercise exercise = new UserStoryExercise();
        exercise.setTitle("UserStory " + shortNameSuffix);
        exercise.setShortName(shortNameSuffix + TEST_PREFIX);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setCourse(course);
        exercise.setMaxPoints(2.0);
        exercise.setExerciseVariantGroup(group);
        exercise.generateAndSetProjectKey();
        return (UserStoryExercise) programmingExerciseRepository.save(exercise);
    }

    private ProgrammingExerciseTestCase createTestCase(ProgrammingExercise exercise, String name) {
        ProgrammingExerciseTestCase testCase = new ProgrammingExerciseTestCase().testName(name).weight(1.0).active(true).exercise(exercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1.0).bonusPoints(0.0);
        return testCaseRepository.save(testCase);
    }

    private ProgrammingExerciseStudentParticipation participationFor(ProgrammingExercise exercise) {
        return participationUtilService.addStudentParticipationForProgrammingExercise(exercise, studentLogin);
    }

    /** Builds an unsaved automatic test-case feedback row, as the build-result processing would. */
    private static TestCaseFeedback testCaseFeedback(ProgrammingExerciseTestCase testCase, Boolean positive) {
        TestCaseFeedback feedback = new TestCaseFeedback();
        feedback.setTestCase(testCase);
        feedback.setPositive(positive);
        return feedback;
    }

    /**
     * Attaches one static-code-analysis issue to the result. Only the tool and its own category are set: that pair is
     * what {@code ProgrammingExerciseFeedbackCreationService.categorizeScaFeedback} resolves to the exercise's Artemis
     * category and penalty - the remaining columns (file, lines, message) play no role in grading.
     */
    private static void addScaFeedback(Result result, StaticCodeAnalysisTool tool, String toolCategory) {
        ScaFeedback scaFeedback = new ScaFeedback();
        scaFeedback.setTool(tool);
        scaFeedback.setToolCategory(toolCategory);
        result.addScaFeedback(scaFeedback);
    }

    private Result buildSourceResult(ProgrammingExerciseStudentParticipation participation, String commitHash, List<TestCaseFeedback> testCaseFeedbacks) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setCommitHash(commitHash);
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setSubmitted(true);
        submission = programmingSubmissionRepository.save(submission);

        Result result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        result.setSuccessful(true);
        result.setExerciseId(participation.getExercise().getId());
        result.setSubmission(submission);
        result.setTestCaseFeedbacks(testCaseFeedbacks);
        return resultRepository.save(result);
    }

    /**
     * Re-reads the participation's latest result with its typed automatic feedback hydrated. The entity graph behind
     * {@code findLatestResultWithFeedbacksForParticipation} only fetches the generic {@code feedbacks} and the
     * submission, so the two typed collections would come back as uninitialized lazy collections - the same two
     * queries the production path runs in {@code ProgrammingExerciseGradingService.hydrateTypedFeedback}. Fetching the
     * test cases along with the rows matters because the fan-out matches them by name.
     */
    private Result reloadWithTypedFeedback(ProgrammingExerciseStudentParticipation participation) {
        Result result = resultRepository.findLatestResultWithFeedbacksForParticipation(participation.getId(), true).orElseThrow();
        result.setTestCaseFeedbacks(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(result.getId())));
        result.setScaFeedbacks(scaFeedbackRepository.findByResultIds(List.of(result.getId())));
        return result;
    }

    /**
     * Builds a rated milestone result carrying one SPOTBUGS "BAD_PRACTICE" issue plus the given test feedback, and runs
     * it through the ordinary grading path - which is what categorizes the issue against the milestone's own categories
     * and writes its penalty into the feedback's credits. That side effect is the single evaluation the whole design
     * rests on, so the tests deliberately go through it rather than hand-setting credits.
     */
    private Result buildGradedMilestoneResult(ProgrammingExerciseStudentParticipation milestoneParticipation, String commitHash, List<TestCaseFeedback> testFeedbacks) {
        List<TestCaseFeedback> feedbacks = new ArrayList<>(testFeedbacks);
        // The grading path only reaches the penalty calculation when the result carries test case feedback at all -
        // without any, it is indistinguishable from a build failure and is returned unscored. So always give it one.
        feedbacks.add(testCaseFeedback(createTestCase(milestoneExercise, "scaAnchorTest"), true));

        Result result = buildSourceResult(milestoneParticipation, commitHash, feedbacks);
        result.setRated(true);
        addScaFeedback(result, StaticCodeAnalysisTool.SPOTBUGS, "BAD_PRACTICE");
        MilestoneExercise freshMilestone = (MilestoneExercise) programmingExerciseRepository.findByIdElseThrow(milestoneExercise.getId());
        resultRepository.save(gradingService.calculateScoreForResult(result, freshMilestone, true));
        return reloadWithTypedFeedback(milestoneParticipation);
    }

    /** Persists a rated result of the given percentage for a user story participation, as a fan-out or a tutor would. */
    private void saveRatedUserStoryResult(ProgrammingExerciseStudentParticipation participation, String commitHash, double score) {
        Result result = buildSourceResult(participation, commitHash, List.of());
        result.setRated(true);
        result.setScore(score);
        resultRepository.save(result);
    }

    private void enableStaticCodeAnalysis(CategoryState badPracticeState, double penalty, Integer maxStaticCodeAnalysisPenalty) {
        MilestoneExercise fresh = (MilestoneExercise) programmingExerciseRepository.findByIdElseThrow(milestoneExercise.getId());
        fresh.setStaticCodeAnalysisEnabled(true);
        fresh.setMaxStaticCodeAnalysisPenalty(maxStaticCodeAnalysisPenalty);
        milestoneExercise = (MilestoneExercise) programmingExerciseRepository.save(fresh);
        staticCodeAnalysisCategoryRepository.save(ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(milestoneExercise, "Bad Practice", badPracticeState, penalty, 10D));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void userStoryExercisesStayIncludedCompletelyAndAreExcludedByGroupMembershipInstead() {
        UserStoryExercise userStory = createUserStoryExercise("us1");

        // A user story's points genuinely count - through its group - so it must not be labelled NOT_INCLUDED, which
        // every UI reads as "these points do not count". Double counting is prevented by the score calculation skipping
        // milestone group members (CourseScoreCalculator.includeIntoScoreCalculation), not by this flag.
        assertThat(userStory.getIncludedInOverallScore()).isEqualTo(IncludedInOverallScore.INCLUDED_COMPLETELY);
        assertThat(milestoneExercise.getIncludedInOverallScore()).isEqualTo(IncludedInOverallScore.INCLUDED_COMPLETELY);
        assertThat(userStory.getExerciseVariantGroup()).isInstanceOf(MilestoneExerciseGroup.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void milestonePointsAreTheSumOfItsUserStoryPoints() {
        createUserStoryExercise("us1");
        createUserStoryExercise("us2");

        milestoneExercisePointsService.syncMaxPoints(milestoneExercise.getId());

        // Two stories worth 2.0 each (see createUserStoryExercise).
        assertThat(programmingExerciseRepository.findByIdElseThrow(milestoneExercise.getId()).getMaxPoints()).isEqualTo(4.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fanOutDoesNotCopyStaticCodeAnalysisFeedbackToUserStories() {
        enableStaticCodeAnalysis(CategoryState.GRADED, 1.0, null);
        UserStoryExercise userStory1 = createUserStoryExercise("us1");
        UserStoryExercise userStory2 = createUserStoryExercise("us2");
        milestoneExercisePointsService.syncMaxPoints(milestoneExercise.getId());
        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");
        createTestCase(userStory1, "testA");
        createTestCase(userStory2, "testA");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation1 = participationFor(userStory1);
        ProgrammingExerciseStudentParticipation participation2 = participationFor(userStory2);

        Result milestoneResult = buildGradedMilestoneResult(milestoneParticipation, "commit-1", List.of(testCaseFeedback(milestoneTestA, true)));
        assertThat(milestoneResult.getScaFeedbacks()).isNotEmpty();

        Result us1Result = gradingService.fanOutResultToUserStoryExercise(milestoneResult, userStory1, participation1);
        Result us2Result = gradingService.fanOutResultToUserStoryExercise(milestoneResult, userStory2, participation2);

        // The violation belongs to the shared codebase - copying it here is what would charge it once per story.
        assertThat(us1Result.getScaFeedbacks()).isEmpty();
        assertThat(us2Result.getScaFeedbacks()).isEmpty();
        assertThat(us1Result.getScore()).isEqualTo(100.0);
        assertThat(us2Result.getScore()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void milestoneScoreIsTheSummedUserStoryPointsMinusTheStaticCodeAnalysisPenalty() {
        enableStaticCodeAnalysis(CategoryState.GRADED, 1.0, null);
        UserStoryExercise userStory1 = createUserStoryExercise("us1");
        UserStoryExercise userStory2 = createUserStoryExercise("us2");
        milestoneExercisePointsService.syncMaxPoints(milestoneExercise.getId());

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        buildGradedMilestoneResult(milestoneParticipation, "commit-1", List.of());
        // 2.0 of 2.0 points on the first story, 1.0 of 2.0 on the second -> 3.0 of the group's 4.0 points.
        saveRatedUserStoryResult(participationFor(userStory1), "commit-1", 100.0);
        saveRatedUserStoryResult(participationFor(userStory2), "commit-1", 50.0);

        Result aggregated = milestoneScoreService.recalculate(milestoneExercise.getId(), userUtilService.getUserByLogin(studentLogin).getId()).orElseThrow();

        // One issue at a penalty of 1.0 point, charged exactly once for the whole group: (3.0 - 1.0) / 4.0.
        assertThat(aggregated.getScore()).isEqualTo(50.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void staticCodeAnalysisPenaltyIsCappedAgainstTheGroupsTotalPoints() {
        // 25 % of the group's 4.0 points is 1.0, so a 3.0-point category penalty may only cost 1.0.
        enableStaticCodeAnalysis(CategoryState.GRADED, 3.0, 25);
        UserStoryExercise userStory1 = createUserStoryExercise("us1");
        UserStoryExercise userStory2 = createUserStoryExercise("us2");
        milestoneExercisePointsService.syncMaxPoints(milestoneExercise.getId());

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        buildGradedMilestoneResult(milestoneParticipation, "commit-1", List.of());
        saveRatedUserStoryResult(participationFor(userStory1), "commit-1", 100.0);
        saveRatedUserStoryResult(participationFor(userStory2), "commit-1", 100.0);

        Result aggregated = milestoneScoreService.recalculate(milestoneExercise.getId(), userUtilService.getUserByLogin(studentLogin).getId()).orElseThrow();

        // (4.0 - 1.0) / 4.0 - the cap is a percentage of the milestone's points, which are the group's points.
        assertThat(aggregated.getScore()).isEqualTo(75.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void aBlockingViolationZeroesTheWholeGroup() {
        enableStaticCodeAnalysis(CategoryState.BLOCKING, 0.0, null);
        UserStoryExercise userStory1 = createUserStoryExercise("us1");
        UserStoryExercise userStory2 = createUserStoryExercise("us2");
        milestoneExercisePointsService.syncMaxPoints(milestoneExercise.getId());

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        buildGradedMilestoneResult(milestoneParticipation, "commit-1", List.of());
        ProgrammingExerciseStudentParticipation participation1 = participationFor(userStory1);
        ProgrammingExerciseStudentParticipation participation2 = participationFor(userStory2);
        saveRatedUserStoryResult(participation1, "commit-1", 100.0);
        saveRatedUserStoryResult(participation2, "commit-1", 100.0);

        Result aggregated = milestoneScoreService.recalculate(milestoneExercise.getId(), userUtilService.getUserByLogin(studentLogin).getId()).orElseThrow();

        // Every story passed, but a blocking violation in the shared codebase costs the group everything.
        assertThat(aggregated.getScore()).isZero();
        // The stories keep their own results and scores - only the aggregate is zeroed.
        assertThat(resultRepository.findFirstBySubmissionParticipationIdAndRatedOrderByCompletionDateDesc(participation1.getId(), true).orElseThrow().getScore()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fanOutStillPersistsStoryResultsWhileTheMilestoneScheduleServiceIsRunning() {
        enableStaticCodeAnalysis(CategoryState.GRADED, 1.0, null);
        UserStoryExercise userStory1 = createUserStoryExercise("us1");
        UserStoryExercise userStory2 = createUserStoryExercise("us2");
        milestoneExercisePointsService.syncMaxPoints(milestoneExercise.getId());
        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");
        createTestCase(userStory1, "testA");
        createTestCase(userStory2, "testA");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation1 = participationFor(userStory1);
        ProgrammingExerciseStudentParticipation participation2 = participationFor(userStory2);

        Result milestoneResult = buildGradedMilestoneResult(milestoneParticipation, "commit-1", List.of(testCaseFeedback(milestoneTestA, true)));

        // The service only starts accepting work after the startup delay, which is why every other test in this class
        // runs with it switched off - and why nothing here caught that saving a story result fires ResultListener into a
        // path that queries the database from inside the flush that is persisting the result.
        milestoneScoreScheduleService.activate();

        gradingService.fanOutResultToUserStoryExercise(milestoneResult, userStory1, participation1);
        gradingService.fanOutResultToUserStoryExercise(milestoneResult, userStory2, participation2);

        // The regression: the results must actually exist and hang off their submissions. Without the fix the save above
        // dies with "Entry for instance of Result has a null identifier", leaving the student with a pending submission
        // and no result - which is what the UI reports as "No corresponding result available".
        assertThat(resultRepository.findFirstBySubmissionParticipationIdAndRatedOrderByCompletionDateDesc(participation1.getId(), true)).isPresent();
        assertThat(resultRepository.findFirstBySubmissionParticipationIdAndRatedOrderByCompletionDateDesc(participation2.getId(), true)).isPresent();

        // And the aggregate still lands: 2.0 + 2.0 story points - 1.0 penalty, over the group's 4.0 points.
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(
                        resultRepository.findFirstBySubmissionParticipationIdAndRatedOrderByCompletionDateDesc(milestoneParticipation.getId(), true).orElseThrow().getScore())
                        .isEqualTo(75.0));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fanOutScoresEachSiblingWithOnlyItsOwnTestCases() {
        UserStoryExercise userStory1 = createUserStoryExercise("us1");
        UserStoryExercise userStory2 = createUserStoryExercise("us2");
        // The milestone owns the full test suite; each UserStoryExercise only has the (already-synced, per
        // UserStoryExerciseService) subset of test cases relevant to it.
        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");
        ProgrammingExerciseTestCase milestoneTestB = createTestCase(milestoneExercise, "testB");
        ProgrammingExerciseTestCase milestoneTestC = createTestCase(milestoneExercise, "testC");
        createTestCase(userStory1, "testA");
        createTestCase(userStory1, "testB");
        createTestCase(userStory2, "testB");
        createTestCase(userStory2, "testC");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation1 = participationFor(userStory1);
        ProgrammingExerciseStudentParticipation participation2 = participationFor(userStory2);

        List<TestCaseFeedback> sourceFeedbacks = List.of(testCaseFeedback(milestoneTestA, true), testCaseFeedback(milestoneTestB, true), testCaseFeedback(milestoneTestC, false));
        Result sourceResult = buildSourceResult(milestoneParticipation, "commit-1", sourceFeedbacks);

        Result us1Result = gradingService.fanOutResultToUserStoryExercise(sourceResult, userStory1, participation1);
        Result us2Result = gradingService.fanOutResultToUserStoryExercise(sourceResult, userStory2, participation2);

        // US1 only knows testA/testB, both positive -> full score; testC feedback isn't even attached (not its test case).
        assertThat(us1Result.getScore()).isEqualTo(100.0);
        assertThat(us1Result.getTestCaseFeedbacks()).hasSize(2).allSatisfy(feedback -> assertThat(feedback.getTestCase().getExercise().getId()).isEqualTo(userStory1.getId()));
        assertThat(((ProgrammingSubmission) us1Result.getSubmission()).getCommitHash()).isEqualTo("commit-1");
        assertThat(us1Result.getSubmission().getParticipation().getId()).isEqualTo(participation1.getId());
        assertThat(us1Result.getSubmission().getId()).isNotEqualTo(sourceResult.getSubmission().getId());

        // US2 knows testB (positive) and testC (negative) -> half score; testA feedback isn't attached.
        assertThat(us2Result.getScore()).isEqualTo(50.0);
        assertThat(us2Result.getTestCaseFeedbacks()).hasSize(2).allSatisfy(feedback -> assertThat(feedback.getTestCase().getExercise().getId()).isEqualTo(userStory2.getId()));

        // Both results are actually persisted, not just returned in-memory.
        assertThat(resultRepository.findById(us1Result.getId())).isPresent();
        assertThat(resultRepository.findById(us2Result.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fanOutDoesNotTurnANeverExecutedTestIntoAPass() {
        UserStoryExercise userStory = createUserStoryExercise("us1");
        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation = participationFor(userStory);
        createTestCase(userStory, "testA");

        // A dynamic/parameterized test JUnit couldn't even generate against a missing class is reported with
        // positive=null (not the same as an executed-and-failed test, which has positive=false). That tri-state has to
        // survive feedbackService.copyTestCaseFeedback unchanged - anything that collapses it to a boolean would score
        // the never-executed test as a pass.
        Result sourceResult = buildSourceResult(milestoneParticipation, "commit-1", List.of(testCaseFeedback(milestoneTestA, null)));

        Result fannedOutResult = gradingService.fanOutResultToUserStoryExercise(sourceResult, userStory, participation);

        assertThat(fannedOutResult.getScore()).isZero();
        assertThat(fannedOutResult.getTestCaseFeedbacks()).singleElement().satisfies(feedback -> assertThat(feedback.isPositive()).isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void provisionPendingSubmissionsForUserStoryExercisesCreatesLiveBuildingIndicatorImmediately() {
        UserStoryExercise userStory = createUserStoryExercise("us1");
        createTestCase(milestoneExercise, "testA");
        createTestCase(userStory, "testA");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation = participationFor(userStory);

        ProgrammingSubmission milestoneSubmission = new ProgrammingSubmission();
        milestoneSubmission.setParticipation(milestoneParticipation);
        milestoneSubmission.setCommitHash("commit-1");
        milestoneSubmission.setType(SubmissionType.MANUAL);
        milestoneSubmission.setSubmissionDate(ZonedDateTime.now());
        milestoneSubmission.setSubmitted(true);
        milestoneSubmission = programmingSubmissionRepository.save(milestoneSubmission);

        // Called at push time, before any build has run - the point is that the sibling's "building..." indicator
        // must exist immediately, not only once fanOutResultToUserStoryExercise runs after the build completes.
        gradingService.provisionPendingSubmissionsForUserStoryExercises(milestoneSubmission, milestoneExercise, milestoneParticipation);

        List<ProgrammingSubmission> siblingSubmissions = programmingSubmissionRepository
                .findByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participation.getId(), "commit-1");
        assertThat(siblingSubmissions).singleElement().satisfies(submission -> assertThat(submission.getResults()).isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fanOutReusesThePendingSubmissionCreatedAtPushTimeInsteadOfDuplicatingIt() {
        UserStoryExercise userStory = createUserStoryExercise("us1");
        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");
        createTestCase(userStory, "testA");

        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        ProgrammingExerciseStudentParticipation participation = participationFor(userStory);

        Result sourceResult = buildSourceResult(milestoneParticipation, "commit-1", List.of(testCaseFeedback(milestoneTestA, true)));
        // Simulates the real push-time flow: a pending submission is created for the sibling first, before the
        // build (and therefore the result) exists.
        gradingService.provisionPendingSubmissionsForUserStoryExercises((ProgrammingSubmission) sourceResult.getSubmission(), milestoneExercise, milestoneParticipation);

        Result fannedOutResult = gradingService.fanOutResultToUserStoryExercise(sourceResult, userStory, participation);

        List<ProgrammingSubmission> siblingSubmissions = programmingSubmissionRepository
                .findByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participation.getId(), "commit-1");
        assertThat(siblingSubmissions).singleElement().satisfies(submission -> assertThat(submission.getId()).isEqualTo(fannedOutResult.getSubmission().getId()));
        assertThat(fannedOutResult.getScore()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void backfillProvisionsParticipationAndScoreForNewUserStoryExercise() {
        UserStoryExercise existingUserStory = createUserStoryExercise("existing");
        createTestCase(existingUserStory, "testA");

        ProgrammingExerciseTestCase milestoneTestA = createTestCase(milestoneExercise, "testA");
        ProgrammingExerciseStudentParticipation milestoneParticipation = participationFor(milestoneExercise);
        participationFor(existingUserStory);

        Result sourceResult = buildSourceResult(milestoneParticipation, "commit-1", List.of(testCaseFeedback(milestoneTestA, true)));

        // A student story added to the group AFTER the student already started it via the milestone.
        UserStoryExercise newUserStory = createUserStoryExercise("new");
        createTestCase(newUserStory, "testA");

        List<ProgrammingExerciseStudentParticipation> created = participationService.provisionParticipationsForNewUserStoryExercise(newUserStory);

        assertThat(created).hasSize(1);
        ProgrammingExerciseStudentParticipation newParticipation = created.get(0);
        assertThat(newParticipation.getExercise().getId()).isEqualTo(newUserStory.getId());
        assertThat(newParticipation.getStudent()).map(User::getLogin).contains(studentLogin);
        assertThat(newParticipation.getRepositoryUri()).isEqualTo(milestoneParticipation.getRepositoryUri());
        assertThat(newParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        assertThat(newParticipation.getBuildPlanId()).isNull();

        // Re-running the backfill is idempotent: the student already has a participation now, so nothing new is created.
        assertThat(participationService.provisionParticipationsForNewUserStoryExercise(newUserStory)).isEmpty();
        assertThat(programmingExerciseStudentParticipationRepository.findAllByExerciseIdAndStudentLogin(newUserStory.getId(), studentLogin)).hasSize(1);

        // The caller (ExerciseVariantGroupResource) derives the initial score from the student's latest milestone result.
        Result backfilledResult = gradingService.fanOutResultToUserStoryExercise(sourceResult, newUserStory, newParticipation);
        assertThat(backfilledResult.getScore()).isEqualTo(100.0);
        assertThat(backfilledResult.getSubmission().getParticipation().getId()).isEqualTo(newParticipation.getId());
    }
}
