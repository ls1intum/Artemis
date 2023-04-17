package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.createTutorialGroupTarget;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

public class TutorialGroupNotificationFactory {

    /**
     * Creates a TutorialGroupNotification for the given tutorial group and notification type.
     *
     * @param tutorialGroup    the tutorial group for which the notification is created
     * @param notificationType the type of the notification
     * @return the created notification
     */
    public static TutorialGroupNotification createTutorialGroupNotification(TutorialGroup tutorialGroup, NotificationType notificationType) {
        var title = findCorrespondingNotificationTitleOrThrow(notificationType);
        var text = "";
        switch (notificationType) {
            case TUTORIAL_GROUP_DELETED -> text = TUTORIAL_GROUP_DELETED_TEXT;

            case TUTORIAL_GROUP_UPDATED -> text = TUTORIAL_GROUP_UPDATED_TEXT;
        }
        var placeholderValues = new String[] { tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle() };
        var notification = new TutorialGroupNotification(tutorialGroup, title, text, true, placeholderValues, notificationType);
        setNotificationTarget(notification);
        return notification;
    }

    /**
     * Creates a TutorialGroupNotification for the given tutorial group and notification type.
     *
     * @param tutorialGroup    the tutorial group for which the notification is created
     * @param notificationType the type of the notification
     * @param notificationText text of the notification
     * @return the created notification
     */
    public static TutorialGroupNotification createTutorialGroupNotification(TutorialGroup tutorialGroup, NotificationType notificationType, String notificationText) {
        var title = findCorrespondingNotificationTitleOrThrow(notificationType);
        var notification = new TutorialGroupNotification(tutorialGroup, title, notificationText, false, new String[] { tutorialGroup.getCourse().getTitle() }, notificationType);
        setNotificationTarget(notification);
        return notification;
    }

    private static void setNotificationTarget(TutorialGroupNotification notification) {
        if (notification.notificationType == NotificationType.TUTORIAL_GROUP_UPDATED) {
            notification.setTransientAndStringTarget(createTutorialGroupTarget(notification.getTutorialGroup(), notification.getTutorialGroup().getCourse().getId(), false, true));
        }
        if (notification.notificationType == NotificationType.TUTORIAL_GROUP_DELETED) {
            notification.setTransientAndStringTarget(createTutorialGroupTarget(notification.getTutorialGroup(), notification.getTutorialGroup().getCourse().getId(), false, false));
        }
    }

}
