package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuildTrigger;
import de.tum.cit.aet.artemis.hyperion.repository.HyperionCourseSettingsRepository;

/**
 * Builds the quiz question pools of the courses that enabled overnight generation, one course after another, on the schedule
 * configured under {@code artemis.hyperion.quiz-pool.schedule-cron}. Courses whose end date has passed are skipped, and so is a
 * course whose build lease another node holds.
 */
@Service
@Lazy(false)
@Profile(PROFILE_SCHEDULING)
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolScheduleService {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionPoolScheduleService.class);

    private final HyperionCourseSettingsRepository settingsRepository;

    private final CourseRepository courseRepository;

    private final HyperionQuizQuestionPoolBuildService buildService;

    public HyperionQuizQuestionPoolScheduleService(HyperionCourseSettingsRepository settingsRepository, CourseRepository courseRepository,
            HyperionQuizQuestionPoolBuildService buildService) {
        this.settingsRepository = settingsRepository;
        this.courseRepository = courseRepository;
        this.buildService = buildService;
    }

    /**
     * Builds every enabled pool. Runs on the scheduling node at the configured time.
     */
    @Scheduled(cron = "${artemis.hyperion.quiz-pool.schedule-cron:0 0 2 * * *}")
    public void buildEnabledPools() {
        buildEnabledPools(ZonedDateTime.now());
    }

    /**
     * Builds the pool of every course that enabled overnight generation and has not ended at {@code now}.
     *
     * @param now the time that decides whether a course has ended
     * @return the number of courses whose pool was built
     */
    int buildEnabledPools(ZonedDateTime now) {
        List<Long> courseIds = settingsRepository.findAll().stream().filter(entity -> entity.getSettings().poolGenerationEnabled()).map(HyperionCourseSettingsEntity::getCourseId)
                .sorted().toList();
        log.info("Scheduled quiz question pool build: {} courses enabled", courseIds.size());
        int built = 0;
        for (Long courseId : courseIds) {
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
            buildService.buildAndRelease(courseId, null, buildId, leaseToken);
            built++;
        }
        log.info("Scheduled quiz question pool build finished: {} courses built", built);
        return built;
    }
}
