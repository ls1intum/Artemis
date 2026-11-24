package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

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
import de.tum.cit.aet.artemis.globalsearch.dto.WeaviateOutboxBacklogDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;

/**
 * Unit tests for the backlog report of {@link WeaviateOutboxDispatcher}: a queue of rows for user actions that does not shrink is reported at {@code ERROR},
 * once per interval, and the end of it is reported once.
 */
// Isolated: the tests read the log of a shared logger, which a Spring context that starts in parallel reconfigures
@Isolated
class WeaviateOutboxDispatcherBacklogTest {

    private static final String COURSE = SearchableEntitySchema.TypeValues.COURSE;

    private static final String READ_ONLY_FAILURE = "HTTP 500: POST /v1/objects/: put object: shard=\"abc\": store is read-only due to: resource pressure";

    private final WeaviateOutboxRepository outboxRepository = mock(WeaviateOutboxRepository.class);

    private final SearchableEntityWeaviateService searchableEntityWeaviateService = mock(SearchableEntityWeaviateService.class);

    private final SearchableEntityEmbeddingClient embeddingClient = mock(SearchableEntityEmbeddingClient.class);

    private final SearchableEntityIndexRebuildService rebuildService = mock(SearchableEntityIndexRebuildService.class);

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
                new WeaviateOutboxProperties(5, 100, 10, 300), reconcileProperties, embeddingProperties, embeddingClient, rebuildService, new WeaviateMigrationProperties(0, 5, 1));

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
        when(outboxRepository.findBacklogByOrigin(WeaviateOutboxOrigin.LIVE))
                .thenReturn(new WeaviateOutboxBacklogDTO(size, oldest == null ? null : ZonedDateTime.ofInstant(oldest, ZoneId.of("Europe/Berlin"))));
    }

    private List<ILoggingEvent> errors() {
        return logAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    /**
     * Lets the dispatcher see one failed write, so that it knows the last failure.
     */
    private void failOneWrite(String message) {
        failOneWrite(new IllegalStateException(message), WeaviateOutboxOrigin.LIVE);
    }

    private void failOneWrite(Exception failure, WeaviateOutboxOrigin origin) {
        WeaviateOutboxEntry entry = entry(origin);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(origin == WeaviateOutboxOrigin.LIVE ? List.of(entry) : List.of()).thenReturn(List.of());
        when(outboxRepository.findDueBackgroundForDispatch(any(), anyInt())).thenReturn(origin == WeaviateOutboxOrigin.LIVE ? List.of() : List.of(entry)).thenReturn(List.of());
        if (origin == WeaviateOutboxOrigin.LIVE) {
            when(searchableEntityWeaviateService.applyOutboxEntry(entry)).thenThrow(failure);
        }
        else {
            // Background rows are prepared first, which is where their failure is recorded
            when(searchableEntityWeaviateService.prepareOutboxEntry(entry)).thenThrow(failure);
        }
        dispatcher.drain();
    }

    private static WeaviateOutboxEntry entry(WeaviateOutboxOrigin origin) {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, origin);
        entry.setId(1L);
        return entry;
    }

    /**
     * The first check of a dispatcher is due one interval after its start; a drain in a test is meant to be checked.
     */
    private void makeTheFirstCheckDue() {
        ReflectionTestUtils.setField(dispatcher, "nextBacklogCheckAt", Instant.MIN);
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

        assertThat(errors()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("none recorded"));
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

        verify(outboxRepository, times(1)).findBacklogByOrigin(WeaviateOutboxOrigin.LIVE);
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
        when(outboxRepository.findBacklogByOrigin(WeaviateOutboxOrigin.LIVE)).thenThrow(new IllegalStateException("database unavailable"));

        dispatcher.monitorBacklog(Instant.now().plus(Duration.ofHours(1)));

        assertThat(logAppender.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void testMonitorBacklog_aFailureOfAReconcileRowIsNotBlamedForTheBacklog() {
        Instant now = Instant.now().plus(Duration.ofHours(1));
        queue(3, now.minus(Duration.ofMinutes(40)));
        failOneWrite(new IllegalStateException(READ_ONLY_FAILURE), WeaviateOutboxOrigin.RECONCILE_MISSING);

        dispatcher.monitorBacklog(now);

        assertThat(errors()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("none recorded").doesNotContain(READ_ONLY_FAILURE));
    }

    @Test
    void testMonitorBacklog_aFailureThatHasGoneIsNotBlamedForALaterBacklog() {
        failOneWrite(READ_ONLY_FAILURE);
        // The next write for a user action succeeds, so the failure is over
        WeaviateOutboxEntry written = entry(WeaviateOutboxOrigin.LIVE);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(written)).thenReturn(List.of());
        doReturn(Optional.empty()).when(searchableEntityWeaviateService).applyOutboxEntry(written);
        dispatcher.drain();

        Instant now = Instant.now().plus(Duration.ofHours(1));
        queue(3, now.minus(Duration.ofMinutes(40)));
        dispatcher.monitorBacklog(now);

        assertThat(errors()).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("none recorded").doesNotContain(READ_ONLY_FAILURE).doesNotContain("Free capacity"));
    }

    @Test
    void testMonitorBacklog_aWriteForAnotherEntityDoesNotHideTheFailure() {
        failOneWrite(READ_ONLY_FAILURE);
        WeaviateOutboxEntry other = WeaviateOutboxEntry.forUpsert(COURSE, 2L, WeaviateOutboxOrigin.LIVE);
        other.setId(2L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(other)).thenReturn(List.of());
        doReturn(Optional.empty()).when(searchableEntityWeaviateService).applyOutboxEntry(other);
        dispatcher.drain();

        Instant now = Instant.now().plus(Duration.ofHours(1));
        queue(1, now.minus(Duration.ofMinutes(40)));
        dispatcher.monitorBacklog(now);

        assertThat(errors()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains(READ_ONLY_FAILURE));
    }

    @Test
    void testMonitorBacklog_aFailureIsForgottenOnceTheQueueIsEmpty() {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        failOneWrite(READ_ONLY_FAILURE);
        // The failed row was superseded by a newer write, which does not confirm a row of its own
        queue(0, null);
        dispatcher.monitorBacklog(start);

        Instant later = start.plus(WeaviateOutboxDispatcher.BACKLOG_CHECK_INTERVAL);
        queue(3, later.minus(Duration.ofMinutes(40)));
        dispatcher.monitorBacklog(later);

        assertThat(errors()).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("none recorded").doesNotContain(READ_ONLY_FAILURE).doesNotContain("Free capacity"));
    }

    @Test
    void testMonitorBacklog_aFailureIsKeptWhileRowsAreQueuedBelowTheThresholds() {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        failOneWrite(READ_ONLY_FAILURE);
        queue(2, start.minus(Duration.ofMinutes(1)));
        dispatcher.monitorBacklog(start);

        Instant later = start.plus(WeaviateOutboxDispatcher.BACKLOG_CHECK_INTERVAL);
        queue(3, later.minus(Duration.ofMinutes(40)));
        dispatcher.monitorBacklog(later);

        assertThat(errors()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains(READ_ONLY_FAILURE));
    }

    @Test
    void testMonitorBacklog_aFailureWithoutAMessageIsNamedByItsType() {
        Instant now = Instant.now().plus(Duration.ofHours(1));
        queue(3, now.minus(Duration.ofMinutes(40)));
        failOneWrite(new IllegalStateException(), WeaviateOutboxOrigin.LIVE);

        dispatcher.monitorBacklog(now);

        assertThat(errors()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("Last failure: IllegalStateException."));
    }

    @Test
    void testDrain_looksAtTheBacklogBetweenFullBatches() {
        makeTheFirstCheckDue();
        queue(0, null);
        List<WeaviateOutboxEntry> fullBatch = IntStream.rangeClosed(1, 100).mapToObj(id -> {
            WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, (long) id, WeaviateOutboxOrigin.LIVE);
            entry.setId((long) id);
            return entry;
        }).toList();
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(fullBatch).thenReturn(List.of());
        when(outboxRepository.findDueBackgroundForDispatch(any(), anyInt())).thenReturn(List.of());

        dispatcher.drain();

        InOrder order = inOrder(outboxRepository);
        order.verify(outboxRepository).findDueLiveForDispatch(any(), anyInt());
        order.verify(outboxRepository).findBacklogByOrigin(WeaviateOutboxOrigin.LIVE);
        order.verify(outboxRepository).findDueLiveForDispatch(any(), anyInt());
    }

    @Test
    void testDrain_looksAtTheBacklogWhileTheIndexIsNotReady() throws Exception {
        makeTheFirstCheckDue();
        Instant now = Instant.now();
        queue(WeaviateOutboxDispatcher.BACKLOG_ALERT_SIZE, now.minus(Duration.ofMinutes(1)));
        when(rebuildService.ensureIndexMatchesConfiguration()).thenThrow(new IOException("Weaviate is not reachable"));

        dispatcher.drain();

        verify(outboxRepository, never()).findDueLiveForDispatch(any(), anyInt());
        assertThat(errors()).anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("backlog").contains("Weaviate is not reachable"));
    }

    @Test
    void testDrain_doesNotQueryTheBacklogBeforeTheFirstCheckInterval() {
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of());
        when(outboxRepository.findDueBackgroundForDispatch(any(), anyInt())).thenReturn(List.of());

        dispatcher.drain();

        verify(outboxRepository, never()).findBacklogByOrigin(any(WeaviateOutboxOrigin.class));
    }
}
