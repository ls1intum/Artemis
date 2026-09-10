package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeWriteRepository.OutcomeWrite;
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
    private IrisSettingsApi irisSettingsApi;

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

    @Test
    void registerEpisode_commitsEvenWhenTheCallersTransactionRollsBack() {
        // Everything the duplicate handling does depends on REQUIRES_NEW taking effect on a custom fragment, so
        // this asserts the wiring rather than trusting it: register inside a transaction that then rolls back, and
        // the row must still be there.
        long userId = userId();
        long exerciseId = exercise.getId();
        var template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            irisProactiveEpisodeRepository.registerOrTouchInNewTransaction(userId, exerciseId, "ep-requires-new");
            status.setRollbackOnly();
        });

        assertThat(irisProactiveEpisodeRepository.find(userId, exerciseId, "ep-requires-new")).isPresent();
    }

    private long userId() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
    }

    // Two tests assert that a write blocks while another transaction holds the row. H2 gives up after one second by
    // default, so raise its limit rather than loosen the assertion; a no-op on the other engines.
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
        // The row has to exist by the time prepareTrigger returns, because Pyris is only dispatched afterwards.
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-register")).isPresent();
    }

    @Test
    void trigger_withoutAnEpisode_registersNothing() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var preparation = struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", null, null, null, null);

        assertThat(preparation.accepted()).isTrue();
        // A client that sends no episode keeps the pre-registry behaviour. Scoped to this test's own user and
        // exercise, because classes run in parallel against one database.
        assertThat(irisProactiveEpisodeRepository.findAll()).noneMatch(e -> e.getUserId() == user.getId() && e.getExerciseId() == exercise.getId());
    }

    @Test
    void outcomeBeforeAnyMessage_isRecordedInsteadOfDeferred() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-early", true, null), null, null, null);

        // The dismiss arrives while the run is in flight, so no message row exists yet. Before the registry this
        // could only be deferred.
        boolean applied = proactiveEpisodeService.writeEpisodeOutcome("ep-early", IrisProactiveOutcome.DISMISSED, user.getId(), exercise.getId());

        assertThat(applied).isTrue();
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-early").orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void terminalisingAnUnrevealedEpisode_clearsTheOfferedHint() {
        // The counterpart to the reveal clearing its own text: an episode that ends unrevealed has no reader for
        // the offer either, and the hint is the one large column on a row that lives until the reset.
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-unrevealed", true, null), null, null, null);
        var episode = irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-unrevealed").orElseThrow();
        episode.setHintText("An offer nobody opened.");
        irisProactiveEpisodeRepository.save(episode);

        proactiveEpisodeService.writeEpisodeOutcome("ep-unrevealed", IrisProactiveOutcome.ABANDONED, user.getId(), exercise.getId());

        var terminal = irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-unrevealed").orElseThrow();
        assertThat(terminal.getOutcome()).isEqualTo(IrisProactiveOutcome.ABANDONED);
        assertThat(terminal.getHintText()).isNull();
    }

    @Test
    void courseStudentDataReset_removesTheCoursesEpisodes_andLeavesAnotherCoursesAlone() {
        // The horizon for a retained episode: a reset preserves the exercises, so the foreign key never fires and
        // these rows would outlive the student data they carry. Goes through IrisSettingsApi because that is the
        // seam CourseResetService calls, and a second real course makes a mis-scoped delete visible.
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var otherCourse = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        var otherExercise = ExerciseUtilService.getFirstExerciseWithType(otherCourse, ProgrammingExercise.class);
        // The second course needs the activation setUp gives the first, or the episode this test asserts survives
        // would never be registered and the test would pass for the wrong reason.
        activateIrisFor(otherCourse);
        activateIrisFor(otherExercise);
        setProactiveStruggleFor(otherCourse, true);
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-reset", true, null), null, null, null);
        struggleTriggerService.prepareTrigger(otherExercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-other-course", true, null), null, null, null);

        int deleted = irisSettingsApi.deleteCourseProactiveEpisodes(exercise.getCourseViaExerciseGroupOrCourseMember().getId());

        assertThat(deleted).isEqualTo(1);
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-reset")).isEmpty();
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), otherExercise.getId(), "ep-other-course")).isPresent();
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
        // Free the slot the first trigger reserved, or the second call is rejected before it reaches the registry.
        pyrisJobService.releaseStruggleInFlightJob(first.trigger().jobToken(), user.getId(), exercise.getId());

        // The confirm_close run that follows a decide run carries the same episode id, so re-registration is normal.
        var second = struggleTriggerService.prepareTrigger(exercise.getId(), user, "confirm_close", new StruggleEpisodeDTO("ep-touch", true, null), "progress", null, null);
        assertThat(second.accepted()).isTrue();

        // Asserted through the retention delete: the row was aged past the cutoff, so it survives only if the
        // second trigger refreshed its timestamp. The count is not asserted, because the delete is table-wide.
        irisProactiveEpisodeRepository.deleteAbandonedEpisodesLastTriggeredBefore(ZonedDateTime.now().minusDays(7));

        assertThat(irisProactiveEpisodeRepository.findById(registered.getId())).as("a repeat trigger must refresh the row and reuse it, not insert a second one").isPresent();
    }

    @Test
    void retentionRemovesOnlyEpisodesThatWentQuietWithNothingToKeep() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var cutoff = ZonedDateTime.now().minusDays(7);
        long abandoned = agedEpisode("ep-abandoned", null, false);
        long terminal = agedEpisode("ep-terminal", IrisProactiveOutcome.DISMISSED, false);
        long revealed = agedEpisode("ep-revealed", null, true);
        struggleTriggerService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-live", true, null), null, null, null);

        // The count is not asserted, because the delete is table-wide. What matters is which of these four rows
        // survived.
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

        // A late outcome finds neither row and is discarded, which is the documented contract.
        assertThat(proactiveEpisodeService.writeEpisodeOutcome("ep-reused", IrisProactiveOutcome.DISMISSED, user.getId(), exercise.getId())).isFalse();

        // Reusing the id is a new lifecycle under the same identity, which the natural key allows.
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

            // The assertion that makes this a test about the lock: without it the write completes here.
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
        // An episode whose terminal state lives on the message row alone. Registering the same id must not hand it
        // a fresh open row that every later check would trust.
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
    void anOpenRegistryRowWithACommittedMessageOutcomeReadsAsTerminal() {
        // What the unlocked carry-over read can leave behind: an outcome commits between that read and the insert.
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        long hintId = proactiveHint(session, "ep-diverged").getId();
        irisMessageRepository.setProactiveOutcomeIfNull(hintId, IrisProactiveOutcome.DISMISSED);
        openRegistryRow("ep-diverged", null);

        // Asserted on the append rather than the service's terminal gate, which is only a fast path.
        var appended = irisProactiveEpisodeRepository.appendProactiveMessageWithOutcome(session.getId(), user.getId(), exercise.getId(), "a late hint", "ep-diverged", null);
        assertThat(appended.terminal()).as("a dismiss the registry row never learned about still ends the episode").isTrue();
        assertThat(appended.message()).as("nothing may be appended for an episode the student has already closed").isNull();
    }

    @Test
    void recordingAnOutcomeOnADivergedEpisodeAdoptsTheStandingOneAndLoses() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        long hintId = proactiveHint(session, "ep-reconcile").getId();
        irisMessageRepository.setProactiveOutcomeIfNull(hintId, IrisProactiveOutcome.DISMISSED);
        openRegistryRow("ep-reconcile", null);

        var write = irisProactiveEpisodeRepository.recordOutcomeUnderLock("ep-reconcile", user.getId(), exercise.getId(), IrisProactiveOutcome.RECOVERED);

        assertThat(write).as("first-terminal-wins is episode-wide, so the standing dismiss beats this write").isEqualTo(OutcomeWrite.LOST);
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-reconcile").orElseThrow().getOutcome())
                .as("the registry is brought in line with what already stands rather than left open").isEqualTo(IrisProactiveOutcome.DISMISSED);
        assertThat(irisMessageRepository.findEpisodeOutcomes("ep-reconcile", user.getId(), exercise.getId()))
                .as("the message row keeps the one outcome it had, and gains no second one").containsExactly(IrisProactiveOutcome.DISMISSED);
    }

    // A persisted proactive hint carrying the episode id, the row an outcome can be written onto.
    private IrisMessage proactiveHint(IrisChatSession session, String episodeId) {
        var hint = new IrisMessage();
        hint.addContent(new IrisTextMessageContent("a hint that carries the episode's outcome"));
        hint.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        hint.setProactiveEpisodeId(episodeId);
        hint.setProactiveExerciseId(exercise.getId());
        return irisMessageService.saveMessage(hint, session, IrisMessageSender.LLM);
    }

    @Test
    void anAmbientOfferIsRefusedOnADivergedEpisode() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        irisMessageRepository.setProactiveOutcomeIfNull(proactiveHint(session, "ep-ambient-diverged").getId(), IrisProactiveOutcome.DISMISSED);
        openRegistryRow("ep-ambient-diverged", null);

        var recorded = irisProactiveEpisodeRepository.recordAmbientOfferUnderLock(user.getId(), exercise.getId(), "ep-ambient-diverged", "a hint nobody may be offered");

        assertThat(recorded).as("an episode the student closed must not be offered a fresh hint").isNull();
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-ambient-diverged").orElseThrow().getHintText())
                .as("and nothing may be stored for it either").isNull();
    }

    @Test
    void revealingAHintIsRefusedOnADivergedEpisode() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user);
        irisMessageRepository.setProactiveOutcomeIfNull(proactiveHint(session, "ep-reveal-diverged").getId(), IrisProactiveOutcome.DISMISSED);
        // With hint text, so the reveal reaches the terminal gate instead of refusing earlier.
        openRegistryRow("ep-reveal-diverged", "an offer that was made before the episode closed");

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> irisProactiveEpisodeRepository.revealAmbient(user.getId(), exercise.getId(), "ep-reveal-diverged", session.getId()))
                .withMessageContaining("can no longer be revealed");
    }

    // A registry row with no outcome, standing in for the one registration inserts after an empty carry-over read.
    private void openRegistryRow(String episodeId, @Nullable String hintText) {
        var episode = new IrisProactiveEpisode();
        episode.setUserId(userId());
        episode.setExerciseId(exercise.getId());
        episode.setEpisodeId(episodeId);
        episode.setLastTriggeredAt(ZonedDateTime.now());
        episode.setHintText(hintText);
        irisProactiveEpisodeRepository.save(episode);
        // Asserted, not assumed: without the row the tests take the unregistered fallback and prove nothing.
        assertThat(irisProactiveEpisodeRepository.find(userId(), exercise.getId(), episodeId)).get().extracting(IrisProactiveEpisode::getOutcome)
                .as("the divergence under test is an OPEN registry row next to a closed message row").isNull();
    }

    @Test
    void theLockingOutcomeReadRunsOnTheRealDatabase() {
        // A scalar projection with a pessimistic lock over a join, which a dialect can reject at execution time
        // rather than at bootstrap, so it is exercised against the real database.
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
        // The pre-registry path: the terminal check has no row to lock, so a dismiss can commit between its read
        // and the append.
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

            // The close runs while the dismiss is uncommitted and invisible, so only the guarded write notices.
            var job = new StruggleInterventionJob("race", courseId, exerciseId, userId, "confirm_close", "ep-race", "progress", null, null);
            var update = new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, true, "All good now.",
                    "Resolved");
            var close = closer.submit(() -> struggleInterventionService.handleConfirmClose(job, update));

            // Without the guarded write the close would sail past here and announce the episode as recovered.
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

        // The dismiss won, so the closing row is rolled back with its outcome write.
        assertThat(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-race", userId, exerciseId)).as("the rolled-back closing row must not be there")
                .containsExactly(hintId);
        assertThat(irisMessageRepository.findEpisodeOutcomes("ep-race", userId, exerciseId)).containsExactly(IrisProactiveOutcome.DISMISSED);
    }
}
