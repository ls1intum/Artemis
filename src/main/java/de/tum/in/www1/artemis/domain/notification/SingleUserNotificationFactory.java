package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;
import java.util.Locale;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
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
                notification.setTarget(targetService.getExercisePostTarget(post, course));
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                title = NotificationTitleTypeConstants.NEW_REPLY_FOR_LECTURE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTarget(targetService.getLecturePostTarget(post, course));
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

    /**
     * Creates an instance of PlagiarismNotification
     *
     * @param plagiarismComparisonId ID of the plagiarism comparison associated with this case.
     * @param recipient              affected student
     * @param author                 instructor creating notification
     * @param message                instructors message
     * @return the PlagiarismNotification
     */
    private static SingleUserNotification createPlagiarismNotification(Long plagiarismComparisonId, Long courseId, User recipient, User author, String message, boolean isUpdate) {
        String title = isUpdate ? "Update on your plagiarism case" : "Possible case of Plagiarism";
        if (recipient.getLangKey() != null) {
            Locale locale = Locale.forLanguageTag(recipient.getLangKey());
            if (locale.equals(Locale.GERMAN) || locale.equals(Locale.GERMANY)) {
                title = isUpdate ? "Update zu deinem Plagiatsfall" : "Möglicher plagiatsfall";
            }
        }
        SingleUserNotification notification = new SingleUserNotification();
        notification.setPriority(NotificationPriority.HIGH);
        notification.setRecipient(recipient);
        notification.setAuthor(author);
        notification.setTarget(notification.targetForPlagiarismCase(plagiarismComparisonId, courseId));
        notification.setNotificationDate(ZonedDateTime.now());
        notification.setText(message);
        notification.setTitle(title);
        return notification;
    }

    public static SingleUserNotification createPlagiarismNotification(Long plagiarismComparisonId, Long courseId, User recipient, User author, String message) {
        return createPlagiarismNotification(plagiarismComparisonId, courseId, recipient, author, message, false);
    }

    public static SingleUserNotification createPlagiarismUpdateNotification(Long plagiarismComparisonId, Long courseId, User recipient, User author) {
        return createPlagiarismNotification(plagiarismComparisonId, courseId, recipient, author, "", true);
    }
}
