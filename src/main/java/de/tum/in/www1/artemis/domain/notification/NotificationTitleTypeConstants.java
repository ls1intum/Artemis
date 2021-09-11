package de.tum.in.www1.artemis.domain.notification;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

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

    // bi directional map
    private static final BiMap<NotificationType, String> NOTIFICATION_TYPE_AND_TITLE_MAP = new ImmutableBiMap.Builder<NotificationType, String>()
            .put(NotificationType.ATTACHMENT_CHANGE, ATTACHMENT_CHANGE_TITLE).put(NotificationType.EXERCISE_CREATED, EXERCISE_CREATED_TITLE)
            .put(NotificationType.EXERCISE_PRACTICE, EXERCISE_PRACTICE_TITLE).put(NotificationType.QUIZ_EXERCISE_STARTED, QUIZ_EXERCISE_STARTED_TITLE)
            .put(NotificationType.EXERCISE_UPDATED, EXERCISE_UPDATED_TITLE).put(NotificationType.DUPLICATE_TEST_CASE, DUPLICATE_TEST_CASE_TITLE)
            .put(NotificationType.ILLEGAL_SUBMISSION, ILLEGAL_SUBMISSION_TITLE).put(NotificationType.NEW_POST_FOR_EXERCISE, NEW_POST_FOR_EXERCISE_TITLE)
            .put(NotificationType.NEW_POST_FOR_LECTURE, NEW_POST_FOR_LECTURE_TITLE).put(NotificationType.NEW_ANSWER_POST_FOR_EXERCISE, NEW_ANSWER_POST_FOR_EXERCISE_TITLE)
            .put(NotificationType.NEW_ANSWER_POST_FOR_LECTURE, NEW_ANSWER_POST_FOR_LECTURE_TITLE).put(NotificationType.COURSE_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED_TITLE)
            .put(NotificationType.COURSE_ARCHIVE_FINISHED, COURSE_ARCHIVE_FINISHED_TITLE).put(NotificationType.COURSE_ARCHIVE_FAILED, COURSE_ARCHIVE_FAILED_TITLE)
            .put(NotificationType.EXAM_ARCHIVE_STARTED, EXAM_ARCHIVE_STARTED_TITLE).put(NotificationType.EXAM_ARCHIVE_FAILED, EXAM_ARCHIVE_FAILED_TITLE).build();

    /**
     * Finds the corresponding NotificationType for the provided notification title
     * @param title based on NotificationTitleTypeConstants
     * @return corresponding NotificationType
     */
    public static NotificationType findCorrespondingNotificationType(String title) {
        return NOTIFICATION_TYPE_AND_TITLE_MAP.inverse().get(title);
    }

    /**
     * Finds the corresponding notification title for the provided NotificationType
     * @param type from NotificationType
     * @return corresponding notification title
     */
    public static String findCorrespondingNotificationTitle(NotificationType type) {
        return NOTIFICATION_TYPE_AND_TITLE_MAP.get(type);
    }
}
