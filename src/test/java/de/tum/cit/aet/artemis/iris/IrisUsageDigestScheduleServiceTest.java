package de.tum.cit.aet.artemis.iris;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardDigestDTO;
import de.tum.cit.aet.artemis.iris.service.IrisAdminDashboardService;
import de.tum.cit.aet.artemis.iris.service.IrisDashboardEmailService;
import de.tum.cit.aet.artemis.iris.service.IrisUsageDigestScheduleService;

class IrisUsageDigestScheduleServiceTest {

    private ProfileService profileService;

    private IrisDashboardProperties properties;

    private IrisAdminDashboardService dashboardService;

    private IrisDashboardEmailService emailService;

    private DistributedDataProvider distributedDataProvider;

    private DistributedMap<String, Instant> scheduleStateMap;

    private IrisUsageDigestScheduleService scheduleService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        profileService = mock(ProfileService.class);
        properties = new IrisDashboardProperties();
        dashboardService = mock(IrisAdminDashboardService.class);
        emailService = mock(IrisDashboardEmailService.class);

        when(profileService.isSchedulingActive()).thenReturn(true);
        when(profileService.isDevActive()).thenReturn(false);
        properties.getDigest().setEnabled(true);
        when(emailService.canSendDigest()).thenReturn(true);
        when(dashboardService.computeDigestData(any(), any(), any())).thenReturn(dummyDigest());
        when(dashboardService.computeStaleBefore(any(), any())).thenReturn(Instant.parse("2026-05-26T23:55:00Z"));

        distributedDataProvider = mock(DistributedDataProvider.class);
        scheduleStateMap = mock(DistributedMap.class);
        DistributedLock digestLock = mock(DistributedLock.class);
        doReturn(scheduleStateMap).when(distributedDataProvider).getExpiringMap(eq("iris-dashboard-schedule-state"), any());
        doReturn(digestLock).when(distributedDataProvider).getLock(any());
        doReturn(true).when(digestLock).tryLock(any());
        when(scheduleStateMap.containsKey(any())).thenReturn(false);

        scheduleService = new IrisUsageDigestScheduleService(profileService, properties, dashboardService, emailService, false, distributedDataProvider);

        TimeUtil.setClock(Clock.fixed(ZonedDateTime.of(2026, 5, 27, 7, 0, 0, 0, ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    void sendDailyDigest_schedulingInactive_skips() {
        when(profileService.isSchedulingActive()).thenReturn(false);
        scheduleService.sendDailyDigest();
        verify(emailService, never()).sendDigest(any());
    }

    @Test
    void sendDailyDigest_devActive_skips() {
        when(profileService.isDevActive()).thenReturn(true);
        scheduleService.sendDailyDigest();
        verify(emailService, never()).sendDigest(any());
    }

    @Test
    void sendDailyDigest_disabled_skips() {
        properties.getDigest().setEnabled(false);
        scheduleService.sendDailyDigest();
        verify(emailService, never()).sendDigest(any());
    }

    @Test
    void sendDailyDigest_cannotSend_skips() {
        when(emailService.canSendDigest()).thenReturn(false);
        scheduleService.sendDailyDigest();
        verify(emailService, never()).sendDigest(any());
    }

    @Test
    void sendDailyDigest_allGuardsPass_sendsDigest() {
        scheduleService.sendDailyDigest();
        verify(emailService).sendDigest(any(IrisDashboardDigestDTO.class));
    }

    @Test
    void sendDailyDigest_alreadySent_skips() {
        when(scheduleStateMap.containsKey(any())).thenReturn(true);
        scheduleService.sendDailyDigest();
        verify(emailService, never()).sendDigest(any());
    }

    private static IrisDashboardDigestDTO dummyDigest() {
        return new IrisDashboardDigestDTO(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"), Instant.parse("2026-05-26T23:55:00Z"), 0L, 0L, 0.0, 0L, 0L,
                0.0, 0L, 0L, 0.0, 0.0, 0.0, 0.0, 0L, 0L, 0L, 0L, 0.0, 0.0, 0.0, 0.0, List.of(), List.of(), "/admin/iris-dashboard");
    }
}
