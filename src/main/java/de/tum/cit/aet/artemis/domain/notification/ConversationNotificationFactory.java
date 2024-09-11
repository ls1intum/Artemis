package de.tum.cit.aet.artemis.domain.notification;

import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.findCorrespondingNotificationTitleOrThrow;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createConversationMessageTarget;

import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;
import de.tum.cit.aet.artemis.domain.metis.Post;

public class ConversationNotificationFactory {

    /**
     * Creates a ConversationNotification for the given conversation and notification type.
     *
     * @param courseId          the id of the course
     * @param message           the message for which the notification is created
     * @param notificationType  the type of the notification
     * @param notificationText  text of the notification
     * @param textIsPlaceholder whether the notification text is a placeholder
     * @param placeholderValues values for the placeholders in the notification text
     * @return the created notification
     */
    public static ConversationNotification createConversationMessageNotification(Long courseId, Post message, NotificationType notificationType, String notificationText,
            boolean textIsPlaceholder, String[] placeholderValues) {
        String title = findCorrespondingNotificationTitleOrThrow(notificationType);
        var notification = new ConversationNotification(message.getAuthor(), message, message.getConversation(), title, notificationText, textIsPlaceholder, placeholderValues);
        setNotificationTarget(courseId, notification);
        return notification;
    }

    private static void setNotificationTarget(Long courseId, ConversationNotification notification) {
        NotificationTarget notificationTarget = createConversationMessageTarget(notification.getMessage(), courseId);
        notification.setTransientAndStringTarget(notificationTarget);
    }
}
