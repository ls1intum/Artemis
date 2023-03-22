package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.createConversationMessageTarget;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.findCorrespondingNotificationTitleOrThrow;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;

public class ConversationNotificationFactory {

    /**
     * Creates a ConversationNotification for the given conversation and notification type.
     *
     * @param message          the message for which the notification is created
     * @param notificationType the type of the notification
     * @param notificationText text of the notification
     * @return the created notification
     */
    public static ConversationNotification createConversationMessageNotification(Post message, NotificationType notificationType, String notificationText) {
        String title = findCorrespondingNotificationTitleOrThrow(notificationType);
        var notification = new ConversationNotification(message.getAuthor(), message, message.getConversation(), title, notificationText);
        setNotificationTarget(notification);
        return notification;
    }

    private static void setNotificationTarget(ConversationNotification notification) {
        Long courseId = notification.getMessage().getConversation().getCourse().getId();
        NotificationTarget notificationTarget = createConversationMessageTarget(notification.getMessage(), courseId);
        notification.setTransientAndStringTarget(notificationTarget);
    }
}
