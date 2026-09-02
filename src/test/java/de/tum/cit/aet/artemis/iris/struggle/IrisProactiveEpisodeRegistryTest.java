package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Integration tests for the episode registry, the row that makes an episode lockable.
 *
 * <p>
 * These are the tests that could not be written before it existed. The terminal state used to live only on a
 * message row, so an outcome arriving before the first message had nowhere to go, and every check-then-write pair
 * had nothing to serialize on.
 */
class IrisProactiveEpisodeRegistryTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "episoderegistry";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    @Autowired
    private IrisStruggleInterventionService struggleInterventionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        userUtilService.setAiSelectionDecision(userUtilService.getUserByLogin(TEST_PREFIX + "student1"), AiSelectionDecision.CLOUD_AI);

        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        activateIrisFor(course);
        activateIrisFor(exercise);

        var courseSettings = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(courseSettings.enabled(), courseSettings.customInstructions(), courseSettings.variant(),
                courseSettings.supportLevel(), courseSettings.rateLimit(), true), true);
    }

    private long userId() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
    }

    @Test
    void trigger_registersTheEpisodeBeforeAnyCallbackCanRun() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var preparation = struggleInterventionService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-register", true, null), null, null, null);

        assertThat(preparation.accepted()).isTrue();
        // The row has to exist by the time prepareTrigger returns: the caller only dispatches Pyris afterwards, so
        // this is what guarantees every later path finds a row to lock.
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-register")).isPresent();
    }

    @Test
    void trigger_withoutAnEpisode_registersNothing() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var preparation = struggleInterventionService.prepareTrigger(exercise.getId(), user, "decide", null, null, null, null);

        assertThat(preparation.accepted()).isTrue();
        // A legacy client that sends no episode keeps the pre-registry behaviour rather than getting a row it can
        // never address.
        assertThat(irisProactiveEpisodeRepository.findAll()).isEmpty();
    }

    @Test
    void outcomeBeforeAnyMessage_isRecordedInsteadOfDeferred() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        struggleInterventionService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-early", true, null), null, null, null);

        // The dismiss arrives while the run is still in flight, so no message row exists yet. Before the registry
        // this could only be deferred, and the episode stayed non-terminal until the client back-filled.
        boolean applied = struggleInterventionService.writeEpisodeOutcome("ep-early", IrisProactiveOutcome.DISMISSED, user.getId(), exercise.getId());

        assertThat(applied).isTrue();
        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-early").orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void firstTerminalWins_onTheRegistry() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        struggleInterventionService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-first", true, null), null, null, null);

        struggleInterventionService.writeEpisodeOutcome("ep-first", IrisProactiveOutcome.DISMISSED, user.getId(), exercise.getId());
        struggleInterventionService.writeEpisodeOutcome("ep-first", IrisProactiveOutcome.RECOVERED, user.getId(), exercise.getId());

        assertThat(irisProactiveEpisodeRepository.find(user.getId(), exercise.getId(), "ep-first").orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void unregisteredEpisode_keepsThePreRegistryDeferral() {
        // No trigger, so no registry row: the outcome has nowhere to live but a message row, and there is none.
        boolean applied = struggleInterventionService.writeEpisodeOutcome("ep-unregistered", IrisProactiveOutcome.DISMISSED, userId(), exercise.getId());

        assertThat(applied).as("an unregistered episode still defers, exactly as before the registry").isFalse();
    }

    @Test
    void anOutcomeWriteWaitsForAWriterHoldingTheEpisodeLock() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        struggleInterventionService.prepareTrigger(exercise.getId(), user, "decide", new StruggleEpisodeDTO("ep-lock", true, null), null, null, null);
        long userId = user.getId();
        long exerciseId = exercise.getId();

        var holderHasLock = new CountDownLatch(1);
        var outcomeAttempted = new CountDownLatch(1);
        var holder = Executors.newSingleThreadExecutor();
        var writer = Executors.newSingleThreadExecutor();
        try {
            // One transaction takes the episode's write lock and keeps it.
            var holding = holder.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                irisProactiveEpisodeRepository.findForUpdate(userId, exerciseId, "ep-lock").orElseThrow();
                holderHasLock.countDown();
                try {
                    // Give the outcome write a generous head start. If the lock did not hold it, it would finish here.
                    outcomeAttempted.await(2, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));

            assertThat(holderHasLock.await(10, TimeUnit.SECONDS)).isTrue();
            var outcome = writer.submit(() -> {
                outcomeAttempted.countDown();
                return struggleInterventionService.writeEpisodeOutcome("ep-lock", IrisProactiveOutcome.DISMISSED, userId, exerciseId);
            });

            // The outcome write only gets through once the holder's transaction commits and releases the row.
            assertThat(outcome.get(30, TimeUnit.SECONDS)).isTrue();
            holding.get(30, TimeUnit.SECONDS);
        }
        finally {
            holder.shutdownNow();
            writer.shutdownNow();
        }

        assertThat(irisProactiveEpisodeRepository.find(userId, exerciseId, "ep-lock").orElseThrow().getOutcome()).isEqualTo(IrisProactiveOutcome.DISMISSED);
    }
}
