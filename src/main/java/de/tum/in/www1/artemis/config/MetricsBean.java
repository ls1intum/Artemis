package de.tum.in.www1.artemis.config;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.*;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import com.zaxxer.hikari.HikariDataSource;

import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StatisticsRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MetricsBean {

    private final Logger log = LoggerFactory.getLogger(MetricsBean.class);

    private static final String ARTEMIS_HEALTH_NAME = "artemis.health";

    private static final String ARTEMIS_HEALTH_DESCRIPTION = "Artemis Health Indicator";

    private static final String ARTEMIS_HEALTH_TAG = "healthindicator";

    private static final int LOGGING_DELAY_SECONDS = 10;

    private final MeterRegistry meterRegistry;

    private final Environment env;

    private final TaskScheduler taskScheduler;

    private final WebSocketMessageBrokerStats webSocketStats;

    private final SimpUserRegistry userRegistry;

    private final WebSocketHandler webSocketHandler;

    private final ExerciseRepository exerciseRepository;

    private final ExamRepository examRepository;

    private final StatisticsRepository statisticsRepository;

    public MetricsBean(MeterRegistry meterRegistry, Environment env, TaskScheduler taskScheduler, WebSocketMessageBrokerStats webSocketStats, SimpUserRegistry userRegistry,
            WebSocketHandler websocketHandler, List<HealthContributor> healthContributors, Optional<HikariDataSource> hikariDataSource, ExerciseRepository exerciseRepository,
            ExamRepository examRepository, StatisticsRepository statisticsRepository) {
        this.meterRegistry = meterRegistry;
        this.env = env;
        this.taskScheduler = taskScheduler;
        this.webSocketStats = webSocketStats;
        this.userRegistry = userRegistry;
        this.webSocketHandler = websocketHandler;
        this.exerciseRepository = exerciseRepository;
        this.examRepository = examRepository;
        this.statisticsRepository = statisticsRepository;

        registerHealthContributors(healthContributors);
        registerWebsocketMetrics();
        registerExerciseAndExamMetrics();
        // the data source is optional as it is not used during testing
        hikariDataSource.ifPresent(this::registerDatasourceMetrics);
    }

    /**
     * initialize the websocket logging
     */
    @PostConstruct
    public void init() {
        // using Autowired leads to a weird bug, because the order of the method execution is changed. This somehow prevents messages send to single clients
        // later one, e.g. in the code editor. Therefore, we call this method here directly to get a reference and adapt the logging period!
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        // Note: this mechanism prevents that this is logged during testing
        if (activeProfiles.contains("websocketLog")) {
            webSocketStats.setLoggingPeriod(LOGGING_DELAY_SECONDS * 1000L);
            taskScheduler.scheduleAtFixedRate(() -> {
                final var connectedUsers = userRegistry.getUsers();
                final var subscriptionCount = connectedUsers.stream().flatMap(simpUser -> simpUser.getSessions().stream()).map(simpSession -> simpSession.getSubscriptions().size())
                        .reduce(0, Integer::sum);
                log.info("Currently connect users {} with active websocket subscriptions: {}", connectedUsers.size(), subscriptionCount);
            }, LOGGING_DELAY_SECONDS * 1000L);
        }
    }

    private void registerHealthContributors(List<HealthContributor> healthContributors) {
        // Publish the health status for each HealthContributor one Gauge with name ARTEMIS_HEALTH_NAME that has several values (one for each HealthIndicator),
        // using different values for the ARTEMIS_HEALTH_TAG tag
        for (HealthContributor healthContributor : healthContributors) {
            // For most HealthContributors, there is only one HealthIndicator that can directly be published.
            // The health status gets mapped to a double value, as only doubles can be returned by a Gauge.
            if (healthContributor instanceof HealthIndicator healthIndicator) {
                Gauge.builder(ARTEMIS_HEALTH_NAME, healthIndicator, h -> mapHealthToDouble(h.health())).strongReference(true).description(ARTEMIS_HEALTH_DESCRIPTION)
                        .tag(ARTEMIS_HEALTH_TAG, healthIndicator.getClass().getSimpleName().toLowerCase()).register(meterRegistry);
            }

            // The DiscoveryCompositeHealthContributor can consist of several HealthIndicators, so they must all be published
            if (healthContributor instanceof DiscoveryCompositeHealthContributor discoveryCompositeHealthContributor) {
                for (NamedContributor<HealthContributor> discoveryHealthContributor : discoveryCompositeHealthContributor) {
                    if (discoveryHealthContributor.getContributor() instanceof HealthIndicator healthIndicator) {
                        Gauge.builder(ARTEMIS_HEALTH_NAME, healthIndicator, h -> mapHealthToDouble(h.health())).strongReference(true).description(ARTEMIS_HEALTH_DESCRIPTION)
                                .tag(ARTEMIS_HEALTH_TAG, discoveryHealthContributor.getName().toLowerCase()).register(meterRegistry);
                    }
                }
            }
        }
    }

    private void registerWebsocketMetrics() {
        // Publish the number of currently (via WebSockets) connected users
        Gauge.builder("artemis.instance.websocket.users", webSocketHandler, MetricsBean::extractWebsocketUserCount).strongReference(true)
                .description("Number of users connected to this Artemis instance").register(meterRegistry);
    }

    private void registerExerciseAndExamMetrics() {
        int[] ranges = { 5, 15, 30, 45, 60, 120 };
        for (int range : ranges) {
            registerExerciseMetrics(range);
            registerExamMetrics(range);
        }
    }

    private void registerExerciseMetrics(int range) {
        for (var exerciseType : ExerciseType.values()) {
            Gauge.builder("artemis.scheduled.exercises.due.count", () -> this.getUpcomingDueExercisesCount(range, exerciseType)).strongReference(true)
                    .tag("range", String.valueOf(range)).tag("exerciseType", exerciseType.toString()).description("Number of exercises ending within the next minutes")
                    .register(meterRegistry);

            Gauge.builder("artemis.scheduled.exercises.due.student_multiplier", () -> this.getUpcomingDueExercisesCountWithStudentMultiplier(range, exerciseType))
                    .strongReference(true).tag("range", String.valueOf(range)).tag("exerciseType", exerciseType.toString())
                    .description("Number of exercises ending within the next minutes multiplied with students in the course").register(meterRegistry);

            Gauge.builder("artemis.scheduled.exercises.due.student_multiplier.active.14",
                    () -> this.getUpcomingDueExercisesCountWithActiveStudentMultiplier(range, exerciseType, 14)).strongReference(true).tag("range", String.valueOf(range))
                    .tag("exerciseType", exerciseType.toString()).description("Number of exercises ending within the next minutes multiplied with students in the course")
                    .register(meterRegistry);

            Gauge.builder("artemis.scheduled.exercises.release.count", () -> this.getUpcomingReleasedExercisesCount(range, exerciseType)).strongReference(true)
                    .tag("range", String.valueOf(range)).tag("exerciseType", exerciseType.toString()).description("Number of exercises starting within the next minutes")
                    .register(meterRegistry);

            Gauge.builder("artemis.scheduled.exercises.release.student_multiplier", () -> this.getUpcomingReleasedExercisesCountWithStudentMultiplier(range, exerciseType))
                    .strongReference(true).tag("range", String.valueOf(range)).tag("exerciseType", exerciseType.toString())
                    .description("Number of exercises starting within the next minutes multiplied with students in the course").register(meterRegistry);

            Gauge.builder("artemis.scheduled.exercises.release.student_multiplier.active.14",
                    () -> this.getUpcomingReleasedExercisesCountWithActiveStudentMultiplier(range, exerciseType, 14)).strongReference(true).tag("range", String.valueOf(range))
                    .tag("exerciseType", exerciseType.toString()).description("Number of exercises starting within the next minutes multiplied with students in the course")
                    .register(meterRegistry);
        }
    }

    private void registerExamMetrics(int range) {
        Gauge.builder("artemis.scheduled.exams.due.count", range, this::getUpcomingEndingExamCount).strongReference(true).tag("range", String.valueOf(range))
                .description("Number of exams ending within the next minutes").register(meterRegistry);

        Gauge.builder("artemis.scheduled.exams.due.student_multiplier", range, this::getUpcomingEndingExamCountWithStudentMultiplier).strongReference(true)
                .tag("range", String.valueOf(range)).description("Number of exams ending within the next minutes multiplied with students in the course").register(meterRegistry);

        Gauge.builder("artemis.scheduled.exams.release.count", range, this::getUpcomingStartingExamCount).strongReference(true).tag("range", String.valueOf(range))
                .description("Number of exams starting within the next minutes").register(meterRegistry);

        Gauge.builder("artemis.scheduled.exams.release.student_multiplier", range, this::getUpcomingStartingExamCountWithStudentMultiplier).strongReference(true)
                .tag("range", String.valueOf(range)).description("Number of exams starting within the next minutes multiplied with students in the course").register(meterRegistry);
    }

    private static double extractWebsocketUserCount(WebSocketHandler webSocketHandler) {
        if (webSocketHandler instanceof SubProtocolWebSocketHandler subProtocolWebSocketHandler) {
            return subProtocolWebSocketHandler.getStats().getWebSocketSessions();
        }
        return -1;
    }

    private double getUpcomingDueExercisesCount(int minutes, ExerciseType exerciseType) {
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        return exerciseRepository.countExercisesWithCurrentOrUpcomingDueDateWithinTimeRange(now, endDate, exerciseType.getExerciseClass());
    }

    private double getUpcomingDueExercisesCountWithStudentMultiplier(int minutes, ExerciseType exerciseType) {
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        return exerciseRepository.countStudentsInExercisesWithCurrentOrUpcomingDueDateWithinTimeRange(now, endDate, exerciseType.getExerciseClass());
    }

    private double getUpcomingDueExercisesCountWithActiveStudentMultiplier(int minutes, ExerciseType exerciseType, int numberOfDaysToCountAsActive) {
        SecurityUtils.setAuthorizationObject();

        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);

        var activeUsers = statisticsRepository.getActiveUsers(ZonedDateTime.now().minusDays(numberOfDaysToCountAsActive), ZonedDateTime.now());
        var activeUserNames = activeUsers.stream().map(StatisticsEntry::getUsername).collect(Collectors.toList());

        return exerciseRepository.countActiveStudentsInExercisesWithCurrentOrUpcomingDueDateWithinTimeRange(now, endDate, exerciseType.getExerciseClass(), activeUserNames);
    }

    private double getUpcomingReleasedExercisesCount(int minutes, ExerciseType exerciseType) {
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        return exerciseRepository.countExercisesWithCurrentOrUpcomingReleaseDateWithinTimeRange(now, endDate, exerciseType.getExerciseClass());
    }

    private double getUpcomingReleasedExercisesCountWithStudentMultiplier(int minutes, ExerciseType exerciseType) {
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        return exerciseRepository.countStudentsInExercisesWithCurrentOrUpcomingReleaseDateWithinTimeRange(now, endDate, exerciseType.getExerciseClass());
    }

    private double getUpcomingReleasedExercisesCountWithActiveStudentMultiplier(int minutes, ExerciseType exerciseType, int numberOfDaysToCountAsActive) {
        SecurityUtils.setAuthorizationObject();

        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);

        var activeUsers = statisticsRepository.getActiveUsers(ZonedDateTime.now().minusDays(numberOfDaysToCountAsActive), ZonedDateTime.now());
        var activeUserNames = activeUsers.stream().map(StatisticsEntry::getUsername).collect(Collectors.toList());

        return exerciseRepository.countActiveStudentsInExercisesWithCurrentOrUpcomingReleaseDateWithinTimeRange(now, endDate, exerciseType.getExerciseClass(), activeUserNames);
    }

    private double getUpcomingEndingExamCount(int minutes) {
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        return examRepository.countExamsWithEndDateGreaterThanEqualButLessOrEqualThan(now, endDate);
    }

    private double getUpcomingEndingExamCountWithStudentMultiplier(int minutes) {
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        return examRepository.countExamUsersInExamsWithEndDateGreaterThanEqualButLessOrEqualThan(now, endDate);
    }

    private double getUpcomingStartingExamCount(int minutes) {
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        return examRepository.countExamsWithStartDateGreaterThanEqualButLessOrEqualThan(now, endDate);
    }

    private double getUpcomingStartingExamCountWithStudentMultiplier(int minutes) {
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        return examRepository.countExamUsersInExamsWithStartDateGreaterThanEqualButLessOrEqualThan(now, endDate);
    }

    private void registerDatasourceMetrics(HikariDataSource dataSource) {
        dataSource.setMetricRegistry(meterRegistry);
    }

    /**
     * Maps the health status to a double
     *
     * @param health the Health whose status should be mapped
     * @return a double corresponding to the health status
     */
    private double mapHealthToDouble(Health health) {
        return switch (health.getStatus().getCode()) {
            case "UP" -> 1;
            case "DOWN" -> 0;
            case "OUT_OF_SERVICE" -> -1;
            case "UNKNOWN" -> -2;
            default -> -3;
        };
    }
}
