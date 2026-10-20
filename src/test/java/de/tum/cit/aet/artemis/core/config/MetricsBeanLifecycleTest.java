package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

import com.zaxxer.hikari.HikariDataSource;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.repository.StatisticsRepository;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseMetricsService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MetricsBeanLifecycleTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    private final ProfileService profiles = mock(ProfileService.class);

    private final WebSocketMessageBrokerStats websocketStats = mock(WebSocketMessageBrokerStats.class);

    private final SimpUserRegistry users = mock(SimpUserRegistry.class);

    private final ExerciseTestRepository exercises = mock(ExerciseTestRepository.class);

    private final CourseTestRepository courses = mock(CourseTestRepository.class);

    private final UserTestRepository accounts = mock(UserTestRepository.class);

    private final StatisticsRepository statistics = mock(StatisticsRepository.class);

    private final BuildJobTestRepository jobs = mock(BuildJobTestRepository.class);

    @ParameterizedTest
    @CsvSource(value = { "UP,1", "DOWN,0", "OUT_OF_SERVICE,-1", "UNKNOWN,-2", "CUSTOM,-3", "NULL,-3" }, nullValues = "NULL")
    void healthGaugesExposeStatusMappingEvenWhenScheduledMetricsAreDisabled(String status, double expected) {
        var indicator = mock(HealthIndicator.class);
        when(indicator.health()).thenReturn(status == null ? null : Health.status(status).build());
        var bean = bean(List.of(indicator, org.springframework.boot.health.contributor.CompositeHealthContributor.fromMap(java.util.Map.of())), Optional.empty(), Optional.empty());
        bean.applicationReady();
        assertThat(registry.get("artemis.health").gauge().value()).isEqualTo(expected);
        bean.calculateActiveUserMetrics();
        bean.recalculateMetrics();
        bean.calculateBuildJobResultMetrics();
        bean.updatePublicArtemisMetrics();
        verifyNoInteractions(exercises, courses, accounts, statistics, jobs);
    }

    @Test
    void localCiGaugesSumOnlyUnpausedAgentCapacityAndExposeQueueCounts() {
        when(profiles.isLocalCIActive()).thenReturn(true);
        var distributed = mock(DistributedDataAccessService.class);
        when(distributed.getProcessingJobsSize()).thenReturn(2);
        when(distributed.getQueuedJobsSize()).thenReturn(3);
        when(distributed.getBuildAgentInformationSize()).thenReturn(4);
        when(distributed.getResultQueueSize()).thenReturn(5);
        when(distributed.getBuildAgentInformation()).thenReturn(
                List.of(agent(BuildAgentStatus.ACTIVE, 2), agent(BuildAgentStatus.PAUSED, 10), agent(BuildAgentStatus.IDLE, 3), agent(BuildAgentStatus.SELF_PAUSED, 4)));
        var bean = bean(List.of(), Optional.of(distributed), Optional.empty());
        bean.applicationReady();
        assertThat(registry.get("artemis.global.localci.running").gauge().value()).isEqualTo(2);
        assertThat(registry.get("artemis.global.localci.queued").gauge().value()).isEqualTo(3);
        assertThat(registry.get("artemis.global.localci.agents").gauge().value()).isEqualTo(4);
        assertThat(registry.get("artemis.global.localci.resultsInQueue").gauge().value()).isEqualTo(5);
        assertThat(registry.get("artemis.global.localci.maxConcurrentBuilds").gauge().value()).isEqualTo(9);
        assertThat(registry.get("artemis.instance.websocket.sessions").gauge().value()).isEqualTo(-1);
    }

    @Test
    void explicitDisableOverridesSchedulingProfileAndLogsWebsocketCountsThroughCapturedTask() {
        when(profiles.isSchedulingActive()).thenReturn(true);
        when(users.getUsers()).thenReturn(Set.of());
        var bean = bean(List.of(), Optional.empty(), Optional.empty());
        ReflectionTestUtils.setField(bean, "scheduledMetricsEnabled", false);
        ReflectionTestUtils.setField(bean, "websocketLogEnabled", true);
        bean.applicationReady();
        verify(websocketStats).setLoggingPeriod(20000);
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(task.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(20)));
        task.getValue().run();
        verify(users).getUsers();
        verifyNoInteractions(exercises, courses, accounts, statistics, jobs);
    }

    @Test
    void existingDatasourceMetricsTrackerPreventsDuplicateMetricRegistryInstallation() {
        var datasource = mock(HikariDataSource.class);
        when(datasource.getMetricsTrackerFactory()).thenReturn(mock(com.zaxxer.hikari.metrics.MetricsTrackerFactory.class));
        bean(List.of(), Optional.empty(), Optional.of(datasource)).applicationReady();
        verify(datasource, org.mockito.Mockito.never()).setMetricRegistry(any());
    }

    private BuildAgentInformation agent(BuildAgentStatus status, int capacity) {
        return new BuildAgentInformation(new BuildAgentDTO("agent", "address", "agent"), capacity, 0, List.of(), status, null, null, 0);
    }

    private MetricsBean bean(List<HealthContributor> health, Optional<DistributedDataAccessService> distributed, Optional<HikariDataSource> datasource) {
        return new MetricsBean(registry, scheduler, websocketStats, users, mock(WebSocketHandler.class), health, datasource, exercises, mock(ExerciseMetricsService.class),
                Optional.empty(), courses, accounts, statistics, profiles, distributed, jobs);
    }
}
