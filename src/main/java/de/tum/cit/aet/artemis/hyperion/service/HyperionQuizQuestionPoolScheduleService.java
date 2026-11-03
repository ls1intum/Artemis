package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuildTrigger;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLastCompletedBuildDTO;
import de.tum.cit.aet.artemis.hyperion.repository.HyperionCourseSettingsRepository;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolBuildRepository;

/**
 * Builds the quiz question pools of the courses that enabled overnight generation, one course after another, on the schedule
 * configured under {@code artemis.hyperion.quiz-pool.schedule-cron}. The run executes on the pool's build executor and lasts at most
 * {@code artemis.hyperion.quiz-pool.schedule-window-hours}: after that it starts no further course, and the running build stops
 * after its current unit of work and leaves its remaining items to the next run.
 * <p>
 * Courses are built in the order of their last completed build, oldest first and never-completed first, so a course the window cut
 * off comes early in the next run. Courses whose end date has passed are skipped, and so is a course whose build lease another node
 * holds.
 */
@Service
@Lazy(false)
@Profile(PROFILE_SCHEDULING)
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolScheduleService {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionPoolScheduleService.class);

    private final HyperionCourseSettingsRepository settingsRepository;

    private final CourseRepository courseRepository;

    private final QuizQuestionPoolBuildRepository buildRepository;

    private final HyperionQuizQuestionPoolBuildService buildService;

    private final TaskExecutor executor;

    private final Duration window;

    public HyperionQuizQuestionPoolScheduleService(HyperionCourseSettingsRepository settingsRepository, CourseRepository courseRepository,
            QuizQuestionPoolBuildRepository buildRepository, HyperionQuizQuestionPoolBuildService buildService,
            @Qualifier("hyperionQuizQuestionPoolTaskExecutor") TaskExecutor executor, @Value("${artemis.hyperion.quiz-pool.schedule-window-hours:7}") int windowHours) {
        if (windowHours < 1 || windowHours > 23) {
            throw new IllegalArgumentException("artemis.hyperion.quiz-pool.schedule-window-hours must be between 1 and 23, got " + windowHours);
        }
        this.settingsRepository = settingsRepository;
        this.courseRepository = courseRepository;
        this.buildRepository = buildRepository;
        this.buildService = buildService;
        this.executor = executor;
        this.window = Duration.ofHours(windowHours);
    }

    /**
     * Starts the overnight run on the pool's build executor and returns at once. Runs on the scheduling node at the configured time.
     */
    @Scheduled(cron = "${artemis.hyperion.quiz-pool.schedule-cron:0 0 0 * * *}")
    public void startScheduledBuilds() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime deadline = now.plus(window);
        try {
            executor.execute(() -> buildEnabledPools(now, deadline));
        }
        catch (TaskRejectedException e) {
            log.error("Scheduled quiz question pool build could not be started: the pool executor is full", e);
        }
    }

    /**
     * Builds the pool of every course that enabled overnight generation and has not ended at {@code now}, until {@code deadline}.
     *
     * @param now      the time that decides whether a course has ended
     * @param deadline the time after which no further course is started and the running build stops after its current unit of work
     * @return the number of courses whose pool was built
     */
    int buildEnabledPools(ZonedDateTime now, ZonedDateTime deadline) {
        List<Long> courseIds = coursesInBuildOrder();
        log.info("Scheduled quiz question pool build: {} courses enabled, window ends at {}", courseIds.size(), deadline);
        int built = 0;
        for (int position = 0; position < courseIds.size(); position++) {
            if (!ZonedDateTime.now().isBefore(deadline)) {
                log.info("Scheduled quiz question pool build: the window ended, {} courses wait for the next run", courseIds.size() - position);
                break;
            }
            long courseId = courseIds.get(position);
            Optional<Course> course = courseRepository.findById(courseId);
            if (course.isEmpty() || (course.get().getEndDate() != null && course.get().getEndDate().isBefore(now))) {
                continue;
            }
            String leaseToken = buildService.tryStart(courseId);
            if (leaseToken == null) {
                log.info("Scheduled quiz question pool build of course [{}] skipped: a build is already running", courseId);
                continue;
            }
            long buildId;
            try {
                int enqueued = buildService.enqueue(course.get());
                buildId = buildService.recordStart(course.get(), QuizQuestionPoolBuildTrigger.SCHEDULED, enqueued).getId();
            }
            catch (RuntimeException e) {
                log.error("Scheduled quiz question pool build of course [{}] could not be enqueued", courseId, e);
                buildService.release(courseId, leaseToken);
                continue;
            }
            buildService.buildAndRelease(courseId, null, buildId, leaseToken, deadline);
            built++;
        }
        log.info("Scheduled quiz question pool build finished: {} courses built", built);
        return built;
    }

    /**
     * Returns the courses that enabled overnight generation, those without a completed build first, then by the time their last
     * completed build finished, oldest first; ties by course id.
     */
    private List<Long> coursesInBuildOrder() {
        Map<Long, ZonedDateTime> lastCompleted = buildRepository.findLastCompletedBuildPerCourse().stream()
                .collect(Collectors.toMap(QuizQuestionPoolLastCompletedBuildDTO::courseId, QuizQuestionPoolLastCompletedBuildDTO::finishedAt));
        Comparator<Long> byLastCompleted = Comparator.comparing(lastCompleted::get, Comparator.nullsFirst(Comparator.naturalOrder()));
        return settingsRepository.findAll().stream().filter(entity -> entity.getSettings().poolGenerationEnabled()).map(HyperionCourseSettingsEntity::getCourseId)
                .sorted(byLastCompleted.thenComparing(Comparator.naturalOrder())).toList();
    }
}
