package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
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
    void drain_upsert_writesWeaviate_refreshesSyncState_deletesRow() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, "{\"entity_id\":1,\"type\":\"course\"}");
        when(outboxRepository.claimBatchForDispatch(any(), anyInt())).thenReturn(List.of(entry));
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
    void drain_failedWrite_keepsRowWithIncrementedAttemptsAndBackoff_thenLaterRunSucceeds() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forUpsert(COURSE, 1L, "{\"entity_id\":1}");
        when(outboxRepository.claimBatchForDispatch(any(), anyInt())).thenReturn(List.of(entry));
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
    void drain_processesRowsInIdOrder_soLatestWins() {
        WeaviateOutboxEntry older = WeaviateOutboxEntry.forUpsert(COURSE, 1L, "{\"title\":\"old\"}");
        WeaviateOutboxEntry newer = WeaviateOutboxEntry.forUpsert(COURSE, 1L, "{\"title\":\"new\"}");
        when(outboxRepository.claimBatchForDispatch(any(), anyInt())).thenReturn(List.of(older, newer));
        when(syncStateRepository.findByEntityTypeAndEntityId(COURSE, 1L)).thenReturn(Optional.empty());

        dispatcher.drain();

        InOrder inOrder = inOrder(searchableEntityWeaviateService);
        inOrder.verify(searchableEntityWeaviateService).applyOutboxEntry(older);
        inOrder.verify(searchableEntityWeaviateService).applyOutboxEntry(newer);
        verify(outboxRepository).delete(older);
        verify(outboxRepository).delete(newer);
    }

    @Test
    void drain_deleteEntity_clearsSyncLedgerRow() {
        WeaviateOutboxEntry entry = WeaviateOutboxEntry.forDeleteEntity(COURSE, 1L);
        when(outboxRepository.claimBatchForDispatch(any(), anyInt())).thenReturn(List.of(entry));

        dispatcher.drain();

        verify(searchableEntityWeaviateService).applyOutboxEntry(entry);
        verify(syncStateRepository).deleteByEntityTypeAndEntityId(COURSE, 1L);
        verify(syncStateRepository, never()).save(any());
        verify(outboxRepository).delete(entry);
    }
}
