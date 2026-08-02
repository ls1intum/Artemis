package de.tum.cit.aet.artemis.lecture.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;

@Conditional(LectureWithIrisEnabled.class)
@ConditionalOnProperty(prefix = "artemis.iris", name = "lecture-unit-sync-scheduling-enabled", havingValue = "true", matchIfMissing = true)
@Service
public class IrisLectureUnitSyncScheduleService {

    private static final long SYNC_INTERVAL_MILLISECONDS = 300_000;

    private final ApplicationEventPublisher eventPublisher;

    public IrisLectureUnitSyncScheduleService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Retries Iris/Pyris metadata and visibility updates that failed during event handling.
     */
    @Scheduled(fixedRate = SYNC_INTERVAL_MILLISECONDS, initialDelay = SYNC_INTERVAL_MILLISECONDS)
    public void retryDirtyStates() {
        eventPublisher.publishEvent(new RetryDirtyStatesEvent());
    }

    /**
     * Creates visibility synchronization state for active legacy units in bounded batches.
     */
    @Scheduled(fixedRate = SYNC_INTERVAL_MILLISECONDS, initialDelay = SYNC_INTERVAL_MILLISECONDS)
    public void backfillMissingSyncStates() {
        eventPublisher.publishEvent(new BackfillMissingSyncStatesEvent());
    }

    public record RetryDirtyStatesEvent() {
    }

    public record BackfillMissingSyncStatesEvent() {
    }
}
