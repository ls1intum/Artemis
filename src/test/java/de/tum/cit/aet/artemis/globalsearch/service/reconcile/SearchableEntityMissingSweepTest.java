package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;

class SearchableEntityMissingSweepTest {

    private static final String COURSE = "course";

    private static final String LECTURE = "lecture";

    private final SearchableEntityIdEnumerator idEnumerator = mock(SearchableEntityIdEnumerator.class);

    private final SearchableEntitySyncStateRepository syncStateRepository = mock(SearchableEntitySyncStateRepository.class);

    private final SearchableEntityReconcileStateRepository reconcileStateRepository = mock(SearchableEntityReconcileStateRepository.class);

    private final ReconcileEnqueueService enqueueService = mock(ReconcileEnqueueService.class);

    private SearchableEntityMissingSweep sweep;

    private void configureTypes(String... types) {
        var properties = new WeaviateReconcileProperties(true, true, true, List.of(types), 500, 100, 200, 1000, 5, 100, 100, 0.25);
        sweep = new SearchableEntityMissingSweep(idEnumerator, syncStateRepository, reconcileStateRepository, enqueueService, properties);
    }

    @BeforeEach
    void setUp() {
        when(enqueueService.canEnqueue()).thenReturn(true);
        when(enqueueService.enqueueUpsert(anyString(), anyLong(), any())).thenReturn(true);
        when(reconcileStateRepository.findByPass(ReconcilePass.MISSING)).thenReturn(Optional.empty());
        when(syncStateRepository.findSyncedEntityIds(anyString(), any())).thenReturn(Set.of());
        configureTypes(COURSE);
    }

    @Test
    void testAnEntityWithNoLedgerRowIsQueued() {
        when(idEnumerator.nextIndexableIds(eq(COURSE), eq(0L), anyInt())).thenReturn(Optional.of(List.of(1L, 2L, 3L)));

        sweep.sweep();

        verify(enqueueService).enqueueUpsert(COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        verify(enqueueService).enqueueUpsert(COURSE, 2L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        verify(enqueueService).enqueueUpsert(COURSE, 3L, WeaviateOutboxOrigin.RECONCILE_MISSING);
    }

    @Test
    void testAnEntityAlreadyOnRecordIsNotQueued() {
        when(idEnumerator.nextIndexableIds(eq(COURSE), eq(0L), anyInt())).thenReturn(Optional.of(List.of(1L, 2L)));
        when(syncStateRepository.findSyncedEntityIds(COURSE, List.of(1L, 2L))).thenReturn(Set.of(2L));

        sweep.sweep();

        verify(enqueueService).enqueueUpsert(COURSE, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        verify(enqueueService, never()).enqueueUpsert(COURSE, 2L, WeaviateOutboxOrigin.RECONCILE_MISSING);
    }

    @Test
    void testNothingIsQueuedWhileTheOutboxIsAtItsDepthLimit() {
        when(enqueueService.canEnqueue()).thenReturn(false);

        sweep.sweep();

        verify(idEnumerator, never()).nextIndexableIds(anyString(), anyLong(), anyInt());
        verify(enqueueService, never()).enqueueUpsert(anyString(), anyLong(), any());
    }

    @Test
    void testADisabledModuleQueuesNothingForItsType() {
        // The distinction that matters: a disabled module means nothing is known about the type, not that it is
        // empty. Reading it as empty would be harmless here but catastrophic in the pass that deletes, so both
        // passes must treat it the same way.
        configureTypes(LECTURE, COURSE);
        when(idEnumerator.nextIndexableIds(eq(LECTURE), anyLong(), anyInt())).thenReturn(Optional.empty());

        sweep.sweep();

        verify(enqueueService, never()).enqueueUpsert(anyString(), anyLong(), any());
        assertThat(savedState().getPositionEntityType()).as("moves on to the next type instead of stalling").isEqualTo(COURSE);
    }

    @Test
    void testTypesOutsideTheConfigurationAreNeverWalked() {
        configureTypes(COURSE);
        when(idEnumerator.nextIndexableIds(eq(COURSE), eq(0L), anyInt())).thenReturn(Optional.of(List.of(1L)));

        sweep.sweep();

        verify(idEnumerator, never()).nextIndexableIds(eq("post"), anyLong(), anyInt());
        verify(idEnumerator, never()).nextIndexableIds(eq(LECTURE), anyLong(), anyInt());
    }

    @Test
    void testTheWatermarkAdvancesToTheLastIdOfThePage() {
        when(idEnumerator.nextIndexableIds(eq(COURSE), eq(0L), anyInt())).thenReturn(Optional.of(List.of(4L, 8L, 15L)));

        sweep.sweep();

        SearchableEntityReconcileState state = savedState();
        assertThat(state.getPositionEntityType()).isEqualTo(COURSE);
        assertThat(state.getPositionEntityId()).isEqualTo(15L);
        assertThat(state.getEntitiesChecked()).isEqualTo(3);
        assertThat(state.getRepairsEnqueued()).isEqualTo(3);
    }

    @Test
    void testTheSweepResumesFromWhereItStopped() {
        var stored = new SearchableEntityReconcileState(ReconcilePass.MISSING);
        stored.setPositionEntityType(COURSE);
        stored.setPositionEntityId(15L);
        when(reconcileStateRepository.findByPass(ReconcilePass.MISSING)).thenReturn(Optional.of(stored));
        when(idEnumerator.nextIndexableIds(eq(COURSE), eq(15L), anyInt())).thenReturn(Optional.of(List.of(16L)));

        sweep.sweep();

        verify(idEnumerator).nextIndexableIds(COURSE, 15L, 100);
        verify(enqueueService).enqueueUpsert(COURSE, 16L, WeaviateOutboxOrigin.RECONCILE_MISSING);
    }

    @Test
    void testAnExhaustedTypeHandsOverToTheNext() {
        configureTypes(COURSE, LECTURE);
        when(idEnumerator.nextIndexableIds(eq(COURSE), anyLong(), anyInt())).thenReturn(Optional.of(List.of()));

        sweep.sweep();

        SearchableEntityReconcileState state = savedState();
        assertThat(state.getPositionEntityType()).isEqualTo(LECTURE);
        assertThat(state.getPositionEntityId()).as("the next type starts from the beginning").isNull();
    }

    @Test
    void testTheLastTypeWrapsAroundIntoAFreshCycle() {
        var stored = new SearchableEntityReconcileState(ReconcilePass.MISSING);
        stored.setPositionEntityType(COURSE);
        stored.setPositionEntityId(99L);
        stored.recordProgress(500, 12, 0);
        when(reconcileStateRepository.findByPass(ReconcilePass.MISSING)).thenReturn(Optional.of(stored));
        when(idEnumerator.nextIndexableIds(eq(COURSE), anyLong(), anyInt())).thenReturn(Optional.of(List.of()));

        sweep.sweep();

        SearchableEntityReconcileState state = savedState();
        assertThat(state.getPositionEntityType()).isEqualTo(COURSE);
        assertThat(state.getPositionEntityId()).isNull();
        assertThat(state.getEntitiesChecked()).as("counters describe one cycle, not all of history").isZero();
        assertThat(state.getRepairsEnqueued()).isZero();
    }

    @Test
    void testAStoredTypeThatIsNoLongerConfiguredDoesNotStrandTheSweep() {
        var stored = new SearchableEntityReconcileState(ReconcilePass.MISSING);
        stored.setPositionEntityType("post");
        stored.setPositionEntityId(42L);
        when(reconcileStateRepository.findByPass(ReconcilePass.MISSING)).thenReturn(Optional.of(stored));
        when(idEnumerator.nextIndexableIds(eq(COURSE), eq(0L), anyInt())).thenReturn(Optional.of(List.of(1L)));

        sweep.sweep();

        verify(idEnumerator).nextIndexableIds(COURSE, 0L, 100);
    }

    private SearchableEntityReconcileState savedState() {
        ArgumentCaptor<SearchableEntityReconcileState> captor = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(captor.capture());
        return captor.getValue();
    }
}
