package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityIndexScanService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityIndexScanService.IndexScanSlice;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityIndexScanService.IndexedRow;

class SearchableEntityOrphanSweepTest {

    private static final String COURSE = "course";

    private static final String POST = "post";

    private static final String LECTURE = "lecture";

    private static final int DELETE_CAP = 3;

    private final SearchableEntityIndexScanService indexScanService = mock(SearchableEntityIndexScanService.class);

    private final SearchableEntityIdEnumerator idEnumerator = mock(SearchableEntityIdEnumerator.class);

    private final SearchableEntitySyncStateRepository syncStateRepository = mock(SearchableEntitySyncStateRepository.class);

    private final SearchableEntityReconcileStateRepository reconcileStateRepository = mock(SearchableEntityReconcileStateRepository.class);

    private final ReconcileEnqueueService enqueueService = mock(ReconcileEnqueueService.class);

    private SearchableEntityOrphanSweep sweep;

    private static IndexedRow row(String entityType, long entityId, String contentHash) {
        return new IndexedRow("uuid-" + entityType + "-" + entityId, entityType, entityId, entityId, contentHash);
    }

    private static SearchableEntitySyncState ledgerRow(String entityType, long entityId, String contentHash) {
        return new SearchableEntitySyncState(entityType, entityId, contentHash, ZonedDateTime.now());
    }

    private void configure(double abortRatio, String... types) {
        var properties = new WeaviateReconcileProperties(true, true, true, List.of(types), 500, 5000, 200, 10, 1, DELETE_CAP, abortRatio);
        sweep = new SearchableEntityOrphanSweep(indexScanService, idEnumerator, syncStateRepository, reconcileStateRepository, enqueueService, properties);
    }

    private void scanReturns(IndexedRow... rows) {
        when(indexScanService.scanFrom(any(), anyInt(), anyInt())).thenReturn(new IndexScanSlice(List.of(rows), "next-cursor"));
    }

    @BeforeEach
    void setUp() {
        when(enqueueService.canEnqueue()).thenReturn(true);
        when(enqueueService.enqueueDelete(anyString(), anyLong(), any())).thenReturn(true);
        when(enqueueService.enqueueUpsert(anyString(), anyLong(), any())).thenReturn(true);
        when(reconcileStateRepository.findByPass(ReconcilePass.ORPHAN)).thenReturn(Optional.empty());
        when(syncStateRepository.findAllByEntityTypeAndEntityIdIn(anyString(), any())).thenReturn(List.of());
        configure(0.9, COURSE, LECTURE);
    }

    @Test
    void testARowWithNoEntityBehindItIsQueuedForRemoval() {
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));

        sweep.sweep();

        verify(enqueueService).enqueueDelete(COURSE, 2L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
        verify(enqueueService, never()).enqueueDelete(COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
    }

    @Test
    void testARowWhoseStoredContentDisagreesWithTheLedgerIsRewritten() {
        scanReturns(row(COURSE, 1L, "v1:stale"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));
        when(syncStateRepository.findAllByEntityTypeAndEntityIdIn(eq(COURSE), any())).thenReturn(List.of(ledgerRow(COURSE, 1L, "v1:current")));

        sweep.sweep();

        verify(enqueueService).enqueueUpsert(COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
    }

    @Test
    void testARowCarryingNoHashIsTreatedAsUnverifiedAndRewritten() {
        // Not corruption: simply a row written before hashes were stored. Rewriting it once brings it under
        // verification, after which it is never singled out again.
        scanReturns(row(COURSE, 1L, null));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));
        when(syncStateRepository.findAllByEntityTypeAndEntityIdIn(eq(COURSE), any())).thenReturn(List.of(ledgerRow(COURSE, 1L, "v1:current")));

        sweep.sweep();

        verify(enqueueService).enqueueUpsert(COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
    }

    @Test
    void testARowWhoseContentMatchesIsLeftAlone() {
        scanReturns(row(COURSE, 1L, "v1:current"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));
        when(syncStateRepository.findAllByEntityTypeAndEntityIdIn(eq(COURSE), any())).thenReturn(List.of(ledgerRow(COURSE, 1L, "v1:current")));

        sweep.sweep();

        verify(enqueueService, never()).enqueueUpsert(anyString(), anyLong(), any());
        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
    }

    @Test
    void testARowOfAnUnmanagedTypeIsSkippedRatherThanRemoved() {
        // Removing an orphaned post while no pass ever re-adds a missing one is worse than leaving posts alone.
        configure(0.9, COURSE);
        scanReturns(row(POST, 7L, "v1:a"));

        sweep.sweep();

        verify(idEnumerator, never()).indexableIdsAmong(eq(POST), any());
        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
    }

    @Test
    void testADisabledModuleQueuesNothingForItsType() {
        scanReturns(row(LECTURE, 5L, "v1:a"));
        when(idEnumerator.indexableIdsAmong(eq(LECTURE), any())).thenReturn(Optional.empty());

        sweep.sweep();

        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
        verify(enqueueService, never()).enqueueUpsert(anyString(), anyLong(), any());
    }

    @Test
    void testAnImplausibleOrphanRatioAbortsAndRemovesNothing() {
        // Most rows looking orphaned means the lookup or this pass is wrong far more often than it means the index
        // really is that far out of step, and removal is unrecoverable.
        configure(0.25, COURSE);
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));

        sweep.sweep();

        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
        verify(reconcileStateRepository, never()).save(any());
    }

    @Test
    void testRemovalsAreCappedPerTick() {
        // Deliberately kept under the abort threshold, because the circuit breaker is checked first: a slice where
        // everything looks orphaned aborts outright rather than removing up to the cap.
        configure(0.9, COURSE);
        IndexedRow[] rows = IntStream.rangeClosed(1, 10).mapToObj(id -> row(COURSE, id, "v1:a")).toArray(IndexedRow[]::new);
        scanReturns(rows);
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L, 2L, 3L, 4L, 5L)));

        sweep.sweep();

        verify(enqueueService, times(DELETE_CAP)).enqueueDelete(eq(COURSE), anyLong(), eq(WeaviateOutboxOrigin.RECONCILE_ORPHAN));
    }

    @Test
    void testTheCircuitBreakerIsCheckedBeforeTheRemovalCap() {
        // Ordering matters: a slice where everything looks orphaned is a signal that something is wrong, and
        // removing up to the cap anyway would destroy rows on exactly the reading least worth trusting.
        configure(0.9, COURSE);
        IndexedRow[] rows = IntStream.rangeClosed(1, 10).mapToObj(id -> row(COURSE, id, "v1:a")).toArray(IndexedRow[]::new);
        scanReturns(rows);
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of()));

        sweep.sweep();

        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
    }

    @Test
    void testNothingIsReadWhileTheOutboxIsAtItsDepthLimit() {
        when(enqueueService.canEnqueue()).thenReturn(false);

        sweep.sweep();

        verifyNoInteractions(indexScanService);
    }

    @Test
    void testTheCursorAdvancesSoTheNextTickContinues() {
        scanReturns(row(COURSE, 1L, "v1:current"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));

        sweep.sweep();

        var captor = org.mockito.ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(captor.capture());
        assertThat(captor.getValue().getPositionCursor()).isEqualTo("next-cursor");
    }

    @Test
    void testReachingTheEndOfTheIndexStartsAFreshCycle() {
        when(indexScanService.scanFrom(any(), anyInt(), anyInt())).thenReturn(new IndexScanSlice(List.of(row(COURSE, 1L, "v1:current")), null));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));

        sweep.sweep();

        var captor = org.mockito.ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(captor.capture());
        assertThat(captor.getValue().getPositionCursor()).as("a fresh cycle restarts from the beginning").isNull();
    }
}
