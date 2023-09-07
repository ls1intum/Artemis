package de.tum.in.www1.artemis.config;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.*;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import com.zaxxer.hikari.HikariDataSource;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metrics.ExerciseTypeMetricsEntry;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import io.micrometer.core.instrument.*;

@Component
public class MetricsBean {

    private final Logger log = LoggerFactory.getLogger(MetricsBean.class);

    private static final String ARTEMIS_HEALTH_NAME = "artemis.health";

    private static final String ARTEMIS_HEALTH_DESCRIPTION = "Artemis Health Indicator";

    private static final String ARTEMIS_HEALTH_TAG = "healthindicator";

    private static final int LOGGING_DELAY_SECONDS = 10;

    private static final int[] minuteRanges = { 5, 15, 30, 45, 60, 120 };

    private final MeterRegistry meterRegistry;

    private final Environment env;

    private final TaskScheduler taskScheduler;

    private final WebSocketMessageBrokerStats webSocketStats;

    private final SimpUserRegistry userRegistry;

    private final WebSocketHandler webSocketHandler;

    private final ExerciseRepository exerciseRepository;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final StatisticsRepository statisticsRepository;

    private final AtomicInteger activeCoursesGauge = new AtomicInteger(0);

    private final AtomicInteger coursesGauge = new AtomicInteger(0);

    private final AtomicInteger activeExamsGauge = new AtomicInteger(0);

    private final AtomicInteger examsGauge = new AtomicInteger(0);

    private MultiGauge activeUserMultiGauge;

    private MultiGauge studentsCourseGauge;

    private MultiGauge studentsExamGauge;

    private MultiGauge exerciseGauge;

    private MultiGauge activeExerciseGauge;

    private MultiGauge dueExerciseStudentMultiplierGauge;

    private MultiGauge dueExerciseStudentMultiplierActive14DaysGauge;

    private MultiGauge releaseExerciseStudentMultiplierGauge;

    private MultiGauge releaseExerciseStudentMultiplierActive14DaysGauge;

    public MetricsBean(MeterRegistry meterRegistry, Environment env, TaskScheduler taskScheduler, WebSocketMessageBrokerStats webSocketStats, SimpUserRegistry userRegistry,
            WebSocketHandler websocketHandler, List<HealthContributor> healthContributors, Optional<HikariDataSource> hikariDataSource, ExerciseRepository exerciseRepository,
            StudentExamRepository studentExamRepository, ExamRepository examRepository, CourseRepository courseRepository, UserRepository userRepository,
            StatisticsRepository statisticsRepository) {
        this.meterRegistry = meterRegistry;
        this.env = env;
        this.taskScheduler = taskScheduler;
        this.webSocketStats = webSocketStats;
        this.userRegistry = userRegistry;
        this.webSocketHandler = websocketHandler;
        this.exerciseRepository = exerciseRepository;
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.statisticsRepository = statisticsRepository;

        registerHealthContributors(healthContributors);
        registerWebsocketMetrics();
        registerExerciseAndExamMetrics();

        registerStudentExerciseMetrics();

        registerPublicArtemisMetrics();

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
        // Publish the number of currently (via WebSockets) connected sessions
        Gauge.builder("artemis.instance.websocket.sessions", webSocketHandler, MetricsBean::extractWebsocketSessionCount).strongReference(true)
                .description("Number of sessions connected to this Artemis instance").register(meterRegistry);
        // TODO: DEPRECATED metric with same value - Should be removed after October 2023
        Gauge.builder("artemis.instance.websocket.users", webSocketHandler, MetricsBean::extractWebsocketSessionCount).strongReference(true)
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

    private void registerExerciseAndExamMetrics() {
        for (int range : minuteRanges) {
            registerExerciseMetrics(range);
            registerExamMetrics(range);
        }
    }

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
     * Update student multiplier metrics that are exposed via Prometheus.
     * The update (and recalculation) is performed every 1 minutes.
     */
    @Scheduled(fixedRate = 1 * 60 * 1000, initialDelay = 0) // Every 15 minutes
    public void updateStudentMultiplierMetrics() {
        var startDate = System.currentTimeMillis();

        // The authorization object has to be set because this method is not called by a user but by the scheduler
        SecurityUtils.setAuthorizationObject();

        var activeUsers = statisticsRepository.getActiveUsers(ZonedDateTime.now().minusDays(14), ZonedDateTime.now());
        var activeUserNames = activeUsers.stream().map(StatisticsEntry::getUsername).toList();

        updateUpcomingDueExercisesCountWithStudentMultiplier();
        updateUpcomingDueExercisesCountWithActiveStudentMultiplier(activeUserNames);
        updateUpcomingReleasedExercisesCountWithStudentMultiplier();
        updateUpcomingReleasedExercisesCountWithActiveStudentMultiplier(activeUserNames);

        log.info("updateStudentMultiplierMetrics took {}ms", System.currentTimeMillis() - startDate);
    }

    private void registerExerciseMetrics(int range) {
        for (var exerciseType : ExerciseType.values()) {
            final List<Tag> sharedTags = List.of(Tag.of("range", String.valueOf(range)), Tag.of("exerciseType", exerciseType.toString()));

            Gauge.builder("artemis.scheduled.exercises.due.count", () -> this.getUpcomingDueExercisesCount(range, exerciseType)).strongReference(true).tags(sharedTags)
                    .description("Number of exercises ending within the next minutes").register(meterRegistry);

            Gauge.builder("artemis.scheduled.exercises.release.count", () -> this.getUpcomingReleasedExercisesCount(range, exerciseType)).strongReference(true).tags(sharedTags)
                    .description("Number of exercises starting within the next minutes").register(meterRegistry);
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

    private double getUpcomingDueExercisesCount(int minutes, ExerciseType exerciseType) {
        var startDate = System.currentTimeMillis();
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        var result = exerciseRepository.countExercisesWithEndDateBetween(now, endDate, exerciseType.getExerciseClass());
        log.info("getUpcomingDueExercisesCount for minutes {} and exerciseType {} took {}ms", minutes, exerciseType, System.currentTimeMillis() - startDate);
        return result;
    }

    private void updateUpcomingDueExercisesCountWithStudentMultiplier() {
        var startDate = System.currentTimeMillis();

        var now = ZonedDateTime.now();
        var results = new ArrayList<MultiGauge.Row<?>>();

        for (var minutes : minuteRanges) {
            var endDate = ZonedDateTime.now().plusMinutes(minutes);
            var result = exerciseRepository.countStudentsInExercisesWithDueDateBetweenGroupByExerciseType(now, endDate);
            extractExerciseTypeMetricsAndAddToMetricsResults(result, results, minutes);
        }

        dueExerciseStudentMultiplierGauge.register(results, true);

        log.info("updateUpcomingDueExercisesCountWithStudentMultiplier took {}ms", System.currentTimeMillis() - startDate);
    }

    private void updateUpcomingDueExercisesCountWithActiveStudentMultiplier(List<String> activeUserNames) {
        var startDate = System.currentTimeMillis();

        var now = ZonedDateTime.now();
        var results = new ArrayList<MultiGauge.Row<?>>();

        for (var minutes : minuteRanges) {
            var endDate = ZonedDateTime.now().plusMinutes(minutes);
            var result = exerciseRepository.countActiveStudentsInExercisesWithDueDateBetweenGroupByExerciseType(now, endDate, activeUserNames);
            extractExerciseTypeMetricsAndAddToMetricsResults(result, results, minutes);
        }

        dueExerciseStudentMultiplierActive14DaysGauge.register(results, true);

        log.info("updateUpcomingDueExercisesCountWithActiveStudentMultiplier took {}ms", System.currentTimeMillis() - startDate);
    }

    private double getUpcomingReleasedExercisesCount(int minutes, ExerciseType exerciseType) {
        var startDate = System.currentTimeMillis();
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        var result = exerciseRepository.countExercisesWithReleaseDateBetween(now, endDate, exerciseType.getExerciseClass());
        log.info("getUpcomingReleasedExercisesCount for minutes {} and exerciseType {} took {}ms", minutes, exerciseType, System.currentTimeMillis() - startDate);
        return result;
    }

    private void updateUpcomingReleasedExercisesCountWithStudentMultiplier() {
        var startDate = System.currentTimeMillis();

        var now = ZonedDateTime.now();
        var results = new ArrayList<MultiGauge.Row<?>>();

        for (var minutes : minuteRanges) {
            var endDate = ZonedDateTime.now().plusMinutes(minutes);
            var result = exerciseRepository.countStudentsInExercisesWithReleaseDateBetweenGroupByExerciseType(now, endDate);
            extractExerciseTypeMetricsAndAddToMetricsResults(result, results, minutes);
        }

        releaseExerciseStudentMultiplierGauge.register(results, true);

        log.info("updateUpcomingReleasedExercisesCountWithStudentMultiplier took {}ms", System.currentTimeMillis() - startDate);
    }

    private void updateUpcomingReleasedExercisesCountWithActiveStudentMultiplier(List<String> activeUserNames) {
        var startDate = System.currentTimeMillis();

        var now = ZonedDateTime.now();
        var results = new ArrayList<MultiGauge.Row<?>>();

        for (var minutes : minuteRanges) {
            var endDate = ZonedDateTime.now().plusMinutes(minutes);
            var result = exerciseRepository.countActiveStudentsInExercisesWithReleaseDateBetweenGroupByExerciseType(now, endDate, activeUserNames);
            extractExerciseTypeMetricsAndAddToMetricsResults(result, results, minutes);
        }

        releaseExerciseStudentMultiplierActive14DaysGauge.register(results, true);

        log.info("updateUpcomingReleasedExercisesCountWithActiveStudentMultiplier took {}ms", System.currentTimeMillis() - startDate);
    }

    private double getUpcomingEndingExamCount(int minutes) {
        var startDate = System.currentTimeMillis();
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        var result = examRepository.countExamsWithEndDateBetween(now, endDate);
        log.info("getUpcomingEndingExamCount for minutes {} took {}ms", minutes, System.currentTimeMillis() - startDate);
        return result;
    }

    private double getUpcomingEndingExamCountWithStudentMultiplier(int minutes) {
        var startDate = System.currentTimeMillis();
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        var result = examRepository.countExamUsersInExamsWithEndDateBetween(now, endDate);
        log.info("getUpcomingEndingExamCountWithStudentMultiplier for minutes {} took {}ms", minutes, System.currentTimeMillis() - startDate);
        return result;
    }

    private double getUpcomingStartingExamCount(int minutes) {
        var startDate = System.currentTimeMillis();
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        var result = examRepository.countExamsWithStartDateBetween(now, endDate);
        log.info("getUpcomingStartingExamCount for minutes {} took {}ms", minutes, System.currentTimeMillis() - startDate);
        return result;
    }

    private double getUpcomingStartingExamCountWithStudentMultiplier(int minutes) {
        var startDate = System.currentTimeMillis();
        SecurityUtils.setAuthorizationObject();
        var now = ZonedDateTime.now();
        var endDate = ZonedDateTime.now().plusMinutes(minutes);
        var result = examRepository.countExamUsersInExamsWithStartDateBetween(now, endDate);
        log.info("getUpcomingStartingExamCountWithStudentMultiplier for minutes {} took {}ms", minutes, System.currentTimeMillis() - startDate);
        return result;
    }

    /**
     * Update artemis public Artemis metrics that are exposed via Prometheus.
     * The update (and recalculation) is performed every 15 minutes.
     */
    @Scheduled(fixedRate = 15 * 60 * 1000, initialDelay = 0) // Every 15 minutes
    public void updatePublicArtemisMetrics() {
        // The authorization object has to be set because this method is not called by a user but by the scheduler
        SecurityUtils.setAuthorizationObject();

        ZonedDateTime now = ZonedDateTime.now();

        var courses = courseRepository.findAll();
        // We set the number of students once to prevent multiple queries for the same date
        courses.forEach(course -> course.setNumberOfStudents(userRepository.countByGroupsIsContaining(course.getStudentGroupName())));

        ensureCourseInformationIsSet(courses);

        var activeCourses = courses.stream()
                .filter(course -> (course.getStartDate() == null || course.getStartDate().isBefore(now)) && (course.getEndDate() == null || course.getEndDate().isAfter(now)))
                .toList();

        List<Exam> examsInActiveCourses = new ArrayList<>();
        activeCourses.forEach(course -> examsInActiveCourses.addAll(examRepository.findByCourseId(course.getId())));

        // Update multi gauges
        updateStudentsCourseMultiGauge(activeCourses);
        updateStudentsExamMultiGauge(examsInActiveCourses, courses);
        updateActiveUserMultiGauge(now);
        updateActiveExerciseMultiGauge();
        updateExerciseMultiGauge();

        // Update normal Gauges
        activeCoursesGauge.set(activeCourses.size());
        coursesGauge.set(courses.size());

        activeExamsGauge.set(examRepository.countAllActiveExams(now));
        examsGauge.set((int) examRepository.count());
    }

    private void updateActiveUserMultiGauge(ZonedDateTime now) {
        var activeUserPeriodsInDays = new Integer[] { 1, 7, 14, 30 };
        activeUserMultiGauge.register(Stream.of(activeUserPeriodsInDays)
                .map(periodInDays -> MultiGauge.Row.of(Tags.of("period", periodInDays.toString()), statisticsRepository.countActiveUsers(now.minusDays(periodInDays), now)))
                // A mutable list is required here because otherwise the values can not be updated correctly
                .collect(Collectors.toCollection(ArrayList::new)), true);
    }

    private void updateStudentsCourseMultiGauge(List<Course> activeCourses) {
        studentsCourseGauge.register(
                activeCourses.stream().map(course -> MultiGauge.Row.of(Tags.of("courseName", course.getTitle(), "semester", course.getSemester()), course.getNumberOfStudents()))
                        // A mutable list is required here because otherwise the values can not be updated correctly
                        .collect(Collectors.toCollection(ArrayList::new)),
                true);
    }

    private void updateStudentsExamMultiGauge(List<Exam> examsInActiveCourses, List<Course> courses) {
        studentsExamGauge.register(examsInActiveCourses.stream().map(exam -> MultiGauge.Row.of(Tags.of("examName", exam.getTitle(),
                // The course semester.getCourse() is not populated (the semester property is not set) -> Use course from the courses list, which contains the semester
                "semester", courses.stream().filter(course -> Objects.equals(course.getId(), exam.getCourse().getId())).findAny().map(Course::getSemester).orElse("No semester")),
                studentExamRepository.findByExamId(exam.getId()).size()))
                // A mutable list is required here because otherwise the values can not be updated correctly
                .collect(Collectors.toCollection(ArrayList::new)), true);
    }

    private void updateActiveExerciseMultiGauge() {
        activeExerciseGauge.register(Stream.of(ExerciseType.values())
                .map(exerciseType -> MultiGauge.Row.of(Tags.of("exerciseType", exerciseType.toString()),
                        exerciseRepository.countActiveExercisesByExerciseType(ZonedDateTime.now(), exerciseType.getExerciseClass())))
                // A mutable list is required here because otherwise the values can not be updated correctly
                .collect(Collectors.toCollection(ArrayList::new)), true);
    }

    private void updateExerciseMultiGauge() {
        exerciseGauge.register(Stream.of(ExerciseType.values())
                .map(exerciseType -> MultiGauge.Row.of(Tags.of("exerciseType", exerciseType.toString()),
                        exerciseRepository.countExercisesByExerciseType(exerciseType.getExerciseClass())))
                // A mutable list is required here because otherwise the values can not be updated correctly
                .collect(Collectors.toCollection(ArrayList::new)), true);
    }

    private void extractExerciseTypeMetricsAndAddToMetricsResults(List<ExerciseTypeMetricsEntry> resultFromDatabase, List<MultiGauge.Row<?>> resultForMetrics, int minutes) {
        for (var exerciseType : ExerciseType.values()) {
            var resultForExerciseType = resultFromDatabase.stream().filter(entry -> entry.exerciseType() == exerciseType.getExerciseClass()).findAny();
            var value = 0L;
            if (resultForExerciseType.isPresent()) {
                value = resultForExerciseType.get().value();
            }

            resultForMetrics.add(MultiGauge.Row.of(Tags.of("exerciseType", exerciseType.toString(), "range", String.valueOf(minutes)), value));
        }
    }

    private void ensureCourseInformationIsSet(List<Course> courses) {
        courses.forEach(course -> {
            if (course.getSemester() == null) {
                course.setSemester("No semester");
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
