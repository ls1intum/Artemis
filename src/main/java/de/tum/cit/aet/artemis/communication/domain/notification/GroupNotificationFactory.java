package de.tum.cit.aet.artemis.communication.domain.notification;

import static de.tum.cit.aet.artemis.communication.domain.NotificationPriority.HIGH;
import static de.tum.cit.aet.artemis.communication.domain.NotificationPriority.MEDIUM;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.ATTACHMENT_CHANGE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.COURSE_ARCHIVE_FAILED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.COURSE_ARCHIVE_FINISHED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.COURSE_ARCHIVE_STARTED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.DUPLICATE_TEST_CASE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXAM_ARCHIVE_FAILED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXAM_ARCHIVE_FINISHED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXAM_ARCHIVE_STARTED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_PRACTICE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_RELEASED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_UPDATED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.ILLEGAL_SUBMISSION;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_ANNOUNCEMENT_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_MANUAL_FEEDBACK_REQUEST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PROGRAMMING_BUILD_RUN_UPDATE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PROGRAMMING_REPOSITORY_LOCKS;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PROGRAMMING_TEST_CASES_CHANGED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.QUIZ_EXERCISE_STARTED;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createAttachmentUpdatedTarget;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createCoursePostTarget;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createCourseTarget;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createDuplicateTestCaseTarget;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createExamExerciseTargetWithExerciseUpdate;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createExamProgrammingExerciseOrTestCaseTarget;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createExerciseReleasedTarget;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationTargetFactory.createExerciseUpdatedTarget;

import java.util.List;

import org.jsoup.Jsoup;

import de.tum.cit.aet.artemis.communication.domain.GroupNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationPriority;
import de.tum.cit.aet.artemis.communication.domain.NotificationType;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

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

        Lecture lecture;
        // we get the lecture either from the directly connected lecture or from the attachment unit
        if (attachment.getAttachmentUnit() != null) {
            lecture = attachment.getAttachmentUnit().getLecture();
        }
        else {
            lecture = attachment.getLecture();
        }

        if (notificationType == ATTACHMENT_CHANGE) {
            title = NotificationConstants.ATTACHMENT_CHANGE_TITLE;
            text = NotificationConstants.ATTACHMENT_CHANGE_TEXT;
            textIsPlaceholder = true;

            String courseTitle = attachment.getExercise() != null ? attachment.getExercise().getCourseViaExerciseGroupOrCourseMember().getTitle() : lecture.getCourse().getTitle();
            String entityTitle = attachment.getExercise() != null ? attachment.getExercise().getTitle() : lecture.getTitle();

            placeholderValues = createPlaceholdersAttachmentChange(courseTitle, attachment.getName(), entityTitle);
        }
        else {
            throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        if (notificationText != null) {
            text = notificationText;
            textIsPlaceholder = false;
            placeholderValues = null;
        }
        Course course = lecture.getCourse();
        GroupNotification notification = new GroupNotification(course, title, text, textIsPlaceholder, placeholderValues, author, groupNotificationType);

        notification.setTransientAndStringTarget(createAttachmentUpdatedTarget(lecture));

        return notification;
    }

    @NotificationPlaceholderCreator(values = { ATTACHMENT_CHANGE })
    public static String[] createPlaceholdersAttachmentChange(String courseTitle, String attachmentName, String exerciseOrLectureName) {
        return new String[] { courseTitle, attachmentName, exerciseOrLectureName };
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
        String[] placeholderValues = createPlaceholderExerciseNotification(exercise.getCourseViaExerciseGroupOrCourseMember().getTitle(), exercise.getTitle());
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
            placeholderValues = createPlaceholderExerciseNotification(exercise.getCourseViaExerciseGroupOrCourseMember().getTitle(), "");
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

    @NotificationPlaceholderCreator(values = { EXERCISE_RELEASED, EXERCISE_PRACTICE, QUIZ_EXERCISE_STARTED, EXERCISE_UPDATED, PROGRAMMING_TEST_CASES_CHANGED,
            NEW_MANUAL_FEEDBACK_REQUEST, DUPLICATE_TEST_CASE, PROGRAMMING_REPOSITORY_LOCKS, PROGRAMMING_BUILD_RUN_UPDATE, ILLEGAL_SUBMISSION })
    public static String[] createPlaceholderExerciseNotification(String courseTitle, String exerciseTitle) {
        return new String[] { courseTitle, exerciseTitle };
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
        placeholderValues = createPlaceholdersNewAnnouncementPost(course.getTitle(), post.getTitle(), Jsoup.parse(post.getContent()).text(), post.getCreationDate().toString(),
                post.getAuthor().getName());
        notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
        return notification;
    }

    @NotificationPlaceholderCreator(values = { NEW_ANNOUNCEMENT_POST })
    public static String[] createPlaceholdersNewAnnouncementPost(String courseTitle, String postTitle, String postContent, String postCreationDate, String postAuthorName) {
        return new String[] { courseTitle, postTitle, postContent, postCreationDate, postAuthorName };
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
                placeholderValues = createPlaceholdersCourseArchiveStarted(course.getTitle());
            }
            case COURSE_ARCHIVE_FINISHED -> {
                title = NotificationConstants.COURSE_ARCHIVE_FINISHED_TITLE;
                if (archiveErrors.isEmpty()) {
                    text = NotificationConstants.COURSE_ARCHIVE_FINISHED_WITHOUT_ERRORS_TEXT;
                    placeholderValues = createPlaceholdersCourseArchiveFinishedOrFailed(course.getTitle(), "");
                }
                else {
                    text = NotificationConstants.COURSE_ARCHIVE_FINISHED_WITH_ERRORS_TEXT;
                    placeholderValues = createPlaceholdersCourseArchiveFinishedOrFailed(course.getTitle(), String.join(", ", archiveErrors));
                }
            }
            case COURSE_ARCHIVE_FAILED -> {
                title = NotificationConstants.COURSE_ARCHIVE_FAILED_TITLE;
                text = NotificationConstants.COURSE_ARCHIVE_FAILED_TEXT;
                placeholderValues = createPlaceholdersCourseArchiveFinishedOrFailed(course.getTitle(), String.join(", ", archiveErrors));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(course, title, text, true, placeholderValues, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCourseTarget(course, NotificationTargetFactory.COURSE_ARCHIVE_UPDATED_TEXT));
        return notification;
    }

    @NotificationPlaceholderCreator(values = { COURSE_ARCHIVE_STARTED })
    public static String[] createPlaceholdersCourseArchiveStarted(String courseTitle) {
        return new String[] { courseTitle };
    }

    @NotificationPlaceholderCreator(values = { COURSE_ARCHIVE_FINISHED, COURSE_ARCHIVE_FAILED })
    public static String[] createPlaceholdersCourseArchiveFinishedOrFailed(String courseTitle, String archiveErrors) {
        return new String[] { courseTitle, archiveErrors };
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
                placeholderValues = createPlaceholdersExamArchiveStarted(exam.getCourse().getTitle(), exam.getTitle());
            }
            case EXAM_ARCHIVE_FINISHED -> {
                title = NotificationConstants.EXAM_ARCHIVE_FINISHED_TITLE;
                if (archiveErrors.isEmpty()) {
                    text = NotificationConstants.EXAM_ARCHIVE_FINISHED_WITHOUT_ERRORS_TEXT;
                    placeholderValues = createPlaceholdersExamArchiveFinishedOrFailed(exam.getCourse().getTitle(), exam.getTitle(), "");
                }
                else {
                    text = NotificationConstants.EXAM_ARCHIVE_FINISHED_WITH_ERRORS_TEXT;
                    placeholderValues = createPlaceholdersExamArchiveFinishedOrFailed(exam.getCourse().getTitle(), exam.getTitle(), String.join(", ", archiveErrors));
                }
            }
            case EXAM_ARCHIVE_FAILED -> {
                title = NotificationConstants.EXAM_ARCHIVE_FAILED_TITLE;
                text = NotificationConstants.EXAM_ARCHIVE_FAILED_TEXT;
                placeholderValues = createPlaceholdersExamArchiveFinishedOrFailed(exam.getCourse().getTitle(), exam.getTitle(), String.join(", ", archiveErrors));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        GroupNotification notification = new GroupNotification(exam.getCourse(), title, text, true, placeholderValues, author, groupNotificationType);
        notification.setTransientAndStringTarget(createCourseTarget(exam.getCourse(), "examArchiveUpdated"));
        return notification;
    }

    @NotificationPlaceholderCreator(values = { EXAM_ARCHIVE_STARTED })
    public static String[] createPlaceholdersExamArchiveStarted(String courseTitle, String examTitle) {
        return new String[] { courseTitle, examTitle };
    }

    @NotificationPlaceholderCreator(values = { EXAM_ARCHIVE_FINISHED, EXAM_ARCHIVE_FAILED })
    public static String[] createPlaceholdersExamArchiveFinishedOrFailed(String courseTitle, String examTitle, String archiveErrors) {
        return new String[] { courseTitle, examTitle, archiveErrors };
    }
}
