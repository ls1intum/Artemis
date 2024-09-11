package de.tum.cit.aet.artemis.domain.notification;

import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DELETED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_UPDATED;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_DELETED_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.TUTORIAL_GROUP_UPDATED_TEXT;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.findCorrespondingNotificationTitleOrThrow;
import static de.tum.cit.aet.artemis.domain.notification.NotificationTargetFactory.createTutorialGroupTarget;

import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroup;

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
        var placeholderValues = createPlaceholdersTutorialGroupDeleted(tutorialGroup.getCourse().getTitle(), tutorialGroup.getTitle());
        var notification = new TutorialGroupNotification(tutorialGroup, title, text, true, placeholderValues, notificationType);
        setNotificationTarget(notification);
        return notification;
    }

    @NotificationPlaceholderCreator(values = { TUTORIAL_GROUP_DELETED })
    public static String[] createPlaceholdersTutorialGroupDeleted(String courseTitle, String tutorialGroupTitle) {
        return new String[] { courseTitle, tutorialGroupTitle };
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
        var notification = new TutorialGroupNotification(tutorialGroup, title, notificationText, false, createPlaceholderTutorialGroupUpdated(tutorialGroup.getCourse().getTitle()),
                notificationType);
        setNotificationTarget(notification);
        return notification;
    }

    @NotificationPlaceholderCreator(values = { TUTORIAL_GROUP_UPDATED })
    public static String[] createPlaceholderTutorialGroupUpdated(String courseTitle) {
        return new String[] { courseTitle };
    }

    private static void setNotificationTarget(TutorialGroupNotification notification) {
        if (notification.notificationType == TUTORIAL_GROUP_UPDATED) {
            notification.setTransientAndStringTarget(createTutorialGroupTarget(notification.getTutorialGroup(), notification.getTutorialGroup().getCourse().getId(), false, true));
        }
        if (notification.notificationType == TUTORIAL_GROUP_DELETED) {
            notification.setTransientAndStringTarget(createTutorialGroupTarget(notification.getTutorialGroup(), notification.getTutorialGroup().getCourse().getId(), false, false));
        }
    }

}
