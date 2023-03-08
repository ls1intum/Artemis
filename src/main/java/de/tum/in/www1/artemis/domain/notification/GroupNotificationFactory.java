package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.EXERCISE_UPDATED_TEXT;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;

import java.util.List;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
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
            title = ATTACHMENT_CHANGE_TITLE;
            text = ATTACHMENT_CHANGE_TEXT;
            textIsPlaceholder = true;
            placeholderValues = new String[] { attachment.getName() };
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
        String[] placeholderValues;
        NotificationPriority priority = MEDIUM;

        switch (notificationType) {
            case EXERCISE_RELEASED -> {
                title = EXERCISE_RELEASED_TITLE;
                text = NotificationConstants.EXERCISE_RELEASED_TEXT;
                textIsPlaceholder = true;
                placeholderValues = new String[] { exercise.getTitle() };
            }
            case EXERCISE_PRACTICE -> {
                title = EXERCISE_PRACTICE_TITLE;
                text = EXERCISE_PRACTICE_TEXT;
                textIsPlaceholder = true;
                placeholderValues = new String[] { exercise.getTitle() };
            }
            case QUIZ_EXERCISE_STARTED -> {
                title = QUIZ_EXERCISE_STARTED_TITLE;
                text = QUIZ_EXERCISE_STARTED_TEXT;
                textIsPlaceholder = true;
                placeholderValues = new String[] { exercise.getTitle() };
            }
            case EXERCISE_UPDATED -> {
                if (exercise.isExamExercise()) {
                    title = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
                    text = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TEXT;
                    textIsPlaceholder = true;
                    placeholderValues = new String[] { exercise.getTitle() };
                }
                else {
                    title = EXERCISE_UPDATED_TITLE;
                    text = NotificationConstants.EXERCISE_UPDATED_TEXT;
                    textIsPlaceholder = true;
                    placeholderValues = new String[] { exercise.getTitle() };
                }
            }
            case PROGRAMMING_TEST_CASES_CHANGED -> {
                title = PROGRAMMING_TEST_CASES_CHANGED_TITLE;
                text = PROGRAMMING_TEST_CASES_CHANGED_TEXT;
                textIsPlaceholder = true;
                placeholderValues = new String[] { exercise.getTitle(), exercise.getCourseViaExerciseGroupOrCourseMember().getTitle() };
            }
            case NEW_MANUAL_FEEDBACK_REQUEST -> {
                title = NEW_MANUAL_FEEDBACK_REQUEST_TITLE;
                text = NEW_MANUAL_FEEDBACK_REQUEST_TEXT;
                textIsPlaceholder = true;
                placeholderValues = new String[] { exercise.getTitle(), exercise.getCourseViaExerciseGroupOrCourseMember().getTitle() };
            }
            case DUPLICATE_TEST_CASE -> {
                title = DUPLICATE_TEST_CASE_TITLE;
                text = notificationText;
                textIsPlaceholder = false;
                placeholderValues = null;
                priority = HIGH;
            }
            case ILLEGAL_SUBMISSION -> {
                title = ILLEGAL_SUBMISSION_TITLE;
                text = ILLEGAL_SUBMISSION_TEXT;
                textIsPlaceholder = true;
                placeholderValues = new String[] { exercise.getTitle() };
                priority = HIGH;
            }

            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        // Catches 3 different use cases : notificationText exists for 1) non-exam exercise update & 2) live exam exercise update with a notification text
        // 3) hidden/silent live exam exercise update without a set notificationText, thus no pop-up will be visible for the students
        if (notificationText != null || LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(title)) {
            text = notificationText;
            textIsPlaceholder = false;
            placeholderValues = null;
        }

        GroupNotification notification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), title, text, textIsPlaceholder, placeholderValues, author,
                groupNotificationType, priority);

        // Exercises for exams
        if (exercise.isExamExercise()) {
            if (LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(title)) {
                notification.setTransientAndStringTarget(createExamExerciseTargetWithExerciseUpdate(exercise));
                notification.setPriority(HIGH);
            }
            else if (exercise instanceof ProgrammingExercise) {
                notification.setTransientAndStringTarget(createExamProgrammingExerciseOrTestCaseTarget((ProgrammingExercise) exercise, EXERCISE_UPDATED_TEXT));
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
     * @param notificationType      type of the notification that should be created
     * @param course                the post belongs to
     * @return an instance of GroupNotification
     */
    public static GroupNotification createNotification(Post post, User author, GroupNotificationType groupNotificationType, NotificationType notificationType, Course course) {
        String title;
        String text;
        String[] placeholderValues;
        GroupNotification notification;
        switch (notificationType) {
            case NEW_EXERCISE_POST -> {
                Exercise exercise = post.getExercise();
                title = NEW_EXERCISE_POST_TITLE;
                text = NEW_EXERCISE_POST_TEXT;
                placeholderValues = new String[] { exercise.getTitle() };
                notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
                notification.setTransientAndStringTarget(createExercisePostTarget(post, course));
            }
            case NEW_LECTURE_POST -> {
                Lecture lecture = post.getLecture();
                title = NEW_LECTURE_POST_TITLE;
                text = NEW_LECTURE_POST_TEXT;
                placeholderValues = new String[] { lecture.getTitle() };
                notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
                notification.setTransientAndStringTarget(createLecturePostTarget(post, course));
            }
            case NEW_COURSE_POST -> {
                title = NEW_COURSE_POST_TITLE;
                text = NEW_COURSE_POST_TEXT;
                placeholderValues = new String[] { course.getTitle() };
                notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
                notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
            }
            case NEW_ANNOUNCEMENT_POST -> {
                title = NEW_ANNOUNCEMENT_POST_TITLE;
                text = NEW_ANNOUNCEMENT_POST_TEXT;
                placeholderValues = new String[] { course.getTitle() };
                notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
                notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
            }
            case NEW_REPLY_FOR_EXERCISE_POST -> {
                Exercise exercise = post.getExercise();
                title = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
                text = NEW_REPLY_FOR_EXERCISE_POST_TEXT;
                placeholderValues = new String[] { exercise.getTitle() };
                notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
                notification.setTransientAndStringTarget(createExercisePostTarget(post, course));
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                Lecture lecture = post.getLecture();
                title = NEW_REPLY_FOR_LECTURE_POST_TITLE;
                text = NEW_REPLY_FOR_LECTURE_POST_TEXT;
                placeholderValues = new String[] { lecture.getTitle() };
                notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
                notification.setTransientAndStringTarget(createLecturePostTarget(post, course));
            }
            case NEW_REPLY_FOR_COURSE_POST -> {
                title = NEW_REPLY_FOR_COURSE_POST_TITLE;
                text = NEW_REPLY_FOR_COURSE_POST_TEXT;
                placeholderValues = new String[] { course.getTitle() };
                notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
                notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
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
        String[] placeholderValues;
        switch (notificationType) {
            case COURSE_ARCHIVE_STARTED -> {
                title = COURSE_ARCHIVE_STARTED_TITLE;
                text = COURSE_ARCHIVE_STARTED_TEXT;
                placeholderValues = new String[] { course.getTitle() };
            }
            case COURSE_ARCHIVE_FINISHED -> {
                title = COURSE_ARCHIVE_FINISHED_TITLE;
                if (archiveErrors.isEmpty()) {
                    text = COURSE_ARCHIVE_FINISHED_WITHOUT_ERRORS_TEXT;
                    placeholderValues = new String[] { course.getTitle() };
                }
                else {
                    text = COURSE_ARCHIVE_FINISHED_WITH_ERRORS_TEXT;
                    placeholderValues = new String[] { course.getTitle(), String.join("<br/><br/>", archiveErrors) };
                }
            }
            case COURSE_ARCHIVE_FAILED -> {
                title = COURSE_ARCHIVE_FAILED_TITLE;
                text = COURSE_ARCHIVE_FAILED_TEXT;
                placeholderValues = new String[] { course.getTitle(), String.join("<br/><br/>", archiveErrors) };
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCourseTarget(course, COURSE_ARCHIVE_UPDATED_TEXT));
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
                title = EXAM_ARCHIVE_STARTED_TITLE;
                text = EXAM_ARCHIVE_STARTED_TEXT;
                placeholderValues = new String[] { exam.getTitle() };
            }
            case EXAM_ARCHIVE_FINISHED -> {
                title = EXAM_ARCHIVE_FINISHED_TITLE;
                if (archiveErrors.isEmpty()) {
                    text = EXAM_ARCHIVE_FINISHED_WITHOUT_ERRORS_TEXT;
                    placeholderValues = new String[] { exam.getTitle() };
                }
                else {
                    text = EXAM_ARCHIVE_FINISHED_WITH_ERRORS_TEXT;
                    placeholderValues = new String[] { exam.getTitle(), String.join("<br/><br/>", archiveErrors) };
                }
            }
            case EXAM_ARCHIVE_FAILED -> {
                title = EXAM_ARCHIVE_FAILED_TITLE;
                text = EXAM_ARCHIVE_FAILED_TEXT;
                placeholderValues = new String[] { exam.getTitle(), String.join("<br/><br/>", archiveErrors) };
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(exam.getCourse(), title, text, true, placeholderValues, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCourseTarget(exam.getCourse(), "examArchiveUpdated"));
        return notification;
    }
}
