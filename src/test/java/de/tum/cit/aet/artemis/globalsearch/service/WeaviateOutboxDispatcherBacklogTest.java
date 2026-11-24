package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEmbeddingProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateMigrationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateOutboxProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;

/**
 * Unit tests for the backlog report of {@link WeaviateOutboxDispatcher}: a queue of rows for user actions that does not shrink is reported at {@code ERROR},
 * once per interval, and the end of it is reported once.
 */
class WeaviateOutboxDispatcherBacklogTest {

    private static final String COURSE = SearchableEntitySchema.TypeValues.COURSE;

    private static final String READ_ONLY_FAILURE = "HTTP 500: POST /v1/objects/: put object: shard=\"abc\": store is read-only due to: resource pressure";

    private final WeaviateOutboxRepository outboxRepository = mock(WeaviateOutboxRepository.class);

    private final SearchableEntityWeaviateService searchableEntityWeaviateService = mock(SearchableEntityWeaviateService.class);

    private final SearchableEntityEmbeddingClient embeddingClient = mock(SearchableEntityEmbeddingClient.class);

    private WeaviateOutboxDispatcher dispatcher;

    private Logger logger;

    private Level previousLevel;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        when(embeddingClient.maxInputsPerRequest()).thenReturn(32);
        var reconcileProperties = new WeaviateReconcileProperties(false, false, false, List.of(COURSE), 500, 100, 200, 1000, 5, 100, 100, 0.25);
        var embeddingProperties = new WeaviateEmbeddingProperties(600, 30, 32, Duration.ofSeconds(3), Duration.ofSeconds(60));
        dispatcher = new WeaviateOutboxDispatcher(outboxRepository, mock(SearchableEntitySyncStateRepository.class), searchableEntityWeaviateService,
                new WeaviateOutboxProperties(5, 100, 10, 300), reconcileProperties, embeddingProperties, embeddingClient, mock(SearchableEntityIndexRebuildService.class),
                new WeaviateMigrationProperties(0, 5, 1));

        logger = (Logger) LoggerFactory.getLogger(WeaviateOutboxDispatcher.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
        logger.setLevel(previousLevel);
    }

    private void queue(long size, Instant oldest) {
        when(outboxRepository.countByOrigin(WeaviateOutboxOrigin.LIVE)).thenReturn(size);
        when(outboxRepository.findOldestCreatedAtByOrigin(WeaviateOutboxOrigin.LIVE))
                .thenReturn(Optional.ofNullable(oldest).map(instant -> ZonedDateTime.ofInstant(instant, ZoneId.of("Europe/Berlin"))));
    }

    private List<ILoggingEvent> errors() {
        return logAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    /**
     * Lets the dispatcher see one failed write, so that it knows the last failure.
     */
    private void failOneWrite(String message) {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, WeaviateOutboxOrigin.LIVE);
        entry.setId(1L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry)).thenReturn(List.of());
        when(outboxRepository.findDueBackgroundForDispatch(any(), anyInt())).thenReturn(List.of());
        when(searchableEntityWeaviateService.applyOutboxEntry(entry)).thenThrow(new IllegalStateException(message));
        dispatcher.drain();
    }

    @Test
    void testMonitorBacklog_oldRowIsReportedWithTheLastFailureAndAHintAtTheReadOnlyStore() {
        Instant now = Instant.now().plus(Duration.ofHours(1));
        queue(3, now.minus(Duration.ofMinutes(40)));
        failOneWrite(READ_ONLY_FAILURE);

        dispatcher.monitorBacklog(now);

        assertThat(errors()).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("3 row(s)").contains("the oldest for 40 minute(s)").contains(READ_ONLY_FAILURE);
            assertThat(event.getFormattedMessage()).contains("Free capacity on the Weaviate host");
        });
    }

    @Test
    void testMonitorBacklog_largeQueueIsReportedEvenWhenItsRowsAreFresh() {
        Instant now = Instant.now().plus(Duration.ofHours(1));
        queue(WeaviateOutboxDispatcher.BACKLOG_ALERT_SIZE, now.minus(Duration.ofSeconds(30)));

        dispatcher.monitorBacklog(now);

        assertThat(errors()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("none since this node started"));
    }

    @Test
    void testMonitorBacklog_shortQueueOfFreshRowsIsNotReported() {
        Instant now = Instant.now().plus(Duration.ofHours(1));
        queue(WeaviateOutboxDispatcher.BACKLOG_ALERT_SIZE - 1, now.minus(Duration.ofMinutes(5)));

        dispatcher.monitorBacklog(now);

        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void testMonitorBacklog_unchangedBacklogIsReportedAgainOnlyAfterTheReportInterval() {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        queue(5, start.minus(Duration.ofHours(1)));

        dispatcher.monitorBacklog(start);
        dispatcher.monitorBacklog(start.plus(WeaviateOutboxDispatcher.BACKLOG_CHECK_INTERVAL));
        assertThat(errors()).hasSize(1);

        dispatcher.monitorBacklog(start.plus(WeaviateOutboxDispatcher.BACKLOG_REPORT_INTERVAL));
        assertThat(errors()).hasSize(2);
    }

    @Test
    void testMonitorBacklog_checksTheQueueOnlyOncePerCheckInterval() {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        queue(0, null);

        dispatcher.monitorBacklog(start);
        dispatcher.monitorBacklog(start.plusSeconds(10));

        verify(outboxRepository, times(1)).countByOrigin(WeaviateOutboxOrigin.LIVE);
    }

    @Test
    void testMonitorBacklog_theEndOfABacklogIsReportedOnce() {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        queue(5, start.minus(Duration.ofHours(1)));
        dispatcher.monitorBacklog(start);

        queue(0, null);
        Instant later = start.plus(WeaviateOutboxDispatcher.BACKLOG_CHECK_INTERVAL);
        dispatcher.monitorBacklog(later);
        dispatcher.monitorBacklog(later.plus(WeaviateOutboxDispatcher.BACKLOG_CHECK_INTERVAL));

        assertThat(logAppender.list.stream().filter(event -> event.getLevel() == Level.INFO)).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("backlog is gone"));
    }

    @Test
    void testMonitorBacklog_aFailingCheckIsLoggedAndDoesNotThrow() {
        when(outboxRepository.countByOrigin(WeaviateOutboxOrigin.LIVE)).thenThrow(new IllegalStateException("database unavailable"));

        dispatcher.monitorBacklog(Instant.now().plus(Duration.ofHours(1)));

        assertThat(logAppender.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void testDrain_doesNotQueryTheBacklogBeforeTheFirstCheckInterval() {
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of());
        when(outboxRepository.findDueBackgroundForDispatch(any(), anyInt())).thenReturn(List.of());

        dispatcher.drain();

        verify(outboxRepository, never()).countByOrigin(any(WeaviateOutboxOrigin.class));
    }
}
