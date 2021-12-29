package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class NotificationTitleTypeConstants {

    public static final String LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE = "Live Exam Exercise Update";

    public static final String ATTACHMENT_CHANGE_TITLE = "Attachment updated";

    public static final String EXERCISE_RELEASED_TITLE = "Exercise released";

    public static final String EXERCISE_PRACTICE_TITLE = "Exercise open for practice";

    public static final String QUIZ_EXERCISE_STARTED_TITLE = "Quiz started";

    public static final String EXERCISE_UPDATED_TITLE = "Exercise updated";

    public static final String DUPLICATE_TEST_CASE_TITLE = "Duplicate test case was found.";

    public static final String ILLEGAL_SUBMISSION_TITLE = "Illegal submission of a student.";

    public static final String NEW_EXERCISE_POST_TITLE = "New exercise post";

    public static final String NEW_LECTURE_POST_TITLE = "New lecture post";

    public static final String NEW_COURSE_POST_TITLE = "New course-wide post";

    public static final String NEW_ANNOUNCEMENT_POST_TITLE = "New announcement";

    public static final String NEW_REPLY_FOR_EXERCISE_POST_TITLE = "New reply for exercise post";

    public static final String NEW_REPLY_FOR_LECTURE_POST_TITLE = "New reply for lecture post";

    public static final String NEW_REPLY_FOR_COURSE_POST_TITLE = "New reply for course-wide post";

    public static final String FILE_SUBMISSION_SUCCESSFUL_TITLE = "File submission successful";

    public static final String COURSE_ARCHIVE_STARTED_TITLE = "Course archival started";

    public static final String COURSE_ARCHIVE_FINISHED_TITLE = "Course archival finished";

    public static final String COURSE_ARCHIVE_FAILED_TITLE = "Course archival failed";

    public static final String EXAM_ARCHIVE_STARTED_TITLE = "Exam archival started";

    public static final String EXAM_ARCHIVE_FINISHED_TITLE = "Exam archival finished";

    public static final String EXAM_ARCHIVE_FAILED_TITLE = "Exam archival failed";

    public static final String PROGRAMMING_TEST_CASES_CHANGED_TITLE = "Test cases for programming exercise changed";

    public static final String NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT_TITLE = "New possible plagiarism case";

    public static final String PLAGIARISM_CASE_FINAL_STATE_STUDENT_TITLE = "Final state for plagiarism case";

    // bidirectional map
    private static final BiMap<NotificationType, String> NOTIFICATION_TYPE_AND_TITLE_MAP = new ImmutableBiMap.Builder<NotificationType, String>()
            .put(ATTACHMENT_CHANGE, ATTACHMENT_CHANGE_TITLE).put(EXERCISE_RELEASED, EXERCISE_RELEASED_TITLE).put(EXERCISE_PRACTICE, EXERCISE_PRACTICE_TITLE)
            .put(QUIZ_EXERCISE_STARTED, QUIZ_EXERCISE_STARTED_TITLE).put(EXERCISE_UPDATED, EXERCISE_UPDATED_TITLE).put(DUPLICATE_TEST_CASE, DUPLICATE_TEST_CASE_TITLE)
            .put(ILLEGAL_SUBMISSION, ILLEGAL_SUBMISSION_TITLE).put(NEW_EXERCISE_POST, NEW_EXERCISE_POST_TITLE).put(NEW_LECTURE_POST, NEW_LECTURE_POST_TITLE)
            .put(NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_EXERCISE_POST_TITLE).put(NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_LECTURE_POST_TITLE)
            .put(NEW_COURSE_POST, NEW_COURSE_POST_TITLE).put(NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_COURSE_POST_TITLE)
            .put(FILE_SUBMISSION_SUCCESSFUL, FILE_SUBMISSION_SUCCESSFUL_TITLE).put(COURSE_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED_TITLE)
            .put(NEW_ANNOUNCEMENT_POST, NEW_ANNOUNCEMENT_POST_TITLE).put(COURSE_ARCHIVE_FINISHED, COURSE_ARCHIVE_FINISHED_TITLE)
            .put(COURSE_ARCHIVE_FAILED, COURSE_ARCHIVE_FAILED_TITLE).put(EXAM_ARCHIVE_STARTED, EXAM_ARCHIVE_STARTED_TITLE).put(EXAM_ARCHIVE_FAILED, EXAM_ARCHIVE_FAILED_TITLE)
            .put(PROGRAMMING_TEST_CASES_CHANGED, PROGRAMMING_TEST_CASES_CHANGED_TITLE).put(NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT, NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT_TITLE)
            .put(PLAGIARISM_CASE_FINAL_STATE_STUDENT, PLAGIARISM_CASE_FINAL_STATE_STUDENT_TITLE).build();

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
