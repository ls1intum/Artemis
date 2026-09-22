package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

class ReconcileEnqueueServiceTest {

    private static final String COURSE = "course";

    private static final int MAX_DEPTH = 500;

    private final WeaviateOutboxRepository outboxRepository = mock(WeaviateOutboxRepository.class);

    private final SearchableEntityWeaviateService searchableEntityWeaviateService = mock(SearchableEntityWeaviateService.class);

    private final ReconcileEnqueueService enqueueService = new ReconcileEnqueueService(outboxRepository, searchableEntityWeaviateService,
            new WeaviateReconcileProperties(true, true, true, List.of(COURSE), MAX_DEPTH, 100, 200, 1000, 5, 100, 100, 0.25));

    @Test
    void testCanEnqueueWhileTheQueueIsBelowItsDepthLimit() {
        when(outboxRepository.count()).thenReturn((long) MAX_DEPTH - 1);

        assertThat(enqueueService.canEnqueue()).isTrue();
    }

    @Test
    void testCannotEnqueueOnceTheQueueReachesItsDepthLimit() {
        // Reaching the limit is enough to stop; the passes wait for the dispatcher to work it down again.
        when(outboxRepository.count()).thenReturn((long) MAX_DEPTH);

        assertThat(enqueueService.canEnqueue()).isFalse();
    }

    @Test
    void testEnqueueUpsertQueuesTheEntityWithTheCallersOrigin() {
        when(outboxRepository.existsByEntityTypeAndEntityId(COURSE, 42L)).thenReturn(false);

        assertThat(enqueueService.enqueueUpsert(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_MISSING)).isTrue();

        verify(searchableEntityWeaviateService).enqueueUpsert(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_MISSING);
    }

    @Test
    void testEnqueueDeleteQueuesTheEntityWithTheCallersOrigin() {
        when(outboxRepository.existsByEntityTypeAndEntityId(COURSE, 42L)).thenReturn(false);

        assertThat(enqueueService.enqueueDelete(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_ORPHAN)).isTrue();

        verify(searchableEntityWeaviateService).enqueueDeleteEntity(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_ORPHAN);
    }

    @Test
    void testAnEntityThatIsAlreadyQueuedIsNotQueuedAgain() {
        // Two passes can both notice the same entity, and a slowly draining backlog would otherwise accumulate
        // duplicates of the same work.
        when(outboxRepository.existsByEntityTypeAndEntityId(COURSE, 42L)).thenReturn(true);

        assertThat(enqueueService.enqueueUpsert(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_MISSING)).isFalse();
        assertThat(enqueueService.enqueueDelete(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_DRIFT)).isFalse();

        verify(searchableEntityWeaviateService, never()).enqueueUpsert(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_MISSING);
        verify(searchableEntityWeaviateService, never()).enqueueDeleteEntity(COURSE, 42L, WeaviateOutboxOrigin.RECONCILE_DRIFT);
    }

    @Test
    void testBlockingAndResumingAreReportedOnceEachRatherThanEveryCheck() {
        // A backlog can take days to drain and the passes tick throughout, so only the transitions are worth saying.
        when(outboxRepository.count()).thenReturn((long) MAX_DEPTH);
        assertThat(enqueueService.canEnqueue()).isFalse();
        assertThat(enqueueService.canEnqueue()).isFalse();

        when(outboxRepository.count()).thenReturn(0L);
        assertThat(enqueueService.canEnqueue()).isTrue();
        assertThat(enqueueService.canEnqueue()).isTrue();
    }
}
