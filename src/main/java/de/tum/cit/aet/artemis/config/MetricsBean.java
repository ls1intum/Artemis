package de.tum.cit.aet.artemis.config;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import com.zaxxer.hikari.HikariDataSource;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseType;
import de.tum.cit.aet.artemis.domain.exam.Exam;
import de.tum.cit.aet.artemis.domain.metrics.ExerciseTypeMetricsEntry;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.ExamRepository;
import de.tum.cit.aet.artemis.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.repository.StatisticsRepository;
import de.tum.cit.aet.artemis.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.security.SecurityUtils;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.connectors.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.service.connectors.localci.dto.BuildAgentInformation;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

@Profile(PROFILE_CORE)
@Component
public class MetricsBean {

    private static final Logger log = LoggerFactory.getLogger(MetricsBean.class);

    private static final String ARTEMIS_HEALTH_NAME = "artemis.health";

    private static final String ARTEMIS_HEALTH_DESCRIPTION = "Artemis Health Indicator";

    private static final String ARTEMIS_HEALTH_TAG = "healthindicator";

    private static final String NO_SEMESTER_TAG = "No semester";

    private static final int LOGGING_DELAY_SECONDS = 10;

    /**
     * Some metrics (e.g. the number of upcoming exercises) are calculated for multiple lookahead periods.
     * Each period/range is exposed as metrics with a according tag "range"
     * NOTE: we reduced the intervals temporarily until more intervals are necessary
     */
    private static final int[] MINUTE_RANGES_LOOKAHEAD = { 15 };

    private final MeterRegistry meterRegistry;

    private final TaskScheduler scheduler;

    private final WebSocketMessageBrokerStats webSocketStats;

    private final SimpUserRegistry userRegistry;

    private final WebSocketHandler webSocketHandler;

    private final ExerciseRepository exerciseRepository;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final StatisticsRepository statisticsRepository;

    private final ProfileService profileService;

    private final List<HealthContributor> healthContributors;

    private final Optional<HikariDataSource> hikariDataSource;

    private final Optional<SharedQueueManagementService> localCIBuildJobQueueService;

    /**
     * List that stores active usernames (users with a submission within the last 14 days) which is refreshed
     * every 60 minutes.
     */
    private List<String> cachedActiveUserNames;

    // Public metrics
    private final AtomicInteger activeCoursesGauge = new AtomicInteger(0);

    private final AtomicInteger coursesGauge = new AtomicInteger(0);

    private final AtomicInteger activeExamsGauge = new AtomicInteger(0);

    private final AtomicInteger examsGauge = new AtomicInteger(0);

    private MultiGauge activeUserMultiGauge;

    private MultiGauge studentsCourseGauge;

    private MultiGauge studentsExamGauge;

    // Internal metrics: Exercises
    private MultiGauge exerciseGauge;

    private MultiGauge activeExerciseGauge;

    private MultiGauge dueExerciseGauge;

    private MultiGauge dueExerciseStudentMultiplierGauge;

    private MultiGauge dueExerciseStudentMultiplierActive14DaysGauge;

    private MultiGauge releaseExerciseGauge;

    private MultiGauge releaseExerciseStudentMultiplierGauge;

    private MultiGauge releaseExerciseStudentMultiplierActive14DaysGauge;

    // Internal metrics: Exams
    private MultiGauge dueExamGauge;

    private MultiGauge dueExamStudentMultiplierGauge;

    private MultiGauge releaseExamGauge;

    private MultiGauge releaseExamStudentMultiplierGauge;

    private MultiGauge activeAdminsGauge;

    private boolean scheduledMetricsEnabled = false;

    public MetricsBean(MeterRegistry meterRegistry, @Qualifier("taskScheduler") TaskScheduler scheduler, WebSocketMessageBrokerStats webSocketStats, SimpUserRegistry userRegistry,
            WebSocketHandler websocketHandler, List<HealthContributor> healthContributors, Optional<HikariDataSource> hikariDataSource, ExerciseRepository exerciseRepository,
            StudentExamRepository studentExamRepository, ExamRepository examRepository, CourseRepository courseRepository, UserRepository userRepository,
            StatisticsRepository statisticsRepository, ProfileService profileService, Optional<SharedQueueManagementService> localCIBuildJobQueueService) {
        this.meterRegistry = meterRegistry;
        this.scheduler = scheduler;
        this.webSocketStats = webSocketStats;
        this.userRegistry = userRegistry;
        this.webSocketHandler = websocketHandler;
        this.healthContributors = healthContributors;
        this.hikariDataSource = hikariDataSource;
        this.exerciseRepository = exerciseRepository;
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.statisticsRepository = statisticsRepository;
        this.profileService = profileService;
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
    }

    /**
     * Event listener method that is invoked when the application is ready. It registers various health and metric
     * contributors, and conditionally enables metrics based on active profiles.
     *
     * <p>
     * Specifically, this method performs the following actions:
     * <ul>
     * <li>Registers health contributors.</li>
     * <li>Registers websocket metrics.</li>
     * <li>If the scheduling profile is active:
     * <ul>
     * <li>Enables scheduled metrics.</li>
     * <li>Calculates cached active user names.</li>
     * <li>Registers exercise and exam metrics.</li>
     * <li>Registers public Artemis metrics.</li>
     * </ul>
     * </li>
     * <li>If the local CI profile is active, registers local CI metrics.</li>
     * <li>Registers datasource metrics if the Hikari datasource is present.</li>
     * <li>If the websocket logging profile is active:
     * <ul>
     * <li>Sets the logging period for websocket statistics.</li>
     * <li>Schedules periodic logging of connected users and active websocket subscriptions.</li>
     * </ul>
     * </li>
     * </ul>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        registerHealthContributors(healthContributors);
        registerWebsocketMetrics();

        if (profileService.isSchedulingActive()) {
            // Should only be activated if the scheduling profile is present, because these metrics are the same for all instances
            this.scheduledMetricsEnabled = true;

            registerActiveAdminMetrics();
            registerExerciseAndExamMetrics();
            registerPublicArtemisMetrics();

            // Initial calculation is done in constructor to ensure the values are present before the first metrics are calculated
            calculateActiveUserMetrics();
        }

        if (profileService.isLocalCiActive()) {
            registerLocalCIMetrics();
        }

        // the data source is optional as it is not used during testing
        hikariDataSource.ifPresent(this::registerDatasourceMetrics);

        // initialize the websocket logging
        // using Autowired leads to a weird bug, because the order of the method execution is changed. This somehow prevents messages send to single clients
        // later one, e.g. in the code editor. Therefore, we call this method here directly to get a reference and adapt the logging period!
        // Note: this mechanism prevents that this is logged during testing
        if (profileService.isProfileActive("websocketLog")) {
            webSocketStats.setLoggingPeriod(LOGGING_DELAY_SECONDS * 1000L);
            scheduler.scheduleAtFixedRate(() -> {
                final var connectedUsers = userRegistry.getUsers();
                final var subscriptionCount = connectedUsers.stream().flatMap(simpUser -> simpUser.getSessions().stream()).map(simpSession -> simpSession.getSubscriptions().size())
                        .reduce(0, Integer::sum);
                log.info("Currently connect users {} with active websocket subscriptions: {}", connectedUsers.size(), subscriptionCount);
            }, Duration.of(LOGGING_DELAY_SECONDS, ChronoUnit.SECONDS));
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

    /**
     * Register metrics for local CI information
     */
    private void registerLocalCIMetrics() {
        // Publish the number of running builds
        Gauge.builder("artemis.global.localci.running", localCIBuildJobQueueService, MetricsBean::extractRunningBuilds).strongReference(true)
                .description("Number of running builds").register(meterRegistry);

        // Publish the number of queued builds
        Gauge.builder("artemis.global.localci.queued", localCIBuildJobQueueService, MetricsBean::extractQueuedBuilds).strongReference(true).description("Number of queued builds")
                .register(meterRegistry);

        // Publish the number of build agents
        Gauge.builder("artemis.global.localci.agents", localCIBuildJobQueueService, MetricsBean::extractBuildAgents).strongReference(true)
                .description("Number of active build agents").register(meterRegistry);

        // Publish the number of max concurrent builds
        Gauge.builder("artemis.global.localci.maxConcurrentBuilds", localCIBuildJobQueueService, MetricsBean::extractMaxConcurrentBuilds).strongReference(true)
                .description("Number of max concurrent builds").register(meterRegistry);
    }

    private static int extractRunningBuilds(Optional<SharedQueueManagementService> sharedQueueManagementService) {
        return sharedQueueManagementService.map(queueManagementService -> queueManagementService.getBuildAgentInformation().stream()
                .map(buildAgentInformation -> buildAgentInformation.runningBuildJobs().size()).reduce(0, Integer::sum)).orElse(0);
    }

    private static int extractQueuedBuilds(Optional<SharedQueueManagementService> sharedQueueManagementService) {
        return sharedQueueManagementService.map(queueManagementService -> queueManagementService.getQueuedJobs().size()).orElse(0);
    }

    private static int extractBuildAgents(Optional<SharedQueueManagementService> sharedQueueManagementService) {
        return sharedQueueManagementService.map(queueManagementService -> queueManagementService.getBuildAgentInformation().size()).orElse(0);
    }

    private static int extractMaxConcurrentBuilds(Optional<SharedQueueManagementService> sharedQueueManagementService) {
        return sharedQueueManagementService.map(queueManagementService -> queueManagementService.getBuildAgentInformation().stream()
                .map(BuildAgentInformation::maxNumberOfConcurrentBuildJobs).reduce(0, Integer::sum)).orElse(0);
    }

    private void registerWebsocketMetrics() {
        // Publish the number of currently (via WebSockets) connected sessions
        Gauge.builder("artemis.instance.websocket.sessions", webSocketHandler, MetricsBean::extractWebsocketSessionCount).strongReference(true)
                .description("Number of sessions connected to this Artemis instance").register(meterRegistry);

        // Publish the number of currently (via WebSockets) connected users
        Gauge.builder("artemis.global.websocket.users", userRegistry, MetricsBean::extractWebsocketUserCount).strongReference(true)
                .description("Number of users connected to all Artemis instances").register(meterRegistry);

        // Publish the number of existing WS subscriptions
        Gauge.builder("artemis.global.websocket.subscriptions", userRegistry, MetricsBean::extractWebsocketSubscriptionCount).strongReference(true)
                .description("Number of subscriptions created on all Artemis instances").register(meterRegistry);
    }

    private static double extractWebsocketUserCount(SimpUserRegistry userRegistry) {
        return userRegistry.getUserCount();
    }

    private static double extractWebsocketSessionCount(WebSocketHandler webSocketHandler) {
        if (webSocketHandler instanceof SubProtocolWebSocketHandler subProtocolWebSocketHandler) {
            return subProtocolWebSocketHandler.getStats().getWebSocketSessions();
        }
        return -1;
    }

    private static double extractWebsocketSubscriptionCount(SimpUserRegistry userRegistry) {
        return userRegistry.getUsers().stream().flatMap(user -> user.getSessions().stream()).map(session -> session.getSubscriptions().size()).reduce(0, Integer::sum);
    }

    /**
     * Register metrics for exercises and exams
     */
    private void registerExerciseAndExamMetrics() {
        dueExerciseGauge = MultiGauge.builder("artemis.scheduled.exercises.due.count").description("Number of exercises ending within the next minutes").register(meterRegistry);
        releaseExerciseGauge = MultiGauge.builder("artemis.scheduled.exercises.release.count").description("Number of exercises starting within the next minutes")
                .register(meterRegistry);

        dueExamGauge = MultiGauge.builder("artemis.scheduled.exams.due.count").description("Number of exams ending within the next minutes").register(meterRegistry);
        releaseExamGauge = MultiGauge.builder("artemis.scheduled.exams.release.count").description("Number of exams starting within the next minutes").register(meterRegistry);

        registerStudentExerciseMetrics();
        registerStudentExamMetrics();
    }

    /**
     * Register metrics for exercises, multiplied with the student that are enrolled for the exercise
     */
    private void registerStudentExerciseMetrics() {
        dueExerciseStudentMultiplierGauge = MultiGauge.builder("artemis.scheduled.exercises.due.student_multiplier")
                .description("Number of exercises ending within the next minutes multiplied with students in the course").register(meterRegistry);
        dueExerciseStudentMultiplierActive14DaysGauge = MultiGauge.builder("artemis.scheduled.exercises.due.student_multiplier.active.14")
                .description("Number of exercises ending within the next minutes multiplied with students in the course that have been active in the past 14 days")
                .register(meterRegistry);
        releaseExerciseStudentMultiplierGauge = MultiGauge.builder("artemis.scheduled.exercises.release.student_multiplier")
                .description("Number of exercises starting within the next minutes multiplied with students in the course").register(meterRegistry);
        releaseExerciseStudentMultiplierActive14DaysGauge = MultiGauge.builder("artemis.scheduled.exercises.release.student_multiplier.active.14")
                .description("Number of exercises starting within the next minutes multiplied with students in the course that have been active in the past 14 days")
                .register(meterRegistry);
    }

    /**
     * Register metrics for exams, multiplied with the student that are enrolled for the exam
     */
    private void registerStudentExamMetrics() {
        dueExamStudentMultiplierGauge = MultiGauge.builder("artemis.scheduled.exams.due.student_multiplier")
                .description("Number of exams ending within the next minutes multiplied with students in the course").register(meterRegistry);
        releaseExamStudentMultiplierGauge = MultiGauge.builder("artemis.scheduled.exams.release.student_multiplier")
                .description("Number of exams starting within the next minutes multiplied with students in the course").register(meterRegistry);
    }

    private void registerActiveAdminMetrics() {
        activeAdminsGauge = MultiGauge.builder("artemis.users.admins.active").description("User logins of active admin accounts").register(meterRegistry);
    }

    /**
     * Calculate active users (active within the last 14 days) and store them in a List.
     * The calculation is performed every 60 minutes.
     * The initial calculation is done in the constructor to ensure it is done BEFORE {@link #recalculateMetrics()}
     * is called.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000, initialDelay = 60 * 60 * 1000) // Every 60 minutes
    public void calculateActiveUserMetrics() {
        var startDate = System.currentTimeMillis();

        // The authorization object has to be set because this method is not called by a user but by the scheduler
        SecurityUtils.setAuthorizationObject();

        cachedActiveUserNames = statisticsRepository.getActiveUserNames(ZonedDateTime.now().minusDays(14), ZonedDateTime.now());

        updateActiveAdminsMetrics();

        log.debug("calculateCachedActiveUserLogins took {}ms", System.currentTimeMillis() - startDate);
    }

    /**
     * Update exams & exercise metrics.
     * The update (and recalculation) is performed every 5 minutes.
     * Only executed if the "scheduling"-profile is present.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 30 * 1000) // Every 5 minutes with an initial delay of 30 seconds
    public void recalculateMetrics() {
        if (!scheduledMetricsEnabled) {
            return;
        }
        var startDate = System.currentTimeMillis();

        // The authorization object has to be set because this method is not called by a user but by the scheduler
        SecurityUtils.setAuthorizationObject();

        // The active users are cached and updated every 60 minutes to reduce the database load

        // Exercise metrics
        updateMultiGaugeMetricsEntryForMinuteRanges(dueExerciseGauge, cachedActiveUserNames,
                (now, endDate, activeUserNamesUnused) -> exerciseRepository.countExercisesWithEndDateBetweenGroupByExerciseType(now, endDate));
        updateMultiGaugeMetricsEntryForMinuteRanges(dueExerciseStudentMultiplierGauge, cachedActiveUserNames,
                (now, endDate, activeUserNamesUnused) -> exerciseRepository.countStudentsInExercisesWithDueDateBetweenGroupByExerciseType(now, endDate));
        updateMultiGaugeMetricsEntryForMinuteRanges(dueExerciseStudentMultiplierActive14DaysGauge, cachedActiveUserNames,
                exerciseRepository::countActiveStudentsInExercisesWithDueDateBetweenGroupByExerciseType);

        updateMultiGaugeMetricsEntryForMinuteRanges(releaseExerciseGauge, cachedActiveUserNames,
                (now, endDate, activeUserNamesUnused) -> exerciseRepository.countExercisesWithReleaseDateBetweenGroupByExerciseType(now, endDate));
        updateMultiGaugeMetricsEntryForMinuteRanges(releaseExerciseStudentMultiplierGauge, cachedActiveUserNames,
                (now, endDate, activeUserNamesUnused) -> exerciseRepository.countStudentsInExercisesWithReleaseDateBetweenGroupByExerciseType(now, endDate));
        updateMultiGaugeMetricsEntryForMinuteRanges(releaseExerciseStudentMultiplierActive14DaysGauge, cachedActiveUserNames,
                exerciseRepository::countActiveStudentsInExercisesWithReleaseDateBetweenGroupByExerciseType);

        // Exam metrics
        updateMultiGaugeIntegerForMinuteRanges(dueExamGauge, examRepository::countExamsWithEndDateBetween);
        updateMultiGaugeIntegerForMinuteRanges(dueExamStudentMultiplierGauge, examRepository::countExamUsersInExamsWithEndDateBetween);

        updateMultiGaugeIntegerForMinuteRanges(releaseExamGauge, examRepository::countExamsWithStartDateBetween);
        updateMultiGaugeIntegerForMinuteRanges(releaseExamStudentMultiplierGauge, examRepository::countExamUsersInExamsWithStartDateBetween);

        log.debug("recalculateMetrics took {}ms", System.currentTimeMillis() - startDate);
    }

    /**
     * Get all users that currently have the role ADMIN and update the activeAdminsGauge.
     */
    private void updateActiveAdminsMetrics() {
        var activeAdmins = userRepository.findAllActiveAdminLogins();
        var results = new ArrayList<MultiGauge.Row<?>>();

        for (var activeAdmin : activeAdmins) {
            results.add(MultiGauge.Row.of(Tags.of("admin", activeAdmin), 1));
        }

        activeAdminsGauge.register(results, true);
    }

    @FunctionalInterface
    interface NowEndDateActiveUserNamesMetricsEntryFunction {

        /**
         * This interface is used to calculate and expose metrics that are based on
         * - the current date
         * - an end date (which is the current date + one of the values of MINUTE_RANGES_LOOKAHEAD), and
         * - a list of active users (that is, users that created a submission within the last 14 days).
         * <p>
         * The implementing method may decide to ignore certain arguments.
         * The method returns a list of ExerciseTypeMetricsEntries, that each correspond to an exercise type and a value.
         *
         * @param now             the current time
         * @param endDate         the end date that should be taken into consideration
         * @param activeUserNames a list of users that was active within the last 14 days
         * @return a list of ExerciseTypeMetricsEntry (one for each exercise type) - if for one exercise type no value is returned, 0 will be assumed
         */
        List<ExerciseTypeMetricsEntry> apply(ZonedDateTime now, ZonedDateTime endDate, List<String> activeUserNames);
    }

    @FunctionalInterface
    interface NowEndDateActiveUserNamesIntegerFunction {

        /**
         * This interface is used to calculate and expose metrics that are based on
         * - the current date, and
         * - an end date (which is the current date + one of the values of MINUTE_RANGES_LOOKAHEAD).
         * <p>
         * The implementing method may decide to ignore certain arguments.
         * The method returns an integer, representing the corresponding value.
         *
         * @param now     the current time
         * @param endDate the end date that should be taken into consideration
         * @return the corresponding value
         */
        Integer apply(ZonedDateTime now, ZonedDateTime endDate);
    }

    /**
     * Update the given multiGauge for each of the values of MINUTE_RANGES_LOOKAHEAD and the given function
     *
     * @param multiGauge               the gauge that should be updated
     * @param activeUserNames          a list of active users that the databaseRetrieveFunction may use
     * @param databaseRetrieveFunction a function that returns a list of ExerciseTypeMetricsEntries, one for each exercise type (if one exercise type is missing, 0 will be assumed)
     */
    private void updateMultiGaugeMetricsEntryForMinuteRanges(MultiGauge multiGauge, List<String> activeUserNames,
            NowEndDateActiveUserNamesMetricsEntryFunction databaseRetrieveFunction) {
        var now = ZonedDateTime.now();
        var results = new ArrayList<MultiGauge.Row<?>>();

        for (var minutes : MINUTE_RANGES_LOOKAHEAD) {
            var endDate = ZonedDateTime.now().plusMinutes(minutes);
            var result = databaseRetrieveFunction.apply(now, endDate, activeUserNames);
            extractExerciseTypeMetricsAndAddToMetricsResults(result, results, Tags.of("range", String.valueOf(minutes)));
        }

        multiGauge.register(results, true);
    }

    /**
     * Update the given multiGauge for each of the values of MINUTE_RANGES_LOOKAHEAD and the given function
     *
     * @param multiGauge               the gauge that should be updated
     * @param databaseRetrieveFunction a function that returns an integer
     */
    private void updateMultiGaugeIntegerForMinuteRanges(MultiGauge multiGauge, NowEndDateActiveUserNamesIntegerFunction databaseRetrieveFunction) {
        var now = ZonedDateTime.now();
        var results = new ArrayList<MultiGauge.Row<?>>();

        for (var minutes : MINUTE_RANGES_LOOKAHEAD) {
            var endDate = ZonedDateTime.now().plusMinutes(minutes);
            var result = databaseRetrieveFunction.apply(now, endDate);
            results.add(MultiGauge.Row.of(Tags.of("range", String.valueOf(minutes)), result));
        }

        multiGauge.register(results, true);
    }

    /**
     * Register publicly exposed metrics.
     */
    private void registerPublicArtemisMetrics() {
        SecurityUtils.setAuthorizationObject();

        activeUserMultiGauge = MultiGauge.builder("artemis.statistics.public.active_users").description("Number of active users within the last period, specified in days")
                .register(meterRegistry);

        Gauge.builder("artemis.statistics.public.active_courses", activeCoursesGauge::get).description("Number of active courses").register(meterRegistry);

        Gauge.builder("artemis.statistics.public.courses", coursesGauge::get).description("Number of courses").register(meterRegistry);

        studentsCourseGauge = MultiGauge.builder("artemis.statistics.public.course_students").description("Number of registered students per course").register(meterRegistry);

        Gauge.builder("artemis.statistics.public.active_exams", activeExamsGauge::get).description("Number of active exams").register(meterRegistry);

        Gauge.builder("artemis.statistics.public.exams", examsGauge::get).description("Number of exams").register(meterRegistry);

        studentsExamGauge = MultiGauge.builder("artemis.statistics.public.exam_students").description("Number of registered students per exam").register(meterRegistry);

        activeExerciseGauge = MultiGauge.builder("artemis.statistics.public.active_exercises").description("Number of active exercises by type").register(meterRegistry);

        exerciseGauge = MultiGauge.builder("artemis.statistics.public.exercises").description("Number of exercises by type").register(meterRegistry);
    }

    /**
     * Update artemis public Artemis metrics that are exposed via Prometheus.
     * The update (and recalculation) is performed every 60 minutes.
     * Only executed if the "scheduling"-profile is present.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000, initialDelay = 0) // Every 60 minutes
    public void updatePublicArtemisMetrics() {
        if (!scheduledMetricsEnabled) {
            return;
        }

        final long startDate = System.currentTimeMillis();

        // The authorization object has to be set because this method is not called by a user but by the scheduler
        SecurityUtils.setAuthorizationObject();

        final ZonedDateTime now = ZonedDateTime.now();

        final List<Course> courses = courseRepository.findAllActiveWithoutTestCourses(now);
        // We set the number of students once to prevent multiple queries for the same date
        courses.forEach(course -> course.setNumberOfStudents(userRepository.countByIsDeletedIsFalseAndGroupsContains(course.getStudentGroupName())));
        ensureCourseInformationIsSet(courses);

        final List<Long> courseIds = courses.stream().mapToLong(Course::getId).boxed().toList();
        final List<Exam> examsInActiveCourses = examRepository.findExamsInCourses(courseIds);

        // Update multi gauges
        updateStudentsCourseMultiGauge(courses);
        updateStudentsExamMultiGauge(examsInActiveCourses, courses);
        updateActiveUserMultiGauge(now);
        updateActiveExerciseMultiGauge();
        updateExerciseMultiGauge();

        // Update normal Gauges
        activeCoursesGauge.set(courses.size());
        coursesGauge.set((int) courseRepository.count());

        activeExamsGauge.set(examRepository.countAllActiveExams(now));
        examsGauge.set((int) examRepository.count());

        log.debug("updatePublicArtemisMetrics took {}ms", System.currentTimeMillis() - startDate);
    }

    private void updateActiveUserMultiGauge(ZonedDateTime now) {
        final Integer[] activeUserPeriodsInDays = new Integer[] { 1, 7, 14, 30 };

        // A mutable list is required here because otherwise the values can not be updated correctly
        final List<MultiGauge.Row<?>> gauges = Stream.of(activeUserPeriodsInDays).map(periodInDays -> {
            final Tags tags = Tags.of("period", periodInDays.toString());
            final long activeUsers = statisticsRepository.countActiveUsers(now.minusDays(periodInDays), now);
            return MultiGauge.Row.of(tags, activeUsers);
        }).collect(Collectors.toCollection(ArrayList::new));

        activeUserMultiGauge.register(gauges, true);
    }

    private void updateStudentsCourseMultiGauge(List<Course> activeCourses) {
        // A mutable list is required here because otherwise the values can not be updated correctly
        final List<MultiGauge.Row<?>> gauges = activeCourses.stream().map(course -> {
            final Tags tags = Tags.of("courseId", Long.toString(course.getId()), "courseName", course.getTitle(), "semester", course.getSemester());
            final long studentCount = course.getNumberOfStudents();
            return MultiGauge.Row.of(tags, studentCount);
        }).collect(Collectors.toCollection(ArrayList::new));

        studentsCourseGauge.register(gauges, true);
    }

    private void updateStudentsExamMultiGauge(List<Exam> examsInActiveCourses, List<Course> courses) {
        // A mutable list is required here because otherwise the values can not be updated correctly
        final List<MultiGauge.Row<?>> gauges = examsInActiveCourses.stream().map(exam -> {
            final Tags tags = getExamMetricTags(courses, exam);
            final long studentCount = studentExamRepository.countByExamId(exam.getId());
            return MultiGauge.Row.of(tags, studentCount);
        }).collect(Collectors.toCollection(ArrayList::new));

        studentsExamGauge.register(gauges, true);
    }

    private Tags getExamMetricTags(final List<Course> courses, final Exam exam) {
        final Optional<Course> examCourse = findExamCourse(courses, exam);

        final List<Tag> tags = new ArrayList<>();
        examCourse.ifPresent(course -> {
            tags.add(Tag.of("courseId", Long.toString(course.getId())));
            tags.add(Tag.of("courseName", course.getTitle()));
        });
        tags.add(Tag.of("examId", Long.toString(exam.getId())));
        tags.add(Tag.of("examName", exam.getTitle()));
        tags.add(Tag.of("semester", examCourse.map(Course::getSemester).orElse(NO_SEMESTER_TAG)));

        return Tags.of(tags);
    }

    private Optional<Course> findExamCourse(final List<Course> courses, final Exam exam) {
        return courses.stream().filter(course -> Objects.equals(course.getId(), exam.getCourse().getId())).findAny();
    }

    private void updateActiveExerciseMultiGauge() {
        var results = new ArrayList<MultiGauge.Row<?>>();
        var result = exerciseRepository.countActiveExercisesGroupByExerciseType(ZonedDateTime.now());
        extractExerciseTypeMetricsAndAddToMetricsResults(result, results, Tags.empty());

        activeExerciseGauge.register(results, true);
    }

    private void updateExerciseMultiGauge() {
        var results = new ArrayList<MultiGauge.Row<?>>();
        var result = exerciseRepository.countExercisesGroupByExerciseType();
        extractExerciseTypeMetricsAndAddToMetricsResults(result, results, Tags.empty());

        exerciseGauge.register(results, true);
    }

    private void extractExerciseTypeMetricsAndAddToMetricsResults(List<ExerciseTypeMetricsEntry> resultFromDatabase, List<MultiGauge.Row<?>> resultForMetrics, Tags existingTags) {
        for (var exerciseType : ExerciseType.values()) {
            var resultForExerciseType = resultFromDatabase.stream().filter(entry -> entry.exerciseType() == exerciseType.getExerciseClass()).findAny();
            var value = 0L;
            if (resultForExerciseType.isPresent()) {
                value = resultForExerciseType.get().value();
            }

            resultForMetrics.add(MultiGauge.Row.of(existingTags.and("exerciseType", exerciseType.toString()), value));
        }
    }

    private void ensureCourseInformationIsSet(List<Course> courses) {
        courses.forEach(course -> {
            if (course.getSemester() == null) {
                course.setSemester(NO_SEMESTER_TAG);
            }
            if (course.getTitle() == null) {
                if (course.getShortName() != null) {
                    course.setTitle("Course" + course.getShortName());
                }
                else {
                    course.setTitle("Course" + course.getId().toString());
                }
            }
        });
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
