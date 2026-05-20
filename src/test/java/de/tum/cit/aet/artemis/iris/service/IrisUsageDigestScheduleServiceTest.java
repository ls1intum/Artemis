package de.tum.cit.aet.artemis.iris.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;

@ExtendWith(MockitoExtension.class)
class IrisUsageDigestScheduleServiceTest {

    @Mock
    private IrisAdminDashboardService dashboardService;

    @Mock
    private ProfileService profileService;

    @Mock
    private MailSendingService mailSendingService;

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private IMap<String, Instant> scheduleStateMap;

    private IrisDashboardProperties properties;

    private IrisUsageDigestScheduleService digestScheduleService;

    private Map<String, Instant> scheduleState;

    @BeforeEach
    void setUp() {
        properties = new IrisDashboardProperties();
        scheduleState = new HashMap<>();
        digestScheduleService = new IrisUsageDigestScheduleService(dashboardService, properties, profileService, mailSendingService, hazelcastInstance);
        ReflectionTestUtils.setField(digestScheduleService, "adminEmail", "admin@example.org");
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    void sendsDigestToInfoContactFallback() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");
        when(dashboardService.getOverview(from, to, null)).thenReturn(overview());

        digestScheduleService.sendDigestForWindow(from, to);

        ArgumentCaptor<User> recipientCaptor = ArgumentCaptor.forClass(User.class);
        verify(mailSendingService).buildAndSendAsync(recipientCaptor.capture(), eq("email.irisDashboard.digest.title"), eq("mail/irisDashboardDigest"), anyMap());
        org.assertj.core.api.Assertions.assertThat(recipientCaptor.getValue().getEmail()).isEqualTo("admin@example.org");
    }

    @Test
    void sendsDigestToAlertRecipientFallback() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");
        properties.getAlert().setRecipients(java.util.List.of("alert@example.org"));
        when(dashboardService.getOverview(from, to, null)).thenReturn(overview());

        digestScheduleService.sendDigestForWindow(from, to);

        ArgumentCaptor<User> recipientCaptor = ArgumentCaptor.forClass(User.class);
        verify(mailSendingService).buildAndSendAsync(recipientCaptor.capture(), eq("email.irisDashboard.digest.title"), eq("mail/irisDashboardDigest"), anyMap());
        org.assertj.core.api.Assertions.assertThat(recipientCaptor.getValue().getEmail()).isEqualTo("alert@example.org");
    }

    @Test
    void scheduledDigestSkipsWhenSchedulingProfileIsInactive() {
        digestScheduleService.sendDailyDigest();

        verify(dashboardService, org.mockito.Mockito.never()).getOverview(any(), any(), any());
    }

    @Test
    void scheduledDigestRunsOncePerWindow() {
        TimeUtil.setClock(Clock.fixed(Instant.parse("2026-01-02T07:00:00Z"), ZoneOffset.UTC));
        properties.getDigest().setEnabled(true);
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(hazelcastInstance.<String, Instant>getMap("iris-dashboard-schedule-state")).thenReturn(scheduleStateMap);
        when(scheduleStateMap.containsKey(anyString())).thenAnswer(invocation -> scheduleState.containsKey(invocation.getArgument(0)));
        when(scheduleStateMap.put(anyString(), any(Instant.class), eq(3L), eq(java.util.concurrent.TimeUnit.DAYS))).thenAnswer(invocation -> {
            scheduleState.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        });
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");
        when(dashboardService.getOverview(from, to, null)).thenReturn(overview());

        digestScheduleService.sendDailyDigest();
        digestScheduleService.sendDailyDigest();

        verify(mailSendingService, times(1)).buildAndSendAsync(org.mockito.ArgumentMatchers.any(), eq("email.irisDashboard.digest.title"), eq("mail/irisDashboardDigest"),
                anyMap());
    }

    private static IrisDashboardOverviewDTO overview() {
        return new IrisDashboardOverviewDTO(1, 1, 100, 2, 1, 1, 1, 0, 0, 0, 100, 0, 50, 0, 1, 0, 2, 2, 2, 0.001);
    }
}
