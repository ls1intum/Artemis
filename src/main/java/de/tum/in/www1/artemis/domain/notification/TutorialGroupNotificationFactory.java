package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.createTutorialGroupTarget;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.findCorrespondingNotificationTitleOrThrow;

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
            case TUTORIAL_GROUP_DELETED -> text = "The tutorial group " + tutorialGroup.getTitle() + " has been deleted.";

            case TUTORIAL_GROUP_UPDATED -> text = "The tutorial group " + tutorialGroup.getTitle() + " has been updated.";
        }
        var notification = new TutorialGroupNotification(tutorialGroup, title, text, notificationType);
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
