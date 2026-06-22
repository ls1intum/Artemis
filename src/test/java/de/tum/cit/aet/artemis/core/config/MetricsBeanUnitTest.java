package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.repository.StatisticsRepository;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseMetricsService;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MetricsBeanUnitTest {

    @Mock
    private TaskScheduler scheduler;

    @Mock
    private WebSocketMessageBrokerStats webSocketStats;

    @Mock
    private SimpUserRegistry userRegistry;

    @Mock
    private WebSocketHandler webSocketHandler;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private ExerciseMetricsService exerciseMetricsService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private BuildJobRepository buildJobRepository;

    private MeterRegistry meterRegistry;

    private MetricsBean metricsBean;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        when(profileService.isSchedulingActive()).thenReturn(true);
        when(profileService.isLocalCIActive()).thenReturn(true);
        when(profileService.isProfileActive(SPRING_PROFILE_TEST)).thenReturn(true);
        when(userRepository.findAllActiveAdminLogins()).thenReturn(Set.<String>of());

        metricsBean = new MetricsBean(meterRegistry, scheduler, webSocketStats, userRegistry, webSocketHandler, List.of(), Optional.empty(), exerciseRepository,
                exerciseMetricsService, Optional.empty(), courseRepository, userRepository, statisticsRepository, profileService, Optional.empty(), buildJobRepository);
        metricsBean.applicationReady();
    }

    @Test
    void shouldExposeFailedBuildsFromBuildJobStatistics() {
        when(buildJobRepository.getBuildJobsResultsStatistics(any(ZonedDateTime.class), isNull())).thenReturn(List.of(new BuildJobResultCountDTO(BuildStatus.FAILED, 2),
                new BuildJobResultCountDTO(BuildStatus.ERROR, 3), new BuildJobResultCountDTO(BuildStatus.MISSING, 1), new BuildJobResultCountDTO(BuildStatus.SUCCESSFUL, 4)));

        metricsBean.calculateBuildJobResultMetrics();

        assertThat(meterRegistry.get("artemis.global.buildjobs.failed").gauge().value()).isEqualTo(5);
        assertThat(meterRegistry.get("artemis.global.buildjobs.missing_results").gauge().value()).isEqualTo(1);
        verify(buildJobRepository).getBuildJobsResultsStatistics(any(ZonedDateTime.class), isNull());
    }
}
