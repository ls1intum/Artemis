package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localci.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.localci.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Detects failed builds that were rate limited by Maven Central (HTTP 429 Too Many Requests) and informs the instructors of the affected course via email.
 * <p>
 * Maven Central rate limits dependency downloads per IP address. Since all build agents of an Artemis instance typically share the same outbound IP addresses, builds of Java and
 * Kotlin exercises can suddenly fail with HTTP 429 errors, especially during high-load periods such as exams. Instructors can fix this themselves by adding a Maven repository
 * mirror as the first repository in the build configuration of the test repository, see the linked documentation. Instructors can opt out of these emails via the
 * {@link GlobalNotificationType#MAVEN_CENTRAL_RATE_LIMIT} setting.
 * <p>
 * The detection runs fully asynchronously so that build result processing (and thereby the feedback students and instructors are waiting for) is never delayed. To avoid flooding
 * instructors when many builds fail at the same time, at most one email per exercise and day is sent. The deduplication state lives in a cluster-shared map obtained through the
 * {@link DistributedDataProvider} abstraction, so it works on both Hazelcast and Redis deployments; the per-key lock linearises concurrent detections from different nodes. On
 * deployments without a {@link DistributedDataProvider} (e.g. Jenkins setups without the {@code localci} profile), the service falls back to a node-local map — in a multi-node
 * setup each node then deduplicates independently, which in the worst case sends one email per node per day instead of one per cluster (an acceptable degradation for this rare
 * topology).
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MavenCentralRateLimitNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MavenCentralRateLimitNotificationService.class);

    /**
     * Error message emitted by Gradle when Maven Central rejects a download due to rate limiting, e.g.
     * {@code Could not GET 'https://repo.maven.apache.org/...'. Received status code 429 from server: Too Many Requests}.
     */
    static final String GRADLE_RATE_LIMIT_ERROR = "Received status code 429 from server: Too Many Requests";

    /**
     * Error message emitted by Maven (Maven Resolver) when Maven Central rejects a download due to rate limiting, e.g.
     * {@code Could not transfer artifact ... from/to central (https://repo.maven.apache.org/maven2): status code: 429, reason phrase: Too Many Requests (429)}.
     */
    static final String MAVEN_RATE_LIMIT_ERROR = "status code: 429, reason phrase: Too Many Requests";

    /** Hosts used by Maven Central. Requiring one avoids misclassifying rate limits from private Maven-compatible registries. */
    private static final List<String> MAVEN_CENTRAL_HOSTS = List.of("repo.maven.apache.org", "repo1.maven.org");

    static final String DOCUMENTATION_URL = "https://docs.artemis.tum.de/instructor/exercises/programming-exercise#prevent-maven-central-rate-limits-java-and-kotlin";

    private static final String NOTIFICATION_SENT_MAP = "maven-central-rate-limit-notification-sent";

    private static final Duration NOTIFICATION_INTERVAL = Duration.ofHours(24);

    private final Optional<DistributedDataProvider> distributedDataProvider;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private final MailSendingService mailSendingService;

    /** Lazily resolved cluster-shared map with the last notification timestamp (epoch millis) per exercise id; {@code null} until first use. */
    private volatile DistributedMap<Long, Long> distributedSentMap;

    /** Node-local fallback for deployments without a {@link DistributedDataProvider}. */
    private final ConcurrentHashMap<Long, Long> localSentMap = new ConcurrentHashMap<>();

    public MavenCentralRateLimitNotificationService(Optional<DistributedDataProvider> distributedDataProvider, ProgrammingExerciseRepository programmingExerciseRepository,
            UserRepository userRepository, GlobalNotificationSettingRepository globalNotificationSettingRepository, MailSendingService mailSendingService) {
        this.distributedDataProvider = distributedDataProvider;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Checks the logs of a failed build for Maven Central rate limiting and, if detected, emails the instructors of the affected course. At most one email per exercise is sent
     * within 24 hours.
     * <p>
     * The method is asynchronous (log scanning, deduplication, and mail sending all happen on the mail executor), so calling it adds no noticeable latency to build result
     * processing. It deliberately takes only immutable values (no entities), because the caller continues to modify the build log entities concurrently. Any error is only logged
     * so that result processing is never affected.
     *
     * @param exerciseId          the id of the programming exercise the failed build belongs to (the exercise and its course are fetched eagerly by id because this method runs
     *                                outside a Hibernate session)
     * @param programmingLanguage the programming language of the exercise
     * @param buildLogs           the log messages of the failed build
     */
    @Async("mailTaskExecutor")
    public void notifyInstructorsIfBuildWasRateLimited(long exerciseId, ProgrammingLanguage programmingLanguage, List<String> buildLogs) {
        try {
            if (programmingLanguage != ProgrammingLanguage.JAVA && programmingLanguage != ProgrammingLanguage.KOTLIN) {
                return;
            }
            if (!isRateLimitedByMavenCentral(buildLogs)) {
                return;
            }
            log.warn("A build of programming exercise {} failed because Maven Central rate limited dependency downloads (HTTP 429)", exerciseId);
            if (!mailSendingService.isMailConfigured()) {
                return;
            }
            if (!tryClaimNotification(exerciseId)) {
                return;
            }
            notifyInstructors(exerciseId);
        }
        catch (Exception ex) {
            log.error("Failed to notify instructors about Maven Central rate limiting for programming exercise {}", exerciseId, ex);
        }
    }

    private static boolean isRateLimitedByMavenCentral(List<String> buildLogs) {
        return buildLogs.stream().filter(Objects::nonNull).anyMatch(logEntry -> {
            boolean rateLimited = logEntry.contains(GRADLE_RATE_LIMIT_ERROR) || logEntry.contains(MAVEN_RATE_LIMIT_ERROR);
            boolean referencesMavenCentral = MAVEN_CENTRAL_HOSTS.stream().anyMatch(host -> logEntry.contains("://" + host + "/"));
            return rateLimited && referencesMavenCentral;
        });
    }

    /**
     * Atomically claims the exercise for the notification interval. Only the first detection within the interval may send emails. Entries are not evicted automatically (the
     * {@link DistributedMap} abstraction has no TTL support), but the map is bounded by the number of exercises that ever hit the rate limit and holds one timestamp each.
     *
     * @param exerciseId the id of the affected exercise
     * @return true if this call claimed the notification and the caller should send the emails
     */
    private boolean tryClaimNotification(long exerciseId) {
        long now = System.currentTimeMillis();
        if (distributedDataProvider.isPresent()) {
            DistributedMap<Long, Long> map = distributedSentMap();
            map.lock(exerciseId);
            try {
                Long lastSent = map.get(exerciseId);
                if (isWithinNotificationInterval(lastSent, now)) {
                    return false;
                }
                map.put(exerciseId, now);
                return true;
            }
            finally {
                map.unlock(exerciseId);
            }
        }
        // Node-local fallback: deployments without a DistributedDataProvider (e.g. Jenkins without the localci profile)
        AtomicBoolean claimed = new AtomicBoolean(false);
        localSentMap.compute(exerciseId, (id, lastSent) -> {
            if (isWithinNotificationInterval(lastSent, now)) {
                return lastSent;
            }
            claimed.set(true);
            return now;
        });
        return claimed.get();
    }

    private static boolean isWithinNotificationInterval(Long lastSent, long now) {
        return lastSent != null && now - lastSent < NOTIFICATION_INTERVAL.toMillis();
    }

    private DistributedMap<Long, Long> distributedSentMap() {
        DistributedMap<Long, Long> resolved = distributedSentMap;
        if (resolved == null) {
            synchronized (this) {
                resolved = distributedSentMap;
                if (resolved == null) {
                    resolved = distributedDataProvider.orElseThrow().getMap(NOTIFICATION_SENT_MAP);
                    distributedSentMap = resolved;
                }
            }
        }
        return resolved;
    }

    private void notifyInstructors(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findWithEagerCourseAndExamById(exerciseId).orElseThrow();
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Set<User> instructors = userRepository.getInstructors(course);
        log.info("Notifying {} instructors of course {} about Maven Central rate limiting in programming exercise {}", instructors.size(), course.getId(), exercise.getId());
        Set<Long> instructorIds = instructors.stream().map(User::getId).collect(Collectors.toSet());
        Set<Long> optedOutUserIds = instructorIds.isEmpty() ? Set.of()
                : globalNotificationSettingRepository.findUserIdsWithNotificationDisabled(instructorIds, GlobalNotificationType.MAVEN_CENTRAL_RATE_LIMIT);
        Map<String, Object> contextVariables = Map.of("exerciseTitle", exercise.getTitle(), "courseTitle", course.getTitle(), "exerciseId", exercise.getId(), "courseId",
                course.getId(), "documentationUrl", DOCUMENTATION_URL);
        for (User instructor : instructors) {
            if (!instructor.getActivated() || instructor.getEmail() == null || optedOutUserIds.contains(instructor.getId())) {
                continue;
            }
            try {
                mailSendingService.buildAndSendAsync(MailRecipientDTO.from(instructor), "email.notification.mavenCentralRateLimit.title", List.of(exercise.getTitle()),
                        "mail/notification/mavenCentralRateLimitEmail", contextVariables);
            }
            catch (Exception ex) {
                log.error("Failed to send Maven Central rate limit email to instructor {}", instructor.getLogin(), ex);
            }
        }
    }
}
