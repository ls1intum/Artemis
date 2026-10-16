package de.tum.cit.aet.artemis.hyperion.service.websocket;

import static de.tum.cit.aet.artemis.hyperion.web.HyperionWebsocketTopics.EXERCISE_GENERATION_STATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserDestination;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.ExerciseGenerationStateChangedEvent;
import de.tum.cit.aet.artemis.hyperion.web.HyperionWebsocketTopics;

/**
 * Regression tests for the {@code taskExecutor} deadlock behind Hyperion code generation jobs that never finished.
 * <p>
 * {@link WebsocketMessagingService} runs every send on the shared {@code taskExecutor}, the same pool the
 * {@code @Async} code generation jobs run on. {@link HyperionWebsocketService#send} used to wait for the send to
 * complete, so a job thread waited for a task that was queued behind the running jobs. With every pool thread waiting
 * like that, nothing on the pool ever ran again until the server was restarted. The send has to be a hand-off: return
 * at once, report the outcome from the completion callback.
 */
class HyperionWebsocketServiceTest {

    private static final String LOGIN = "instructor1";

    private static final WebsocketUserDestination DESTINATION = HyperionWebsocketTopics.CODE_GENERATION_JOB.at("job-1");

    private static final String TOPIC = DESTINATION.value();

    private static final String PAYLOAD = "progress event";

    private WebsocketMessagingService websocketMessagingService;

    private HyperionWebsocketService hyperionWebsocketService;

    // Never completes on its own: this is what a send looks like while it sits in the taskExecutor queue behind the job that submitted it.
    private CompletableFuture<Void> pendingSend;

    private ExecutorService callerThread;

    private Logger logger;

    private Level previousLevel;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        hyperionWebsocketService = new HyperionWebsocketService(websocketMessagingService);
        pendingSend = new CompletableFuture<>();
        callerThread = Executors.newSingleThreadExecutor();

        logger = (Logger) LoggerFactory.getLogger(HyperionWebsocketService.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
        logger.setLevel(previousLevel);
        // Release a caller that a regression back to a blocking wait would have parked on the pending send.
        pendingSend.complete(null);
        callerThread.shutdownNow();
    }

    @Test
    void shouldReturnBeforeTheMessageIsDelivered() {
        when(websocketMessagingService.sendMessageToUser(LOGIN, DESTINATION, PAYLOAD)).thenReturn(pendingSend);

        sendWithinTimeout();

        verify(websocketMessagingService).sendMessageToUser(LOGIN, DESTINATION, PAYLOAD);
        assertThat(pendingSend).isNotDone();
        assertThat(ownLogEvents()).isEmpty();
    }

    @Test
    void shouldLogADeliveryFailureThatHappensAfterTheHandOff() {
        when(websocketMessagingService.sendMessageToUser(LOGIN, DESTINATION, PAYLOAD)).thenReturn(pendingSend);
        sendWithinTimeout();
        assertThat(ownLogEvents()).isEmpty();

        pendingSend.completeExceptionally(new IllegalStateException("broker unavailable"));

        assertThat(ownLogEvents()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains(LOGIN, TOPIC, PAYLOAD);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("broker unavailable");
        });
    }

    @Test
    void shouldNotThrowWhenTheSendFailsRightAway() {
        when(websocketMessagingService.sendMessageToUser(LOGIN, DESTINATION, PAYLOAD)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        assertThatCode(() -> hyperionWebsocketService.send(LOGIN, DESTINATION, PAYLOAD)).doesNotThrowAnyException();

        assertThat(ownLogEvents()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("broker unavailable");
        });
    }

    @Test
    void shouldLogASuccessfulDeliveryAtDebugLevel() {
        when(websocketMessagingService.sendMessageToUser(LOGIN, DESTINATION, PAYLOAD)).thenReturn(CompletableFuture.completedFuture(null));

        hyperionWebsocketService.send(LOGIN, DESTINATION, PAYLOAD);

        assertThat(ownLogEvents()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage()).contains(LOGIN, TOPIC, PAYLOAD);
        });
    }

    /**
     * Calls {@code send} on another thread with a bound, so that a regression back to a blocking wait fails the test
     * instead of hanging the whole suite.
     */
    private void sendWithinTimeout() {
        var call = CompletableFuture.runAsync(() -> hyperionWebsocketService.send(LOGIN, DESTINATION, PAYLOAD), callerThread);
        assertThatCode(() -> call.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
    }

    /**
     * The logger is shared with every other test in the JVM that drives the real service, so only the events about this
     * test's topic count.
     */
    private List<ILoggingEvent> ownLogEvents() {
        return logAppender.list.stream().filter(event -> event.getFormattedMessage().contains(TOPIC)).toList();
    }

    @Test
    void publishesExerciseStateToItsSharedTopic() {
        var messaging = mock(WebsocketMessagingService.class);
        var state = new ExerciseGenerationStateDTO(42, "job-1", true);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("core");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("hyperion-test", Map.of("artemis.hyperion.enabled", "true")));
            context.registerBean(HyperionWebsocketService.class, () -> new HyperionWebsocketService(messaging));
            context.refresh();
            context.publishEvent(new ExerciseGenerationStateChangedEvent(state));
        }

        verify(messaging).sendMessage(EXERCISE_GENERATION_STATE.at(42), state);
    }
}
