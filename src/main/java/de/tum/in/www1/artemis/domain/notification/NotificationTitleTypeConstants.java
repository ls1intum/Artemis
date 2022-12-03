package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class NotificationTitleTypeConstants {

    public static final String LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE = "Live Exam Exercise Update";

    public static final String EXERCISE_SUBMISSION_ASSESSED_TITLE = "Exercise Submission Assessed";

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

    public static final String NEW_MANUAL_FEEDBACK_REQUEST_TITLE = "A new manual feedback request has been submitted";

    public static final String NEW_PLAGIARISM_CASE_STUDENT_TITLE = "New plagiarism case";

    public static final String PLAGIARISM_CASE_VERDICT_STUDENT_TITLE = "Verdict for your plagiarism case";

    public static final String TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE = "You have been registered to a tutorial group";

    public static final String TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE = "You have been deregistered from a tutorial group";

    public static final String TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE = "A student has been registered to your tutorial group";

    public static final String TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE = "A student has been deregistered from your tutorial group";

    public static final String TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE = "Multiple students have been registered to your tutorial group";

    public static final String TUTORIAL_GROUP_DELETED_TITLE = "Tutorial Group deleted";

    public static final String TUTORIAL_GROUP_UPDATED_TITLE = "Tutorial Group updated";

    public static final String TUTORIAL_GROUP_ASSIGNED_TITLE = "You have been assigned to lead a tutorial group";

    public static final String TUTORIAL_GROUP_UNASSIGNED_TITLE = "You have been unassigned from leading a tutorial group";

    // bidirectional map
    private static final BiMap<NotificationType, String> NOTIFICATION_TYPE_AND_TITLE_MAP = new ImmutableBiMap.Builder<NotificationType, String>()
            .put(EXERCISE_SUBMISSION_ASSESSED, EXERCISE_SUBMISSION_ASSESSED_TITLE).put(ATTACHMENT_CHANGE, ATTACHMENT_CHANGE_TITLE).put(EXERCISE_RELEASED, EXERCISE_RELEASED_TITLE)
            .put(EXERCISE_PRACTICE, EXERCISE_PRACTICE_TITLE).put(QUIZ_EXERCISE_STARTED, QUIZ_EXERCISE_STARTED_TITLE).put(EXERCISE_UPDATED, EXERCISE_UPDATED_TITLE)
            .put(DUPLICATE_TEST_CASE, DUPLICATE_TEST_CASE_TITLE).put(ILLEGAL_SUBMISSION, ILLEGAL_SUBMISSION_TITLE).put(NEW_EXERCISE_POST, NEW_EXERCISE_POST_TITLE)
            .put(NEW_LECTURE_POST, NEW_LECTURE_POST_TITLE).put(NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_EXERCISE_POST_TITLE)
            .put(NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_LECTURE_POST_TITLE).put(NEW_COURSE_POST, NEW_COURSE_POST_TITLE)
            .put(NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_COURSE_POST_TITLE).put(FILE_SUBMISSION_SUCCESSFUL, FILE_SUBMISSION_SUCCESSFUL_TITLE)
            .put(COURSE_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED_TITLE).put(NEW_ANNOUNCEMENT_POST, NEW_ANNOUNCEMENT_POST_TITLE)
            .put(COURSE_ARCHIVE_FINISHED, COURSE_ARCHIVE_FINISHED_TITLE).put(COURSE_ARCHIVE_FAILED, COURSE_ARCHIVE_FAILED_TITLE)
            .put(EXAM_ARCHIVE_STARTED, EXAM_ARCHIVE_STARTED_TITLE).put(EXAM_ARCHIVE_FAILED, EXAM_ARCHIVE_FAILED_TITLE).put(EXAM_ARCHIVE_FINISHED, EXAM_ARCHIVE_FINISHED_TITLE)
            .put(PROGRAMMING_TEST_CASES_CHANGED, PROGRAMMING_TEST_CASES_CHANGED_TITLE).put(NEW_MANUAL_FEEDBACK_REQUEST, NEW_MANUAL_FEEDBACK_REQUEST_TITLE)
            .put(NEW_PLAGIARISM_CASE_STUDENT, NEW_PLAGIARISM_CASE_STUDENT_TITLE).put(PLAGIARISM_CASE_VERDICT_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT_TITLE)
            .put(TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE)
            .put(TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE).put(TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE)
            .put(TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE).put(TUTORIAL_GROUP_DELETED, TUTORIAL_GROUP_DELETED_TITLE)
            .put(TUTORIAL_GROUP_UPDATED, TUTORIAL_GROUP_UPDATED_TITLE).put(TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE)
            .put(TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_ASSIGNED_TITLE).put(TUTORIAL_GROUP_UNASSIGNED, TUTORIAL_GROUP_UNASSIGNED_TITLE).build();

    /**
     * Finds the corresponding NotificationType for the provided notification title
     *
     * @param title based on NotificationTitleTypeConstants
     * @return corresponding NotificationType
     */
    public static NotificationType findCorrespondingNotificationType(String title) {
        return NOTIFICATION_TYPE_AND_TITLE_MAP.inverse().get(title);
    }

    /**
     * Finds the corresponding notification title for the provided NotificationType
     *
     * @param type from NotificationType
     * @return corresponding notification title
     */
    public static String findCorrespondingNotificationTitle(NotificationType type) {
        return NOTIFICATION_TYPE_AND_TITLE_MAP.get(type);
    }

    /**
     * Finds the corresponding notification title for the provided NotificationType or throws an exception if the type is not found
     *
     * @param type from NotificationType
     * @return corresponding notification title
     */
    public static String findCorrespondingNotificationTitleOrThrow(NotificationType type) {
        var result = findCorrespondingNotificationTitle(type);
        if (result == null) {
            throw new UnsupportedOperationException("No matching title found for: " + type);
        }
        return result;
    }

}
