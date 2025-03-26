package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_NEW_MESSAGE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.notification.NotificationTarget;
import de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSerializedDTO;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.PushNotificationDataDTO;
import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Service responsible for transforming course notifications into a format compatible with native mobile devices.
 *
 * <p>
 * This service acts as a compatibility layer to translate course notifications into the expected format
 * for push notifications on mobile devices.
 * </p>
 *
 * <p>
 * Note: This service is intended as a temporary solution until both iOS and Android devices
 * can handle the new notification payload directly, at which point this service can be removed.
 * </p>
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationPushProxyService {

    /**
     * Converts a CourseNotificationDTO into a {@link PushNotificationDataDTO} suitable for native device consumption.
     *
     * <p>
     * This method handles the notification type-specific transformations, setting up the appropriate
     * placeholders, targets, and other metadata based on the notification type.
     * </p>
     *
     * @param courseNotificationDTO The course notification to transform
     * @return A {@link PushNotificationDataDTO} object containing the transformed notification data
     */
    public PushNotificationDataDTO fromCourseNotification(CourseNotificationDTO courseNotificationDTO) {
        String[] notificationPlaceholders;
        String target;
        String type;
        String date;
        int version = Constants.PUSH_NOTIFICATION_VERSION;
        Map<String, String> parameters = courseNotificationDTO.parameters().entrySet().stream().collect(HashMap::new,
                (map, entry) -> map.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue())), HashMap::putAll);

        switch (courseNotificationDTO.notificationType()) {
            case "newMessageNotification":
                notificationPlaceholders = new String[] { parameters.get("courseTitle"), parameters.get("postMarkdownContent"), courseNotificationDTO.creationDate().toString(),
                        parameters.get("channelName"), parameters.get("authorName"), parameters.get("channelType"), parameters.get("authorImageUrl"), parameters.get("authorId"),
                        parameters.get("postId"), };

                var notificationTarget = new NotificationTarget(NotificationTargetFactory.NEW_MESSAGE_TEXT, Long.parseLong(parameters.get("postId")),
                        NotificationTargetFactory.MESSAGE_TEXT, courseNotificationDTO.courseId(), NotificationTargetFactory.COURSES_TEXT);
                notificationTarget.setConversationId(Long.parseLong(parameters.get("channelId")));
                target = notificationTarget.toJsonString();
                type = CONVERSATION_NEW_MESSAGE.toString();
                date = courseNotificationDTO.creationDate().toString();
                break;
            default:
                return new PushNotificationDataDTO(new CourseNotificationSerializedDTO(courseNotificationDTO));
        }

        return new PushNotificationDataDTO(replaceNullPlaceholders(notificationPlaceholders), target, type, date, version,
                new CourseNotificationSerializedDTO(courseNotificationDTO));
    }

    /**
     * Since the native applications expect an array of non-null strings, we need to replace null values with "".
     *
     * @param notificationPlaceholders the array of placeholders
     * @return the array of placeholders without any null values
     */
    private String[] replaceNullPlaceholders(String[] notificationPlaceholders) {
        return Arrays.stream(notificationPlaceholders).map((s) -> s == null ? "" : s).toArray(String[]::new);
    }
}
