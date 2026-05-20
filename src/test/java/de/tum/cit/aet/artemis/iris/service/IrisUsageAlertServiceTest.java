package de.tum.cit.aet.artemis.iris.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.springframework.context.ApplicationContext;
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
class IrisUsageAlertServiceTest {

    @Mock
    private IrisAdminDashboardService dashboardService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ProfileService profileService;

    @Mock
    private MailSendingService mailSendingService;

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private IMap<String, Instant> scheduleStateMap;

    private IrisDashboardProperties properties;

    private IrisUsageAlertService alertService;

    private Map<String, Instant> scheduleState;

    @BeforeEach
    void setUp() {
        properties = new IrisDashboardProperties();
        properties.getAlert().setMinimumActiveSessions(2);
        properties.getAlert().setMinimumUserMessages(20);
        properties.getAlert().setNoResponseRateThreshold(10);
        scheduleState = new HashMap<>();
        when(hazelcastInstance.<String, Instant>getMap("iris-dashboard-schedule-state")).thenReturn(scheduleStateMap);
        when(scheduleStateMap.get(anyString())).thenAnswer(invocation -> scheduleState.get(invocation.getArgument(0)));
        alertService = new IrisUsageAlertService(applicationContext, properties, profileService, mailSendingService, hazelcastInstance);
        ReflectionTestUtils.setField(alertService, "adminEmail", "admin@example.org");
        TimeUtil.setClock(Clock.fixed(Instant.parse("2026-01-01T02:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    void doesNotSendAlertWhenMinimumUserMessagesAreNotMet() {
        when(applicationContext.getBean(IrisAdminDashboardService.class)).thenReturn(dashboardService);
        when(dashboardService.getOverview(Instant.parse("2026-01-01T01:00:00Z"), Instant.parse("2026-01-01T02:00:00Z"), null))
                .thenReturn(new IrisDashboardOverviewDTO(2, 2, 100, 19, 2, 19, 2, 50, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        alertService.checkAndSendAlert();

        verify(mailSendingService, never()).buildAndSendAsync(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void sendsAlertAndAppliesCooldown() {
        when(applicationContext.getBean(IrisAdminDashboardService.class)).thenReturn(dashboardService);
        when(scheduleStateMap.put(anyString(), any(Instant.class))).thenAnswer(invocation -> scheduleState.put(invocation.getArgument(0), invocation.getArgument(1)));
        when(dashboardService.getOverview(Instant.parse("2026-01-01T01:00:00Z"), Instant.parse("2026-01-01T02:00:00Z"), null))
                .thenReturn(new IrisDashboardOverviewDTO(2, 2, 100, 20, 2, 20, 2, 50, 3, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        alertService.checkAndSendAlert();
        alertService.checkAndSendAlert();

        ArgumentCaptor<User> recipientCaptor = ArgumentCaptor.forClass(User.class);
        verify(mailSendingService).buildAndSendAsync(recipientCaptor.capture(), eq("email.irisDashboard.alert.title"), eq("mail/irisDashboardAlert"), anyMap());
        org.assertj.core.api.Assertions.assertThat(recipientCaptor.getValue().getEmail()).isEqualTo("admin@example.org");
    }
}
