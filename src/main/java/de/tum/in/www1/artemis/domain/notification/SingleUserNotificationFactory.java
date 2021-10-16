package de.tum.in.www1.artemis.domain.notification;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.notifications.NotificationTargetService;

public class SingleUserNotificationFactory {

    private static NotificationTargetService targetService = new NotificationTargetService();

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param post which is answered
     * @param notificationType type of the notification that should be created
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Post post, NotificationType notificationType) {
        if (notificationType == NotificationType.NEW_ANSWER_POST_FOR_EXERCISE || notificationType == NotificationType.NEW_ANSWER_POST_FOR_LECTURE
                || notificationType == NotificationType.NEW_ANSWER_POST_FOR_COURSE) {
            User recipient = post.getAuthor();
            String title = NotificationTitleTypeConstants.NEW_ANSWER_POST_FOR_EXERCISE_TITLE;
            String text = "Your post got replied.";
            SingleUserNotification notification = new SingleUserNotification(recipient, title, text);
            if (notificationType == NotificationType.NEW_ANSWER_POST_FOR_EXERCISE) {
                notification.setTarget(targetService.getExercisePostTarget(post));
            }
            else if (notificationType == NotificationType.NEW_ANSWER_POST_FOR_LECTURE) {
                notification.setTarget(targetService.getLecturePostTarget(post));
            }
            else {
                notification.setTarget(targetService.getCoursePostTarget(post));
            }
            return notification;
        }
        throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
    }
}
