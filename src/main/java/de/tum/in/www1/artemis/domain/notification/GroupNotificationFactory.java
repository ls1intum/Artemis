package de.tum.in.www1.artemis.domain.notification;

import java.util.List;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;

public class GroupNotificationFactory {

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param attachment            for which a notification should be created
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType      type of the notification that should be created
     * @param notificationText      custom notification text
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Attachment attachment, User author, GroupNotificationType groupNotificationType, NotificationType notificationType,
            String notificationText) {
        String title, text;
        if (notificationType == NotificationType.ATTACHMENT_CHANGE) {
            title = NotificationTitleTypeConstants.ATTACHMENT_CHANGE_TITLE;
            text = "Attachment \"" + attachment.getName() + "\" updated.";
        }
        else {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        if (notificationText != null) {
            text = notificationText;
        }

        Lecture lecture;
        // we get the lecture either from the directly connected lecture or from the attachment unit
        if (attachment.getAttachmentUnit() != null) {
            lecture = attachment.getAttachmentUnit().getLecture();
        }
        else {
            lecture = attachment.getLecture();
        }
        Course course = lecture.getCourse();
        GroupNotification notification = new GroupNotification(course, title, text, author, groupNotificationType);

        notification.setTarget(notification.getAttachmentUpdated(lecture));

        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param exercise              for which a notification should be created
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType      type of the notification that should be created
     * @param notificationText      custom notification text
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Exercise exercise, User author, GroupNotificationType groupNotificationType, NotificationType notificationType,
            String notificationText) {
        String title, text;
        switch (notificationType) {
            case EXERCISE_CREATED -> {
                title = NotificationTitleTypeConstants.EXERCISE_CREATED_TITLE;
                text = "A new exercise \"" + exercise.getTitle() + "\" got created.";
            }
            case EXERCISE_PRACTICE -> {
                title = NotificationTitleTypeConstants.EXERCISE_PRACTICE_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" is now open for practice.";
            }
            case QUIZ_EXERCISE_STARTED -> {
                title = NotificationTitleTypeConstants.QUIZ_EXERCISE_STARTED_TITLE;
                text = "Quiz \"" + exercise.getTitle() + "\" just started.";
            }
            case EXERCISE_UPDATED -> {
                if (exercise.isExamExercise()) {
                    title = NotificationTitleTypeConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
                    text = "Exam Exercise \"" + exercise.getTitle() + "\" updated.";
                }
                else {
                    title = NotificationTitleTypeConstants.EXERCISE_UPDATED_TITLE;
                    text = "Exercise \"" + exercise.getTitle() + "\" updated.";
                }
            }
            case DUPLICATE_TEST_CASE -> {
                title = NotificationTitleTypeConstants.DUPLICATE_TEST_CASE_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" has multiple test cases with the same name.";
            }
            case ILLEGAL_SUBMISSION -> {
                title = NotificationTitleTypeConstants.ILLEGAL_SUBMISSION_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" has illegal submissions of students.";
            }

            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        // Catches 3 different use cases : notificationText exists for 1) non-exam exercise update & 2) live exam exercise update with a notification text
        // 3) hidden/silent live exam exercise update without a set notificationText, thus no pop-up will be visible for the students
        if (notificationText != null || NotificationTitleTypeConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(title)) {
            text = notificationText;
        }

        GroupNotification notification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), title, text, author, groupNotificationType);

        // Exercises for exams
        if (exercise.isExamExercise()) {
            notification.setTarget(notification.getExamExerciseTargetWithExerciseUpdate(exercise));
            notification.setPriority(NotificationPriority.HIGH);
        }
        // Exercises for courses (not for exams)
        else if (notificationType == NotificationType.EXERCISE_CREATED) {
            notification.setTarget(notification.getExerciseCreatedTarget(exercise));
        }
        else if (notificationType == NotificationType.DUPLICATE_TEST_CASE) {
            notification.setTarget(notification.getExamProgrammingExerciseOrTestCaseTarget((ProgrammingExercise) exercise, "duplicateTestCase"));
        }
        else {
            notification.setTarget(notification.getExerciseUpdatedTarget(exercise));
        }

        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param post              for which a notification should be created
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType      type of the notification that should be created
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Post post, User author, GroupNotificationType groupNotificationType, NotificationType notificationType) {
        String title, text;
        Course course;
        switch (notificationType) {
            case NEW_POST_FOR_EXERCISE -> {
                Exercise exercise = post.getExercise();
                title = NotificationTitleTypeConstants.NEW_POST_FOR_EXERCISE_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" got a new post.";
                course = exercise.getCourseViaExerciseGroupOrCourseMember();
            }
            case NEW_POST_FOR_LECTURE -> {
                Lecture lecture = post.getLecture();
                title = NotificationTitleTypeConstants.NEW_POST_FOR_LECTURE_TITLE;
                text = "Lecture \"" + lecture.getTitle() + "\" got a new post.";
                course = lecture.getCourse();
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, author, groupNotificationType);

        if (notificationType == NotificationType.NEW_POST_FOR_EXERCISE) {
            notification.setTarget(notification.getExercisePostTarget(post.getExercise()));
        }
        else {
            notification.setTarget(notification.getLecturePostTarget(post.getLecture()));
        }

        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param answerPost            for which a notification should be created
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType      type of the notification that should be created
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(AnswerPost answerPost, User author, GroupNotificationType groupNotificationType, NotificationType notificationType) {
        String text, title;
        Course course;
        switch (notificationType) {
            case NEW_ANSWER_POST_FOR_EXERCISE -> {
                Exercise exercise = answerPost.getPost().getExercise();
                title = NotificationTitleTypeConstants.NEW_ANSWER_POST_FOR_EXERCISE_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" got a new reply.";
                course = exercise.getCourseViaExerciseGroupOrCourseMember();
            }
            case NEW_ANSWER_POST_FOR_LECTURE -> {
                Lecture lecture = answerPost.getPost().getLecture();
                title = NotificationTitleTypeConstants.NEW_ANSWER_POST_FOR_LECTURE_TITLE;
                text = "Lecture \"" + lecture.getTitle() + "\" got a new reply.";
                course = lecture.getCourse();
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, author, groupNotificationType);

        if (notificationType == NotificationType.NEW_ANSWER_POST_FOR_EXERCISE) {
            notification.setTarget(notification.getExerciseAnswerPostTarget(answerPost.getPost().getExercise()));
        }
        else {
            notification.setTarget(notification.getLectureAnswerPostTarget(answerPost.getPost().getLecture()));
        }

        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param course                the course being archived
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType      type of the notification that should be created
     * @param archiveErrors         a list of errors that occured during archiving
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Course course, User author, GroupNotificationType groupNotificationType, NotificationType notificationType,
            List<String> archiveErrors) {
        String title, text;
        switch (notificationType) {
            case COURSE_ARCHIVE_STARTED -> {
                title = NotificationTitleTypeConstants.COURSE_ARCHIVE_STARTED_TITLE;
                text = "The course \"" + course.getTitle() + "\" is being archived.";
            }
            case COURSE_ARCHIVE_FINISHED -> {
                title = NotificationTitleTypeConstants.COURSE_ARCHIVE_FINISHED_TITLE;
                text = "The course \"" + course.getTitle() + "\" has been archived.";

                if (!archiveErrors.isEmpty()) {
                    text += " Some exercises couldn't be included in the archive:<br/><br/>" + String.join("<br/><br/>", archiveErrors);
                }
            }
            case COURSE_ARCHIVE_FAILED -> {
                title = NotificationTitleTypeConstants.COURSE_ARCHIVE_FAILED_TITLE;
                text = "The was a problem archiving course \"" + course.getTitle() + "\": <br/><br/>" + String.join("<br/><br/>", archiveErrors);
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, author, groupNotificationType);
        notification.setTarget(notification.getCourseTarget(course, "courseArchiveUpdated"));
        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param exam                the exam being archived
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType      type of the notification that should be created
     * @param archiveErrors         a list of errors that occured during archiving
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Exam exam, User author, GroupNotificationType groupNotificationType, NotificationType notificationType,
            List<String> archiveErrors) {
        String title, text;
        switch (notificationType) {
            case EXAM_ARCHIVE_STARTED -> {
                title = NotificationTitleTypeConstants.EXAM_ARCHIVE_STARTED_TITLE;
                text = "The exam \"" + exam.getTitle() + "\" is being archived.";
            }
            case EXAM_ARCHIVE_FINISHED -> {
                title = NotificationTitleTypeConstants.EXAM_ARCHIVE_FINISHED_TITLE;
                text = "The exam \"" + exam.getTitle() + "\" has been archived.";

                if (!archiveErrors.isEmpty()) {
                    text += " Some exercises couldn't be included in the archive:<br/><br/>" + String.join("<br/><br/>", archiveErrors);
                }
            }
            case EXAM_ARCHIVE_FAILED -> {
                title = NotificationTitleTypeConstants.EXAM_ARCHIVE_FAILED_TITLE;
                text = "The was a problem archiving exam \"" + exam.getTitle() + "\": <br/><br/>" + String.join("<br/><br/>", archiveErrors);
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(exam.getCourse(), title, text, author, groupNotificationType);
        notification.setTarget(notification.getCourseTarget(exam.getCourse(), "examArchiveUpdated"));
        return notification;
    }
}
