package de.tum.cit.aet.artemis.lecture.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;

@Conditional(LectureWithIrisEnabled.class)
@ConditionalOnProperty(prefix = "artemis.iris", name = "lecture-unit-sync-scheduling-enabled", havingValue = "true", matchIfMissing = true)
@Service
public class IrisLectureUnitSyncScheduleService {

    private final IrisLectureUnitSyncEventListener syncEventListener;

    public IrisLectureUnitSyncScheduleService(IrisLectureUnitSyncEventListener syncEventListener) {
        this.syncEventListener = syncEventListener;
    }

    /**
     * Retries Iris/Pyris metadata and visibility updates that failed during event handling.
     */
    @Scheduled(fixedRate = 300000)
    public void retryDirtyStates() {
        syncEventListener.retryDirtyStates();
    }

    /**
     * Creates visibility synchronization state for active legacy units in bounded batches.
     */
    @Scheduled(fixedRate = 300000)
    public void backfillMissingSyncStates() {
        syncEventListener.backfillMissingSyncStates();
    }
}
