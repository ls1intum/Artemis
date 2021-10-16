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
        User recipient = post.getAuthor();
        String title;
        String text = "Your post got replied.";
        SingleUserNotification notification;
        switch (notificationType) {
            case NEW_ANSWER_POST_FOR_EXERCISE -> {
                title = NotificationTitleTypeConstants.NEW_ANSWER_POST_FOR_EXERCISE_TITLE;
                notification = new SingleUserNotification(recipient, title, text);
                notification.setTarget(targetService.getExercisePostTarget(post));
            }
            case NEW_ANSWER_POST_FOR_LECTURE -> {
                title = NotificationTitleTypeConstants.NEW_ANSWER_POST_FOR_LECTURE_TITLE;
                notification = new SingleUserNotification(recipient, title, text);
                notification.setTarget(targetService.getLecturePostTarget(post));
            }
            case NEW_ANSWER_POST_FOR_COURSE -> {
                title = NotificationTitleTypeConstants.NEW_ANSWER_POST_FOR_COURSE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, text);
                notification.setTarget(targetService.getCoursePostTarget(post));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }
}
