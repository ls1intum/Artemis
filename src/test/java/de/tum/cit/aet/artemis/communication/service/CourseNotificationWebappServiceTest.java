package de.tum.cit.aet.artemis.communication.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.core.domain.User;

@ExtendWith(MockitoExtension.class)
class CourseNotificationWebappServiceTest {

    private CourseNotificationWebappService courseNotificationWebappService;

    @Mock
    private WebsocketMessagingService websocketMessagingService;

    private static final String WEBSOCKET_TOPIC_PREFIX = "/topic/communication/notification/";

    @BeforeEach
    void setUp() {
        courseNotificationWebappService = new CourseNotificationWebappService(websocketMessagingService);
    }

    @Test
    void shouldSendNotificationToEachRecipientWhenMultipleRecipientsProvided() {
        CourseNotificationDTO notification = createTestNotification(123L);
        List<User> recipients = List.of(createTestUser(1L, "user1"), createTestUser(2L, "user2"), createTestUser(3L, "user3"));

        ReflectionTestUtils.invokeMethod(courseNotificationWebappService, "sendCourseNotification", notification, recipients);

        verify(websocketMessagingService, times(1)).sendMessageToUser("user1", WEBSOCKET_TOPIC_PREFIX + "123", notification);
        verify(websocketMessagingService, times(1)).sendMessageToUser("user2", WEBSOCKET_TOPIC_PREFIX + "123", notification);
        verify(websocketMessagingService, times(1)).sendMessageToUser("user3", WEBSOCKET_TOPIC_PREFIX + "123", notification);
    }

    @Test
    void shouldNotSendMessagesWhenRecipientListIsEmpty() {
        CourseNotificationDTO notification = createTestNotification(123L);
        List<User> emptyRecipients = List.of();

        ReflectionTestUtils.invokeMethod(courseNotificationWebappService, "sendCourseNotification", notification, emptyRecipients);

        verify(websocketMessagingService, times(0)).sendMessageToUser(any(), any(), any());
    }

    @Test
    void shouldSendToCorrectTopicWhenCourseIdProvided() {
        long courseId = 456L;
        CourseNotificationDTO notification = createTestNotification(courseId);
        User user = createTestUser(1L, "testuser");

        ReflectionTestUtils.invokeMethod(courseNotificationWebappService, "sendCourseNotification", notification, List.of(user));

        verify(websocketMessagingService, times(1)).sendMessageToUser("testuser", WEBSOCKET_TOPIC_PREFIX + "456", notification);
    }

    private User createTestUser(Long id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    private CourseNotificationDTO createTestNotification(Long courseId) {
        return new CourseNotificationDTO("Test Notification", courseId, ZonedDateTime.now(), CourseNotificationCategory.GENERAL, Map.of("key1", "value1", "key2", "value2"));
    }
}
