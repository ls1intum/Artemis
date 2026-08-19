package de.tum.cit.aet.artemis.notification.service;

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

import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.cit.aet.artemis.notification.service.notifications.push_notifications.FirebasePushNotificationService;

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
        List<CourseNotificationRecipientDTO> recipients = createTestRecipients();
        HashSet<CourseNotificationRecipientDTO> expectedRecipientSet = new HashSet<>(recipients);

        ReflectionTestUtils.invokeMethod(courseNotificationPushService, "sendCourseNotification", notification, recipients);

        verify(applePushNotificationService, times(1)).sendCourseNotification(notification, expectedRecipientSet);
        verify(firebasePushNotificationService, times(1)).sendCourseNotification(notification, expectedRecipientSet);
    }

    @Test
    void shouldSendNotificationsWhenRecipientListIsEmpty() {
        CourseNotificationDTO notification = createTestNotification();
        List<CourseNotificationRecipientDTO> emptyRecipients = List.of();
        HashSet<CourseNotificationRecipientDTO> expectedEmptySet = new HashSet<>(emptyRecipients);

        ReflectionTestUtils.invokeMethod(courseNotificationPushService, "sendCourseNotification", notification, emptyRecipients);

        verify(applePushNotificationService, times(1)).sendCourseNotification(notification, expectedEmptySet);
        verify(firebasePushNotificationService, times(1)).sendCourseNotification(notification, expectedEmptySet);
    }

    private CourseNotificationDTO createTestNotification() {
        return new CourseNotificationDTO("testNotification", 1L, 1L, ZonedDateTime.parse("2023-01-01T12:00:00Z"), CourseNotificationCategory.COMMUNICATION, Map.of(), "/");
    }

    private List<CourseNotificationRecipientDTO> createTestRecipients() {
        var user1 = new CourseNotificationRecipientDTO(1L, "user1", null, null, null, null);
        var user2 = new CourseNotificationRecipientDTO(2L, "user2", null, null, null, null);
        return List.of(user1, user2);
    }
}
