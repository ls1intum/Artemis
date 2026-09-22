package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
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
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityResolver;
import tools.jackson.databind.json.JsonMapper;

class SearchableEntityOrphanSweepTest {

    private static final String COURSE = "course";

    private static final String POST = "post";

    private static final String LECTURE = "lecture";

    private static final int DELETE_CAP = 3;

    private static final int REPAIR_CAP = 3;

    private final SearchableEntityIndexScanService indexScanService = mock(SearchableEntityIndexScanService.class);

    private final SearchableEntityIdEnumerator idEnumerator = mock(SearchableEntityIdEnumerator.class);

    private final SearchableEntityResolver resolver = mock(SearchableEntityResolver.class);

    private final SearchableEntitySyncStateRepository syncStateRepository = mock(SearchableEntitySyncStateRepository.class);

    private final SearchableEntityReconcileStateRepository reconcileStateRepository = mock(SearchableEntityReconcileStateRepository.class);

    private final ReconcileEnqueueService enqueueService = mock(ReconcileEnqueueService.class);

    private final JsonMapper objectMapper = JsonObjectMapper.get();

    private SearchableEntityOrphanSweep sweep;

    private static IndexedRow row(String entityType, long entityId, String contentHash) {
        return new IndexedRow("uuid-" + entityType + "-" + entityId, entityType, entityId, entityId, contentHash);
    }

    private static SearchableEntitySyncState ledgerRow(String entityType, long entityId, String contentHash) {
        return new SearchableEntitySyncState(entityType, entityId, contentHash, ZonedDateTime.now());
    }

    private void configure(double abortRatio, String... types) {
        var properties = new WeaviateReconcileProperties(true, true, true, List.of(types), 500, 100, 200, 10, 1, DELETE_CAP, REPAIR_CAP, abortRatio);
        sweep = new SearchableEntityOrphanSweep(indexScanService, idEnumerator, resolver, syncStateRepository, reconcileStateRepository, enqueueService, properties, objectMapper);
    }

    private void scanReturns(IndexedRow... rows) {
        when(indexScanService.scanFrom(any(), anyInt(), anyInt())).thenReturn(new IndexScanSlice(List.of(rows), "next-cursor"));
    }

    /**
     * Captures the state saved by the tick just run and feeds it back as what the next {@code sweep()} call reads,
     * so a streak recorded on one tick is visible to the next. The captured object is mutated in place by later
     * ticks too, so this only needs calling once per test even across more than two ticks.
     */
    private SearchableEntityReconcileState carryStateForward() {
        ArgumentCaptor<SearchableEntityReconcileState> saved = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository, atLeastOnce()).save(saved.capture());
        SearchableEntityReconcileState latest = saved.getValue();
        when(reconcileStateRepository.findByPass(ReconcilePass.ORPHAN)).thenReturn(Optional.of(latest));
        return latest;
    }

    @BeforeEach
    void setUp() {
        when(enqueueService.canEnqueue()).thenReturn(true);
        when(enqueueService.enqueueDelete(anyString(), anyLong(), any())).thenReturn(true);
        when(enqueueService.enqueueUpsert(anyString(), anyLong(), any())).thenReturn(true);
        when(reconcileStateRepository.findByPass(ReconcilePass.ORPHAN)).thenReturn(Optional.empty());
        when(syncStateRepository.findAllByEntityTypeAndEntityIdIn(anyString(), any())).thenReturn(List.of());
        // Agrees with the eligibility check by default: the entity really is gone. Individual tests override this
        // to simulate the two mechanisms disagreeing.
        when(resolver.resolve(anyString(), anyLong())).thenReturn(Optional.empty());
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

    /**
     * Regression test for a real bug found in review: a different set of flagged rows on a later tick is not
     * proof either, since a systematically wrong eligibility check produces a different wrong answer on every
     * different set of rows it is asked about, purely because the rows differ, not because it stopped being
     * wrong. Simulates exactly that: two disjoint slices both come back with every row supposedly orphaned (the
     * fingerprint genuinely differs between them, so the streak alone would trust it), but re-deriving those
     * specific rows directly still finds them live, so nothing is removed.
     */
    @Test
    void testASystematicallyWrongEligibilityCheckNeverDeletesRowsTheDirectLookupStillFindsLive() {
        configure(0.25, COURSE);
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"));
        // The eligibility check is broken for this whole type: every row looks orphaned, whichever rows they are.
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of()));
        // But every one of them still resolves directly: they are all still genuinely live.
        when(resolver.resolve(eq(COURSE), anyLong())).thenReturn(Optional.of(Map.of("title", "still here")));

        sweep.sweep(); // First slice {1,2,3,4}: refused, remembered.
        carryStateForward();

        // A disjoint second slice: a different fingerprint, which the streak alone would treat as confirmation.
        scanReturns(row(COURSE, 5L, "v1:e"), row(COURSE, 6L, "v1:f"), row(COURSE, 7L, "v1:g"), row(COURSE, 8L, "v1:h"));
        sweep.sweep();

        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
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
        // Skipping the type must not stall the scan: the slice is still consumed and the cursor still advances.
        ArgumentCaptor<SearchableEntityReconcileState> saved = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getPositionCursor()).isEqualTo("next-cursor");
    }

    @Test
    void testAnImplausibleOrphanRatioIsRefusedOnASingleReading() {
        // Most rows looking orphaned means the lookup or this pass is wrong far more often than it means the index
        // really is that far out of step, so one such reading, on its own, is refused rather than acted on.
        configure(0.25, COURSE);
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));

        sweep.sweep();

        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
        // The scan still moves on: a type awaiting confirmation must not block the rest of the collection from
        // ever being looked at again while it waits.
        ArgumentCaptor<SearchableEntityReconcileState> saved = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(saved.capture());
        assertThat(saved.getValue().getPositionCursor()).isEqualTo("next-cursor");
    }

    @Test
    void testATypeIsTrustedOnlyOnceADifferentSetOfRowsAlsoLooksOrphaned() {
        configure(0.25, COURSE);
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));

        sweep.sweep(); // First sighting: rows 2, 3, 4 flagged. Refused, remembered.
        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
        carryStateForward();

        // A different page of the same type, flagging a disjoint set of rows: genuinely new evidence, not the
        // same reading (or the same bug) recurring, so this is trusted.
        scanReturns(row(COURSE, 5L, "v1:e"), row(COURSE, 6L, "v1:f"), row(COURSE, 7L, "v1:g"), row(COURSE, 8L, "v1:h"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(5L)));
        sweep.sweep();

        verify(enqueueService).enqueueDelete(COURSE, 6L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
        verify(enqueueService).enqueueDelete(COURSE, 7L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
        verify(enqueueService).enqueueDelete(COURSE, 8L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
        // The rows flagged only on the refused first sighting are never acted on at all.
        verify(enqueueService, never()).enqueueDelete(eq(COURSE), eq(2L), any());
    }

    /**
     * Regression test for a real bug found in review: the previous design trusted a type as soon as the same
     * ratio came back over threshold on a later tick, treating that repetition as independent confirmation. It
     * is not. A collection too small to page past (or any collection where nothing has changed) reads the exact
     * same rows every tick, and a deterministic bug in the eligibility check reproduces identically on every row
     * it touches regardless of which rows those are — so the same over-threshold reading recurring, on its own,
     * proves nothing and must never be trusted, however many times it recurs.
     */
    @Test
    void testTheExactSameFlaggedRowsNeverConfirmNoMatterHowManyTimesTheyRecur() {
        configure(0.25, COURSE);
        // A collection smaller than the scan budget: every tick reads this identical page, exactly as scanFrom
        // behaves once the whole collection fits in one read (its cursor comes back null every time).
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));

        for (int tick = 0; tick < 5; tick++) {
            sweep.sweep();
            carryStateForward();
        }

        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
    }

    @Test
    void testATrustedTypeStaysTrustedWithoutReconfirmingEveryTick() {
        configure(0.25, COURSE);
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));
        sweep.sweep(); // First sighting: refused.
        carryStateForward();

        // A different set earns trust.
        scanReturns(row(COURSE, 5L, "v1:e"), row(COURSE, 6L, "v1:f"), row(COURSE, 7L, "v1:g"), row(COURSE, 8L, "v1:h"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(5L)));
        sweep.sweep(); // Second sighting, a different set: trusted, acts.
        carryStateForward();

        // The exact same set as the previous tick this time (a large batch this tiny cap has not fully drained
        // yet), but already trusted, so it acts again without needing yet another different set to re-earn that.
        sweep.sweep();

        verify(enqueueService, times(2)).enqueueDelete(eq(COURSE), eq(6L), eq(WeaviateOutboxOrigin.RECONCILE_ORPHAN));
    }

    @Test
    void testAHealthyReadingClearsAnEarlierStreakInsteadOfCarryingItForward() {
        configure(0.25, COURSE);
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));
        sweep.sweep(); // First sighting: refused, remembered.
        carryStateForward();

        // A different, healthy page for the same type: nothing here is suspicious, so the earlier streak clears.
        scanReturns(row(COURSE, 10L, "v1:x"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(10L)));
        sweep.sweep();

        // Over the ratio again, but this is only the first occurrence since the streak was cleared, so it is
        // refused again rather than trusted.
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"));
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L)));
        sweep.sweep();

        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
    }

    @Test
    void testAnAbortForOneTypeDoesNotWithholdAHealthyTypesSameTickRepairs() {
        // Each type in a scanned page is judged and acted on independently: lecture's implausible ratio must not
        // cost course its perfectly ordinary, single orphan in the very same tick.
        configure(0.25, COURSE, LECTURE);
        scanReturns(row(COURSE, 1L, "v1:a"), row(COURSE, 2L, "v1:b"), row(COURSE, 3L, "v1:c"), row(COURSE, 4L, "v1:d"), row(COURSE, 5L, "v1:e"), row(COURSE, 6L, "v1:f"),
                row(COURSE, 7L, "v1:g"), row(COURSE, 8L, "v1:h"), row(LECTURE, 101L, "v1:a"), row(LECTURE, 102L, "v1:b"), row(LECTURE, 103L, "v1:c"), row(LECTURE, 104L, "v1:d"));
        // course: 1 of 8 orphaned (12.5%), safely under the 25% threshold on its own.
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(2L, 3L, 4L, 5L, 6L, 7L, 8L)));
        // lecture: 3 of 4 orphaned (75%), well above the threshold, and only a first sighting so far.
        when(idEnumerator.indexableIdsAmong(eq(LECTURE), any())).thenReturn(Optional.of(Set.of(104L)));

        sweep.sweep();

        verify(enqueueService).enqueueDelete(COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
        verify(enqueueService, never()).enqueueDelete(eq(LECTURE), anyLong(), any());
    }

    @Test
    void testRemovalsAreCappedPerTick() {
        // Deliberately kept under the abort threshold, because the circuit breaker is checked first: a slice where
        // everything looks orphaned is refused outright rather than removing up to the cap.
        configure(0.9, COURSE);
        IndexedRow[] rows = IntStream.rangeClosed(1, 10).mapToObj(id -> row(COURSE, id, "v1:a")).toArray(IndexedRow[]::new);
        scanReturns(rows);
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L, 2L, 3L, 4L, 5L)));

        sweep.sweep();

        verify(enqueueService, times(DELETE_CAP)).enqueueDelete(eq(COURSE), anyLong(), eq(WeaviateOutboxOrigin.RECONCILE_ORPHAN));
    }

    @Test
    void testRepairsAreCappedPerTick() {
        // The delete cap has always bounded removal, but repairing a content mismatch was unbounded: a tick could
        // queue a rewrite for every live row in the scanned page. This mirrors that same missing-guard shape for
        // deletes, now closed the same way.
        configure(0.9, COURSE);
        IndexedRow[] rows = IntStream.rangeClosed(1, 10).mapToObj(id -> row(COURSE, id, "v1:stale")).toArray(IndexedRow[]::new);
        scanReturns(rows);
        when(idEnumerator.indexableIdsAmong(eq(COURSE), any())).thenReturn(Optional.of(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)));
        when(syncStateRepository.findAllByEntityTypeAndEntityIdIn(eq(COURSE), any()))
                .thenReturn(IntStream.rangeClosed(1, 10).mapToObj(id -> ledgerRow(COURSE, id, "v1:current")).toList());

        sweep.sweep();

        verify(enqueueService, times(REPAIR_CAP)).enqueueUpsert(eq(COURSE), anyLong(), eq(WeaviateOutboxOrigin.RECONCILE_ORPHAN));
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
