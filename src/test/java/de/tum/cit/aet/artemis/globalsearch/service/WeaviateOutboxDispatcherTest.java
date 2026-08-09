package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;

/**
 * Unit tests for {@link WeaviateOutboxDispatcher}.
 * <p>
 * The dispatcher must apply a claimed row to Weaviate, refresh the sync ledger and delete the row on success,
 * keep the row with an incremented attempt count and a backed-off next attempt on failure, and process rows
 * in id order so multiple pending rows for the same entity apply latest-wins. A real {@code TransactionTemplate}
 * backed by a mock transaction manager runs the drain callbacks so the orchestration is exercised directly.
 */
class WeaviateOutboxDispatcherTest {

    private static final String COURSE = SearchableEntitySchema.TypeValues.COURSE;

    private final WeaviateOutboxRepository outboxRepository = mock(WeaviateOutboxRepository.class);

    private final SearchableEntitySyncStateRepository syncStateRepository = mock(SearchableEntitySyncStateRepository.class);

    private final SearchableEntityWeaviateService searchableEntityWeaviateService = mock(SearchableEntityWeaviateService.class);

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private WeaviateOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new WeaviateOutboxDispatcher(outboxRepository, syncStateRepository, searchableEntityWeaviateService, transactionManager);
    }

    @Test
    void testDrainUpsert_writesWeaviateRefreshesLedgerAndDeletesRow() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, "{\"entity_id\":1,\"type\":\"course\"}");
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenReturn(List.of(entry));
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 1L)).thenReturn(Optional.empty());

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(entry);
        ArgumentCaptor<SearchableEntitySyncState> stateCaptor = ArgumentCaptor.forClass(SearchableEntitySyncState.class);
        verify(syncStateRepository).save(stateCaptor.capture());
        SearchableEntitySyncState state = stateCaptor.getValue();
        assertThat(state.getEntityType()).isEqualTo(COURSE);
        assertThat(state.getEntityId()).isEqualTo(1L);
        assertThat(state.getContentHash()).hasSize(64);
        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrainFailedWrite_keepsRowWithBackoffThenLaterRunSucceeds() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, "{\"entity_id\":1}");
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenReturn(List.of(entry));
        doThrow(new RuntimeException("weaviate unavailable")).when(searchableEntityWeaviateService).applyOutboxEntry(entry);

        ZonedDateTime before = ZonedDateTime.now();
        dispatcher.drain();

        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(entry.getNextAttemptAt()).isAfter(before);
        verify(outboxRepository).save(entry);
        verify(outboxRepository, never()).delete(any());
        verify(syncStateRepository, never()).save(any());

        // Weaviate recovered: a later drain applies the same row and clears it.
        doNothing().when(searchableEntityWeaviateService).applyOutboxEntry(entry);
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 1L)).thenReturn(Optional.empty());

        dispatcher.drain();

        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrain_processesRowsInIdOrderSoLatestWins() {
        WeaviateOutboxEntry older = WeaviateOutboxEntry.forUpsert(COURSE, 1L, "{\"title\":\"old\"}");
        WeaviateOutboxEntry newer = WeaviateOutboxEntry.forUpsert(COURSE, 1L, "{\"title\":\"new\"}");
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenReturn(List.of(older, newer));
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 1L)).thenReturn(Optional.empty());

        dispatcher.drain();

        InOrder inOrder = inOrder(searchableEntityWeaviateService);
        inOrder.verify(searchableEntityWeaviateService).applyOutboxEntry(older);
        inOrder.verify(searchableEntityWeaviateService).applyOutboxEntry(newer);
        verify(outboxRepository).delete(older);
        verify(outboxRepository).delete(newer);
    }

    @Test
    void testDrainDeleteEntity_clearsSyncLedgerRow() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forDeleteEntity(COURSE, 1L);
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenReturn(List.of(entry));

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(entry);
        verify(syncStateRepository).deleteByEntityTypeAndEntityId(COURSE, 1L);
        verify(syncStateRepository, never()).save(any());
        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrainPerEntitySuccess_collapsesOlderRowsForSameEntity() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 42L, "{\"title\":\"new\"}");
        entry.setId(10L);
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenReturn(List.of(entry));
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 42L)).thenReturn(Optional.empty());

        dispatcher.drain();

        verify(outboxRepository).deleteSupersededByEntity(COURSE, 42L, 10L);
        verify(outboxRepository).delete(entry);
    }

    @Test
    void testDrainBulkDelete_doesNotCollapse() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forBulkDelete(WeaviateOutboxOperation.DELETE_ALL_FOR_COURSE, "{\"courseId\":7}");
        entry.setId(10L);
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenReturn(List.of(entry));

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

        WeaviateOutboxEntry older = WeaviateOutboxEntry.forUpsert(COURSE, 42L, "old");
        older.setId(5L);
        older.setNextAttemptAt(ZonedDateTime.now().plusMinutes(1));
        WeaviateOutboxEntry newer = WeaviateOutboxEntry.forUpsert(COURSE, 42L, "new");
        newer.setId(10L);
        table.add(older);
        table.add(newer);

        dispatcher.drain();

        // Make any surviving row eligible: without the collapse the older row would now overwrite the newer one.
        table.forEach(entry -> entry.setNextAttemptAt(ZonedDateTime.now().minusMinutes(1)));
        dispatcher.drain();

        assertThat(weaviate).containsEntry(COURSE + ":42", "new");
        assertThat(table).isEmpty();
    }

    /**
     * Backs the outbox and Weaviate mocks with in-memory state so a full drain can be exercised end to end.
     */
    private void wireInMemoryOutbox(List<WeaviateOutboxEntry> table, Map<String, String> weaviate) {
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenAnswer(invocation -> {
            ZonedDateTime now = invocation.getArgument(0);
            int limit = invocation.getArgument(1);
            return table.stream().filter(entry -> !entry.getNextAttemptAt().isAfter(now)).sorted(Comparator.comparing(WeaviateOutboxEntry::getId)).limit(limit).toList();
        });
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
                weaviate.put(key, entry.getPayload());
            }
            else if (entry.getOperation() == WeaviateOutboxOperation.DELETE_ENTITY) {
                weaviate.remove(key);
            }
            return null;
        }).when(searchableEntityWeaviateService).applyOutboxEntry(any(WeaviateOutboxEntry.class));
        when(syncStateRepository.findByEntityTypeAndEntityId(anyString(), any())).thenReturn(Optional.empty());
    }
}
