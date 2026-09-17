package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityContentHasher;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityResolver;

class SearchableEntityDriftSweepTest {

    private static final String COURSE = "course";

    private static final String LECTURE = "lecture";

    private static final Map<String, Object> PROPERTIES = Map.of("type", COURSE, "entity_id", 42L, "title", "Algorithms");

    private final SearchableEntitySyncStateRepository syncStateRepository = mock(SearchableEntitySyncStateRepository.class);

    private final SearchableEntityResolver resolver = mock(SearchableEntityResolver.class);

    // The real hasher, not a mock: the point of this pass is that its hash agrees with the write path's, and a
    // stubbed hash would assert nothing about that.
    private final SearchableEntityContentHasher contentHasher = new SearchableEntityContentHasher(JsonObjectMapper.get());

    private final SearchableEntityIdEnumerator idEnumerator = mock(SearchableEntityIdEnumerator.class);

    private final ReconcileEnqueueService enqueueService = mock(ReconcileEnqueueService.class);

    private final SearchableEntityReconcileStateRepository reconcileStateRepository = mock(SearchableEntityReconcileStateRepository.class);

    private final SearchableEntityDriftSweep sweep = new SearchableEntityDriftSweep(syncStateRepository, reconcileStateRepository, resolver, contentHasher, idEnumerator,
            enqueueService, new WeaviateReconcileProperties(true, true, true, List.of(COURSE, LECTURE), 500, 100, 200, 1000, 5, 100, 100, 0.25));

    private static SearchableEntitySyncState ledgerRow(String entityType, long entityId, String contentHash) {
        var state = new SearchableEntitySyncState(entityType, entityId, contentHash, ZonedDateTime.now().minusDays(7));
        state.setId(entityId);
        return state;
    }

    @BeforeEach
    void setUp() {
        when(enqueueService.canEnqueue()).thenReturn(true);
        when(enqueueService.enqueueUpsert(anyString(), anyLong(), any())).thenReturn(true);
        when(enqueueService.enqueueDelete(anyString(), anyLong(), any())).thenReturn(true);
        when(idEnumerator.isTypeAvailable(anyString())).thenReturn(true);
        when(reconcileStateRepository.findByPass(ReconcilePass.DRIFT)).thenReturn(Optional.empty());
    }

    @Test
    void testAnEntityThatStillMatchesIsMarkedVerifiedAndQueuesNothing() {
        // The ledger hash is produced by the same hasher the write path uses, so a match here proves the two agree.
        var state = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(state));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.of(PROPERTIES));

        sweep.sweep();

        verify(enqueueService, never()).enqueueUpsert(anyString(), anyLong(), any());
        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
        // Checking a row moves it to the back of the queue for the next cycle.
        verify(syncStateRepository).markVerified(eq(COURSE), eq(42L), any());
    }

    @Test
    void testCheckingARowNeverSavesTheWholeStaleEntity() {
        // A save of the batch-loaded snapshot would overwrite a newer contentHash/syncedAt the dispatcher wrote
        // for this same row in the meantime, or resurrect it after a concurrent delete (see markVerified's
        // javadoc). Only a scoped verifiedAt update is allowed; the full entity must never be saved.
        var state = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(state));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.of(PROPERTIES));

        sweep.sweep();

        verify(syncStateRepository, never()).save(any());
        verify(syncStateRepository).markVerified(eq(COURSE), eq(42L), any());
    }

    @Test
    void testAnEntityWhoseContentChangedIsQueuedForRepair() {
        var state = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(state));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.of(Map.of("type", COURSE, "entity_id", 42L, "title", "Advanced Algorithms")));

        sweep.sweep();

        verify(enqueueService).enqueueUpsert(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_DRIFT);
    }

    @Test
    void testAnEntityThatIsGoneOrNoLongerIndexableIsQueuedForRemoval() {
        var state = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(state));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.empty());

        sweep.sweep();

        verify(enqueueService).enqueueDelete(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_DRIFT);
        verify(enqueueService, never()).enqueueUpsert(anyString(), anyLong(), any());
    }

    @Test
    void testATypeWhoseModuleIsDisabledIsLeftOutOfTheCandidateQuery() {
        // A skipped row keeps its old verifiedAt and would lead every following slice, so the type must not be read
        // at all while its module is away; otherwise the pass stalls for every other type too.
        when(idEnumerator.isTypeAvailable(LECTURE)).thenReturn(false);
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of());

        sweep.sweep();

        verify(syncStateRepository).findLeastRecentlyVerified(eq(List.of(COURSE)), any());
    }

    @Test
    void testADisabledModuleQueuesNothingForItsType() {
        // Resolving anything from a disabled module yields nothing, which is indistinguishable from the entity
        // having been deleted. Without this guard the pass would queue a delete for every indexed row of the type.
        var state = ledgerRow(LECTURE, 7L, "v1:whatever");
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(state));
        when(idEnumerator.isTypeAvailable(LECTURE)).thenReturn(false);

        sweep.sweep();

        verifyNoInteractions(resolver);
        verify(enqueueService, never()).enqueueDelete(anyString(), anyLong(), any());
        verify(enqueueService, never()).enqueueUpsert(anyString(), anyLong(), any());
    }

    @Test
    void testNothingIsCheckedWhileTheOutboxIsAtItsDepthLimit() {
        when(enqueueService.canEnqueue()).thenReturn(false);

        sweep.sweep();

        verifyNoInteractions(syncStateRepository);
        verifyNoInteractions(resolver);
    }

    @Test
    void testAnEntityThatIsGoneIsStillMarkedChecked() {
        // Otherwise it stays at the front of the queue and every tick re-derives it until the dispatcher applies
        // the removal, spending the slice on work already in flight.
        var state = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(state));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.empty());

        sweep.sweep();

        verify(syncStateRepository).markVerified(eq(COURSE), eq(42L), any());
    }

    @Test
    void testADivergenceWithARepairAlreadyQueuedIsNotCountedAgain() {
        // The same problem would otherwise be reported once per tick until the dispatcher works through the queue.
        var state = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(state));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.of(Map.of("type", COURSE, "entity_id", 42L, "title", "Changed")));
        when(enqueueService.enqueueUpsert(anyString(), anyLong(), any())).thenReturn(false);

        sweep.sweep();

        var captor = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(captor.capture());
        assertThat(captor.getValue().getEntitiesChecked()).as("the entity was still examined").isEqualTo(1);
        assertThat(captor.getValue().getRepairsEnqueued()).as("but nothing new was queued for it").isZero();
    }

    @Test
    void testProgressIsRecordedForThePass() {
        var state = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(state));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.of(PROPERTIES));

        sweep.sweep();

        var captor = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(captor.capture());
        assertThat(captor.getValue().getEntitiesChecked()).isEqualTo(1);
    }

    @Test
    void testACycleCompletesOnceEvenTheOldestEntityHasBeenCheckedSinceItBegan() {
        // The pass has no position of its own, so the corpus answers the question: the slice always starts with the
        // entity checked longest ago, and once even that one is newer than the cycle start, nothing is left over.
        var pass = new SearchableEntityReconcileState(ReconcilePass.DRIFT);
        pass.setCycleStartedAt(ZonedDateTime.now().minusDays(7));
        pass.recordProgress(1_000, 4, 0);
        when(reconcileStateRepository.findByPass(ReconcilePass.DRIFT)).thenReturn(Optional.of(pass));

        var checkedYesterday = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        checkedYesterday.setVerifiedAt(ZonedDateTime.now().minusDays(1));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(checkedYesterday));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.of(PROPERTIES));

        sweep.sweep();

        var captor = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(captor.capture());
        assertThat(captor.getValue().getEntitiesChecked()).as("a fresh cycle counts only what it has checked itself").isEqualTo(1);
    }

    @Test
    void testACycleStaysOpenWhileEntitiesRemainUnchecked() {
        var pass = new SearchableEntityReconcileState(ReconcilePass.DRIFT);
        pass.setCycleStartedAt(ZonedDateTime.now().minusDays(7));
        pass.recordProgress(1_000, 4, 0);
        when(reconcileStateRepository.findByPass(ReconcilePass.DRIFT)).thenReturn(Optional.of(pass));

        var checkedBeforeTheCycle = ledgerRow(COURSE, 42L, contentHasher.hash(PROPERTIES));
        checkedBeforeTheCycle.setVerifiedAt(ZonedDateTime.now().minusDays(30));
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of(checkedBeforeTheCycle));
        when(resolver.resolve(COURSE, 42L)).thenReturn(Optional.of(PROPERTIES));

        sweep.sweep();

        var captor = ArgumentCaptor.forClass(SearchableEntityReconcileState.class);
        verify(reconcileStateRepository).save(captor.capture());
        assertThat(captor.getValue().getEntitiesChecked()).as("the running cycle keeps its totals").isEqualTo(1_001);
    }

    @Test
    void testOnlyConfiguredTypesAreRead() {
        when(syncStateRepository.findLeastRecentlyVerified(any(), any())).thenReturn(List.of());

        sweep.sweep();

        verify(syncStateRepository).findLeastRecentlyVerified(eq(List.of(COURSE, LECTURE)), any());
    }
}
