package de.tum.in.www1.artemis.domain.notification;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class GroupNotificationFactory {

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param attachment for which a notification should be created
     * @param author of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType type of the notification that should be created
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Attachment attachment, User author, GroupNotificationType groupNotificationType, NotificationType notificationType) {
        String title, text;
        if (notificationType == NotificationType.ATTACHMENT_CHANGE) {
            title = "Attachment updated";
            text = "Attachment \"" + attachment.getName() + "\" updated.";
        }
        else {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        Course course = attachment.getLecture().getCourse();
        GroupNotification notification = new GroupNotification(course, title, text, author, groupNotificationType);

        // Set target
        notification.setTarget(notification.getAttachmentUpdated(attachment.getLecture()));

        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param exercise for which a notification should be created
     * @param author of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType type of the notification that should be created
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Exercise exercise, User author, GroupNotificationType groupNotificationType, NotificationType notificationType) {
        String title, text;
        if (notificationType == NotificationType.EXERCISE_CREATED) {
            title = "Exercise created";
            text = "A new exercise \"" + exercise.getTitle() + "\" got created.";
        }
        else if (notificationType == NotificationType.EXERCISE_PRACTICE) {
            title = "Exercise open for practice";
            text = "Exercise \"" + exercise.getTitle() + "\" is now open for practice.";
        }
        else if (notificationType == NotificationType.EXERCISE_STARTED) {
            title = "Exercise started";
            text = "Exercise \"" + exercise.getTitle() + "\" just started.";
        }
        else if (notificationType == NotificationType.EXERCISE_UPDATED) {
            title = "Exercise updated";
            text = "Exercise \"" + exercise.getTitle() + "\" updated.";
        }
        else {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(exercise.getCourse(), title, text, author, groupNotificationType);

        // Set the target
        if (notificationType == NotificationType.EXERCISE_CREATED) {
            notification.setTarget(notification.getExerciseCreatedTarget(exercise));
        }
        else {
            notification.setTarget(notification.getExerciseUpdatedTarget(exercise));
        }

        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param question for which a notification should be created
     * @param author of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType type of the notification that should be created
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(StudentQuestion question, User author, GroupNotificationType groupNotificationType, NotificationType notificationType) {
        String title, text;
        Course course;
        if (notificationType == NotificationType.NEW_QUESTION_FOR_EXERCISE) {
            Exercise exercise = question.getExercise();
            title = "New Question";
            text = "Exercise \"" + exercise.getTitle() + "\" got a new question.";
            course = exercise.getCourse();
        }
        else if (notificationType == NotificationType.NEW_QUESTION_FOR_LECTURE) {
            Lecture lecture = question.getLecture();
            title = "New Question";
            text = "Lecture \"" + lecture.getTitle() + "\" got a new question.";
            course = lecture.getCourse();
        }
        else {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, author, groupNotificationType);

        // Set target
        if (notificationType == NotificationType.NEW_QUESTION_FOR_EXERCISE) {
            notification.setTarget(notification.getExerciseQuestionTarget(question.getExercise()));
        }
        else {
            notification.setTarget(notification.getLectureQuestionTarget(question.getLecture()));
        }

        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param answer for which a notification should be created
     * @param author of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType type of the notification that should be created
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(StudentQuestionAnswer answer, User author, GroupNotificationType groupNotificationType, NotificationType notificationType) {
        String text, title;
        Course course;
        if (notificationType == NotificationType.NEW_ANSWER_FOR_EXERCISE) {
            Exercise exercise = answer.getQuestion().getExercise();
            title = "New Answer";
            text = "Exercise \"" + exercise.getTitle() + "\" got a new answer.";
            course = exercise.getCourse();
        }
        else if (notificationType == NotificationType.NEW_ANSWER_FOR_LECTURE) {
            Lecture lecture = answer.getQuestion().getLecture();
            title = "New Answer";
            text = "Lecture \"" + lecture.getTitle() + "\" got a new answer.";
            course = lecture.getCourse();
        }
        else {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, author, groupNotificationType);

        // Set target
        if (notificationType == NotificationType.NEW_ANSWER_FOR_EXERCISE) {
            notification.setTarget(notification.getExerciseAnswerTarget(answer.getQuestion().getExercise()));
        }
        else {
            notification.setTarget(notification.getLectureAnswerTarget(answer.getQuestion().getLecture()));
        }

        return notification;
    }
}
