package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.communication.domain.NotificationType;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSerializedDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.PushNotificationDataDTO;
import de.tum.cit.aet.artemis.core.config.Constants;

class CourseNotificationPushProxyServiceTest {

    private final CourseNotificationPushProxyService courseNotificationPushProxyService = new CourseNotificationPushProxyService();

    @Test
    void shouldTransformNewMessageNotificationCorrectly() {
        ZonedDateTime creationDate = ZonedDateTime.parse("2023-01-01T12:00:00Z");
        CourseNotificationDTO notification = createNewMessageNotification(creationDate, "Math 101", "Hello, this is a test message", "General", "John Doe", "PUBLIC",
                "https://example.com/image.jpg", "123");

        PushNotificationDataDTO result = courseNotificationPushProxyService.fromCourseNotification(notification);

        assertThat(result.notificationPlaceholders()).containsExactly("Math 101", "Hello, this is a test message", creationDate.toString(), "General", "John Doe", "PUBLIC",
                "https://example.com/image.jpg", "123", "456");
        assertThat(result.type()).isEqualTo(NotificationType.CONVERSATION_NEW_MESSAGE.toString());
        assertThat(result.date()).isEqualTo(creationDate.toString());
        assertThat(result.version()).isEqualTo(Constants.PUSH_NOTIFICATION_VERSION);

        assertThat(result.target()).contains("\"message\":\"new-message\"");
        assertThat(result.target()).contains("\"entity\":\"message\"");
        assertThat(result.target()).contains("\"mainPage\":\"courses\"");
        assertThat(result.target()).contains("\"conversation\":789");
        assertThat(result.target()).contains("\"course\":1");
    }

    @Test
    void shouldHandleNullParametersInNewMessageNotification() {
        ZonedDateTime creationDate = ZonedDateTime.parse("2023-01-01T12:00:00Z");
        CourseNotificationDTO notification = createNewMessageNotification(creationDate, null,  // courseTitle
                null,  // postMarkdownContent
                null,  // channelName
                null,  // authorName
                null,  // channelType
                null,  // authorImageUrl
                null  // authorId
        // postId
        // channelId
        );

        PushNotificationDataDTO result = courseNotificationPushProxyService.fromCourseNotification(notification);

        assertThat(result.notificationPlaceholders()).containsExactly("", "", creationDate.toString(), "", "", "", "", "", "456");
        assertThat(result.type()).isEqualTo(NotificationType.CONVERSATION_NEW_MESSAGE.toString());
    }

    @ParameterizedTest
    @CsvSource({ "unknownType", "otherNotification" })
    void shouldReturnOriginalDTOWhenNotificationTypeIsNotSupported(String notificationType) {
        ZonedDateTime creationDate = ZonedDateTime.parse("2023-01-01T12:00:00Z");
        CourseNotificationDTO notification = createNotificationWithType(creationDate, notificationType);
        var notificationSerializedDTO = new CourseNotificationSerializedDTO(notification);

        PushNotificationDataDTO result = courseNotificationPushProxyService.fromCourseNotification(notification);

        assertThat(result.courseNotificationDTO()).isEqualTo(notificationSerializedDTO);
        assertThat(result.notificationPlaceholders()).isNull();
    }

    /**
     * Helper method to create a new message notification with specific parameters
     */
    private CourseNotificationDTO createNewMessageNotification(ZonedDateTime creationDate, String courseTitle, String postMarkdownContent, String channelName, String authorName,
            String channelType, String authorImageUrl, String authorId) {

        var parameters = new HashMap<String, Object>();
        parameters.put("courseTitle", courseTitle);
        parameters.put("postMarkdownContent", postMarkdownContent);
        parameters.put("channelName", channelName);
        parameters.put("authorName", authorName);
        parameters.put("channelType", channelType);
        parameters.put("authorImageUrl", authorImageUrl);
        parameters.put("authorId", authorId);
        parameters.put("postId", "456");
        parameters.put("channelId", "789");

        return new CourseNotificationDTO("newMessageNotification", 1L, 1L, creationDate, CourseNotificationCategory.COMMUNICATION, parameters);
    }

    /**
     * Helper method to create a notification with a specific type
     */
    private CourseNotificationDTO createNotificationWithType(ZonedDateTime creationDate, String notificationType) {
        return new CourseNotificationDTO(notificationType, 1L, 1L, creationDate, CourseNotificationCategory.COMMUNICATION, new HashMap<>());
    }
}
