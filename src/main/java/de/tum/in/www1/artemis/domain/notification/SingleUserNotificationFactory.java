package de.tum.in.www1.artemis.domain.notification;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class SingleUserNotificationFactory {

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param studentQuestionAnswer answer for which a notification should be created
     * @param notificationType type of the notification that should be created
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(StudentQuestionAnswer studentQuestionAnswer, NotificationType notificationType) {
        if (notificationType == NotificationType.NEW_ANSWER_FOR_EXERCISE || notificationType == NotificationType.NEW_ANSWER_FOR_LECTURE) {
            User recipient = studentQuestionAnswer.getQuestion().getAuthor();
            User author = studentQuestionAnswer.getAuthor();
            String title = "New Answer";
            String text = "Your Question got answered.";
            SingleUserNotification notification = new SingleUserNotification(recipient, author, title, text);
            if (notificationType == NotificationType.NEW_ANSWER_FOR_EXERCISE) {
                notification.setTarget(notification.studentQuestionAnswerTargetForExercise(studentQuestionAnswer));
            }
            else {
                notification.setTarget(notification.studentQuestionAnswerTargetForLecture(studentQuestionAnswer));
            }
            return notification;
        }
        throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
    }
}
