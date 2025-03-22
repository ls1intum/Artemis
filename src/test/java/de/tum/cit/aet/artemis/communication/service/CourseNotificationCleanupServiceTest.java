package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;

@ExtendWith(MockitoExtension.class)
class CourseNotificationCleanupServiceTest {

    @Mock
    private CourseNotificationTestRepository courseNotificationRepository;

    @Mock
    private CourseNotificationCacheService courseNotificationCacheService;

    private CourseNotificationCleanupService courseNotificationCleanupService;

    @BeforeEach
    void setUp() {
        courseNotificationCleanupService = new CourseNotificationCleanupService(courseNotificationRepository, courseNotificationCacheService);
    }

    @Test
    void shouldDeleteExpiredNotificationsWhenTheyExist() {
        List<CourseNotification> expiredNotifications = createExpiredNotifications(3);

        when(courseNotificationRepository.findByDeletionDateBefore(any(ZonedDateTime.class))).thenReturn(expiredNotifications);

        courseNotificationCleanupService.cleanupCourseNotifications();

        verify(courseNotificationRepository).deleteAll(eq(expiredNotifications));
        verify(courseNotificationCacheService).clearCourseNotificationCache();
    }

    @Test
    void shouldNotDeleteAnythingWhenNoExpiredNotificationsExist() {
        when(courseNotificationRepository.findByDeletionDateBefore(any(ZonedDateTime.class))).thenReturn(new ArrayList<>());

        courseNotificationCleanupService.cleanupCourseNotifications();

        verify(courseNotificationRepository, never()).deleteAll(anyList());
        verify(courseNotificationCacheService).clearCourseNotificationCache();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10 })
    void shouldDeleteCorrectNumberOfNotificationsWhenMultipleExpiredNotificationsExist(int notificationCount) {
        List<CourseNotification> expiredNotifications = createExpiredNotifications(notificationCount);

        when(courseNotificationRepository.findByDeletionDateBefore(any(ZonedDateTime.class))).thenReturn(expiredNotifications);

        courseNotificationCleanupService.cleanupCourseNotifications();

        verify(courseNotificationRepository).deleteAll(eq(expiredNotifications));
        assertThat(expiredNotifications).hasSize(notificationCount);
        verify(courseNotificationCacheService).clearCourseNotificationCache();
    }

    @Test
    void shouldAlwaysClearCacheWhenCleanupIsPerformed() {
        when(courseNotificationRepository.findByDeletionDateBefore(any(ZonedDateTime.class))).thenReturn(new ArrayList<>());

        courseNotificationCleanupService.cleanupCourseNotifications();

        verify(courseNotificationCacheService).clearCourseNotificationCache();
    }

    /**
     * Helper method to create a list of expired CourseNotification objects
     *
     * @param count The number of notification objects to create
     * @return List of CourseNotification objects with deletion dates in the past
     */
    private List<CourseNotification> createExpiredNotifications(int count) {
        List<CourseNotification> notifications = new ArrayList<>();
        ZonedDateTime pastTime = ZonedDateTime.now().minusDays(1);

        for (int i = 0; i < count; i++) {
            CourseNotification notification = new CourseNotification();
            notification.setId((long) (i + 1));
            notification.setDeletionDate(pastTime);
            notifications.add(notification);
        }

        return notifications;
    }
}
