package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;
import de.tum.cit.aet.artemis.notification.dto.payload.ExerciseOpenForPracticePayloadDTO;

@ExtendWith(MockitoExtension.class)
class CourseNotificationWebappServiceTest {

    private CourseNotificationWebappService courseNotificationWebappService;

    @Mock
    private WebsocketMessagingService websocketMessagingService;

    private static final String WEBSOCKET_TOPIC_PREFIX = "/topic/notification/";

    private static final String WEBSOCKET_BROADCAST_TOPIC_PREFIX = "/topic/notification/all";

    @BeforeEach
    void setUp() {
        courseNotificationWebappService = new CourseNotificationWebappService(websocketMessagingService);
    }

    /**
     * The channel's future is what the feature usage analysis reads as the delivery outcome, so it has to wait for the
     * websocket sends rather than for having started them. Discarding the nested futures completed it immediately, and a
     * broker failure arriving afterwards was recorded as a successful delivery with dispatch-only latency.
     */
    @Test
    void shouldCompleteExceptionallyWhenANestedSendFailsAfterDispatch() {
        CourseNotificationDTO notification = createTestNotification(123L);
        List<CourseNotificationRecipientDTO> recipients = List.of(createTestUser(1L, "user1"));
        var brokerFailure = new CompletableFuture<Void>();
        // Two sends per recipient: the course-specific topic and the broadcast topic.
        when(websocketMessagingService.sendMessageToUser(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null), brokerFailure);

        CompletableFuture<Void> delivery = ReflectionTestUtils.invokeMethod(courseNotificationWebappService, "sendCourseNotification", notification, recipients);

        // still open, because the last send has not finished: completing early is exactly the defect
        assertThat(delivery).isNotDone();
        brokerFailure.completeExceptionally(new IllegalStateException("the broker went away"));
        assertThat(delivery).isCompletedExceptionally();
    }

    @Test
    void shouldCompleteNormallyWhenEverySendSucceeds() {
        CourseNotificationDTO notification = createTestNotification(123L);
        List<CourseNotificationRecipientDTO> recipients = List.of(createTestUser(1L, "user1"));

        CompletableFuture<Void> delivery = ReflectionTestUtils.invokeMethod(courseNotificationWebappService, "sendCourseNotification", notification, recipients);

        assertThat(delivery).isCompletedWithValue(null);
    }

    @Test
    void shouldSendNotificationToEachRecipientWhenMultipleRecipientsProvided() {
        CourseNotificationDTO notification = createTestNotification(123L);
        List<CourseNotificationRecipientDTO> recipients = List.of(createTestUser(1L, "user1"), createTestUser(2L, "user2"), createTestUser(3L, "user3"));

        ReflectionTestUtils.invokeMethod(courseNotificationWebappService, "sendCourseNotification", notification, recipients);

        verify(websocketMessagingService, times(1)).sendMessageToUser("user1", WEBSOCKET_TOPIC_PREFIX + "123", notification);
        verify(websocketMessagingService, times(1)).sendMessageToUser("user2", WEBSOCKET_TOPIC_PREFIX + "123", notification);
        verify(websocketMessagingService, times(1)).sendMessageToUser("user3", WEBSOCKET_TOPIC_PREFIX + "123", notification);
        verify(websocketMessagingService, times(1)).sendMessageToUser("user1", WEBSOCKET_BROADCAST_TOPIC_PREFIX, notification);
        verify(websocketMessagingService, times(1)).sendMessageToUser("user2", WEBSOCKET_BROADCAST_TOPIC_PREFIX, notification);
        verify(websocketMessagingService, times(1)).sendMessageToUser("user3", WEBSOCKET_BROADCAST_TOPIC_PREFIX, notification);
        // Exactly two sends per recipient: the retired /topic/communication/notification/ mirrors must not come back.
        verify(websocketMessagingService, times(6)).sendMessageToUser(any(), any(), any());
    }

    @Test
    void shouldNotSendMessagesWhenRecipientListIsEmpty() {
        CourseNotificationDTO notification = createTestNotification(123L);
        List<CourseNotificationRecipientDTO> emptyRecipients = List.of();

        ReflectionTestUtils.invokeMethod(courseNotificationWebappService, "sendCourseNotification", notification, emptyRecipients);

        verify(websocketMessagingService, times(0)).sendMessageToUser(any(), any(), any());
    }

    @Test
    void shouldSendToCorrectTopicWhenCourseIdProvided() {
        long courseId = 456L;
        CourseNotificationDTO notification = createTestNotification(courseId);
        var user = createTestUser(1L, "testuser");

        ReflectionTestUtils.invokeMethod(courseNotificationWebappService, "sendCourseNotification", notification, List.of(user));

        verify(websocketMessagingService, times(1)).sendMessageToUser("testuser", WEBSOCKET_TOPIC_PREFIX + "456", notification);
        verify(websocketMessagingService, times(1)).sendMessageToUser("testuser", WEBSOCKET_BROADCAST_TOPIC_PREFIX, notification);
        verify(websocketMessagingService, times(2)).sendMessageToUser(any(), any(), any());
    }

    private CourseNotificationRecipientDTO createTestUser(Long id, String login) {
        return new CourseNotificationRecipientDTO(id, login, null, null, null, null);
    }

    private CourseNotificationDTO createTestNotification(Long courseId) {
        return new CourseNotificationDTO("Test Notification", 1L, courseId, ZonedDateTime.now(), CourseNotificationCategory.GENERAL, "Test Course", null,
                new ExerciseOpenForPracticePayloadDTO(1L, "Test Exercise"), "/");
    }
}
