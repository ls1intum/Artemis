package de.tum.cit.aet.artemis.coursenotification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.coursenotification.domain.CourseNotification;
import de.tum.cit.aet.artemis.coursenotification.repository.CourseNotificationRepository;

@Service
@Profile(PROFILE_SCHEDULING)
public class CourseNotificationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationCleanupService.class);

    private final CourseNotificationRepository courseNotificationRepository;

    private final CourseNotificationCacheService courseNotificationCacheService;

    public CourseNotificationCleanupService(CourseNotificationRepository courseNotificationRepository, CourseNotificationCacheService courseNotificationCacheService) {
        this.courseNotificationRepository = courseNotificationRepository;
        this.courseNotificationCacheService = courseNotificationCacheService;
    }

    /**
     * Cleans up all notifications that are past their deletion date and clears the notification cache.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupCourseNotifications() {
        List<CourseNotification> courseNotificationsToDelete = courseNotificationRepository.findByDeletionDateBefore(ZonedDateTime.now());
        if (!courseNotificationsToDelete.isEmpty()) {
            log.info("Deleted {} course notifications", courseNotificationsToDelete.size());

            courseNotificationRepository.deleteAll(courseNotificationsToDelete);
        }

        courseNotificationCacheService.clearCourseNotificationCache();
    }
}
