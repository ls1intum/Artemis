package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Detects failed builds that were rate limited by Maven Central (HTTP 429 Too Many Requests) and informs the instructors of the affected course via email.
 * <p>
 * Maven Central rate limits dependency downloads per IP address. Since all build agents of an Artemis instance typically share the same outbound IP addresses, builds of Java and
 * Kotlin exercises can suddenly fail with HTTP 429 errors, especially during high-load periods such as exams. Instructors can fix this themselves by adding a Maven repository
 * mirror as the first repository in the build configuration of the test repository, see the linked documentation.
 * <p>
 * The detection runs fully asynchronously so that build result processing (and thereby the feedback students and instructors are waiting for) is never delayed. To avoid flooding
 * instructors when many builds fail at the same time, at most one email per exercise and day is sent. The deduplication uses a Hazelcast map with a per-entry time-to-live, so it
 * is safe across multiple core nodes (the atomic {@code putIfAbsent} guarantees a single notification even if several nodes detect the error concurrently).
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MavenCentralRateLimitNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MavenCentralRateLimitNotificationService.class);

    /**
     * Error message emitted by Gradle/Maven when Maven Central rejects a download due to rate limiting.
     */
    static final String RATE_LIMIT_ERROR = "Received status code 429 from server: Too Many Requests";

    private static final String MAVEN_MARKER = "maven";

    static final String DOCUMENTATION_URL = "https://docs.artemis.tum.de/instructor/exercises/programming-exercise#prevent-maven-central-rate-limits-java-and-kotlin";

    private static final String NOTIFICATION_SENT_MAP = "maven-central-rate-limit-notification-sent";

    private static final long NOTIFICATION_INTERVAL_HOURS = 24;

    private final HazelcastInstance hazelcastInstance;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    public MavenCentralRateLimitNotificationService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance,
            ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository, MailSendingService mailSendingService) {
        this.hazelcastInstance = hazelcastInstance;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Checks the logs of a failed build for Maven Central rate limiting and, if detected, emails the instructors of the affected course. At most one email per exercise is sent
     * within {@value #NOTIFICATION_INTERVAL_HOURS} hours.
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
            // Atomically claim the exercise for the notification interval; only the first detection within the interval sends emails (cluster-safe)
            if (getNotificationSentMap().putIfAbsent(exerciseId, System.currentTimeMillis(), NOTIFICATION_INTERVAL_HOURS, TimeUnit.HOURS) != null) {
                return;
            }
            notifyInstructors(exerciseId);
        }
        catch (Exception ex) {
            log.error("Failed to notify instructors about Maven Central rate limiting for programming exercise {}", exerciseId, ex);
        }
    }

    private static boolean isRateLimitedByMavenCentral(List<String> buildLogs) {
        boolean rateLimited = buildLogs.stream().anyMatch(logEntry -> logEntry != null && logEntry.contains(RATE_LIMIT_ERROR));
        if (!rateLimited) {
            return false;
        }
        return buildLogs.stream().anyMatch(logEntry -> logEntry != null && logEntry.toLowerCase(Locale.ROOT).contains(MAVEN_MARKER));
    }

    private void notifyInstructors(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findWithEagerCourseAndExamById(exerciseId).orElseThrow();
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Set<User> instructors = userRepository.getInstructors(course);
        log.info("Notifying {} instructors of course {} about Maven Central rate limiting in programming exercise {}", instructors.size(), course.getId(), exercise.getId());
        Map<String, Object> contextVariables = Map.of("exerciseTitle", exercise.getTitle(), "courseTitle", course.getTitle(), "exerciseId", exercise.getId(), "courseId",
                course.getId(), "documentationUrl", DOCUMENTATION_URL);
        for (User instructor : instructors) {
            if (!instructor.getActivated() || instructor.getEmail() == null) {
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

    private IMap<Long, Long> getNotificationSentMap() {
        return hazelcastInstance.getMap(NOTIFICATION_SENT_MAP);
    }
}
