package de.tum.cit.aet.artemis.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ManualResultCreationRepository;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextParticipationDTO;
import de.tum.cit.aet.artemis.text.service.TextSubmissionService;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Two tutors who open the same submission at the same time must not both create a result for the same correction round.
 * <p>
 * The tests hold the lock of the submission row in a transaction of their own, so that the request under test reaches the
 * point where it creates its result while the result of the other tutor is not committed yet. This makes the interleaving
 * that produced duplicate results deterministic instead of depending on timing.
 */
class TextSubmissionLockConcurrencyTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textsubmissionlock";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ManualResultCreationRepository manualResultCreationRepository;

    @Autowired
    private TextSubmissionService textSubmissionService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 2, 0, 0);
        Course course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        textExercise = ExerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void retrieveParticipationForSubmission_otherTutorCreatesResultAtTheSameTime_locked() throws Exception {
        TextSubmission submission = textExerciseUtilService.saveTextSubmission(textExercise, ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true),
                TEST_PREFIX + "student1");
        long otherTutorId = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1").getId();
        String url = "/api/text/text-submissions/" + submission.getId() + "/for-assessment";
        String databaseProductName = jdbcTemplate.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());

        // one worker for the request, one for the observer that waits until the request is blocked on the held lock
        try (var executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Void> openedByThisTutor = new TransactionTemplate(transactionManager).execute(status -> {
                // the other tutor opens the submission: it holds the lock of the submission and has created, but not yet committed, its result of round 0
                jdbcTemplate.queryForList("SELECT id FROM submission WHERE id = ? FOR UPDATE", submission.getId());
                jdbcTemplate.update("INSERT INTO result (submission_id, exercise_id, correction_round, assessment_type, assessor_id) VALUES (?, ?, 0, 'MANUAL', ?)",
                        submission.getId(), textExercise.getId(), otherTutorId);
                int holder = currentDatabaseSessionId(databaseProductName);

                var opened = CompletableFuture.runAsync(() -> {
                    userUtilService.changeUser(TEST_PREFIX + "tutor2");
                    try {
                        request.get(url, HttpStatus.LOCKED, TextParticipationDTO.class);
                    }
                    catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }, executor);
                // Query from another connection so that PostgreSQL's per-transaction statistics snapshot stays fresh.
                CompletableFuture.runAsync(
                        () -> Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(25))
                                .untilAsserted(() -> assertThat(sessionsWaitingForTheHeldLock(databaseProductName, holder)).as("the request waits for the held lock").isPositive()),
                        executor).orTimeout(15, TimeUnit.SECONDS).join();
                return opened;
            });
            // get() rethrows a failed status assertion of the request
            openedByThisTutor.get(30, TimeUnit.SECONDS);
        }

        assertThat(countResultsOfRound(submission.getId(), 0)).as("only the result of the other tutor exists for round 0").isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void lockTextSubmissionToBeAssessed_secondRound_copiesTheResultOfTheFirstRound() {
        TextSubmission submission = saveSubmissionWithManualResultOfRoundZero();
        Result firstRoundResult = submission.getResultForCorrectionRound(0);
        firstRoundResult = participationUtilService.addFeedbackToResult(ParticipationFactory.createManualTextFeedback(2.0, "reference"), firstRoundResult);

        TextSubmission lockedSubmission = textSubmissionService.lockTextSubmissionToBeAssessed(submission.getId(), 1);

        Result secondRoundResult = lockedSubmission.getResultForCorrectionRound(1);
        assertThat(secondRoundResult).isNotNull();
        assertThat(secondRoundResult.getId()).isNotNull();
        assertThat(secondRoundResult.getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor2");
        assertThat(secondRoundResult.getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        long firstRoundFeedbackId = firstRoundResult.getFeedbacks().iterator().next().getId();
        assertThat(secondRoundResult.getFeedbacks()).as("the feedback of round 0 is copied, not moved").singleElement().satisfies(feedback -> {
            assertThat(feedback.getId()).isNotNull().isNotEqualTo(firstRoundFeedbackId);
            assertThat(feedback.getCredits()).isEqualTo(2.0);
        });
        assertThat(countFeedbacksOfResult(firstRoundResult.getId())).as("round 0 keeps its feedback").isEqualTo(1);
        assertThat(countFeedbacksOfResult(secondRoundResult.getId())).as("the copy is persisted exactly once").isEqualTo(1);
        assertThat(countResultsOfRound(submission.getId(), 1)).isEqualTo(1);
    }

    @Test
    void saveManualResultIfCorrectionRoundIsFree_roundTaken_savesNothing() {
        TextSubmission submission = saveSubmissionWithManualResultOfRoundZero();

        Optional<Result> savedResult = manualResultCreationRepository.saveManualResultIfCorrectionRoundIsFree(submission.getId(), 0, newManualResult(submission, 0));

        assertThat(savedResult).isEmpty();
        assertThat(countResultsOfRound(submission.getId(), 0)).isEqualTo(1);
    }

    @Test
    void saveManualResultIfCorrectionRoundIsFree_roundTakenByResultWithoutAssessmentType_savesNothing() {
        // an empty result is stored before its assessment type is set, and it already holds its round
        TextSubmission submission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise,
                ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true), TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM result WHERE submission_id = ? AND correction_round = 0 AND assessment_type IS NULL", Long.class,
                submission.getId())).as("the result of round 0 has no assessment type yet").isEqualTo(1);

        Optional<Result> savedResult = manualResultCreationRepository.saveManualResultIfCorrectionRoundIsFree(submission.getId(), 0, newManualResult(submission, 0));

        assertThat(savedResult).isEmpty();
        assertThat(countResultsOfRound(submission.getId(), 0)).isEqualTo(1);
    }

    @Test
    void saveManualResultIfCorrectionRoundIsFree_onlyAutomaticResultInRound_saves() {
        TextSubmission submission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise,
                ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true), TEST_PREFIX + "student1");
        // an automatic result does not hold a round of manual correction, even if one is recorded on it
        // (the test connections do not auto-commit, so the update needs a transaction of its own to be visible)
        Integer updatedResults = new TransactionTemplate(transactionManager).execute(
                status -> jdbcTemplate.update("UPDATE result SET correction_round = 0 WHERE submission_id = ? AND assessment_type = 'AUTOMATIC_ATHENA'", submission.getId()));
        assertThat(updatedResults).as("the Athena result is placed in round 0").isEqualTo(1);

        Optional<Result> savedResult = manualResultCreationRepository.saveManualResultIfCorrectionRoundIsFree(submission.getId(), 0, newManualResult(submission, 0));

        assertThat(savedResult).hasValueSatisfying(result -> assertThat(result.getId()).isNotNull());
        assertThat(countResultsOfRound(submission.getId(), 0, "AUTOMATIC_ATHENA")).as("the Athena result stays in round 0").isEqualTo(1);
        assertThat(countResultsOfRound(submission.getId(), 0, "MANUAL")).as("the manual result is saved next to it").isEqualTo(1);
    }

    private TextSubmission saveSubmissionWithManualResultOfRoundZero() {
        TextSubmission submission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise,
                ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true), TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        Result result = submission.getResultForCorrectionRound(0);
        result.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(result);
        return submission;
    }

    private Result newManualResult(TextSubmission submission, int correctionRound) {
        Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(textExercise.getId());
        result.setCorrectionRound(correctionRound);
        result.setAssessmentType(AssessmentType.MANUAL);
        return result;
    }

    private long countResultsOfRound(long submissionId, int correctionRound) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM result WHERE submission_id = ? AND correction_round = ?", Long.class, submissionId, correctionRound);
    }

    private long countResultsOfRound(long submissionId, int correctionRound, String assessmentType) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM result WHERE submission_id = ? AND correction_round = ? AND assessment_type = ?", Long.class, submissionId,
                correctionRound, assessmentType);
    }

    private long countFeedbacksOfResult(long resultId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feedback WHERE result_id = ?", Long.class, resultId);
    }

    private int currentDatabaseSessionId(String databaseProductName) {
        return switch (databaseProductName) {
            case "PostgreSQL" -> jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
            case "MySQL" -> jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Integer.class);
            default -> throw new AssertionError("Unsupported test database: " + databaseProductName);
        };
    }

    /**
     * Counts the sessions that wait for the lock the test holds: on PostgreSQL every session the holder blocks, on MySQL every other session that runs the lock of the submission
     * (with the fix) or the insert of a result (without it), which can only proceed once the holder commits.
     */
    private long sessionsWaitingForTheHeldLock(String databaseProductName, int holder) {
        return switch (databaseProductName) {
            case "PostgreSQL" -> jdbcTemplate.queryForObject("""
                    WITH RECURSIVE blocked(pid) AS (
                        SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                        UNION
                        SELECT activity.pid FROM pg_stat_activity activity
                        JOIN blocked ON blocked.pid = ANY(pg_blocking_pids(activity.pid))
                    )
                    SELECT COUNT(*) FROM blocked
                    """, Long.class, holder);
            case "MySQL" -> jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.PROCESSLIST
                    WHERE ID <> ? AND ID <> CONNECTION_ID()
                        AND (LOWER(INFO) LIKE '%from submission%for update%' OR LOWER(INFO) LIKE 'insert into result%')
                    """, Long.class, holder);
            default -> throw new AssertionError("Unsupported test database: " + databaseProductName);
        };
    }
}
