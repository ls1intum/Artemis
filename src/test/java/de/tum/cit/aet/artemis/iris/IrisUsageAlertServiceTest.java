package de.tum.cit.aet.artemis.iris;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardAlertDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAdminDashboardService;
import de.tum.cit.aet.artemis.iris.service.IrisDashboardEmailService;
import de.tum.cit.aet.artemis.iris.service.IrisUsageAlertService;

class IrisUsageAlertServiceTest {

    private ProfileService profileService;

    private IrisDashboardProperties properties;

    private IrisAdminDashboardService dashboardService;

    private IrisDashboardEmailService emailService;

    private IrisAdminDashboardRepository dashboardRepository;

    private IrisUsageAlertService alertService;

    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        properties = new IrisDashboardProperties();
        dashboardService = mock(IrisAdminDashboardService.class);
        emailService = mock(IrisDashboardEmailService.class);
        dashboardRepository = mock(IrisAdminDashboardRepository.class);

        when(profileService.isSchedulingActive()).thenReturn(true);
        when(profileService.isDevActive()).thenReturn(false);
        properties.getAlert().setEnabled(true);
        when(emailService.canSendAlert()).thenReturn(true);
        when(dashboardRepository.countUserMessages(any(), any())).thenReturn(100L);

        alertService = new IrisUsageAlertService(profileService, properties, dashboardService, emailService, dashboardRepository, false);

        TimeUtil.setClock(Clock.fixed(ZonedDateTime.of(2026, 5, 27, 12, 0, 0, 0, ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    void checkAlertThresholds_schedulingInactive_skips() {
        when(profileService.isSchedulingActive()).thenReturn(false);
        alertService.checkAlertThresholds();
        verify(emailService, never()).sendAlert(any());
    }

    @Test
    void checkAlertThresholds_disabled_skips() {
        properties.getAlert().setEnabled(false);
        alertService.checkAlertThresholds();
        verify(emailService, never()).sendAlert(any());
    }

    @Test
    void checkAlertThresholds_cooldownActive_skips() {
        when(emailService.sendAlert(any())).thenReturn(1);
        // Trigger once to set cooldown
        mockHighNoResponseRate();
        alertService.checkAlertThresholds();
        verify(emailService).sendAlert(any());

        // Second call within cooldown should skip
        alertService.checkAlertThresholds();
        verify(emailService).sendAlert(any()); // still just 1 invocation
    }

    @Test
    void checkAlertThresholds_belowThreshold_doesNotSend() {
        mockLowNoResponseRate();
        alertService.checkAlertThresholds();
        verify(emailService, never()).sendAlert(any());
    }

    @Test
    void checkAlertThresholds_aboveThreshold_sends() {
        mockHighNoResponseRate();
        when(emailService.sendAlert(any())).thenReturn(1);
        alertService.checkAlertThresholds();
        verify(emailService).sendAlert(any(IrisDashboardAlertDTO.class));
    }

    // IrisDashboardOverviewDTO has 20 fields:
    // (totalSessions, activeSessions, engagementRate, totalMessages, uniqueUsers, noResponseRate,
    // noResponseMessageCount, noResponseSessionCount, thumbsUpRatio, thumbsDownRatio,
    // thumbsUpAbsoluteRate, thumbsDownAbsoluteRate, sessionsWithThumbsUp, sessionsWithThumbsDown,
    // thumbsUpCount, thumbsDownCount,
    // avgResponseTimeSeconds, p50ResponseTimeSeconds, p95ResponseTimeSeconds, totalTokenCostEur)

    private void mockHighNoResponseRate() {
        // noResponseRate=15.0 which is > default threshold of 10.0
        // activeSessions=50 which is >= default minimumEligibleSessions of 10
        when(dashboardService.computeOverview(any(), any()))
                .thenReturn(new IrisDashboardOverviewDTO(100, 50, 50.0, 200, 20, 15.0, 30, 15, 80.0, 20.0, 60.0, 15.0, 40, 10, 20, 5, 5.0, 4.0, 8.0, 10.0));
        when(dashboardService.computeStaleBefore(any(), any())).thenReturn(ZonedDateTime.of(2026, 5, 27, 11, 55, 0, 0, ZoneOffset.UTC).toInstant());
    }

    private void mockLowNoResponseRate() {
        // noResponseRate=5.0 which is <= default threshold of 10.0
        when(dashboardService.computeOverview(any(), any()))
                .thenReturn(new IrisDashboardOverviewDTO(100, 50, 50.0, 200, 20, 5.0, 10, 5, 80.0, 20.0, 60.0, 15.0, 40, 10, 20, 5, 5.0, 4.0, 8.0, 10.0));
        when(dashboardService.computeStaleBefore(any(), any())).thenReturn(ZonedDateTime.of(2026, 5, 27, 11, 55, 0, 0, ZoneOffset.UTC).toInstant());
    }
}
