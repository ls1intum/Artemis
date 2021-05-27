package de.tum.in.www1.artemis.domain.notification;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;

public class SingleUserNotificationFactory {

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param answerPost answer for which a notification should be created
     * @param notificationType type of the notification that should be created
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(AnswerPost answerPost, NotificationType notificationType) {
        if (notificationType == NotificationType.NEW_ANSWER_FOR_EXERCISE || notificationType == NotificationType.NEW_ANSWER_FOR_LECTURE) {
            User recipient = answerPost.getPost().getAuthor();
            User author = answerPost.getAuthor();
            String title = "New Answer";
            String text = "Your Question got answered.";
            SingleUserNotification notification = new SingleUserNotification(recipient, author, title, text);
            if (notificationType == NotificationType.NEW_ANSWER_FOR_EXERCISE) {
                notification.setTarget(notification.studentQuestionAnswerTargetForExercise(answerPost));
            }
            else {
                notification.setTarget(notification.studentQuestionAnswerTargetForLecture(answerPost));
            }
            return notification;
        }
        throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
    }
}
