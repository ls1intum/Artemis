package de.tum.cit.aet.artemis.admin.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.core.service.ProfileService;

class TelemetryServiceTest {

    private final ProfileService profiles = mock(ProfileService.class);

    private final TelemetrySendingService sender = mock(TelemetrySendingService.class);

    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    private final Instant startedAt = Instant.parse("2026-09-25T10:00:00Z");

    private final Instant readyAt = startedAt.plusSeconds(60);

    @Test
    void collectsOnceTenMinutesAfterReadiness() {
        var future = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(future).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        var service = new TelemetryService(profiles, sender, scheduler, true, false, false);
        service.scheduleTelemetry(startedAt, readyAt);
        service.scheduleTelemetry(startedAt, readyAt);
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), eq(readyAt.plusSeconds(600)));
        verify(sender, never()).sendTelemetryByPostRequest(org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
        task.getValue().run();
        var startupId = ArgumentCaptor.forClass(String.class);
        verify(sender).sendTelemetryByPostRequest(eq(false), startupId.capture(), eq(startedAt));
        assertThat(startupId.getValue()).matches("[a-f0-9-]{36}");
        service.cancelTelemetry();
        verify(future).cancel(false);
    }

    @Test
    void skipsDisabledDevelopmentAndTestServers() {
        new TelemetryService(profiles, sender, scheduler, false, true, false).scheduleTelemetry(startedAt, readyAt);
        new TelemetryService(profiles, sender, scheduler, true, true, true).scheduleTelemetry(startedAt, readyAt);
        when(profiles.isDevActive()).thenReturn(true);
        new TelemetryService(profiles, sender, scheduler, true, true, false).scheduleTelemetry(startedAt, readyAt);
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shutdownBeforeReadinessPreventsScheduling() {
        var service = new TelemetryService(profiles, sender, scheduler, true, true, false);
        service.cancelTelemetry();
        service.scheduleTelemetry(startedAt, readyAt);
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
