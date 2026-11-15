package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;

class SearchableEntityRefillSweepTest {

    private static final String POST = "post";

    private static final String ANSWER_POST = "answer_post";

    private final SearchableEntityIdEnumerator idEnumerator = mock(SearchableEntityIdEnumerator.class);

    private final SearchableEntityReconcileStateRepository reconcileStateRepository = mock(SearchableEntityReconcileStateRepository.class);

    private final ReconcileEnqueueService enqueueService = mock(ReconcileEnqueueService.class);

    private final SearchableEntityRefillSweep sweep = new SearchableEntityRefillSweep(idEnumerator, reconcileStateRepository, enqueueService,
            new WeaviateReconcileProperties(true, true, false, List.of("course"), 500, 100, 200, 1000, 5, 100, 100, 0.25));

    private final AtomicReference<SearchableEntityReconcileState> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        when(reconcileStateRepository.findByPass(ReconcilePass.REFILL)).thenAnswer(_ -> Optional.ofNullable(stored.get()));
        when(reconcileStateRepository.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(enqueueService.remainingCapacity()).thenReturn(1000);
        when(enqueueService.enqueueUpsert(anyString(), anyLong(), any())).thenReturn(true);
        when(idEnumerator.nextIndexableIds(anyString(), anyLong(), anyInt())).thenReturn(Optional.of(List.of()));
    }

    /**
     * The state the index rebuild creates.
     */
    private void startRefill() {
        var state = new SearchableEntityReconcileState(ReconcilePass.REFILL);
        state.setPositionEntityType(POST);
        state.setPositionEntityId(0L);
        stored.set(state);
    }

    @Test
    void doesNothingWithoutARebuild() {
        sweep.sweep();

        verify(idEnumerator, never()).nextIndexableIds(anyString(), anyLong(), anyInt());
        verify(enqueueService, never()).enqueueUpsert(anyString(), anyLong(), any());
    }

    @Test
    void queuesEveryPostAndThenEveryAnswerPostOnceAndThenGoesIdle() {
        startRefill();
        when(idEnumerator.nextIndexableIds(eq(POST), eq(0L), anyInt())).thenReturn(Optional.of(List.of(1L, 2L)));
        when(idEnumerator.nextIndexableIds(eq(ANSWER_POST), eq(0L), anyInt())).thenReturn(Optional.of(List.of(5L)));

        sweep.sweep();

        verify(enqueueService).enqueueUpsert(POST, 1L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        verify(enqueueService).enqueueUpsert(POST, 2L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        verify(enqueueService).enqueueUpsert(ANSWER_POST, 5L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        assertThat(stored.get().getPositionEntityType()).as("done").isNull();
        assertThat(stored.get().getRepairsEnqueued()).isEqualTo(3);

        // Idle until the next rebuild.
        sweep.sweep();
        verify(idEnumerator).nextIndexableIds(eq(POST), eq(0L), anyInt());
    }

    @Test
    void stopsWhenTheOutboxIsFullAndContinuesFromItsPositionLater() {
        startRefill();
        when(idEnumerator.nextIndexableIds(eq(POST), eq(0L), anyInt())).thenReturn(Optional.of(List.of(1L, 2L)));
        when(idEnumerator.nextIndexableIds(eq(POST), eq(2L), anyInt())).thenReturn(Optional.of(List.of(3L)));
        when(enqueueService.remainingCapacity()).thenReturn(2, 0);

        sweep.sweep();

        verify(idEnumerator).nextIndexableIds(POST, 0L, 2);
        assertThat(stored.get().getPositionEntityType()).isEqualTo(POST);
        assertThat(stored.get().getPositionEntityId()).isEqualTo(2L);

        when(enqueueService.remainingCapacity()).thenReturn(1000);
        sweep.sweep();

        verify(enqueueService).enqueueUpsert(POST, 3L, WeaviateOutboxOrigin.RECONCILE_MISSING);
    }

    @Test
    void aFailedEnqueueKeepsThePositionSoNoIdIsSkipped() {
        startRefill();
        when(idEnumerator.nextIndexableIds(eq(POST), eq(0L), anyInt())).thenReturn(Optional.of(List.of(1L, 2L)));
        when(enqueueService.enqueueUpsert(POST, 2L, WeaviateOutboxOrigin.RECONCILE_MISSING)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(sweep::sweep).isInstanceOf(IllegalStateException.class);

        assertThat(stored.get().getPositionEntityType()).isEqualTo(POST);
        assertThat(stored.get().getPositionEntityId()).isZero();
        verify(reconcileStateRepository, never()).save(any());
    }
}
