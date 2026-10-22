package de.tum.cit.aet.artemis.iris;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardAiSelection;
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

    private DistributedDataProvider distributedDataProvider;

    private DistributedMap<String, java.time.Instant> scheduleStateMap;

    private IrisUsageAlertService alertService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
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
        when(dashboardRepository.countSessionsWithUserMessages(any(), any())).thenReturn(50L);

        distributedDataProvider = mock(DistributedDataProvider.class);
        scheduleStateMap = mock(DistributedMap.class);
        DistributedLock alertLock = mock(DistributedLock.class);
        doReturn(scheduleStateMap).when(distributedDataProvider).getMap("iris-dashboard-schedule-state");
        doReturn(alertLock).when(distributedDataProvider).getLock(any());
        doReturn(true).when(alertLock).tryLock(any());
        when(scheduleStateMap.get("last-alert-sent-at")).thenReturn(null).thenReturn(java.time.Instant.parse("2026-05-27T12:00:00Z"));

        alertService = new IrisUsageAlertService(profileService, properties, dashboardService, emailService, dashboardRepository, false, distributedDataProvider);

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

    @Test
    void checkAlertThresholds_belowEligibleSessions_doesNotSend() {
        // The no-response rate is high and overview.activeSessions() (creation-time based) is well above the minimum,
        // but the message-time-based eligible-session count is below it. The gate must key off the latter and skip,
        // so that a high no-response rate driven by too few active sessions does not trigger a noisy alert.
        mockHighNoResponseRate();
        when(dashboardRepository.countSessionsWithUserMessages(any(), any())).thenReturn(1L);
        when(emailService.sendAlert(any())).thenReturn(1);
        alertService.checkAlertThresholds();
        verify(emailService, never()).sendAlert(any());
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
        // The alert has no AI selection filter, so it must evaluate all users.
        when(dashboardService.computeOverview(any(), any(), eq(IrisDashboardAiSelection.ALL)))
                .thenReturn(new IrisDashboardOverviewDTO(100, 50, 50.0, 200, 20, 15.0, 30, 15, 80.0, 20.0, 60.0, 15.0, 40, 10, 20, 5, 5.0, 4.0, 8.0, 10.0));
        when(dashboardService.computeStaleBefore(any(), any())).thenReturn(ZonedDateTime.of(2026, 5, 27, 11, 55, 0, 0, ZoneOffset.UTC).toInstant());
    }

    private void mockLowNoResponseRate() {
        // noResponseRate=5.0 which is <= default threshold of 10.0
        // The alert has no AI selection filter, so it must evaluate all users.
        when(dashboardService.computeOverview(any(), any(), eq(IrisDashboardAiSelection.ALL)))
                .thenReturn(new IrisDashboardOverviewDTO(100, 50, 50.0, 200, 20, 5.0, 10, 5, 80.0, 20.0, 60.0, 15.0, 40, 10, 20, 5, 5.0, 4.0, 8.0, 10.0));
        when(dashboardService.computeStaleBefore(any(), any())).thenReturn(ZonedDateTime.of(2026, 5, 27, 11, 55, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "interval", "lowThreshold", "highThreshold", "lookback", "cooldown", "maxWindow", "sessions", "messages" })
    void invalidConfigurationPreventsAcquiringLockAndSendingAlerts(String invalidField) {
        var alert = properties.getAlert();
        switch (invalidField) {
            case "interval" -> alert.setCheckIntervalMinutes(0);
            case "lowThreshold" -> alert.setNoResponseRateThreshold(-1);
            case "highThreshold" -> alert.setNoResponseRateThreshold(101);
            case "lookback" -> alert.setLookbackMinutes(properties.getStaleThresholdMinutes());
            case "cooldown" -> alert.setCooldownMinutes(alert.getCheckIntervalMinutes());
            case "maxWindow" -> alert.setLookbackMinutes(properties.getMaxQueryWindowDays() * 1440 + 1);
            case "sessions" -> alert.setMinimumEligibleSessions(0);
            case "messages" -> alert.setMinimumUserMessages(0);
            default -> throw new IllegalArgumentException(invalidField);
        }
        alertService.validateConfig();
        alertService.checkAlertThresholds();
        org.mockito.Mockito.verifyNoInteractions(distributedDataProvider);
        verify(emailService, never()).sendAlert(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "dev", "test", "email" })
    void runtimeGatesPreventQueriesAndLockAcquisition(String gate) {
        switch (gate) {
            case "dev" -> when(profileService.isDevActive()).thenReturn(true);
            case "test" -> alertService = new IrisUsageAlertService(profileService, properties, dashboardService, emailService, dashboardRepository, true, distributedDataProvider);
            case "email" -> when(emailService.canSendAlert()).thenReturn(false);
            default -> throw new IllegalArgumentException(gate);
        }
        alertService.checkAlertThresholds();
        org.mockito.Mockito.verifyNoInteractions(distributedDataProvider, dashboardService);
        verify(emailService, never()).sendAlert(any());
    }

    @Test
    void unavailableLockIsNotUnlocked() throws Exception {
        DistributedLock lock = distributedDataProvider.getLock("unused");
        org.mockito.Mockito.clearInvocations(distributedDataProvider);
        when(lock.tryLock(any())).thenReturn(false);
        alertService.checkAlertThresholds();
        verify(lock, never()).unlock();
        verify(emailService, never()).sendAlert(any());
    }

    @Test
    void insufficientMessagesUnlocksWithoutSending() {
        mockHighNoResponseRate();
        when(dashboardRepository.countUserMessages(any(), any())).thenReturn(0L);
        alertService.checkAlertThresholds();
        verify(distributedDataProvider.getLock("unused")).unlock();
        verify(emailService, never()).sendAlert(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    void failedOrEmptyDeliveryLeavesCooldownUnsetAndUnlocks(boolean throwsException) {
        mockHighNoResponseRate();
        if (throwsException) {
            when(emailService.sendAlert(any())).thenThrow(new IllegalStateException("mail unavailable"));
        }
        else {
            when(emailService.sendAlert(any())).thenReturn(0);
        }
        alertService.checkAlertThresholds();
        verify(emailService).sendAlert(any(IrisDashboardAlertDTO.class));
        verify(scheduleStateMap, never()).put(any(), any());
        verify(distributedDataProvider.getLock("unused")).unlock();
    }

    @Test
    void deliveredAlertContainsExactEligibleWindowAndCounts() {
        mockHighNoResponseRate();
        when(emailService.sendAlert(any())).thenReturn(2);
        alertService.checkAlertThresholds();
        var captured = org.mockito.ArgumentCaptor.forClass(IrisDashboardAlertDTO.class);
        verify(emailService).sendAlert(captured.capture());
        var dto = captured.getValue();
        org.assertj.core.api.Assertions.assertThat(dto.noResponseRate()).isEqualTo(15.0);
        org.assertj.core.api.Assertions.assertThat(dto.eligibleSessions()).isEqualTo(50L);
        org.assertj.core.api.Assertions.assertThat(dto.userMessageCount()).isEqualTo(100L);
        verify(scheduleStateMap).put("last-alert-sent-at", java.time.Instant.parse("2026-05-27T12:00:00Z"));
    }
}
