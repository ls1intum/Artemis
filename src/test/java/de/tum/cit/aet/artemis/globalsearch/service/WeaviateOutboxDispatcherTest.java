package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEmbeddingProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateMigrationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateOutboxProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;

/**
 * Unit tests for {@link WeaviateOutboxDispatcher}.
 * <p>
 * The dispatcher must apply a claimed row to Weaviate, refresh the sync ledger and delete the row on success,
 * keep the row with an incremented attempt count and a backed-off next attempt on failure, and process rows
 * in id order so multiple pending rows for the same entity apply latest-wins. The outcome writes are plain
 * repository calls, so the orchestration is exercised directly against the mocked repositories.
 * <p>
 * Writes of users ({@code LIVE} rows) always go before background work, which runs in small steps within a per-minute
 * budget; the dispatcher never waits for that budget.
 */
class WeaviateOutboxDispatcherTest {

    private static final String COURSE = SearchableEntitySchema.TypeValues.COURSE;

    private static final String POST = SearchableEntitySchema.TypeValues.POST;

    /** Stand-in content hash returned by the mocked service for a successful upsert (a real hash is 64 hex chars). */
    private static final String HASH = "a".repeat(64);

    private final WeaviateOutboxRepository outboxRepository = mock(WeaviateOutboxRepository.class);

    private final SearchableEntitySyncStateRepository syncStateRepository = mock(SearchableEntitySyncStateRepository.class);

    private final SearchableEntityWeaviateService searchableEntityWeaviateService = mock(SearchableEntityWeaviateService.class);

    private final SearchableEntityEmbeddingClient embeddingClient = mock(SearchableEntityEmbeddingClient.class);

    private final SearchableEntityIndexRebuildService rebuildService = mock(SearchableEntityIndexRebuildService.class);

    /** Checks the index at the first drain; retries a failed check after one second. */
    private static final WeaviateMigrationProperties CHECK_AT_ONCE = new WeaviateMigrationProperties(0, 5, 1);

    private WeaviateOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        // Construct with the production default tuning values.
        when(embeddingClient.maxInputsPerRequest()).thenReturn(32);
        dispatcher = dispatcher(searchableEntityWeaviateService, new WeaviateOutboxProperties(5, 100, 10, 300), DEFAULT_EMBEDDING_PROPERTIES);
    }

    private static final WeaviateEmbeddingProperties DEFAULT_EMBEDDING_PROPERTIES = new WeaviateEmbeddingProperties(600, 30, 32, Duration.ofSeconds(3), Duration.ofSeconds(60));

    private WeaviateOutboxDispatcher dispatcher(SearchableEntityWeaviateService service, WeaviateOutboxProperties outboxProperties,
            WeaviateEmbeddingProperties embeddingProperties) {
        var reconcileProperties = new WeaviateReconcileProperties(false, false, false, List.of(COURSE, "lecture", "lecture_unit", "exam", "exercise", "faq", "channel"), 500, 100,
                200, 1000, 5, 100, 100, 0.25);
        return new WeaviateOutboxDispatcher(outboxRepository, syncStateRepository, service, outboxProperties, reconcileProperties, embeddingProperties, embeddingClient,
                rebuildService, CHECK_AT_ONCE);
    }

    private SearchableEntityWeaviateService realService(WeaviateService weaviateService, SearchableEntityResolver resolver) {
        return new SearchableEntityWeaviateService(weaviateService, outboxRepository, resolver, JsonObjectMapper.get(), new SearchableEntityContentHasher(JsonObjectMapper.get()),
                mock(ApplicationEventPublisher.class), mock(SearchableEntityVectorWriter.class), embeddingClient);
    }

    @Test
    void testDrainUpsert_writesWeaviateRefreshesLedgerAndDeletesRow() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, WeaviateOutboxOrigin.LIVE);
        entry.setId(101L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry));
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 1L)).thenReturn(Optional.empty());
        when(searchableEntityWeaviateService.applyOutboxEntry(entry)).thenReturn(Optional.of(HASH));

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(entry);
        ArgumentCaptor<SearchableEntitySyncState> stateCaptor = ArgumentCaptor.forClass(SearchableEntitySyncState.class);
        verify(syncStateRepository).save(stateCaptor.capture());
        SearchableEntitySyncState state = stateCaptor.getValue();
        assertThat(state.getEntityType()).isEqualTo(COURSE);
        assertThat(state.getEntityId()).isEqualTo(1L);
        assertThat(state.getContentHash()).isEqualTo(HASH);
        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrainFailedWrite_keepsRowWithBackoffThenLaterRunSucceeds() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, WeaviateOutboxOrigin.LIVE);
        entry.setId(102L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry));
        doThrow(new RuntimeException("weaviate unavailable")).when(searchableEntityWeaviateService).applyOutboxEntry(entry);

        ZonedDateTime before = ZonedDateTime.now();
        dispatcher.drain();

        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(entry.getNextAttemptAt()).isAfter(before);
        verify(outboxRepository).save(entry);
        verify(outboxRepository, never()).delete(any());
        verify(syncStateRepository, never()).save(any());

        // Weaviate recovered: a later drain applies the same row and clears it.
        doReturn(Optional.of(HASH)).when(searchableEntityWeaviateService).applyOutboxEntry(entry);
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 1L)).thenReturn(Optional.empty());

        dispatcher.drain();

        verify(outboxRepository).delete(entry);
    }

    @ParameterizedTest
    @EnumSource(value = WeaviateOutboxOrigin.class, names = { "RECONCILE_DRIFT", "RECONCILE_ORPHAN" })
    void testDrainFailedReconcileDelete_keepsTheRowUnacknowledgedForRetry(WeaviateOutboxOrigin origin) {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forDeleteEntity(COURSE, 1L, origin);
        entry.setId(103L);
        when(outboxRepository.findDueBackgroundForDispatch(any(), anyInt())).thenReturn(List.of(entry), List.of());
        doThrow(new IllegalStateException("resolver unavailable")).when(searchableEntityWeaviateService).prepareOutboxEntry(entry);

        dispatcher.drain();

        assertThat(entry.getAttempts()).isEqualTo(1);
        verify(outboxRepository).save(entry);
        verify(outboxRepository, never()).delete(entry);
        verify(syncStateRepository, never()).save(any());
        verify(syncStateRepository, never()).deleteByEntityTypeAndEntityId(anyString(), anyLong());
    }

    @Test
    void testDrainMalformedBulkParamsLeavesTheRowForRetryInsteadOfAcknowledgingIt() {
        WeaviateOutboxDispatcher realDispatcher = dispatcher(realService(mock(WeaviateService.class), mock(SearchableEntityResolver.class)),
                new WeaviateOutboxProperties(5, 100, 10, 300), DEFAULT_EMBEDDING_PROPERTIES);
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forBulkDelete(WeaviateOutboxOperation.DELETE_ALL_FOR_COURSE, "{not-json", WeaviateOutboxOrigin.LIVE);
        entry.setId(104L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry), List.of());

        realDispatcher.drain();

        assertThat(entry.getAttempts()).isEqualTo(1);
        verify(outboxRepository).save(entry);
        verify(outboxRepository, never()).delete(entry);
        verify(syncStateRepository, never()).save(any());
        verify(syncStateRepository, never()).deleteByEntityTypeAndEntityId(anyString(), anyLong());
    }

    @Test
    void testDrainHashProviderFailureKeepsTheUpsertForRetryWithoutWritingOrAcknowledging() {
        WeaviateService weaviateService = mock(WeaviateService.class);
        SearchableEntityResolver resolver = mock(SearchableEntityResolver.class);
        WeaviateOutboxDispatcher realDispatcher = dispatcher(realService(weaviateService, resolver), new WeaviateOutboxProperties(5, 100, 10, 300), DEFAULT_EMBEDDING_PROPERTIES);
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, WeaviateOutboxOrigin.LIVE);
        entry.setId(105L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry), List.of());
        when(resolver.resolve(COURSE, 1L)).thenReturn(Optional.of(Map.of(SearchableEntitySchema.Properties.TYPE, COURSE, SearchableEntitySchema.Properties.ENTITY_ID, 1L)));

        try (MockedStatic<MessageDigest> messageDigest = mockStatic(MessageDigest.class, CALLS_REAL_METHODS)) {
            messageDigest.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(new NoSuchAlgorithmException("provider unavailable"));

            realDispatcher.drain();

            messageDigest.verify(() -> MessageDigest.getInstance("SHA-256"));
        }

        assertThat(entry.getAttempts()).isEqualTo(1);
        verify(outboxRepository).save(entry);
        verify(outboxRepository, never()).delete(entry);
        verify(outboxRepository, never()).deleteSupersededByEntity(anyString(), anyLong(), anyLong());
        verify(syncStateRepository, never()).save(any());
        verify(syncStateRepository, never()).deleteByEntityTypeAndEntityId(anyString(), anyLong());
        verify(resolver).resolve(COURSE, 1L);
        verify(weaviateService, never()).getCollection(any());
    }

    @Test
    void testDrain_processesRowsInIdOrderSoLatestWins() {
        WeaviateOutboxEntry older = WeaviateOutboxEntry.forUpsert(COURSE, 1L, WeaviateOutboxOrigin.LIVE);
        older.setId(106L);
        WeaviateOutboxEntry newer = WeaviateOutboxEntry.forUpsert(COURSE, 1L, WeaviateOutboxOrigin.LIVE);
        newer.setId(107L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(older, newer));
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 1L)).thenReturn(Optional.empty());
        when(searchableEntityWeaviateService.applyOutboxEntry(any())).thenReturn(Optional.of(HASH));

        dispatcher.drain();

        InOrder inOrder = inOrder(searchableEntityWeaviateService);
        inOrder.verify(searchableEntityWeaviateService).applyOutboxEntry(older);
        inOrder.verify(searchableEntityWeaviateService).applyOutboxEntry(newer);
        verify(outboxRepository).delete(older);
        verify(outboxRepository).delete(newer);
    }

    @Test
    void testDrainDeleteEntity_clearsSyncLedgerRow() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forDeleteEntity(COURSE, 1L, WeaviateOutboxOrigin.LIVE);
        entry.setId(108L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry));

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(entry);
        verify(syncStateRepository).deleteByEntityTypeAndEntityId(COURSE, 1L);
        verify(syncStateRepository, never()).save(any());
        verify(outboxRepository).delete(entry);
    }

    /**
     * Regression test for a ledger that only ever grows: post and answer post are excluded from every reconcile
     * pass ({@code WeaviateReconcileProperties.entityTypes}), so nothing reads their ledger rows back, and a bulk
     * delete has no entity id to clean one up with. The dispatcher must not write a row for them at all, rather
     * than writing one a bulk delete then permanently fails to remove.
     */
    @Test
    void testDrainUpsertOfAnUnmanagedType_neverTouchesTheLedger() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(POST, 1L, WeaviateOutboxOrigin.LIVE);
        entry.setId(109L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry));
        when(searchableEntityWeaviateService.applyOutboxEntry(entry)).thenReturn(Optional.of(HASH));

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(entry);
        verify(syncStateRepository, never()).findByEntityTypeAndEntityId(anyString(), anyLong());
        verify(syncStateRepository, never()).save(any());
        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrainDeleteEntityOfAnUnmanagedType_neverTouchesTheLedger() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forDeleteEntity(POST, 1L, WeaviateOutboxOrigin.LIVE);
        entry.setId(110L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry));

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(entry);
        verify(syncStateRepository, never()).deleteByEntityTypeAndEntityId(anyString(), anyLong());
        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrainPerEntitySuccess_collapsesOlderRowsForSameEntity() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 42L, WeaviateOutboxOrigin.LIVE);
        entry.setId(10L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry));
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 42L)).thenReturn(Optional.empty());
        when(searchableEntityWeaviateService.applyOutboxEntry(entry)).thenReturn(Optional.of(HASH));

        dispatcher.drain();

        verify(outboxRepository).deleteSupersededByEntity(COURSE, 42L, 10L);
        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrainBulkDelete_doesNotCollapse() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forBulkDelete(WeaviateOutboxOperation.DELETE_ALL_FOR_COURSE, "{\"courseId\":7}", WeaviateOutboxOrigin.LIVE);
        entry.setId(10L);
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenReturn(List.of(entry));

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(entry);
        verify(outboxRepository, never()).deleteSupersededByEntity(anyString(), anyLong(), anyLong());
        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrainBackedOffOlderRow_cannotOverwriteNewerRow() {
        // An older upsert is deferred by backoff (as after a transient failure) while a newer upsert for the
        // same course is due. The newer value must win permanently, even once the older row's backoff elapses.
        Map<String, String> weaviate = new HashMap<>();
        List<WeaviateOutboxEntry> table = new ArrayList<>();
        wireInMemoryOutbox(table, weaviate);

        WeaviateOutboxEntry older = WeaviateOutboxEntry.forUpsert(COURSE, 42L, WeaviateOutboxOrigin.LIVE);
        older.setId(5L);
        older.setNextAttemptAt(ZonedDateTime.now().plusMinutes(1));
        WeaviateOutboxEntry newer = WeaviateOutboxEntry.forUpsert(COURSE, 42L, WeaviateOutboxOrigin.LIVE);
        newer.setId(10L);
        table.add(older);
        table.add(newer);

        dispatcher.drain();

        // Make any surviving row eligible: without the collapse the older row would now overwrite the newer one.
        table.forEach(entry -> entry.setNextAttemptAt(ZonedDateTime.now().minusMinutes(1)));
        dispatcher.drain();

        // The stored value is the id of the row that wrote it; the newer row (id 10) must remain the final writer.
        assertThat(weaviate).containsEntry(COURSE + ":42", "10");
        assertThat(table).isEmpty();
    }

    @Test
    void testDrain_nudgeDuringShortBatchDrainsNewlyEnqueuedRowWithoutWaitingForScheduledDrain() throws Exception {
        // A short batch stops after the first row. While that row is being applied, another thread commits a new
        // row and sends its after-commit nudge. The nudge must not be lost merely because the first drain owns the lock.
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        Map<String, String> weaviate = new HashMap<>();
        wireInMemoryOutbox(table, weaviate);
        dispatcher = dispatcher(searchableEntityWeaviateService, new WeaviateOutboxProperties(5, 2, 10, 300), DEFAULT_EMBEDDING_PROPERTIES);

        WeaviateOutboxEntry first = WeaviateOutboxEntry.forUpsert(COURSE, 1L, WeaviateOutboxOrigin.LIVE);
        first.setId(1L);
        WeaviateOutboxEntry second = WeaviateOutboxEntry.forUpsert(COURSE, 2L, WeaviateOutboxOrigin.LIVE);
        second.setId(2L);
        table.add(first);

        CountDownLatch firstApplyStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstApply = new CountDownLatch(1);
        AtomicInteger activeApplies = new AtomicInteger();
        AtomicInteger maximumConcurrentApplies = new AtomicInteger();
        doAnswer(invocation -> {
            WeaviateOutboxEntry entry = invocation.getArgument(0);
            int active = activeApplies.incrementAndGet();
            maximumConcurrentApplies.accumulateAndGet(active, Math::max);
            try {
                if (entry == first) {
                    firstApplyStarted.countDown();
                    if (!releaseFirstApply.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test did not release the first outbox apply");
                    }
                }
                weaviate.put(entry.getEntityType() + ":" + entry.getEntityId(), String.valueOf(entry.getId()));
                return Optional.of(HASH);
            }
            finally {
                activeApplies.decrementAndGet();
            }
        }).when(searchableEntityWeaviateService).applyOutboxEntry(any(WeaviateOutboxEntry.class));

        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> drain = executor.submit(dispatcher::drain);
            assertThat(firstApplyStarted.await(5, TimeUnit.SECONDS)).isTrue();

            table.add(second);
            dispatcher.onOutboxEnqueued(new WeaviateOutboxEnqueuedEvent());
            releaseFirstApply.countDown();

            drain.get(5, TimeUnit.SECONDS);
        }

        assertThat(weaviate).containsEntry(COURSE + ":1", "1").containsEntry(COURSE + ":2", "2");
        assertThat(table).isEmpty();
        assertThat(maximumConcurrentApplies.get()).isOne();
        verify(outboxRepository).delete(first);
        verify(outboxRepository).delete(second);
    }

    @Test
    void testDrain_databaseReadFailureDoesNotSpinOnConcurrentNudge() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                Future<?> nudge = executor.submit(() -> dispatcher.onOutboxEnqueued(new WeaviateOutboxEnqueuedEvent()));
                nudge.get(5, TimeUnit.SECONDS);
                throw new IllegalStateException("database unavailable");
            }).when(outboxRepository).findDueLiveForDispatch(any(), anyInt());

            dispatcher.drain();
        }

        verify(outboxRepository, times(1)).findDueLiveForDispatch(any(), anyInt());
        verify(searchableEntityWeaviateService, never()).applyOutboxEntry(any());
    }

    @Test
    void testDrain_rebuildsTheIndexUnderTheDrainLockBeforeTheFirstWrite() throws Exception {
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        Map<String, String> weaviate = new HashMap<>();
        wireInMemoryOutbox(table, weaviate);
        table.add(entry(1L, COURSE, 1L, WeaviateOutboxOrigin.LIVE));

        CountDownLatch rebuildRunning = new CountDownLatch(1);
        CountDownLatch releaseRebuild = new CountDownLatch(1);
        when(rebuildService.ensureIndexMatchesConfiguration()).thenAnswer(_ -> {
            rebuildRunning.countDown();
            if (!releaseRebuild.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release the rebuild");
            }
            return true;
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> firstDrain = executor.submit(dispatcher::drain);
            assertThat(rebuildRunning.await(5, TimeUnit.SECONDS)).isTrue();

            // A nudge while the collection is replaced writes nothing; the drain holding the lock picks the row up afterwards.
            dispatcher.onOutboxEnqueued(new WeaviateOutboxEnqueuedEvent());
            verify(searchableEntityWeaviateService, never()).applyOutboxEntry(any());

            releaseRebuild.countDown();
            firstDrain.get(5, TimeUnit.SECONDS);
        }

        assertThat(weaviate).containsEntry(COURSE + ":1", "1");
        assertThat(table).isEmpty();
        verify(rebuildService).ensureIndexMatchesConfiguration();
    }

    @Test
    void testDrain_aFailedIndexCheckKeepsEveryWriteQueuedAndIsRetriedAfterTheDelay() throws Exception {
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        Map<String, String> weaviate = new HashMap<>();
        wireInMemoryOutbox(table, weaviate);
        table.add(entry(1L, COURSE, 1L, WeaviateOutboxOrigin.LIVE));
        when(rebuildService.ensureIndexMatchesConfiguration()).thenThrow(new IOException("connection refused")).thenReturn(false);

        dispatcher.drain();
        // Within the retry delay, a drain does not ask Weaviate again.
        dispatcher.drain();

        verify(rebuildService, times(1)).ensureIndexMatchesConfiguration();
        verify(outboxRepository, never()).findDueLiveForDispatch(any(), anyInt());
        assertThat(table).hasSize(1);

        Thread.sleep(1_100);
        dispatcher.drain();

        verify(rebuildService, times(2)).ensureIndexMatchesConfiguration();
        assertThat(weaviate).containsEntry(COURSE + ":1", "1");
        assertThat(table).isEmpty();
    }

    /**
     * Backs the outbox and Weaviate mocks with in-memory state so a full drain can be exercised end to end.
     */
    private void wireInMemoryOutbox(List<WeaviateOutboxEntry> table, Map<String, String> weaviate) {
        when(outboxRepository.findDueLiveForDispatch(any(), anyInt())).thenAnswer(invocation -> due(table, invocation.getArgument(0), invocation.getArgument(1), true));
        when(outboxRepository.findDueBackgroundForDispatch(any(), anyInt())).thenAnswer(invocation -> due(table, invocation.getArgument(0), invocation.getArgument(1), false));
        doAnswer(invocation -> {
            table.remove(invocation.<WeaviateOutboxEntry>getArgument(0));
            return null;
        }).when(outboxRepository).delete(any(WeaviateOutboxEntry.class));
        doAnswer(invocation -> {
            String type = invocation.getArgument(0);
            Long entityId = invocation.getArgument(1);
            Long appliedId = invocation.getArgument(2);
            table.removeIf(entry -> type.equals(entry.getEntityType()) && entityId.equals(entry.getEntityId()) && entry.getId() < appliedId);
            return null;
        }).when(outboxRepository).deleteSupersededByEntity(anyString(), anyLong(), anyLong());
        doAnswer(invocation -> {
            WeaviateOutboxEntry entry = invocation.getArgument(0);
            String key = entry.getEntityType() + ":" + entry.getEntityId();
            if (entry.getOperation() == WeaviateOutboxOperation.UPSERT) {
                // Stand in for the re-derived write: record which row (by id) last wrote this entity.
                weaviate.put(key, String.valueOf(entry.getId()));
                return Optional.of(HASH);
            }
            else if (entry.getOperation() == WeaviateOutboxOperation.DELETE_ENTITY) {
                weaviate.remove(key);
            }
            return Optional.empty();
        }).when(searchableEntityWeaviateService).applyOutboxEntry(any(WeaviateOutboxEntry.class));
        doAnswer(invocation -> new SearchableEntityWeaviateService.PreparedOther(invocation.getArgument(0))).when(searchableEntityWeaviateService)
                .prepareOutboxEntry(any(WeaviateOutboxEntry.class));
        doAnswer(invocation -> {
            SearchableEntityWeaviateService.PreparedEntry prepared = invocation.getArgument(0);
            weaviate.put(prepared.entry().getEntityType() + ":" + prepared.entry().getEntityId(), String.valueOf(prepared.entry().getId()));
            return Optional.of(HASH);
        }).when(searchableEntityWeaviateService).applyPrepared(any(), any());
        when(syncStateRepository.findByEntityTypeAndEntityId(anyString(), any())).thenReturn(Optional.empty());
    }

    private static List<WeaviateOutboxEntry> due(List<WeaviateOutboxEntry> table, ZonedDateTime now, int limit, boolean live) {
        return table.stream().filter(entry -> !entry.getNextAttemptAt().isAfter(now)).filter(entry -> (entry.getOrigin() == WeaviateOutboxOrigin.LIVE) == live)
                .sorted(Comparator.comparing(WeaviateOutboxEntry::getId)).limit(limit).toList();
    }

    private static WeaviateOutboxEntry entry(long id, String type, long entityId, WeaviateOutboxOrigin origin) {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(type, entityId, origin);
        entry.setId(id);
        return entry;
    }

    /**
     * Prepares background upserts with the given chunks still to embed (none reused) and records every embedding request.
     */
    private List<List<String>> wirePreparedUpserts(Map<Long, List<String>> chunksByEntryId) {
        doAnswer(invocation -> {
            WeaviateOutboxEntry entry = invocation.getArgument(0);
            List<String> chunks = chunksByEntryId.getOrDefault(entry.getId(), List.of());
            var row = new SearchableEntityVectorWriter.PreparedRow("uuid-" + entry.getId(), Map.of(), chunks, new float[chunks.size()][]);
            return new SearchableEntityWeaviateService.PreparedUpsert(entry, row, HASH);
        }).when(searchableEntityWeaviateService).prepareOutboxEntry(any(WeaviateOutboxEntry.class));
        List<List<String>> requests = new CopyOnWriteArrayList<>();
        when(embeddingClient.embedDocuments(any())).thenAnswer(invocation -> {
            List<String> inputs = List.copyOf(invocation.getArgument(0));
            requests.add(inputs);
            return inputs.stream().map(_ -> new float[] { 1f, 0f }).toList();
        });
        return requests;
    }

    @Test
    void testDrainBeforeTheInitialDelay_neitherChecksTheIndexNorWrites() throws Exception {
        var notReady = new WeaviateOutboxDispatcher(outboxRepository, syncStateRepository, searchableEntityWeaviateService, new WeaviateOutboxProperties(5, 100, 10, 300),
                new WeaviateReconcileProperties(false, false, false, List.of(COURSE), 500, 100, 200, 1000, 5, 100, 100, 0.25), DEFAULT_EMBEDDING_PROPERTIES, embeddingClient,
                rebuildService, new WeaviateMigrationProperties(30, 5, 120));

        notReady.drain();

        verify(rebuildService, never()).ensureIndexMatchesConfiguration();
        verify(outboxRepository, never()).findDueLiveForDispatch(any(), anyInt());
        verify(outboxRepository, never()).findDueBackgroundForDispatch(any(), anyInt());
    }

    @Test
    void testDrain_appliesWritesOfUsersBeforeBackgroundWork() {
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        Map<String, String> weaviate = new HashMap<>();
        wireInMemoryOutbox(table, weaviate);
        WeaviateOutboxEntry background = entry(1L, COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        WeaviateOutboxEntry live = entry(2L, COURSE, 2L, WeaviateOutboxOrigin.LIVE);
        table.add(background);
        table.add(live);

        dispatcher.drain();

        InOrder inOrder = inOrder(searchableEntityWeaviateService);
        inOrder.verify(searchableEntityWeaviateService).applyOutboxEntry(live);
        inOrder.verify(searchableEntityWeaviateService).applyPrepared(any(), any());
        assertThat(weaviate).containsEntry(COURSE + ":1", "1").containsEntry(COURSE + ":2", "2");
        assertThat(table).isEmpty();
    }

    @Test
    void testDrain_skipsABackgroundRowWhoseEntityHadANewerWriteInTheSameTurn() {
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        Map<String, String> weaviate = new HashMap<>();
        wireInMemoryOutbox(table, weaviate);
        // The reconcile row is older than the user's edit, but the edit is applied first; the reconcile row must not
        // overwrite it afterwards.
        WeaviateOutboxEntry staleReconcile = entry(1L, COURSE, 7L, WeaviateOutboxOrigin.RECONCILE_DRIFT);
        WeaviateOutboxEntry userEdit = entry(2L, COURSE, 7L, WeaviateOutboxOrigin.LIVE);
        table.add(staleReconcile);
        table.add(userEdit);
        // The collapse would also remove the older row; disable it to exercise the in-memory guard on its own.
        doAnswer(_ -> null).when(outboxRepository).deleteSupersededByEntity(anyString(), anyLong(), anyLong());

        dispatcher.drain();

        verify(searchableEntityWeaviateService, never()).prepareOutboxEntry(staleReconcile);
        verify(searchableEntityWeaviateService, never()).applyPrepared(any(), any());
        assertThat(weaviate).containsEntry(COURSE + ":7", "2");
        assertThat(table).isEmpty();
    }

    @Test
    void testBackgroundStep_embedsTheChunksOfSeveralRowsTogetherAndSplitsByTheInputLimit() {
        when(embeddingClient.maxInputsPerRequest()).thenReturn(2);
        dispatcher = dispatcher(searchableEntityWeaviateService, new WeaviateOutboxProperties(5, 100, 10, 300), DEFAULT_EMBEDDING_PROPERTIES);
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        wireInMemoryOutbox(table, new HashMap<>());
        table.add(entry(1L, COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING));
        table.add(entry(2L, COURSE, 2L, WeaviateOutboxOrigin.RECONCILE_MISSING));
        // Row 2 shares chunk "b" with row 1, which is embedded only once.
        List<List<String>> requests = wirePreparedUpserts(Map.of(1L, List.of("a", "b", "c"), 2L, List.of("b", "d", "e")));

        dispatcher.drain();

        assertThat(requests).containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e"));
        ArgumentCaptor<Map<String, float[]>> embedded = ArgumentCaptor.captor();
        verify(searchableEntityWeaviateService, times(2)).applyPrepared(any(), embedded.capture());
        assertThat(embedded.getValue()).containsOnlyKeys("a", "b", "c", "d", "e");
        assertThat(table).isEmpty();
    }

    @Test
    void testBackgroundStep_leavesRowsUntouchedWhenTheRequestBudgetIsUsedUpAndStillAppliesWritesOfUsers() {
        // One embedding request per minute, one input per request: the first row's chunk uses the whole budget.
        when(embeddingClient.maxInputsPerRequest()).thenReturn(1);
        dispatcher = dispatcher(searchableEntityWeaviateService, new WeaviateOutboxProperties(5, 100, 10, 300),
                new WeaviateEmbeddingProperties(600, 1, 1, Duration.ofSeconds(3), Duration.ofSeconds(60)));
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        Map<String, String> weaviate = new HashMap<>();
        wireInMemoryOutbox(table, weaviate);
        WeaviateOutboxEntry first = entry(1L, COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        WeaviateOutboxEntry second = entry(2L, COURSE, 2L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        table.add(first);
        table.add(second);
        List<List<String>> requests = wirePreparedUpserts(Map.of(1L, List.of("a"), 2L, List.of("b")));

        long start = System.nanoTime();
        dispatcher.drain();

        assertThat(requests).containsExactly(List.of("a"));
        assertThat(table).containsExactly(second);
        assertThat(second.getAttempts()).as("a row left for later is not a failure").isZero();
        verify(outboxRepository, never()).save(second);

        // A user's write that arrives while the budget is used up is applied right away.
        WeaviateOutboxEntry live = entry(3L, COURSE, 3L, WeaviateOutboxOrigin.LIVE);
        table.add(live);
        dispatcher.onOutboxEnqueued(new WeaviateOutboxEnqueuedEvent());

        assertThat(weaviate).containsEntry(COURSE + ":3", "3");
        assertThat(table).containsExactly(second);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).as("the dispatcher never waits for the budget").isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void testBackgroundStep_aRowNeedingMoreRequestsThanTheBudgetHoldsCompletesOverSeveralStepsWithoutRepeatingRequests() throws Exception {
        // 600 requests per minute (10 per second), one chunk per request, and a row of 605 chunks: the first step uses the
        // whole budget, the next one finishes the row with the vectors kept from the first.
        when(embeddingClient.maxInputsPerRequest()).thenReturn(1);
        dispatcher = dispatcher(searchableEntityWeaviateService, new WeaviateOutboxProperties(5, 100, 10, 300),
                new WeaviateEmbeddingProperties(600, 600, 1, Duration.ofSeconds(3), Duration.ofSeconds(60)));
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        wireInMemoryOutbox(table, new HashMap<>());
        WeaviateOutboxEntry entry = entry(1L, COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        table.add(entry);
        List<String> chunks = java.util.stream.IntStream.range(0, 605).mapToObj(i -> "chunk " + i).toList();
        List<List<String>> requests = wirePreparedUpserts(Map.of(1L, chunks));

        dispatcher.drain();

        // The budget refills while the first step runs, so the exact number depends on the speed of the machine.
        assertThat(requests).hasSizeGreaterThanOrEqualTo(600);
        if (requests.size() < 605) {
            assertThat(table).as("not complete yet, and not a failure").containsExactly(entry);
            Thread.sleep(1_100);
            dispatcher.drain();
        }
        assertThat(entry.getAttempts()).isZero();

        assertThat(requests).hasSize(605);
        assertThat(requests.stream().flatMap(List::stream).distinct()).hasSize(605);
        assertThat(table).isEmpty();
    }

    @Test
    void testBackgroundStep_appliesWritesOfUsersBetweenTwoEmbeddingRequests() {
        when(embeddingClient.maxInputsPerRequest()).thenReturn(1);
        dispatcher = dispatcher(searchableEntityWeaviateService, new WeaviateOutboxProperties(5, 100, 10, 300), DEFAULT_EMBEDDING_PROPERTIES);
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        Map<String, String> weaviate = new HashMap<>();
        wireInMemoryOutbox(table, weaviate);
        table.add(entry(1L, COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING));
        wirePreparedUpserts(Map.of(1L, List.of("a", "b")));
        WeaviateOutboxEntry live = entry(2L, COURSE, 2L, WeaviateOutboxOrigin.LIVE);
        // A user edits something while the first request of the background row is running.
        doAnswer(invocation -> {
            List<String> inputs = invocation.getArgument(0);
            if (inputs.equals(List.of("a"))) {
                table.add(live);
            }
            return inputs.stream().map(_ -> new float[] { 1f, 0f }).toList();
        }).when(embeddingClient).embedDocuments(any());

        dispatcher.drain();

        InOrder inOrder = inOrder(embeddingClient, searchableEntityWeaviateService);
        inOrder.verify(embeddingClient).embedDocuments(List.of("a"));
        inOrder.verify(searchableEntityWeaviateService).applyOutboxEntry(live);
        inOrder.verify(embeddingClient).embedDocuments(List.of("b"));
        inOrder.verify(searchableEntityWeaviateService).applyPrepared(any(), any());
        assertThat(table).isEmpty();
    }

    @Test
    void testBackgroundStep_aRowSupersededByAUserDeleteIsNotRetriedWhenItsEmbeddingFailed() {
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        Map<String, String> weaviate = new HashMap<>();
        wireInMemoryOutbox(table, weaviate);
        WeaviateOutboxEntry background = entry(1L, COURSE, 7L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        table.add(background);
        wirePreparedUpserts(Map.of(1L, List.of("a")));
        WeaviateOutboxEntry userDelete = WeaviateOutboxEntry.forDeleteEntity(COURSE, 7L, WeaviateOutboxOrigin.LIVE);
        userDelete.setId(2L);
        // The embedding request fails, and meanwhile a user deletes the same entity.
        doAnswer(_ -> {
            table.add(userDelete);
            throw new IllegalStateException("embedding endpoint unavailable");
        }).when(embeddingClient).embedDocuments(any());

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(userDelete);
        verify(outboxRepository, never()).save(background);
        assertThat(background.getAttempts()).isZero();
        assertThat(table).isEmpty();
    }

    @Test
    void testBackgroundStep_aRowWhoseVectorsAreAllReusedNeedsNoEmbeddingRequest() {
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        wireInMemoryOutbox(table, new HashMap<>());
        table.add(entry(1L, COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_DRIFT));
        wirePreparedUpserts(Map.of(1L, List.of()));

        dispatcher.drain();

        verify(embeddingClient, never()).embedDocuments(any());
        verify(searchableEntityWeaviateService).applyPrepared(any(), any());
        assertThat(table).isEmpty();
    }

    @Test
    void testBackgroundStep_aFailedEmbeddingRequestRetriesItsRowsWithBackoff() {
        List<WeaviateOutboxEntry> table = new CopyOnWriteArrayList<>();
        wireInMemoryOutbox(table, new HashMap<>());
        WeaviateOutboxEntry entry = entry(1L, COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        table.add(entry);
        wirePreparedUpserts(Map.of(1L, List.of("a")));
        doThrow(new IllegalStateException("embedding endpoint unavailable")).when(embeddingClient).embedDocuments(any());
        doThrow(new IllegalStateException("chunk without vector")).when(searchableEntityWeaviateService).applyPrepared(any(), any());

        dispatcher.drain();

        assertThat(entry.getAttempts()).isEqualTo(1);
        verify(outboxRepository).save(entry);
        assertThat(table).containsExactly(entry);
    }
}
