package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;
import java.util.Locale;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;

public class SingleUserNotificationFactory {

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param answerPost       answer for which a notification should be created
     * @param notificationType type of the notification that should be created
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(AnswerPost answerPost, NotificationType notificationType) {
        if (notificationType == NotificationType.NEW_ANSWER_POST_FOR_EXERCISE || notificationType == NotificationType.NEW_ANSWER_POST_FOR_LECTURE) {
            User recipient = answerPost.getPost().getAuthor();
            User author = answerPost.getAuthor();
            String title = "New Reply";
            String text = "Your post got replied.";
            SingleUserNotification notification = new SingleUserNotification(recipient, author, title, text);
            if (notificationType == NotificationType.NEW_ANSWER_POST_FOR_EXERCISE) {
                notification.setTarget(notification.answerPostTargetForExercise(answerPost));
            }
            else {
                notification.setTarget(notification.answerPostTargetForLecture(answerPost));
            }
            return notification;
        }
        throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
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
