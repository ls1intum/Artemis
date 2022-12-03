package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;

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
        if (notificationType == ATTACHMENT_CHANGE) {
            title = ATTACHMENT_CHANGE_TITLE;
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
        NotificationPriority priority = MEDIUM;

        switch (notificationType) {
            case EXERCISE_RELEASED -> {
                title = EXERCISE_RELEASED_TITLE;
                text = "A new exercise \"" + exercise.getTitle() + "\" got released.";
            }
            case EXERCISE_PRACTICE -> {
                title = EXERCISE_PRACTICE_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" is now open for practice.";
            }
            case QUIZ_EXERCISE_STARTED -> {
                title = QUIZ_EXERCISE_STARTED_TITLE;
                text = "Quiz \"" + exercise.getTitle() + "\" just started.";
            }
            case EXERCISE_UPDATED -> {
                if (exercise.isExamExercise()) {
                    title = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
                    text = "Exam Exercise \"" + exercise.getTitle() + "\" updated.";
                }
                else {
                    title = EXERCISE_UPDATED_TITLE;
                    text = "Exercise \"" + exercise.getTitle() + "\" updated.";
                }
            }
            case PROGRAMMING_TEST_CASES_CHANGED -> {
                title = PROGRAMMING_TEST_CASES_CHANGED_TITLE;
                text = "The test cases of the programming exercise \"" + exercise.getTitle() + "\" in the course \"" + exercise.getCourseViaExerciseGroupOrCourseMember().getTitle()
                        + "\" were updated." + " The students' submissions should be rebuilt and tested in order to create new results.";
            }
            case NEW_MANUAL_FEEDBACK_REQUEST -> {
                title = NEW_MANUAL_FEEDBACK_REQUEST_TITLE;
                text = "The programming exercise \"" + exercise.getTitle() + "\" in the course \"" + exercise.getCourseViaExerciseGroupOrCourseMember().getTitle()
                        + "\" has a new manual feedback request." + " Please assess the feedback before the deadline.";
            }
            case DUPLICATE_TEST_CASE -> {
                title = DUPLICATE_TEST_CASE_TITLE;
                text = notificationText;
                priority = HIGH;
            }
            case ILLEGAL_SUBMISSION -> {
                title = ILLEGAL_SUBMISSION_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" has illegal submissions of students.";
                priority = HIGH;
            }

            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        // Catches 3 different use cases : notificationText exists for 1) non-exam exercise update & 2) live exam exercise update with a notification text
        // 3) hidden/silent live exam exercise update without a set notificationText, thus no pop-up will be visible for the students
        if (notificationText != null || LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(title)) {
            text = notificationText;
        }

        GroupNotification notification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), title, text, author, groupNotificationType, priority);

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
                title = NEW_EXERCISE_POST_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" got a new post.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTransientAndStringTarget(createExercisePostTarget(post, course));
            }
            case NEW_LECTURE_POST -> {
                Lecture lecture = post.getLecture();
                title = NEW_LECTURE_POST_TITLE;
                text = "Lecture \"" + lecture.getTitle() + "\" got a new post.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTransientAndStringTarget(createLecturePostTarget(post, course));
            }
            case NEW_COURSE_POST -> {
                title = NEW_COURSE_POST_TITLE;
                text = "Course \"" + course.getTitle() + "\" got a new course-wide post.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
            }
            case NEW_ANNOUNCEMENT_POST -> {
                title = NEW_ANNOUNCEMENT_POST_TITLE;
                text = "Course \"" + course.getTitle() + "\" got a new announcement.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
            }
            case NEW_REPLY_FOR_EXERCISE_POST -> {
                Exercise exercise = post.getExercise();
                title = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
                text = "Exercise \"" + exercise.getTitle() + "\" got a new reply.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTransientAndStringTarget(createExercisePostTarget(post, course));
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                Lecture lecture = post.getLecture();
                title = NEW_REPLY_FOR_LECTURE_POST_TITLE;
                text = "Lecture \"" + lecture.getTitle() + "\" got a new reply.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
                notification.setTransientAndStringTarget(createLecturePostTarget(post, course));
            }
            case NEW_REPLY_FOR_COURSE_POST -> {
                title = NEW_REPLY_FOR_COURSE_POST_TITLE;
                text = "Course-wide post in course \"" + course.getTitle() + "\" got a new reply.";
                notification = new GroupNotification(course, title, text, author, groupNotificationType);
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
        switch (notificationType) {
            case COURSE_ARCHIVE_STARTED -> {
                title = COURSE_ARCHIVE_STARTED_TITLE;
                text = "The course \"" + course.getTitle() + "\" is being archived.";
            }
            case COURSE_ARCHIVE_FINISHED -> {
                title = COURSE_ARCHIVE_FINISHED_TITLE;
                text = "The course \"" + course.getTitle() + "\" has been archived.";

                if (!archiveErrors.isEmpty()) {
                    text += " Some exercises couldn't be included in the archive:<br/><br/>" + String.join("<br/><br/>", archiveErrors);
                }
            }
            case COURSE_ARCHIVE_FAILED -> {
                title = COURSE_ARCHIVE_FAILED_TITLE;
                text = "The was a problem archiving course \"" + course.getTitle() + "\": <br/><br/>" + String.join("<br/><br/>", archiveErrors);
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, author, groupNotificationType);
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
        switch (notificationType) {
            case EXAM_ARCHIVE_STARTED -> {
                title = EXAM_ARCHIVE_STARTED_TITLE;
                text = "The exam \"" + exam.getTitle() + "\" is being archived.";
            }
            case EXAM_ARCHIVE_FINISHED -> {
                title = EXAM_ARCHIVE_FINISHED_TITLE;
                text = "The exam \"" + exam.getTitle() + "\" has been archived.";

                if (!archiveErrors.isEmpty()) {
                    text += " Some exercises couldn't be included in the archive:<br/><br/>" + String.join("<br/><br/>", archiveErrors);
                }
            }
            case EXAM_ARCHIVE_FAILED -> {
                title = EXAM_ARCHIVE_FAILED_TITLE;
                text = "The was a problem archiving exam \"" + exam.getTitle() + "\": <br/><br/>" + String.join("<br/><br/>", archiveErrors);
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(exam.getCourse(), title, text, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCourseTarget(exam.getCourse(), "examArchiveUpdated"));
        return notification;
    }
}
