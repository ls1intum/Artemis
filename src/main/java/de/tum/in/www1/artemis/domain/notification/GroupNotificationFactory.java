package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.HIGH;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.MEDIUM;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;

import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

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
        String title;
        String text;
        boolean textIsPlaceholder;
        String[] placeholderValues;
        if (notificationType == ATTACHMENT_CHANGE) {
            title = NotificationConstants.ATTACHMENT_CHANGE_TITLE;
            text = NotificationConstants.ATTACHMENT_CHANGE_TEXT;
            textIsPlaceholder = true;
            placeholderValues = new String[] {
                    attachment.getExercise() != null ? attachment.getExercise().getCourseViaExerciseGroupOrCourseMember().getTitle()
                            : attachment.getLecture().getCourse().getTitle(),
                    attachment.getName(), attachment.getExercise() != null ? attachment.getExercise().getTitle() : attachment.getLecture().getTitle() };
        }
        else {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        if (notificationText != null) {
            text = notificationText;
            textIsPlaceholder = false;
            placeholderValues = null;
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
        GroupNotification notification = new GroupNotification(course, title, text, textIsPlaceholder, placeholderValues, author, groupNotificationType);

        notification.setTransientAndStringTarget(createAttachmentUpdatedTarget(lecture));

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
        boolean textIsPlaceholder;
        String[] placeholderValues = new String[] { exercise.getCourseViaExerciseGroupOrCourseMember().getTitle(), exercise.getTitle() };
        NotificationPriority priority = MEDIUM;

        switch (notificationType) {
            case EXERCISE_RELEASED -> {
                title = NotificationConstants.EXERCISE_RELEASED_TITLE;
                text = NotificationConstants.EXERCISE_RELEASED_TEXT;
                textIsPlaceholder = true;
            }
            case EXERCISE_PRACTICE -> {
                title = NotificationConstants.EXERCISE_PRACTICE_TITLE;
                text = NotificationConstants.EXERCISE_PRACTICE_TEXT;
                textIsPlaceholder = true;
            }
            case QUIZ_EXERCISE_STARTED -> {
                title = NotificationConstants.QUIZ_EXERCISE_STARTED_TITLE;
                text = NotificationConstants.QUIZ_EXERCISE_STARTED_TEXT;
                textIsPlaceholder = true;
            }
            case EXERCISE_UPDATED -> {
                if (exercise.isExamExercise()) {
                    title = NotificationConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
                    text = NotificationConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TEXT;
                }
                else {
                    title = NotificationConstants.EXERCISE_UPDATED_TITLE;
                    text = NotificationConstants.EXERCISE_UPDATED_TEXT;
                }
                textIsPlaceholder = true;
            }
            case PROGRAMMING_TEST_CASES_CHANGED -> {
                title = NotificationConstants.PROGRAMMING_TEST_CASES_CHANGED_TITLE;
                text = NotificationConstants.PROGRAMMING_TEST_CASES_CHANGED_TEXT;
                textIsPlaceholder = true;
            }
            case NEW_MANUAL_FEEDBACK_REQUEST -> {
                title = NotificationConstants.NEW_MANUAL_FEEDBACK_REQUEST_TITLE;
                text = NotificationConstants.NEW_MANUAL_FEEDBACK_REQUEST_TEXT;
                textIsPlaceholder = true;
            }
            case DUPLICATE_TEST_CASE -> {
                title = NotificationConstants.DUPLICATE_TEST_CASE_TITLE;
                text = notificationText;
                textIsPlaceholder = false;
                priority = HIGH;
            }
            case PROGRAMMING_REPOSITORY_LOCKS -> {
                title = NotificationConstants.PROGRAMMING_REPOSITORY_LOCKS_TITLE;
                text = notificationText;
                textIsPlaceholder = false;
            }
            case PROGRAMMING_BUILD_RUN_UPDATE -> {
                title = NotificationConstants.PROGRAMMING_BUILD_RUN_UPDATE_TITLE;
                text = notificationText;
                textIsPlaceholder = false;
            }
            case ILLEGAL_SUBMISSION -> {
                title = NotificationConstants.ILLEGAL_SUBMISSION_TITLE;
                text = NotificationConstants.ILLEGAL_SUBMISSION_TEXT;
                textIsPlaceholder = true;
                priority = HIGH;
            }

            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        // Catches 3 different use cases : notificationText exists for 1) non-exam exercise update & 2) live exam exercise update with a notification text
        // 3) hidden/silent live exam exercise update without a set notificationText, thus no pop-up will be visible for the students
        if (notificationText != null || NotificationConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(title)) {
            text = notificationText;
            textIsPlaceholder = false;
            placeholderValues = new String[] { exercise.getCourseViaExerciseGroupOrCourseMember().getTitle() };
        }

        GroupNotification notification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), title, text, textIsPlaceholder, placeholderValues, author,
                groupNotificationType, priority);

        // Exercises for exams
        if (exercise.isExamExercise()) {
            if (NotificationConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(title)) {
                notification.setTransientAndStringTarget(createExamExerciseTargetWithExerciseUpdate(exercise));
                notification.setPriority(HIGH);
            }
            else if (exercise instanceof ProgrammingExercise programmingExercise) {
                notification.setTransientAndStringTarget(createExamProgrammingExerciseOrTestCaseTarget(programmingExercise, NotificationTargetFactory.EXERCISE_UPDATED_TEXT));
            }
        }
        // Exercises for courses (not for exams)
        else if (notificationType == EXERCISE_RELEASED) {
            notification.setTransientAndStringTarget(createExerciseReleasedTarget(exercise));
        }
        else if (notificationType == DUPLICATE_TEST_CASE) {
            notification.setTransientAndStringTarget(createDuplicateTestCaseTarget(exercise));
        }
        else {
            notification.setTransientAndStringTarget(createExerciseUpdatedTarget(exercise));
        }
        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param post                  for which a notification should be created
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param course                the post belongs to
     * @return an instance of GroupNotification
     */
    public static GroupNotification createAnnouncementNotification(Post post, User author, GroupNotificationType groupNotificationType, Course course) {
        String title;
        String text;
        String[] placeholderValues;
        GroupNotification notification;
        title = NotificationConstants.NEW_ANNOUNCEMENT_POST_TITLE;
        text = NotificationConstants.NEW_ANNOUNCEMENT_POST_TEXT;
        placeholderValues = ArrayUtils.addAll(NotificationFactory.generatePlaceholderValuesForMessageNotifications(course, post));
        notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
        return notification;
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param post                  for which a notification should be created
     * @param answerPost            to the post for which the notification should be created
     * @param author                of the notification
     * @param groupNotificationType user group type the notification should target
     * @param notificationType      type of the notification that should be created
     * @param course                the post belongs to
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Post post, AnswerPost answerPost, User author, GroupNotificationType groupNotificationType,
            NotificationType notificationType, Course course) {
        return NotificationFactory.createNotificationImplementation(post, answerPost, notificationType, course, (title, placeholderValues) -> {
            String text = "";
            switch (notificationType) {
                case NEW_REPLY_FOR_EXERCISE_POST -> text = NotificationConstants.NEW_REPLY_FOR_EXERCISE_POST_GROUP_TEXT;
                case NEW_REPLY_FOR_LECTURE_POST -> text = NotificationConstants.NEW_REPLY_FOR_LECTURE_POST_GROUP_TEXT;
                case NEW_REPLY_FOR_COURSE_POST -> text = NotificationConstants.NEW_REPLY_FOR_COURSE_POST_GROUP_TEXT;
            }

            return new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
        });
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
        String[] placeholderValues;
        switch (notificationType) {
            case COURSE_ARCHIVE_STARTED -> {
                title = NotificationConstants.COURSE_ARCHIVE_STARTED_TITLE;
                text = NotificationConstants.COURSE_ARCHIVE_STARTED_TEXT;
                placeholderValues = new String[] { course.getTitle() };
            }
            case COURSE_ARCHIVE_FINISHED -> {
                title = NotificationConstants.COURSE_ARCHIVE_FINISHED_TITLE;
                if (archiveErrors.isEmpty()) {
                    text = NotificationConstants.COURSE_ARCHIVE_FINISHED_WITHOUT_ERRORS_TEXT;
                    placeholderValues = new String[] { course.getTitle() };
                }
                else {
                    text = NotificationConstants.COURSE_ARCHIVE_FINISHED_WITH_ERRORS_TEXT;
                    placeholderValues = new String[] { course.getTitle(), String.join(", ", archiveErrors) };
                }
            }
            case COURSE_ARCHIVE_FAILED -> {
                title = NotificationConstants.COURSE_ARCHIVE_FAILED_TITLE;
                text = NotificationConstants.COURSE_ARCHIVE_FAILED_TEXT;
                placeholderValues = new String[] { course.getTitle(), String.join(", ", archiveErrors) };
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCourseTarget(course, NotificationTargetFactory.COURSE_ARCHIVE_UPDATED_TEXT));
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
        String[] placeholderValues;
        switch (notificationType) {
            case EXAM_ARCHIVE_STARTED -> {
                title = NotificationConstants.EXAM_ARCHIVE_STARTED_TITLE;
                text = NotificationConstants.EXAM_ARCHIVE_STARTED_TEXT;
                placeholderValues = new String[] { exam.getCourse().getTitle(), exam.getTitle() };
            }
            case EXAM_ARCHIVE_FINISHED -> {
                title = NotificationConstants.EXAM_ARCHIVE_FINISHED_TITLE;
                if (archiveErrors.isEmpty()) {
                    text = NotificationConstants.EXAM_ARCHIVE_FINISHED_WITHOUT_ERRORS_TEXT;
                    placeholderValues = new String[] { exam.getCourse().getTitle(), exam.getTitle() };
                }
                else {
                    text = NotificationConstants.EXAM_ARCHIVE_FINISHED_WITH_ERRORS_TEXT;
                    placeholderValues = new String[] { exam.getCourse().getTitle(), exam.getTitle(), String.join(", ", archiveErrors) };
                }
            }
            case EXAM_ARCHIVE_FAILED -> {
                title = NotificationConstants.EXAM_ARCHIVE_FAILED_TITLE;
                text = NotificationConstants.EXAM_ARCHIVE_FAILED_TEXT;
                placeholderValues = new String[] { exam.getCourse().getTitle(), exam.getTitle(), String.join(", ", archiveErrors) };
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(exam.getCourse(), title, text, true, placeholderValues, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCourseTarget(exam.getCourse(), "examArchiveUpdated"));
        return notification;
    }
}
