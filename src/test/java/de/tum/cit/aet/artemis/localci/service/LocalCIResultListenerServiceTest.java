package de.tum.cit.aet.artemis.localci.service;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;

class LocalCIResultListenerServiceTest {

    @Test
    void retriesOnlyTheDeferredSnapshotEvenWhenTheItemIsDeferredAgain() {
        DistributedDataAccessService data = mock(DistributedDataAccessService.class);
        LocalCIResultProcessingService processing = mock(LocalCIResultProcessingService.class);
        DistributedQueue<ResultQueueItem> ordinary = mock(DistributedQueue.class);
        DistributedQueue<ResultQueueItem> deferred = mock(DistributedQueue.class);
        when(data.getDistributedBuildResultQueue()).thenReturn(ordinary);
        when(data.getDistributedDeferredBuildResultQueue()).thenReturn(deferred);
        when(deferred.size()).thenReturn(1);
        // A busy item is re-added, so the queue remains nonempty throughout the sweep.
        when(deferred.peek()).thenReturn(mock(ResultQueueItem.class));

        new LocalCIResultListenerService(data, processing).processQueuedResults();

        verify(processing).processDeferredResultAsync();
        verifyNoMoreInteractions(processing);
    }

    @Test
    void newlyDeferredResultsWaitForTheNextSweep() {
        DistributedDataAccessService data = mock(DistributedDataAccessService.class);
        LocalCIResultProcessingService processing = mock(LocalCIResultProcessingService.class);
        DistributedQueue<ResultQueueItem> ordinary = mock(DistributedQueue.class);
        DistributedQueue<ResultQueueItem> deferred = mock(DistributedQueue.class);
        when(data.getDistributedBuildResultQueue()).thenReturn(ordinary);
        when(data.getDistributedDeferredBuildResultQueue()).thenReturn(deferred);
        when(ordinary.size()).thenReturn(1);
        when(ordinary.peek()).thenReturn(mock(ResultQueueItem.class));
        when(deferred.size()).thenReturn(0);
        doAnswer(invocation -> {
            when(deferred.size()).thenReturn(1);
            return null;
        }).when(processing).processResultAsync();

        new LocalCIResultListenerService(data, processing).processQueuedResults();

        verify(processing).processResultAsync();
        verify(processing, never()).processDeferredResultAsync();
    }
}
