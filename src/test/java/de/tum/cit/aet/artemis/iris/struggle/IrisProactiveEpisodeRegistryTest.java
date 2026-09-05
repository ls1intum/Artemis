package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisProactiveEpisodeService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleTriggerService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Integration tests for the episode registry, the row that makes an episode lockable.
 *
 * <p>
 * Without that row an outcome arriving before the episode's first message has nowhere to go, and every
 * check-then-write pair has nothing to serialize on. These tests cover both.
 */
class IrisProactiveEpisodeRegistryTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "episoderegistry";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    @Autowired
    private IrisStruggleTriggerService struggleTriggerService;

    @Autowired
    private IrisProactiveEpisodeService proactiveEpisodeService;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisChatSessionService irisChatSessionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PyrisJobService pyrisJobService;

    @Autowired
    private IrisStruggleInterventionService struggleInterventionService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() throws SQLException {
        raiseLockTimeoutOnH2();
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        userUtilService.setAiSelectionDecision(userUtilService.getUserByLogin(TEST_PREFIX + "student1"), AiSelectionDecision.CLOUD_AI);

        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        activateIrisFor(course);
        activateIrisFor(exercise);

        setProactiveStruggleFor(course, true);
    }

    private long userId() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
    }

    /**
     * Two tests here assert that a write BLOCKS while another transaction holds the row. H2, which the suite
     * supports in-process as an alternative to the Postgres container, gives up after one second by default, so the
     * write would fail rather than wait and the assertion would see the wrong exception. Raise the database's limit
     * instead of loosening the assertion: a write that fails fast is not the behaviour under test. No-op on every
     * other engine, whose defaults are already well above the two seconds these tests wait.
     */
    private void raiseLockTimeoutOnH2() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:")) {
                return;
            }
            try (var statement = connection.createStatement()) {
                statement.execute("SET DEFAULT_LOCK_TIMEOUT 10000");
            }
        }
    }

    @Test
    void trigger_registersTheEpisodeBeforeAnyCallbackCanRun() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var preparation = struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-register", true, null), null, null, null);

        assertThat(preparation.accepted()).isTrue();
        // The row has to exist by the time prepareTrigger returns: the caller only dispatches Pyris afterwards, so
        // this is what guarantees every later path finds a row to lock.
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-register")).isPresent();
    }

    @Test
    void trigger_withoutAnEpisode_registersNothing() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var preparation = struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", null, null, null, null);

        assertThat(preparation.accepted()).isTrue();
        // A legacy client that sends no episode keeps the pre-registry behaviour rather than getting a row it can
        // never address. Scoped to this test's own user and exercise: the suite runs classes in parallel against one
        // database, so asserting the table is empty would fail on whatever another class registered meanwhile.
        assertThat(irisProactiveEpisodeRepository.findAll()).noneMatch(e -> e.getUserId() == user.getId() && e.getExerciseId() == exercise.getId());
    }

    @Test
    void outcomeBeforeAnyMessage_isRecordedInsteadOfDeferred() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-early", true, null), null, null, null);

        // The dismiss arrives while the run is still in flight, so no message row exists yet. Before the registry
        // this could only be deferred, and the episode stayed non-terminal until the client back-filled.
        boolean applied = proactiveEpisodeService.writeEpisodeOutcome("ep-early", IrisProactiveOutcome.DISMISSED, user.getId(), exercise.getId());

        assertThat(applied).isTrue();
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-early").orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void firstTerminalWins_onTheRegistry() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-first", true, null), null, null, null);

        proactiveEpisodeService.writeEpisodeOutcome("ep-first", IrisProactiveOutcome.DISMISSED, user.getId(), exercise.getId());
        proactiveEpisodeService.writeEpisodeOutcome("ep-first", IrisProactiveOutcome.RECOVERED, user.getId(), exercise.getId());

        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-first").orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void unregisteredEpisode_keepsThePreRegistryDeferral() {
        // No trigger, so no registry row: the outcome has nowhere to live but a message row, and there is none.
        boolean applied = proactiveEpisodeService.writeEpisodeOutcome("ep-unregistered", IrisProactiveOutcome.DISMISSED, userId(), exercise.getId());

        assertThat(applied).as("an unregistered episode still defers, exactly as before the registry").isFalse();
    }

    @Test
    void repeatingATriggerRefreshesLastTriggeredAt() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var first = struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-touch", true, null), null, null, null);
        var registered = irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-touch").orElseThrow();
        // Backdate the registration so the refresh is unambiguous, and so this row would be reaped as it stands.
        registered.setLastTriggeredAt(ZonedDateTime.now().minusDays(30));
        irisProactiveEpisodeRepository.save(registered);
        // Free the single-flight slot the first trigger reserved. Without this the second call is rejected before it
        // ever reaches the registry, and the test would report a missing refresh that never had a chance to happen.
        pyrisJobService.releaseStruggleInFlightJob(first.trigger().jobToken(), user.getId(), exercise.getId());

        // The confirm_close run that follows a decide run carries the same episode id, so re-registration is normal.
        var second = struggleTriggerService.prepareTrigger(exercise.getId(), user, "confirm_close", new StruggleEpisodeDTO("ep-touch", true, null), "progress", null, null);
        assertThat(second.accepted()).isTrue();

        // Asserted through the retention delete rather than by reading the timestamp back. A query cannot prove the
        // refresh landed: Hibernate returns the instance this test already holds for that id, stale timestamp and
        // all, so the assertion would fail even with a correct refresh. The delete reads the database.
        int deleted = irisProactiveEpisodeRepository.deleteAbandonedEpisodesLastTriggeredBefore(ZonedDateTime.now().minusDays(7));

        assertThat(deleted).as("a repeat trigger must move the row out of the retention window").isZero();
        assertThat(irisProactiveEpisodeRepository.findById(registered.getId())).as("a repeat trigger must reuse the row, not insert a second one").isPresent();
    }

    @Test
    void retentionRemovesOnlyEpisodesThatWentQuietWithNothingToKeep() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var cutoff = ZonedDateTime.now().minusDays(7);
        long abandoned = agedEpisode("ep-abandoned", null, false);
        long terminal = agedEpisode("ep-terminal", IrisProactiveOutcome.DISMISSED, false);
        long revealed = agedEpisode("ep-revealed", null, true);
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-live", true, null), null, null, null);

        // The count is deliberately not asserted: the delete is table-wide and classes run in parallel, so another
        // class's aged row would make it flaky. What matters is which of THESE four rows survived.
        irisProactiveEpisodeRepository.deleteAbandonedEpisodesLastTriggeredBefore(cutoff);

        assertThat(irisProactiveEpisodeRepository.findById(abandoned)).as("an episode nobody triggered for a week and that holds nothing is reaped").isEmpty();
        assertThat(irisProactiveEpisodeRepository.findById(terminal)).as("a terminal outcome is what suppresses a late message, so it is kept").isPresent();
        assertThat(irisProactiveEpisodeRepository.findById(revealed)).as("a consumed offer is what keeps a replayed reveal idempotent, so it is kept").isPresent();
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-live")).as("a freshly triggered episode is never reaped").isPresent();
    }

    @Test
    void aReapedEpisodeCanBeRegisteredAgainAsANewLifecycle() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        long reaped = agedEpisode("ep-reused", null, false);
        irisProactiveEpisodeRepository.deleteAbandonedEpisodesLastTriggeredBefore(ZonedDateTime.now().minusDays(7));

        // A late outcome for the reaped episode finds neither a registry row nor a message row: it is discarded,
        // which is the documented contract rather than a silent write into whatever comes next.
        assertThat(proactiveEpisodeService.writeEpisodeOutcome("ep-reused", IrisProactiveOutcome.DISMISSED, user.getId(), exercise.getId())).isFalse();

        // Reusing the id afterwards is a NEW lifecycle under the same identity. Episode identity is
        // (user, exercise, episodeId) with no generation, so this is a property of the natural key.
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-reused", true, null), null, null, null);

        var fresh = irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-reused").orElseThrow();
        assertThat(fresh.getId()).as("the reaped row is gone, so this is a new row").isNotEqualTo(reaped);
        assertThat(fresh.getOutcome()).as("the discarded outcome must not carry into the new lifecycle").isNull();
        // A stale outcome arriving now resolves against the new row, which is the aliasing the contract accepts.
        assertThat(proactiveEpisodeService.writeEpisodeOutcome("ep-reused", IrisProactiveOutcome.ABANDONED, user.getId(), exercise.getId())).isTrue();
        assertThat(irisProactiveEpisodeRepository.findById(fresh.getId()).orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.ABANDONED);
    }

    /** An episode last triggered well before the retention cutoff, optionally terminal and optionally with a consumed offer. */
    private long agedEpisode(String episodeId, IrisProactiveOutcome outcome, boolean revealed) {
        var episode = new IrisProactiveEpisode();
        episode.setUserId(userId());
        episode.setExerciseId(exercise.getId());
        episode.setEpisodeId(episodeId);
        episode.setOutcome(outcome);
        episode.setLastTriggeredAt(ZonedDateTime.now().minusDays(30));
        if (revealed) {
            episode.setHintText("An offer the student already revealed.");
            episode.setConsumedAt(ZonedDateTime.now().minusDays(29));
        }
        return irisProactiveEpisodeRepository.save(episode).getId();
    }

    @Test
    void anOutcomeWriteWaitsForAWriterHoldingTheEpisodeLock() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-lock", true, null), null, null, null);
        long userId = user.getId();
        long exerciseId = exercise.getId();

        var holderHasLock = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var holder = Executors.newSingleThreadExecutor();
        var writer = Executors.newSingleThreadExecutor();
        try {
            // One transaction takes the episode's write lock and keeps it until this test lets go.
            var holding = holder.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                irisProactiveEpisodeRepository.findForUpdate(userId, exerciseId, "ep-lock").orElseThrow();
                holderHasLock.countDown();
                try {
                    releaseHolder.await(30, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(holderHasLock.await(10, TimeUnit.SECONDS)).isTrue();

            var outcome = writer.submit(() -> proactiveEpisodeService.writeEpisodeOutcome("ep-lock", IrisProactiveOutcome.DISMISSED, userId, exerciseId));

            // This is the assertion that makes the test about the lock: while the holder still has the row, the
            // outcome write must NOT be able to finish. Without the lock it would complete here and the test fails.
            assertThatThrownBy(() -> outcome.get(2, TimeUnit.SECONDS)).as("the outcome write must block while the episode row is locked").isInstanceOf(TimeoutException.class);

            releaseHolder.countDown();
            assertThat(outcome.get(30, TimeUnit.SECONDS)).isTrue();
            holding.get(30, TimeUnit.SECONDS);
        }
        finally {
            releaseHolder.countDown();
            holder.shutdownNow();
            writer.shutdownNow();
        }

        assertThat(irisProactiveEpisodeRepository.find(userId, exerciseId, "ep-lock").orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void aLegacyTerminalOutcomeSurvivesRegistration() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        // An episode that reached a terminal outcome before the registry existed: its state lives on the message row
        // alone. Registering the same id afterwards must not hand it a fresh open row, or every later check would
        // trust that row and let a late message through for an episode the student had already closed.
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        var legacy = new IrisMessage();
        legacy.addContent(new IrisTextMessageContent("hint"));
        legacy.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        legacy.setProactiveEpisodeId("ep-legacy");
        legacy.setProactiveExerciseId(exercise.getId());
        var saved = irisMessageService.saveMessage(legacy, session, IrisMessageSender.LLM);
        irisMessageRepository.setProactiveOutcomeIfNull(saved.getId(), IrisProactiveOutcome.DISMISSED);

        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-legacy", true, null), null, null, null);

        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-legacy").orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void theLockingOutcomeReadRunsOnTheRealDatabase() {
        // The classification read in the zero-rows branch of the legacy outcome write. It is a scalar projection with
        // a pessimistic lock over a join to the session table, which is the kind of query a dialect can reject at
        // execution time rather than at bootstrap, so it gets exercised against the real database here.
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        long userId = user.getId();
        long exerciseId = exercise.getId();
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId, user);
        var hint = new IrisMessage();
        hint.addContent(new IrisTextMessageContent("a hint that carries the episode's outcome"));
        hint.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        hint.setProactiveEpisodeId("ep-lockread");
        hint.setProactiveExerciseId(exerciseId);
        long hintId = irisMessageService.saveMessage(hint, session, IrisMessageSender.LLM).getId();

        // A locking read has to run inside a transaction.
        var beforeOutcome = new TransactionTemplate(transactionManager).execute(status -> irisMessageRepository.findEpisodeOutcomesForUpdate("ep-lockread", userId, exerciseId));
        assertThat(beforeOutcome).isEmpty();

        irisMessageRepository.setProactiveOutcomeIfNull(hintId, IrisProactiveOutcome.DISMISSED);

        var afterOutcome = new TransactionTemplate(transactionManager).execute(status -> irisMessageRepository.findEpisodeOutcomesForUpdate("ep-lockread", userId, exerciseId));
        assertThat(afterOutcome).containsExactly(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void aCloseLosingTheRaceOnAnUnregisteredEpisodeCommitsNothing() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        long userId = user.getId();
        long exerciseId = exercise.getId();
        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
        // No trigger, so no registry row. This is the pre-registry path: the terminal check has no row to lock and
        // reads the message rows instead, which is why a dismiss can commit between that read and the append.
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId, user);
        var hint = new IrisMessage();
        hint.addContent(new IrisTextMessageContent("the hint the student is about to dismiss"));
        hint.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        hint.setProactiveEpisodeId("ep-race");
        hint.setProactiveExerciseId(exerciseId);
        long hintId = irisMessageService.saveMessage(hint, session, IrisMessageSender.LLM).getId();

        var dismissHoldsTheRow = new CountDownLatch(1);
        var releaseDismiss = new CountDownLatch(1);
        var dismisser = Executors.newSingleThreadExecutor();
        var closer = Executors.newSingleThreadExecutor();
        try {
            // The student's dismiss writes the episode's outcome and keeps the row until this test lets it commit.
            var dismiss = dismisser.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                irisMessageRepository.setProactiveOutcomeIfNull(hintId, IrisProactiveOutcome.DISMISSED);
                dismissHoldsTheRow.countDown();
                try {
                    releaseDismiss.await(30, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(dismissHoldsTheRow.await(10, TimeUnit.SECONDS)).isTrue();

            // Pyris confirms the close while that dismiss is uncommitted and therefore invisible: the terminal check
            // passes, the closing row is appended, and only the guarded outcome write can still notice.
            var job = new StruggleInterventionJob("race", courseId, exerciseId, userId, "confirm_close", "ep-race", "progress", null, null);
            var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, true, "All good now.",
                    "Resolved");
            var close = closer.submit(() -> struggleInterventionService.handleConfirmClose(job, update));

            // The close cannot finish while the dismiss holds the row it has to write through. Without that guarded
            // write it would sail past here, commit its closing row, and announce the episode as recovered.
            assertThatThrownBy(() -> close.get(2, TimeUnit.SECONDS)).as("the close must wait for the row the dismiss is holding").isInstanceOf(TimeoutException.class);

            releaseDismiss.countDown();
            close.get(30, TimeUnit.SECONDS);
            dismiss.get(30, TimeUnit.SECONDS);
        }
        finally {
            releaseDismiss.countDown();
            dismisser.shutdownNow();
            closer.shutdownNow();
        }

        // The dismiss won, so nothing the close wrote may survive: the closing row is rolled back with its outcome
        // write, and the episode ends the way the student ended it.
        assertThat(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-race", userId, exerciseId)).as("the rolled-back closing row must not be there")
                .containsExactly(hintId);
        assertThat(irisMessageRepository.findEpisodeOutcomes("ep-race", userId, exerciseId)).containsExactly(IrisProactiveOutcome.DISMISSED);
    }
}
