package de.tum.in.www1.artemis.domain.notification;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class NotificationTitleTypeConstants {

    public static final String LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE = "Live Exam Exercise Update";

    public static final String ATTACHMENT_CHANGE_TITLE = "Attachment updated";

    public static final String EXERCISE_CREATED_TITLE = "Exercise created";

    public static final String EXERCISE_PRACTICE_TITLE = "Exercise open for practice";

    public static final String QUIZ_EXERCISE_STARTED_TITLE = "Quiz started";

    public static final String EXERCISE_UPDATED_TITLE = "Exercise updated";

    public static final String DUPLICATE_TEST_CASE_TITLE = "Duplicate test case was found.";

    public static final String ILLEGAL_SUBMISSION_TITLE = "Illegal submission of a student.";

    public static final String NEW_POST_FOR_EXERCISE_TITLE = "New Exercise Post";

    public static final String NEW_POST_FOR_LECTURE_TITLE = "New Lecture Post";

    public static final String NEW_ANSWER_POST_FOR_EXERCISE_TITLE = "New Exercise Reply";

    public static final String NEW_ANSWER_POST_FOR_LECTURE_TITLE = "New Lecture Reply";

    public static final String COURSE_ARCHIVE_STARTED_TITLE = "Course archival started";

    public static final String COURSE_ARCHIVE_FINISHED_TITLE = "Course archival finished";

    public static final String COURSE_ARCHIVE_FAILED_TITLE = "Course archival failed";

    public static final String EXAM_ARCHIVE_STARTED_TITLE = "Exam archival started";

    public static final String EXAM_ARCHIVE_FINISHED_TITLE = "Exam archival finished";

    public static final String EXAM_ARCHIVE_FAILED_TITLE = "Exam archival failed";

    /*
     * public EnumMap<NotificationType, String> mappingBetweenNotificationTypeAndTitle = Map.of(NotificationType.ATTACHMENT_CHANGE, ATTACHMENT_CHANGE_TITLE);
     */
    /*
     * Map<NotificationType, String> map = Map.of( NotificationType.ATTACHMENT_CHANGE, ATTACHMENT_CHANGE_TITLE, NotificationType.EXERCISE_CREATED, EXERCISE_CREATED_TITLE,
     * NotificationType.EXERCISE_PRACTICE, EXERCISE_PRACTICE_TITLE, NotificationType.QUIZ_EXERCISE_STARTED, QUIZ_EXERCISE_STARTED_TITLE, NotificationType.EXERCISE_UPDATED,
     * EXERCISE_UPDATED_TITLE, NotificationType.DUPLICATE_TEST_CASE, DUPLICATE_TEST_CASE_TITLE, NotificationType.ILLEGAL_SUBMISSION, ILLEGAL_SUBMISSION_TITLE,
     * NotificationType.NEW_POST_FOR_EXERCISE, NEW_POST_FOR_EXERCISE_TITLE, NotificationType.NEW_POST_FOR_LECTURE, NEW_POST_FOR_LECTURE_TITLE,
     * NotificationType.NEW_ANSWER_POST_FOR_EXERCISE, NEW_ANSWER_POST_FOR_EXERCISE_TITLE, NotificationType.NEW_ANSWER_POST_FOR_LECTURE, NEW_ANSWER_POST_FOR_LECTURE_TITLE,
     * NotificationType.COURSE_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED_TITLE, NotificationType.COURSE_ARCHIVE_FINISHED, COURSE_ARCHIVE_FINISHED_TITLE,
     * NotificationType.COURSE_ARCHIVE_FAILED, COURSE_ARCHIVE_FAILED_TITLE, NotificationType.EXAM_ARCHIVE_STARTED, EXAM_ARCHIVE_STARTED_TITLE, NotificationType.EXAM_ARCHIVE_FAILED,
     * EXAM_ARCHIVE_FAILED_TITLE );
     */

    public static NotificationType findCorrespondingNotificationType(String title) {
        switch (title) {
            case ATTACHMENT_CHANGE_TITLE:
                return NotificationType.ATTACHMENT_CHANGE;
            case EXERCISE_CREATED_TITLE:
                return NotificationType.EXERCISE_CREATED;
            case EXERCISE_PRACTICE_TITLE:
                return NotificationType.EXERCISE_PRACTICE;
            case QUIZ_EXERCISE_STARTED_TITLE:
                return NotificationType.QUIZ_EXERCISE_STARTED;
            case EXERCISE_UPDATED_TITLE:
                return NotificationType.EXERCISE_UPDATED;
            case DUPLICATE_TEST_CASE_TITLE:
                return NotificationType.DUPLICATE_TEST_CASE;
            case ILLEGAL_SUBMISSION_TITLE:
                return NotificationType.ILLEGAL_SUBMISSION;
            case NEW_POST_FOR_EXERCISE_TITLE:
                return NotificationType.NEW_POST_FOR_EXERCISE;
            case NEW_POST_FOR_LECTURE_TITLE:
                return NotificationType.NEW_POST_FOR_LECTURE;
            case NEW_ANSWER_POST_FOR_EXERCISE_TITLE:
                return NotificationType.NEW_ANSWER_POST_FOR_EXERCISE;
            case NEW_ANSWER_POST_FOR_LECTURE_TITLE:
                return NotificationType.NEW_ANSWER_POST_FOR_LECTURE;
            case COURSE_ARCHIVE_STARTED_TITLE:
                return NotificationType.COURSE_ARCHIVE_STARTED;
            case COURSE_ARCHIVE_FINISHED_TITLE:
                return NotificationType.COURSE_ARCHIVE_FINISHED;
            case COURSE_ARCHIVE_FAILED_TITLE:
                return NotificationType.COURSE_ARCHIVE_FAILED;
            case EXAM_ARCHIVE_STARTED_TITLE:
                return NotificationType.EXAM_ARCHIVE_STARTED;
            case EXAM_ARCHIVE_FINISHED_TITLE:
                return NotificationType.EXAM_ARCHIVE_FINISHED;
            case EXAM_ARCHIVE_FAILED_TITLE:
                return NotificationType.EXAM_ARCHIVE_FAILED;
            default:
                // is needed for other strings (e.g. system notifications) & for Exam Notifications
                return null;
        }
    }

}
