package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class StudentVersionRegenerationScheduleServiceTest {

    @Test
    void publishesRetryEvent() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        var service = new StudentVersionRegenerationScheduleService(eventPublisher);

        service.retryPendingStudentVersions();

        verify(eventPublisher).publishEvent(new StudentVersionRegenerationScheduleService.RetryPendingStudentVersionsEvent());
    }
}
