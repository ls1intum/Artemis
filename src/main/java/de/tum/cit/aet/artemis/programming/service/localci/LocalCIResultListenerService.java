package de.tum.cit.aet.artemis.programming.service.localci;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically drains and processes build results that are still present in the
 * distributed build result queue used by the local CI.
 * <p>
 * This service acts as a safety net on core nodes: if event-based listeners fail
 * to process results (e.g. due to high system load or transient network issues),
 * this scheduled job will pick them up so that no results remain stuck in the queue.
 * </p>
 * <p>
 * Active only in the {@code localci} Spring profile.
 * </p>
 */
@Lazy
@Service
@Profile("localci")
public class LocalCIResultListenerService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIResultListenerService.class);

    private final DistributedDataAccessService distributedDataAccessService;

    private final LocalCIResultProcessingService localCIResultProcessingService;

    public LocalCIResultListenerService(DistributedDataAccessService distributedDataAccessService, LocalCIResultProcessingService localCIResultProcessingService) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.localCIResultProcessingService = localCIResultProcessingService;
    }

    /**
     * Processes any queued build results from the distributed build result queue.
     * <p>
     * This method is a fallback mechanism that runs on each core node every five
     * seconds. It ensures that no results remain unprocessed in the Hazelcast-backed
     * queue, for example if listener events were lost under high system load or
     * during short network interruptions.
     * </p>
     * <p>
     * For each invocation, the current queue size is determined once and up to that
     * many results are processed. If the queue becomes empty earlier, the loop stops
     * immediately.
     * </p>
     */
    @Scheduled(fixedRate = 5 * 1000) // every 5 seconds
    public void processQueuedResults() {
        final int resultQueueSize = distributedDataAccessService.getResultQueueSize();
        if (resultQueueSize == 0) {
            return;
        }
        log.info("Scheduled task found {} queued results in the Hazelcast distributed build result queue. Will process these results now.", resultQueueSize);
        for (int i = 0; i < resultQueueSize; i++) {
            if (distributedDataAccessService.getDistributedBuildResultQueue().peek() == null) {
                log.info("Finished processing queued results early as the queue is now empty.");
                break;
            }
            try {
                localCIResultProcessingService.processResultAsync();
            }
            catch (Exception ex) {
                log.warn("Processing a queued result failed. Continuing with remaining items", ex);
            }
        }
        log.info("Finished processing queued results.");
    }
}
