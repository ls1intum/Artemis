package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class NotificationConstants {

    // Titles
    public static final String LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE = "artemisApp.groupNotification.title.liveExamExerciseUpdate";

    public static final String EXERCISE_SUBMISSION_ASSESSED_TITLE = "artemisApp.singleUserNotification.title.exerciseSubmissionAssessed";

    public static final String ATTACHMENT_CHANGE_TITLE = "artemisApp.groupNotification.title.attachmentChange";

    public static final String EXERCISE_RELEASED_TITLE = "artemisApp.groupNotification.title.exerciseReleased";

    public static final String EXERCISE_PRACTICE_TITLE = "artemisApp.groupNotification.title.exercisePractice";

    public static final String QUIZ_EXERCISE_STARTED_TITLE = "artemisApp.groupNotification.title.quizExerciseStarted";

    public static final String EXERCISE_UPDATED_TITLE = "artemisApp.groupNotification.title.exerciseUpdated";

    public static final String DUPLICATE_TEST_CASE_TITLE = "artemisApp.groupNotification.title.duplicateTestCase";

    public static final String ILLEGAL_SUBMISSION_TITLE = "artemisApp.groupNotification.title.illegalSubmission";

    public static final String NEW_EXERCISE_POST_TITLE = "artemisApp.groupNotification.title.newExercisePost";

    public static final String NEW_LECTURE_POST_TITLE = "artemisApp.groupNotification.title.newLecturePost";

    public static final String NEW_COURSE_POST_TITLE = "artemisApp.groupNotification.title.newCoursePost";

    public static final String NEW_ANNOUNCEMENT_POST_TITLE = "artemisApp.groupNotification.title.newAnnouncementPost";

    public static final String NEW_REPLY_FOR_EXERCISE_POST_TITLE = "artemisApp.groupNotification.title.newReplyForExercisePost";

    public static final String NEW_REPLY_FOR_LECTURE_POST_TITLE = "artemisApp.groupNotification.title.newReplyForLecturePost";

    public static final String NEW_REPLY_FOR_COURSE_POST_TITLE = "artemisApp.groupNotification.title.newReplyForCoursePost";

    public static final String FILE_SUBMISSION_SUCCESSFUL_TITLE = "artemisApp.singleUserNotification.title.fileSubmissionSuccessful";

    public static final String COURSE_ARCHIVE_STARTED_TITLE = "artemisApp.groupNotification.title.courseArchiveStarted";

    public static final String COURSE_ARCHIVE_FINISHED_TITLE = "artemisApp.groupNotification.title.courseArchiveFinished";

    public static final String COURSE_ARCHIVE_FAILED_TITLE = "artemisApp.groupNotification.title.courseArchiveFailed";

    public static final String EXAM_ARCHIVE_STARTED_TITLE = "artemisApp.groupNotification.title.examArchiveStarted";

    public static final String EXAM_ARCHIVE_FINISHED_TITLE = "artemisApp.groupNotification.title.examArchiveFinished";

    public static final String EXAM_ARCHIVE_FAILED_TITLE = "artemisApp.groupNotification.title.examArchiveFailed";

    public static final String PROGRAMMING_TEST_CASES_CHANGED_TITLE = "artemisApp.groupNotification.title.programmingTestCasesChanged";

    public static final String NEW_MANUAL_FEEDBACK_REQUEST_TITLE = "artemisApp.groupNotification.title.newManualFeedbackRequest";

    public static final String NEW_PLAGIARISM_CASE_STUDENT_TITLE = "artemisApp.singleUserNotification.title.newPlagiarismCaseStudent";

    public static final String PLAGIARISM_CASE_VERDICT_STUDENT_TITLE = "artemisApp.singleUserNotification.title.plagiarismCaseVerdictStudent";

    public static final String TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE = "artemisApp.singleUserNotification.title.tutorialGroupRegistrationStudent";

    public static final String TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE = "artemisApp.singleUserNotification.title.tutorialGroupDeregistrationStudent";

    public static final String TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE = "artemisApp.singleUserNotification.title.tutorialGroupRegistrationTutor";

    public static final String TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE = "artemisApp.singleUserNotification.title.tutorialGroupDeregistrationTutor";

    public static final String TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE = "artemisApp.singleUserNotification.title.tutorialGroupMultipleRegistrationTutor";

    public static final String TUTORIAL_GROUP_DELETED_TITLE = "artemisApp.tutorialGroupNotification.title.tutorialGroupDeleted";

    public static final String TUTORIAL_GROUP_UPDATED_TITLE = "artemisApp.tutorialGroupNotification.title.tutorialGroupUpdated";

    public static final String TUTORIAL_GROUP_ASSIGNED_TITLE = "artemisApp.singleUserNotification.title.tutorialGroupAssigned";

    public static final String TUTORIAL_GROUP_UNASSIGNED_TITLE = "artemisApp.singleUserNotification.title.tutorialGroupUnassigned";

    // Texts
    public static final String LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TEXT = "artemisApp.groupNotification.text.liveExamExerciseUpdate";

    public static final String EXERCISE_SUBMISSION_ASSESSED_TEXT = "artemisApp.singleUserNotification.text.exerciseSubmissionAssessed";

    public static final String ATTACHMENT_CHANGE_TEXT = "artemisApp.groupNotification.text.attachmentChange";

    public static final String EXERCISE_RELEASED_TEXT = "artemisApp.groupNotification.text.exerciseReleased";

    public static final String EXERCISE_PRACTICE_TEXT = "artemisApp.groupNotification.text.exercisePractice";

    public static final String QUIZ_EXERCISE_STARTED_TEXT = "artemisApp.groupNotification.text.quizExerciseStarted";

    public static final String EXERCISE_UPDATED_TEXT = "artemisApp.groupNotification.text.exerciseUpdated";

    public static final String DUPLICATE_TEST_CASE_TEXT = "artemisApp.groupNotification.text.duplicateTestCase";

    public static final String ILLEGAL_SUBMISSION_TEXT = "artemisApp.groupNotification.text.illegalSubmission";

    public static final String NEW_EXERCISE_POST_TEXT = "artemisApp.groupNotification.text.newExercisePost";

    public static final String NEW_LECTURE_POST_TEXT = "artemisApp.groupNotification.text.newLecturePost";

    public static final String NEW_COURSE_POST_TEXT = "artemisApp.groupNotification.text.newCoursePost";

    public static final String NEW_ANNOUNCEMENT_POST_TEXT = "artemisApp.groupNotification.text.newAnnouncementPost";

    public static final String NEW_REPLY_FOR_EXERCISE_POST_GROUP_TEXT = "artemisApp.groupNotification.text.newReplyForExercisePost";

    public static final String NEW_REPLY_FOR_LECTURE_POST_GROUP_TEXT = "artemisApp.groupNotification.text.newReplyForLecturePost";

    public static final String NEW_REPLY_FOR_COURSE_POST_GROUP_TEXT = "artemisApp.groupNotification.text.newReplyForCoursePost";

    public static final String NEW_REPLY_FOR_EXERCISE_POST_SINGLE_TEXT = "artemisApp.singleUserNotification.text.newReplyForExercisePost";

    public static final String NEW_REPLY_FOR_LECTURE_POST_SINGLE_TEXT = "artemisApp.singleUserNotification.text.newReplyForLecturePost";

    public static final String NEW_REPLY_FOR_COURSE_POST_SINGLE_TEXT = "artemisApp.singleUserNotification.text.newReplyForCoursePost";

    public static final String FILE_SUBMISSION_SUCCESSFUL_TEXT = "artemisApp.singleUserNotification.text.fileSubmissionSuccessful";

    public static final String COURSE_ARCHIVE_STARTED_TEXT = "artemisApp.groupNotification.text.courseArchiveStarted";

    public static final String COURSE_ARCHIVE_FINISHED_WITH_ERRORS_TEXT = "artemisApp.groupNotification.text.courseArchiveFinishedWithErrors";

    public static final String COURSE_ARCHIVE_FINISHED_WITHOUT_ERRORS_TEXT = "artemisApp.groupNotification.text.courseArchiveFinishedWithoutErrors";

    public static final String COURSE_ARCHIVE_FAILED_TEXT = "artemisApp.groupNotification.text.courseArchiveFailed";

    public static final String EXAM_ARCHIVE_STARTED_TEXT = "artemisApp.groupNotification.text.examArchiveStarted";

    public static final String EXAM_ARCHIVE_FINISHED_WITH_ERRORS_TEXT = "artemisApp.groupNotification.text.examArchiveFinishedWithErrors";

    public static final String EXAM_ARCHIVE_FINISHED_WITHOUT_ERRORS_TEXT = "artemisApp.groupNotification.text.examArchiveFinishedWithoutErrors";

    public static final String EXAM_ARCHIVE_FAILED_TEXT = "artemisApp.groupNotification.text.examArchiveFailed";

    public static final String PROGRAMMING_TEST_CASES_CHANGED_TEXT = "artemisApp.groupNotification.text.programmingTestCasesChanged";

    public static final String NEW_MANUAL_FEEDBACK_REQUEST_TEXT = "artemisApp.groupNotification.text.newManualFeedbackRequest";

    public static final String NEW_PLAGIARISM_CASE_STUDENT_TEXT = "artemisApp.singleUserNotification.text.newPlagiarismCaseStudent";

    public static final String PLAGIARISM_CASE_VERDICT_STUDENT_TEXT = "artemisApp.singleUserNotification.text.plagiarismCaseVerdictStudent";

    public static final String TUTORIAL_GROUP_REGISTRATION_STUDENT_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupRegistrationStudent";

    public static final String TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupDeregistrationStudent";

    public static final String TUTORIAL_GROUP_REGISTRATION_TUTOR_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupRegistrationTutor";

    public static final String TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupDeregistrationTutor";

    public static final String TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupRegistrationMultipleTutor";

    public static final String TUTORIAL_GROUP_DELETED_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupDeleted";

    public static final String TUTORIAL_GROUP_UPDATED_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupUpdated";

    public static final String TUTORIAL_GROUP_ASSIGNED_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupAssigned";

    public static final String TUTORIAL_GROUP_UNASSIGNED_TEXT = "artemisApp.singleUserNotification.text.tutorialGroupUnassigned";

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
