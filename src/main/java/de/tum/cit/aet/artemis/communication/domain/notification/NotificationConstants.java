package de.tum.cit.aet.artemis.communication.domain.notification;

import static de.tum.cit.aet.artemis.communication.domain.NotificationType.ATTACHMENT_CHANGE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_ADD_USER_CHANNEL;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_ADD_USER_GROUP_CHAT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_CREATE_GROUP_CHAT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_CREATE_ONE_TO_ONE_CHAT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_DELETE_CHANNEL;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_NEW_MESSAGE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_NEW_REPLY_MESSAGE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_REMOVE_USER_CHANNEL;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_REMOVE_USER_GROUP_CHAT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_USER_MENTIONED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.COURSE_ARCHIVE_FAILED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.COURSE_ARCHIVE_FINISHED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.COURSE_ARCHIVE_STARTED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.DATA_EXPORT_CREATED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.DATA_EXPORT_FAILED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.DUPLICATE_TEST_CASE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXAM_ARCHIVE_FAILED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXAM_ARCHIVE_FINISHED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXAM_ARCHIVE_STARTED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_PRACTICE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_RELEASED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_SUBMISSION_ASSESSED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.EXERCISE_UPDATED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.FILE_SUBMISSION_SUCCESSFUL;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.ILLEGAL_SUBMISSION;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_ANNOUNCEMENT_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_COURSE_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_CPC_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_EXAM_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_EXERCISE_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_LECTURE_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_MANUAL_FEEDBACK_REQUEST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_REPLY_FOR_COURSE_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_REPLY_FOR_EXAM_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_REPLY_FOR_EXERCISE_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.NEW_REPLY_FOR_LECTURE_POST;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PLAGIARISM_CASE_VERDICT_STUDENT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PROGRAMMING_BUILD_RUN_UPDATE;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PROGRAMMING_REPOSITORY_LOCKS;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.PROGRAMMING_TEST_CASES_CHANGED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.QUIZ_EXERCISE_STARTED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_ASSIGNED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_DELETED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_DEREGISTRATION_STUDENT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_DEREGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_REGISTRATION_STUDENT;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_REGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_UNASSIGNED;
import static de.tum.cit.aet.artemis.communication.domain.NotificationType.TUTORIAL_GROUP_UPDATED;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

import de.tum.cit.aet.artemis.communication.domain.NotificationType;

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

    public static final String NEW_EXAM_POST_TITLE = "artemisApp.groupNotification.title.newExamPost";

    public static final String NEW_REPLY_FOR_EXERCISE_POST_TITLE = "artemisApp.groupNotification.title.newReplyForExercisePost";

    public static final String NEW_REPLY_FOR_LECTURE_POST_TITLE = "artemisApp.groupNotification.title.newReplyForLecturePost";

    public static final String NEW_REPLY_FOR_COURSE_POST_TITLE = "artemisApp.groupNotification.title.newReplyForCoursePost";

    public static final String NEW_REPLY_FOR_EXAM_POST_TITLE = "artemisApp.groupNotification.title.newReplyForExamPost";

    public static final String FILE_SUBMISSION_SUCCESSFUL_TITLE = "artemisApp.singleUserNotification.title.fileSubmissionSuccessful";

    public static final String DATA_EXPORT_CREATED_TITLE = "artemisApp.singleUserNotification.title.dataExportCreated";

    public static final String DATA_EXPORT_FAILED_TITLE = "artemisApp.singleUserNotification.title.dataExportFailed";

    public static final String COURSE_ARCHIVE_STARTED_TITLE = "artemisApp.groupNotification.title.courseArchiveStarted";

    public static final String COURSE_ARCHIVE_FINISHED_TITLE = "artemisApp.groupNotification.title.courseArchiveFinished";

    public static final String COURSE_ARCHIVE_FAILED_TITLE = "artemisApp.groupNotification.title.courseArchiveFailed";

    public static final String EXAM_ARCHIVE_STARTED_TITLE = "artemisApp.groupNotification.title.examArchiveStarted";

    public static final String EXAM_ARCHIVE_FINISHED_TITLE = "artemisApp.groupNotification.title.examArchiveFinished";

    public static final String EXAM_ARCHIVE_FAILED_TITLE = "artemisApp.groupNotification.title.examArchiveFailed";

    public static final String PROGRAMMING_TEST_CASES_CHANGED_TITLE = "artemisApp.groupNotification.title.programmingTestCasesChanged";

    public static final String NEW_MANUAL_FEEDBACK_REQUEST_TITLE = "artemisApp.groupNotification.title.newManualFeedbackRequest";

    public static final String PROGRAMMING_REPOSITORY_LOCKS_TITLE = "artemisApp.groupNotification.title.repositoryLocks";

    public static final String PROGRAMMING_BUILD_RUN_UPDATE_TITLE = "artemisApp.groupNotification.title.buildRun";

    public static final String NEW_PLAGIARISM_CASE_STUDENT_TITLE = "artemisApp.singleUserNotification.title.newPlagiarismCaseStudent";

    public static final String NEW_CPC_PLAGIARISM_CASE_STUDENT_TITLE = "artemisApp.singleUserNotification.title.newPlagiarismCaseStudentSignificantSimilarity";

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

    public static final String DATA_EXPORT_CREATED_TEXT = "artemisApp.singleUserNotification.text.dataExportCreated";

    public static final String DATA_EXPORT_FAILED_TEXT = "artemisApp.singleUserNotification.text.dataExportFailed";

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

    public static final String NEW_CPC_PLAGIARISM_CASE_STUDENT_TEXT = "artemisApp.singleUserNotification.text.newPlagiarismCaseStudentSignificantSimilarity";

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

    public static final String NEW_MESSAGE_TITLE = "artemisApp.conversationNotification.title.newMessage";

    public static final String NEW_MESSAGE_CHANNEL_TEXT = "artemisApp.conversationNotification.text.newMessageChannel";

    public static final String NEW_MESSAGE_GROUP_CHAT_TEXT = "artemisApp.conversationNotification.text.newMessageGroupChat";

    public static final String NEW_MESSAGE_DIRECT_TEXT = "artemisApp.conversationNotification.text.newMessageDirect";

    public static final String MESSAGE_REPLY_IN_CONVERSATION_TITLE = "artemisApp.singleUserNotification.title.messageReply";

    public static final String MESSAGE_REPLY_IN_CONVERSATION_TEXT = "artemisApp.singleUserNotification.text.messageReply";

    public static final String MESSAGE_REPLY_IN_CHANNEL_TEXT = "artemisApp.singleUserNotification.text.channelMessageReply";

    public static final String MENTIONED_IN_MESSAGE_TITLE = "artemisApp.singleUserNotification.title.mentionedInMessage";

    public static final String CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE = "artemisApp.singleUserNotification.title.createOneToOneChat";

    public static final String CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TEXT = "artemisApp.singleUserNotification.text.createOneToOneChat";

    public static final String CONVERSATION_CREATE_GROUP_CHAT_TITLE = "artemisApp.singleUserNotification.title.createGroupChat";

    public static final String CONVERSATION_CREATE_GROUP_CHAT_TEXT = "artemisApp.singleUserNotification.text.createGroupChat";

    public static final String CONVERSATION_ADD_USER_CHANNEL_TITLE = "artemisApp.singleUserNotification.title.addUserChannel";

    public static final String CONVERSATION_ADD_USER_CHANNEL_TEXT = "artemisApp.singleUserNotification.text.addUserChannel";

    public static final String CONVERSATION_ADD_USER_GROUP_CHAT_TITLE = "artemisApp.singleUserNotification.title.addUserGroupChat";

    public static final String CONVERSATION_ADD_USER_GROUP_CHAT_TEXT = "artemisApp.singleUserNotification.text.addUserGroupChat";

    public static final String CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE = "artemisApp.singleUserNotification.title.removeUserGroupChat";

    public static final String CONVERSATION_REMOVE_USER_GROUP_CHAT_TEXT = "artemisApp.singleUserNotification.text.removeUserGroupChat";

    public static final String CONVERSATION_REMOVE_USER_CHANNEL_TITLE = "artemisApp.singleUserNotification.title.removeUserChannel";

    public static final String CONVERSATION_REMOVE_USER_CHANNEL_TEXT = "artemisApp.singleUserNotification.text.removeUserChannel";

    public static final String CONVERSATION_DELETE_CHANNEL_TITLE = "artemisApp.singleUserNotification.title.deleteChannel";

    public static final String CONVERSATION_DELETE_CHANNEL_TEXT = "artemisApp.singleUserNotification.text.deleteChannel";

    // bidirectional map
    private static final BiMap<NotificationType, String> NOTIFICATION_TYPE_AND_TITLE_MAP = new ImmutableBiMap.Builder<NotificationType, String>()
            .put(EXERCISE_SUBMISSION_ASSESSED, EXERCISE_SUBMISSION_ASSESSED_TITLE).put(ATTACHMENT_CHANGE, ATTACHMENT_CHANGE_TITLE).put(EXERCISE_RELEASED, EXERCISE_RELEASED_TITLE)
            .put(EXERCISE_PRACTICE, EXERCISE_PRACTICE_TITLE).put(QUIZ_EXERCISE_STARTED, QUIZ_EXERCISE_STARTED_TITLE).put(EXERCISE_UPDATED, EXERCISE_UPDATED_TITLE)
            .put(DUPLICATE_TEST_CASE, DUPLICATE_TEST_CASE_TITLE).put(ILLEGAL_SUBMISSION, ILLEGAL_SUBMISSION_TITLE).put(NEW_EXERCISE_POST, NEW_EXERCISE_POST_TITLE)
            .put(NEW_LECTURE_POST, NEW_LECTURE_POST_TITLE).put(NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_EXERCISE_POST_TITLE)
            .put(NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_LECTURE_POST_TITLE).put(NEW_COURSE_POST, NEW_COURSE_POST_TITLE).put(NEW_EXAM_POST, NEW_EXAM_POST_TITLE)
            .put(NEW_REPLY_FOR_EXAM_POST, NEW_REPLY_FOR_EXAM_POST_TITLE).put(NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_COURSE_POST_TITLE)
            .put(FILE_SUBMISSION_SUCCESSFUL, FILE_SUBMISSION_SUCCESSFUL_TITLE).put(COURSE_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED_TITLE)
            .put(NEW_ANNOUNCEMENT_POST, NEW_ANNOUNCEMENT_POST_TITLE).put(COURSE_ARCHIVE_FINISHED, COURSE_ARCHIVE_FINISHED_TITLE)
            .put(COURSE_ARCHIVE_FAILED, COURSE_ARCHIVE_FAILED_TITLE).put(EXAM_ARCHIVE_STARTED, EXAM_ARCHIVE_STARTED_TITLE).put(EXAM_ARCHIVE_FAILED, EXAM_ARCHIVE_FAILED_TITLE)
            .put(EXAM_ARCHIVE_FINISHED, EXAM_ARCHIVE_FINISHED_TITLE).put(PROGRAMMING_TEST_CASES_CHANGED, PROGRAMMING_TEST_CASES_CHANGED_TITLE)
            .put(NEW_MANUAL_FEEDBACK_REQUEST, NEW_MANUAL_FEEDBACK_REQUEST_TITLE).put(NEW_PLAGIARISM_CASE_STUDENT, NEW_PLAGIARISM_CASE_STUDENT_TITLE)
            .put(NEW_CPC_PLAGIARISM_CASE_STUDENT, NEW_CPC_PLAGIARISM_CASE_STUDENT_TITLE).put(PLAGIARISM_CASE_VERDICT_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT_TITLE)
            .put(TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE)
            .put(TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE).put(TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE)
            .put(TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE).put(TUTORIAL_GROUP_DELETED, TUTORIAL_GROUP_DELETED_TITLE)
            .put(TUTORIAL_GROUP_UPDATED, TUTORIAL_GROUP_UPDATED_TITLE).put(TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE)
            .put(TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_ASSIGNED_TITLE).put(TUTORIAL_GROUP_UNASSIGNED, TUTORIAL_GROUP_UNASSIGNED_TITLE)
            .put(CONVERSATION_NEW_MESSAGE, NEW_MESSAGE_TITLE).put(CONVERSATION_NEW_REPLY_MESSAGE, MESSAGE_REPLY_IN_CONVERSATION_TITLE)
            .put(CONVERSATION_USER_MENTIONED, MENTIONED_IN_MESSAGE_TITLE).put(CONVERSATION_CREATE_ONE_TO_ONE_CHAT, CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE)
            .put(CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_CREATE_GROUP_CHAT_TITLE).put(CONVERSATION_ADD_USER_CHANNEL, CONVERSATION_ADD_USER_CHANNEL_TITLE)
            .put(CONVERSATION_ADD_USER_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT_TITLE).put(CONVERSATION_REMOVE_USER_GROUP_CHAT, CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE)
            .put(CONVERSATION_REMOVE_USER_CHANNEL, CONVERSATION_REMOVE_USER_CHANNEL_TITLE).put(CONVERSATION_DELETE_CHANNEL, CONVERSATION_DELETE_CHANNEL_TITLE)
            .put(DATA_EXPORT_CREATED, DATA_EXPORT_CREATED_TITLE).put(DATA_EXPORT_FAILED, DATA_EXPORT_FAILED_TITLE)
            .put(PROGRAMMING_REPOSITORY_LOCKS, PROGRAMMING_REPOSITORY_LOCKS_TITLE).put(PROGRAMMING_BUILD_RUN_UPDATE, PROGRAMMING_BUILD_RUN_UPDATE_TITLE).build();

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
