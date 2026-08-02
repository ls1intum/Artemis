package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class IrisLectureUnitSyncScheduleServiceTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final IrisLectureUnitSyncScheduleService scheduleService = new IrisLectureUnitSyncScheduleService(eventPublisher);

    @Test
    void publishesRetryEvent() {
        scheduleService.retryDirtyStates();

        verify(eventPublisher).publishEvent(new IrisLectureUnitSyncScheduleService.RetryDirtyStatesEvent());
    }

    @Test
    void publishesBackfillEvent() {
        scheduleService.backfillMissingSyncStates();

        verify(eventPublisher).publishEvent(new IrisLectureUnitSyncScheduleService.BackfillMissingSyncStatesEvent());
    }
}
