package de.tum.in.www1.artemis.domain.notification;

import java.util.List;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.notifications.NotificationTargetService;

public class GroupNotificationFactory {

    private static NotificationTargetService targetService = new NotificationTargetService();

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
        String title;
        String text;
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

        notification.setTarget(targetService.getAttachmentUpdatedTarget(lecture));

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
        String title;
        String text;
        switch (notificationType) {
            case EXERCISE_RELEASED -> {
                title = NotificationTitleTypeConstants.EXERCISE_RELEASED_TITLE;
                text = "A new exercise \"" + exercise.getTitle() + "\" got released.";
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
            if (NotificationTitleTypeConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(title)) {
                notification.setTarget(targetService.getExamExerciseTargetWithExerciseUpdate(exercise));
                notification.setPriority(NotificationPriority.HIGH);
            }
            else if (exercise instanceof ProgrammingExercise) {
                notification.setTarget(targetService.getExamProgrammingExerciseOrTestCaseTarget((ProgrammingExercise) exercise, "exerciseUpdated"));
            }
        }
        // Exercises for courses (not for exams)
        else if (notificationType == NotificationType.EXERCISE_RELEASED) {
            notification.setTarget(targetService.getExerciseReleasedTarget(exercise));
        }
        else if (notificationType == NotificationType.DUPLICATE_TEST_CASE) {
            notification.setTarget(targetService.getExamProgrammingExerciseOrTestCaseTarget((ProgrammingExercise) exercise, "duplicateTestCase"));
        }
        else {
            notification.setTarget(targetService.getExerciseUpdatedTarget(exercise));
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
     * @param course                the post belongs to
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Post post, User author, GroupNotificationType groupNotificationType, NotificationType notificationType, Course course) {
        String title;
        String text;
        GroupNotification notification;
        switch (notificationType) {
            case NEW_EXERCISE_POST -> {
                Exercise exercise = post.getExercise();
                title = NotificationTitleTypeConstants.NEW_EXERCISE_POST_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" got a new post.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTarget(targetService.getExercisePostTarget(post, course));
            }
            case NEW_LECTURE_POST -> {
                Lecture lecture = post.getLecture();
                title = NotificationTitleTypeConstants.NEW_LECTURE_POST_TITLE;
                text = "Lecture \"" + lecture.getTitle() + "\" got a new post.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTarget(targetService.getLecturePostTarget(post, course));
            }
            case NEW_COURSE_POST -> {
                title = NotificationTitleTypeConstants.NEW_COURSE_POST_TITLE;
                text = "Course \"" + course.getTitle() + "\" got a new course-wide post.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTarget(targetService.getCoursePostTarget(post, course));
            }
            case NEW_ANNOUNCEMENT_POST -> {
                title = NotificationTitleTypeConstants.NEW_ANNOUNCEMENT_POST_TITLE;
                text = "Course \"" + course.getTitle() + "\" got a new announcement.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTarget(targetService.getCoursePostTarget(post, course));
            }
            case NEW_REPLY_FOR_EXERCISE_POST -> {
                Exercise exercise = post.getExercise();
                title = NotificationTitleTypeConstants.NEW_REPLY_FOR_EXERCISE_POST_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" got a new reply.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTarget(targetService.getExercisePostTarget(post, course));
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                Lecture lecture = post.getLecture();
                title = NotificationTitleTypeConstants.NEW_REPLY_FOR_LECTURE_POST_TITLE;
                text = "Lecture \"" + lecture.getTitle() + "\" got a new reply.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTarget(targetService.getLecturePostTarget(post, course));
            }
            case NEW_REPLY_FOR_COURSE_POST -> {
                title = NotificationTitleTypeConstants.NEW_REPLY_FOR_COURSE_POST_TITLE;
                text = "Course-wide post in course\"" + course.getTitle() + "\" got a new reply.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTarget(targetService.getCoursePostTarget(post, course));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
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
     * @param archiveErrors         a list of errors that occurred during archiving
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Course course, User author, GroupNotificationType groupNotificationType, NotificationType notificationType,
            List<String> archiveErrors) {
        String title;
        String text;
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
        notification.setTarget(targetService.getCourseTarget(course, "courseArchiveUpdated"));
        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param exam                  the exam being archived
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType      type of the notification that should be created
     * @param archiveErrors         a list of errors that occurred during archiving
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Exam exam, User author, GroupNotificationType groupNotificationType, NotificationType notificationType,
            List<String> archiveErrors) {
        String title;
        String text;
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
        notification.setTarget(targetService.getCourseTarget(exam.getCourse(), "examArchiveUpdated"));
        return notification;
    }
}
