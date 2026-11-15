package de.tum.cit.aet.artemis.assessment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.service.TextExerciseFeedbackService;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/** Database-backed request lifecycle checks; Athena is the only external boundary held open. */
class AthenaFeedbackRequestPersistenceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ResultTestRepository results;

    @Autowired
    private AthenaFeedbackRequestRepository feedbackRequests;

    @Autowired
    private ResultGradeRepository resultGrades;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private StudentParticipationTestRepository participations;

    @Autowired
    private TextSubmissionTestRepository submissions;

    @Autowired
    private TextExerciseUtilService textExercises;

    @Autowired
    private UserUtilService users;

    @Autowired
    private ParticipantScoreRepository participantScores;

    @Autowired
    private CompetencyUtilService competencies;

    @Autowired
    private CourseCompetencyRepository courseCompetencies;

    @Autowired
    private TeamUtilService teams;

    private TextExercise exercise;

    private StudentParticipation participation;

    private TextSubmission submission;

    private AthenaFeedbackApi athena;

    private TextExerciseFeedbackService service;

    private CountDownLatch terminalWrites = new CountDownLatch(0);

    @BeforeEach
    void setUpRequest() {
        users.addUsers("athenaclaims", 1, 0, 0, 0);
        var course = textExercises.addEnrolledCourseWithOneReleasedTextExercise("Athena claims", "athenaclaims");
        exercise = (TextExercise) course.getExercises().iterator().next();
        participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(users.getUserByLogin("athenaclaimsstudent1"));
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation = participations.save(participation);
        submission = new TextSubmission();
        submission.setText("The originally submitted answer.");
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setParticipation(participation);
        submission = submissions.save(submission);
        participation.addSubmission(submission);
        athena = mock(AthenaFeedbackApi.class);
        var participationService = mock(ParticipationService.class);
        when(participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(participation.getId())).thenReturn(participation);
        var requestingUsers = mock(UserTestRepository.class);
        when(requestingUsers.getUser()).thenReturn(participation.getStudent().orElseThrow());
        // Observe the end of actual repository transactions so a background worker cannot outlive this test's database.
        // Every operation delegates to the real Spring Data proxy; assertions below always reload the real database.
        AthenaFeedbackRequestRepository observedRequests = mock(AthenaFeedbackRequestRepository.class, delegatesTo(feedbackRequests));
        doAnswer(invocation -> {
            try {
                return feedbackRequests.completeAthenaFeedbackRequest(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            }
            finally {
                terminalWrites.countDown();
            }
        }).when(observedRequests).completeAthenaFeedbackRequest(any(), anyDouble(), any(), any());
        doAnswer(invocation -> {
            try {
                return feedbackRequests.failAthenaFeedbackRequest(invocation.getArgument(0));
            }
            finally {
                terminalWrites.countDown();
            }
        }).when(observedRequests).failAthenaFeedbackRequest(any());
        service = new TextExerciseFeedbackService(java.util.Optional.of(athena), observedRequests, mock(ResultWebsocketService.class), participationService, requestingUsers);
    }

    @AfterEach
    void awaitWorkersBeforeDatabaseCleanup() throws InterruptedException {
        assertThat(terminalWrites.await(5, TimeUnit.SECONDS)).as("Every async terminal transaction finished before database cleanup").isTrue();
    }

    @Test
    @WithMockUser(username = "athenaclaimsstudent1", roles = "USER")
    void aReloadSeesPendingAndFailureAllowsRetry() throws Exception {
        terminalWrites = new CountDownLatch(2);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(athena.getTextFeedbackSuggestions(any(), any(), anyBoolean(), any())).thenAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            throw new IllegalStateException("Athena unavailable");
        });
        try {
            service.handleNonGradedFeedbackRequest(participation, exercise);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var stored = storedResults();
            assertThat(stored).hasSize(1);
            assertThat(stored.getFirst().isSuccessful()).isNull();
            assertThat(stored.getFirst().getCompletionDate()).isAfter(ZonedDateTime.now());
            JsonNode details = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, JsonNode.class);
            JsonNode pendingWire = details.at("/exercise/studentParticipations/0/submissions/0/results/0");
            assertThat(pendingWire.path("id").asLong()).isEqualTo(stored.getFirst().getId());
            assertThat(pendingWire.path("assessmentType").asString()).isEqualTo("AUTOMATIC_ATHENA");
            assertThat(pendingWire.path("completionDate").isString()).isTrue();
            assertThat(pendingWire.path("successful").isMissingNode() || pendingWire.path("successful").isNull()).isTrue();
            assertThatThrownBy(() -> service.handleNonGradedFeedbackRequest(participation, exercise)).isInstanceOf(BadRequestAlertException.class);
        }
        finally {
            release.countDown();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(storedResults().getFirst().isSuccessful()).isFalse());
        doReturn(List.of()).when(athena).getTextFeedbackSuggestions(any(), any(), anyBoolean(), any());
        service.handleNonGradedFeedbackRequest(participation, exercise);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(storedResults()).anyMatch(result -> Boolean.TRUE.equals(result.isSuccessful())));
        assertThat(storedResults()).hasSize(2);
    }

    @Test
    void simultaneousRequestsReserveOnlyOneResult() throws Exception {
        terminalWrites = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var invocations = new AtomicInteger();
        when(athena.getTextFeedbackSuggestions(any(), any(), anyBoolean(), any())).thenAnswer(invocation -> {
            invocations.incrementAndGet();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of();
        });
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> requestAfter(start));
            var second = executor.submit(() -> requestAfter(start));
            start.countDown();
            int accepted = (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(accepted).isEqualTo(1);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(invocations).hasValue(1));
            assertThat(storedResults()).hasSize(1);
        }
        finally {
            release.countDown();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(storedResults().getFirst().isSuccessful()).isTrue());
    }

    @Test
    void expiredWorkerCannotOverwriteItsRetryOrNewStudentWork() throws Exception {
        terminalWrites = new CountDownLatch(2);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(athena.getTextFeedbackSuggestions(any(), any(), anyBoolean(), any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return List.of();
        });
        try {
            service.handleNonGradedFeedbackRequest(participation, exercise);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var pending = storedResults().getFirst();
            pending.setCompletionDate(ZonedDateTime.now().minusMinutes(1));
            results.save(pending);
            var newerAnswer = submissions.findByIdElseThrow(submission.getId());
            newerAnswer.setText("Newer student work must survive.");
            submissions.save(newerAnswer);
            service.handleNonGradedFeedbackRequest(participation, exercise);
            assertThat(storedResults()).hasSize(2);
            assertThat(results.findByIdElseThrow(pending.getId()).isSuccessful()).isFalse();
            // The low-load test JVM can have a single common-pool worker. Reserve the retry now, then release the old
            // call before waiting for the retry's worker; waiting first would test executor starvation, not fencing.
        }
        finally {
            release.countDown();
        }
        assertThat(terminalWrites.await(5, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(storedResults()).hasSize(2);
            assertThat(storedResults()).filteredOn(result -> Boolean.TRUE.equals(result.isSuccessful())).hasSize(1);
            assertThat(submissions.findByIdElseThrow(submission.getId()).getText()).isEqualTo("Newer student work must survive.");
        });
    }

    @Test
    void completionCommitsFeedbackAndBlocksTogetherAndRejectsASecondCompletion() {
        Result pending = feedbackRequests.reserveAthenaFeedbackRequest(submission);
        Feedback feedback = new Feedback();
        feedback.setCredits(2.0);
        feedback.setDetailText("A durable explanation");
        feedback.setType(FeedbackType.AUTOMATIC);
        TextBlock block = new TextBlock().submission(submission).text("The").startIndex(0).endIndex(3).automatic();
        block.computeId();
        block.setFeedback(feedback);
        feedback.setReference(block.getId());

        assertThat(feedbackRequests.completeAthenaFeedbackRequest(pending, 20.0, List.of(feedback), Set.of(block))).isTrue();
        Result stored = results.findByIdElseThrow(pending.getId());
        assertThat(stored.isSuccessful()).isTrue();
        assertThat(stored.getScore()).isEqualTo(20.0);
        assertThat(feedbackRepository.findByResult(stored)).singleElement().extracting(Feedback::getDetailText).isEqualTo("A durable explanation");
        assertThat(textBlockRepository.findAllBySubmissionId(submission.getId())).singleElement().extracting(TextBlock::getId).isEqualTo(block.getId());
        assertThat(feedbackRequests.completeAthenaFeedbackRequest(pending, 100.0, List.of(), Set.of())).isFalse();
        assertThat(feedbackRequests.failAthenaFeedbackRequest(pending)).isFalse();
        assertThat(results.findByIdElseThrow(pending.getId()).getScore()).isEqualTo(20.0);
    }

    @Test
    void aBlockPersistenceFailureRollsBackFeedbackAndCompletion() {
        Result pending = feedbackRequests.reserveAthenaFeedbackRequest(submission);
        Feedback feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("Must roll back");
        feedback.setType(FeedbackType.AUTOMATIC);
        TextBlock invalidBlock = new TextBlock().submission(submission).startIndex(0).endIndex(3);
        invalidBlock.setId("a".repeat(40));
        invalidBlock.setFeedback(feedback);
        assertThatThrownBy(() -> feedbackRequests.completeAthenaFeedbackRequest(pending, 10.0, List.of(feedback), Set.of(invalidBlock))).isInstanceOf(RuntimeException.class);
        assertThat(results.findByIdElseThrow(pending.getId()).isSuccessful()).isNull();
        assertThat(feedbackRepository.findByResult(pending)).isEmpty();
        assertThat(textBlockRepository.findAllBySubmissionId(submission.getId())).isEmpty();
    }

    @Test
    void completedRequestsEnforceTheQuotaInsideTheClaim() {
        for (int index = 0; index < 10; index++) {
            Result completed = new Result();
            completed.setExerciseId(exercise.getId());
            completed.setSubmission(submission);
            completed.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
            completed.setSuccessful(true);
            completed.setCompletionDate(ZonedDateTime.now());
            results.save(completed);
        }
        TextSubmission next = new TextSubmission();
        next.setText("Another submitted answer");
        next.setParticipation(participation);
        next.setSubmitted(true);
        next.setSubmissionDate(ZonedDateTime.now());
        next = submissions.save(next);
        TextSubmission target = next;
        assertThatThrownBy(() -> feedbackRequests.reserveAthenaFeedbackRequest(target)).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("Maximum number");
        assertThat(storedResults()).hasSize(10);
    }

    @Test
    void anExpiredRequestCannotCompleteAfterANewReservation() {
        Result expired = feedbackRequests.reserveAthenaFeedbackRequest(submission);
        expired.setCompletionDate(ZonedDateTime.now().minusMinutes(1));
        results.save(expired);
        Result retry = feedbackRequests.reserveAthenaFeedbackRequest(submission);
        assertThat(feedbackRequests.completeAthenaFeedbackRequest(expired, 100.0, List.of(), Set.of())).isFalse();
        assertThat(feedbackRequests.failAthenaFeedbackRequest(expired)).isFalse();
        assertThat(feedbackRequests.completeAthenaFeedbackRequest(retry, 25.0, List.of(), Set.of())).isTrue();
        assertThat(results.findByIdElseThrow(expired.getId()).isSuccessful()).isFalse();
        assertThat(results.findByIdElseThrow(retry.getId()).getScore()).isEqualTo(25.0);
    }

    private boolean requestAfter(CountDownLatch start) throws InterruptedException {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            service.handleNonGradedFeedbackRequest(participation, exercise);
            return true;
        }
        catch (BadRequestAlertException duplicate) {
            return false;
        }
    }

    private List<Result> storedResults() {
        return results.findAllBySubmissionParticipationIdOrderByCompletionDateDesc(participation.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void pendingFailedAndExpiredRequestsPreserveAnAssessedGrade(boolean team) {
        long competencyId = prepareGradeScenario(team);
        Result assessed = saveGrade(AssessmentType.MANUAL, true, 80.0, ZonedDateTime.now().minusMinutes(1));
        assertGradeIntegrity(team, competencyId, assessed, 80.0);

        Result pending = feedbackRequests.reserveAthenaFeedbackRequest(submission);
        assertGradeIntegrity(team, competencyId, assessed, 80.0);
        assertThat(results.findByIdElseThrow(pending.getId()).getScore()).isNull();
        assertThat(results.findByIdElseThrow(pending.getId()).isRated()).isFalse();

        assertThat(feedbackRequests.failAthenaFeedbackRequest(pending)).isTrue();
        assertGradeIntegrity(team, competencyId, assessed, 80.0);

        Result expired = feedbackRequests.reserveAthenaFeedbackRequest(submission);
        expired.setCompletionDate(ZonedDateTime.now().minusSeconds(1));
        results.saveAndFlush(expired);
        Result retry = feedbackRequests.reserveAthenaFeedbackRequest(submission);
        assertThat(results.findByIdElseThrow(expired.getId()).isSuccessful()).isFalse();
        assertGradeIntegrity(team, competencyId, assessed, 80.0);

        assertThat(feedbackRequests.completeAthenaFeedbackRequest(retry, 70.0, List.of(), Set.of())).isTrue();
        assertThat(results.findByIdElseThrow(retry.getId()).isRated()).isTrue();
        assertGradeIntegrity(team, competencyId, retry, 70.0);

        // A failed ordinary automatic assessment is a real zero grade, unlike a failed provider request.
        Result failedBuild = saveGrade(AssessmentType.AUTOMATIC, false, 0.0, ZonedDateTime.now().plusSeconds(1));
        assertGradeIntegrity(team, competencyId, failedBuild, 0.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void residualIneligibleAthenaResultsCannotHideAnAssessedGrade(boolean team) {
        long competencyId = prepareGradeScenario(team);
        Result assessed = saveGrade(AssessmentType.MANUAL, true, 80.0, ZonedDateTime.now().minusMinutes(1));
        assertGradeIntegrity(team, competencyId, assessed, 80.0);

        // Guard the SQL candidate and MAX/newer-result predicates independently of the new reservation encoding.
        Result residual = saveGrade(AssessmentType.AUTOMATIC_ATHENA, null, 0.0, ZonedDateTime.now().plusMinutes(5));
        assertGradeIntegrity(team, competencyId, assessed, 80.0);
        residual.setSuccessful(false);
        residual.setCompletionDate(ZonedDateTime.now());
        results.saveAndFlush(residual);
        assertGradeIntegrity(team, competencyId, assessed, 80.0);
        residual.setSuccessful(null);
        residual.setCompletionDate(ZonedDateTime.now().minusSeconds(1));
        results.saveAndFlush(residual);
        assertGradeIntegrity(team, competencyId, assessed, 80.0);
    }

    private long prepareGradeScenario(boolean team) {
        exercise.setMaxPoints(10.0);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exercise.setAssessmentDueDate(null);
        if (team) {
            exercise.setMode(ExerciseMode.TEAM);
        }
        exerciseRepository.saveAndFlush(exercise);
        if (team) {
            var student = users.getUserByLogin("athenaclaimsstudent1");
            var teamParticipant = teams.createTeam(Set.of(student), student, exercise, "athenagrades");
            // setParticipant(Team) does not clear the original student, which getParticipant() would otherwise prefer.
            participation.setParticipant(null);
            participation.setParticipant(teamParticipant);
            participation = participations.saveAndFlush(participation);
            assertThat(participation.getStudent()).isEmpty();
            assertThat(participation.getParticipant().getId()).isEqualTo(teamParticipant.getId());
            submission.setParticipation(participation);
        }
        return competencies.createCompetencyWithExercise(exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow(), exercise).getId();
    }

    private Result saveGrade(AssessmentType type, Boolean successful, double score, ZonedDateTime completed) {
        Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(exercise.getId());
        result.setAssessmentType(type);
        result.setScore(score);
        result.setSuccessful(successful);
        result.setRated(true);
        result.setCompletionDate(completed);
        Result saved = results.saveAndFlush(result);
        assertThat(results.findByIdElseThrow(saved.getId()).isSuccessful()).isEqualTo(successful);
        return saved;
    }

    private void assertGradeIntegrity(boolean team, long competencyId, Result expected, double score) {
        // Recompute through the real selectors independently of delayed listener/message delivery.
        participantScoreScheduleService.scheduleTask(exercise.getId(), participation.getParticipant().getId(), null);
        participantScoreScheduleService.executeScheduledTasks();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(participantScores.findAllByExercise(exercise)).singleElement().satisfies(saved -> {
            assertThat(saved.getLastResult()).isNotNull().extracting(Result::getId).isEqualTo(expected.getId());
            assertThat(saved.getLastRatedResult()).isNotNull().extracting(Result::getId).isEqualTo(expected.getId());
            assertThat(saved.getLastScore()).isEqualTo(score);
            assertThat(saved.getLastRatedScore()).isEqualTo(score);
        }));
        await().atMost(Duration.ofSeconds(5)).until(participantScoreScheduleService::isIdle);
        long studentId = users.getUserByLogin("athenaclaimsstudent1").getId();
        long courseId = exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow().getId();
        var courseGrades = team ? participations.findTeamGradesByCourseId(courseId) : participations.findIndividualGradesByCourseId(courseId);
        var ownGrades = team ? participations.findTeamGradesByCourseIdAndStudentId(Set.of(courseId), studentId)
                : participations.findIndividualGradesByCourseIdAndStudentId(Set.of(courseId), studentId);
        ZonedDateTime calculationTime = ZonedDateTime.now();
        var overview = team ? participations.findTeamGradesForCourseOverview(Set.of(exercise.getId()), studentId, calculationTime)
                : participations.findIndividualGradesForCourseOverview(Set.of(exercise.getId()), studentId, calculationTime);
        assertThat(courseGrades).extracting(CourseGradeScoreDTO::score).containsExactly(score);
        assertThat(ownGrades).extracting(CourseGradeScoreDTO::score).containsExactly(score);
        assertThat(overview).extracting(CourseGradeScoreDTO::score).containsExactly(score);
        // These are the persisted StudentScore/TeamScore inputs used by competency progress and confidence.
        assertThat(courseCompetencies.findAllExerciseInfoByCompetencyIdAndUser(competencyId, users.getUserByLogin("athenaclaimsstudent1"))).singleElement().satisfies(info -> {
            assertThat(info.lastScore()).isEqualTo(score);
            assertThat(info.lastPoints()).isEqualTo(score / 10.0);
        });
        var ordered = team ? results.getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(exercise.getId(), participation.getParticipant().getId())
                : results.getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(exercise.getId(), studentId);
        var rated = team ? results.getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(exercise.getId(), participation.getParticipant().getId())
                : results.getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(exercise.getId(), studentId);
        assertThat(ordered).first().extracting(Result::getId).isEqualTo(expected.getId());
        assertThat(rated).first().extracting(Result::getId).isEqualTo(expected.getId());
        assertThat(resultGrades.findLatestGradeRelevantResultWithCompletionDate(participation.getId())).isPresent().get().extracting(Result::getId).isEqualTo(expected.getId());
    }
}
