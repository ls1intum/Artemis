package de.tum.in.www1.artemis.domain.notification;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.notifications.NotificationTargetService;

public class SingleUserNotificationFactory {

    private static NotificationTargetService targetService = new NotificationTargetService();

    private static final String POST_NOTIFICATION_TEXT = "Your post got replied.";

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param post which is answered
     * @param notificationType type of the notification that should be created
     * @param course that the post belongs to
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Post post, NotificationType notificationType, Course course) {
        User recipient = post.getAuthor();
        String title;
        SingleUserNotification notification;
        switch (notificationType) {
            case NEW_REPLY_FOR_EXERCISE_POST -> {
                title = NotificationTitleTypeConstants.NEW_REPLY_FOR_EXERCISE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTarget(targetService.getExercisePostTarget(post));
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                title = NotificationTitleTypeConstants.NEW_REPLY_FOR_LECTURE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTarget(targetService.getLecturePostTarget(post));
            }
            case NEW_REPLY_FOR_COURSE_POST -> {
                title = NotificationTitleTypeConstants.NEW_REPLY_FOR_COURSE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTarget(targetService.getCoursePostTarget(post, course));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }
}
