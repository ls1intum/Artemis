package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.notifications.NotificationTargetService;

public class SingleUserNotificationFactory {

    private static NotificationTargetService targetService = new NotificationTargetService();

    private static final String POST_NOTIFICATION_TEXT = "Your post got replied.";

    /**
     * Creates an instance of SingleUserNotification based on posts.
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
                title = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTarget(targetService.getExercisePostTarget(post, course));
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                title = NEW_REPLY_FOR_LECTURE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTarget(targetService.getLecturePostTarget(post, course));
            }
            case NEW_REPLY_FOR_COURSE_POST -> {
                title = NEW_REPLY_FOR_COURSE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTarget(targetService.getCoursePostTarget(post, course));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification based on exercises.
     *
     * @param exercise for which a notification should be created
     * @param notificationType type of the notification that should be created
     * @param recipient who should be notified
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Exercise exercise, NotificationType notificationType, User recipient) {
        String title;
        String notificationText;
        SingleUserNotification notification;
        switch (notificationType) {
            case FILE_SUBMISSION_SUCCESSFUL -> {
                title = FILE_SUBMISSION_SUCCESSFUL_TITLE;
                notificationText = "Your file for the exercise \"" + exercise.getTitle() + "\" was successfully submitted.";
                notification = new SingleUserNotification(recipient, title, notificationText);
                notification.setTarget(targetService.getExerciseTarget(exercise, title));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification based on plagiarisms.
     *
     * @param plagiarismComparisonId ID of the plagiarism comparison associated with this case.
     * @param recipient              affected student
     * @param author                 instructor creating notification
     * @param message                instructors message
     * @return the PlagiarismNotification
     */
    private static SingleUserNotification createNotification(NotificationType notificationType, Long plagiarismComparisonId, Long courseId, User recipient, User author,
            String message) {
        String title;
        SingleUserNotification notification;
        switch (notificationType) {
            case POSSIBLE_PLAGIARISM_CASE -> title = POSSIBLE_PLAGIARISM_CASE_TITLE;
            case PLAGIARISM_CASE_UPDATE -> title = PLAGIARISM_CASE_UPDATE_TITLE;
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        notification = new SingleUserNotification(recipient, title, message);
        notification.setPriority(NotificationPriority.HIGH);
        notification.setAuthor(author);

        notification.setTarget(targetService.getTargetForPlagiarismCase(plagiarismComparisonId, courseId));
        return notification;
    }

    public static SingleUserNotification createNotification(Long plagiarismComparisonId, Long courseId, User recipient, User author, String message) {
        return createNotification(plagiarismComparisonId, courseId, recipient, author, message, false);
    }

    public static SingleUserNotification createPlagiarismUpdateNotification(Long plagiarismComparisonId, Long courseId, User recipient, User author) {
        return createNotification(plagiarismComparisonId, courseId, recipient, author, "", true);
    }
}
