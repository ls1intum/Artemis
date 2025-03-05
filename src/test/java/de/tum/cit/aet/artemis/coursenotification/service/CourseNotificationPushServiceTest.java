package de.tum.cit.aet.artemis.coursenotification.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.FirebasePushNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.coursenotification.domain.notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.coursenotification.dto.CourseNotificationDTO;

@ExtendWith(MockitoExtension.class)
class CourseNotificationPushServiceTest {

    private CourseNotificationPushService courseNotificationPushService;

    @Mock
    private ApplePushNotificationService applePushNotificationService;

    @Mock
    private FirebasePushNotificationService firebasePushNotificationService;

    @BeforeEach
    void setUp() {
        courseNotificationPushService = new CourseNotificationPushService(applePushNotificationService, firebasePushNotificationService);
    }

    @Test
    void shouldSendNotificationsToBothServicesWhenRecipientListProvided() {
        CourseNotificationDTO notification = createTestNotification();
        List<User> recipients = createTestRecipients();
        HashSet<User> expectedRecipientSet = new HashSet<>(recipients);

        ReflectionTestUtils.invokeMethod(courseNotificationPushService, "sendCourseNotification", notification, recipients);

        verify(applePushNotificationService, times(1)).sendCourseNotification(notification, expectedRecipientSet);
        verify(firebasePushNotificationService, times(1)).sendCourseNotification(notification, expectedRecipientSet);
    }

    @Test
    void shouldSendNotificationsWhenRecipientListIsEmpty() {
        CourseNotificationDTO notification = createTestNotification();
        List<User> emptyRecipients = List.of();
        HashSet<User> expectedEmptySet = new HashSet<>(emptyRecipients);

        ReflectionTestUtils.invokeMethod(courseNotificationPushService, "sendCourseNotification", notification, emptyRecipients);

        verify(applePushNotificationService, times(1)).sendCourseNotification(notification, expectedEmptySet);
        verify(firebasePushNotificationService, times(1)).sendCourseNotification(notification, expectedEmptySet);
    }

    private CourseNotificationDTO createTestNotification() {
        return new CourseNotificationDTO("testNotification", 1L, ZonedDateTime.parse("2023-01-01T12:00:00Z"), CourseNotificationCategory.COMMUNICATION, Map.of());
    }

    private List<User> createTestRecipients() {
        User user1 = new User();
        user1.setId(1L);
        user1.setLogin("user1");

        User user2 = new User();
        user2.setId(2L);
        user2.setLogin("user2");

        return List.of(user1, user2);
    }
}
